package com.pethunter.math;

import com.pethunter.data.ContributionRange;
import com.pethunter.data.HuntMethod;
import com.pethunter.data.Pet;
import com.pethunter.data.PetSource;
import com.pethunter.data.RateModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import javax.annotation.Nullable;

/**
 * Turns dataset entries plus a {@link PlayerProgress} into tiered dryness results, applying each
 * {@link RateModel}'s rules. Never invents a number: anything unverified or uncounted comes back
 * UNKNOWN with the reason.
 */
public final class SourceEstimator
{
	private SourceEstimator()
	{
	}

	/**
	 * Estimates a whole pet.
	 *
	 * <p>Sources derived from skill XP all draw on the same XP, so at most one of them is used: the
	 * player cannot have earned the same XP from two activities. {@code assumedSourceId} picks it.
	 * With no choice and exactly one XP-derived source, that one is used; with no choice and several,
	 * the XP-derived part is UNKNOWN until the player picks a method. Every other source is estimated
	 * independently and combined.
	 *
	 * @param assumedSourceId the XP-derived source the player trained with, or null
	 */
	public static DrynessResult estimatePet(Pet pet, PlayerProgress progress, @Nullable String assumedSourceId)
	{
		List<DrynessResult> results = new ArrayList<>();
		List<PetSource> xpSources = new ArrayList<>();
		for (PetSource source : pet.getSources())
		{
			if (isXpDerived(source.getRateModel()))
			{
				xpSources.add(source);
			}
			else
			{
				results.add(estimate(pet, source, progress));
			}
		}

		if (!xpSources.isEmpty())
		{
			PetSource chosen = xpSources.stream().filter(s -> s.getId().equals(assumedSourceId)).findFirst().orElse(null);
			results.add(chosen != null ? estimate(pet, chosen, progress) : withoutAChosenMethod(pet, xpSources, progress));
		}

		return DrynessCombiner.combine(results);
	}

	/**
	 * Estimates a single source in isolation.
	 */
	public static DrynessResult estimate(Pet pet, PetSource source, PlayerProgress progress)
	{
		try
		{
			switch (source.getRateModel())
			{
				case ONE_OFF:
					return DrynessResult.notApplicable(source.getLabel() + ": obtained without a drop chance, so there is no dryness.");
				case FLAT_PER_KILL:
					return estimateCounted(source, progress, verifiedRate(source, source.getFlatRate()), "kills", "");
				case FLAT_PER_ROLL:
					return estimateCounted(source, progress, verifiedRate(source, source.getFlatRate()), "reward rolls", "");
				case UNIQUE_CONDITIONAL:
					return estimateCounted(source, progress, verifiedRate(source, source.getFlatRate()), "unique drops",
						" Rolled only when a unique is received, not on every completion.");
				case CONTRIBUTION_SCALED:
					return estimateContribution(source, progress);
				case SKILL_LEVEL_SCALED:
				case STATIC_IGNORES_FORMULA:
					return estimateFromXp(pet, source, progress);
				default:
					return DrynessResult.unknown(source.getLabel() + ": unsupported rate model " + source.getRateModel() + ".");
			}
		}
		catch (IllegalArgumentException e)
		{
			// Only reachable with data the build validator should have rejected
			return DrynessResult.unknown(source.getLabel() + ": dataset values for this source are invalid.");
		}
	}

	/**
	 * The "1 in N" per-attempt rate for a source at a given level, for method comparison.
	 * Empty when the rate is unverified or the source has no rate.
	 *
	 * @param level unboosted level; only SKILL_LEVEL_SCALED sources depend on it
	 */
	public static OptionalDouble perAttemptDenominator(PetSource source, int level, boolean at200mXp)
	{
		if (!source.isVerified())
		{
			return OptionalDouble.empty();
		}
		switch (source.getRateModel())
		{
			case FLAT_PER_KILL:
			case FLAT_PER_ROLL:
			case UNIQUE_CONDITIONAL:
			case STATIC_IGNORES_FORMULA:
				return source.getFlatRate() == null ? OptionalDouble.empty() : OptionalDouble.of(source.getFlatRate());
			case SKILL_LEVEL_SCALED:
				return source.getBaseChance() == null ? OptionalDouble.empty()
					: OptionalDouble.of(LevelBandIntegrator.denominatorAt(source.getBaseChance(), level, at200mXp));
			case CONTRIBUTION_SCALED:
				return source.getContributionRange() == null ? OptionalDouble.empty()
					: OptionalDouble.of(source.getContributionRange().getRarestDenominator());
			default:
				return OptionalDouble.empty();
		}
	}

	/**
	 * Estimates from total skill XP when the player has not said which activity it came from.
	 *
	 * <p>No mix can be inferred from the client, and assuming one would invent a number. Instead the
	 * whole XP is evaluated at the worst rate of any usable activity for this pet, which cannot claim
	 * the player is drier than they are, and the assumption quotes the best-case figure as well.
	 */
	private static DrynessResult withoutAChosenMethod(Pet pet, List<PetSource> xpSources, PlayerProgress progress)
	{
		if (xpSources.size() == 1)
		{
			return estimate(pet, xpSources.get(0), progress);
		}

		List<DrynessResult> figures = new ArrayList<>();
		List<PetSource> figureSources = new ArrayList<>();
		for (PetSource source : xpSources)
		{
			DrynessResult result = estimate(pet, source, progress);
			if (result.hasFigure())
			{
				figures.add(result);
				figureSources.add(source);
			}
		}
		if (figures.isEmpty())
		{
			// Nothing usable: report the first source's reason, which says what the dataset is missing
			return estimate(pet, xpSources.get(0), progress);
		}
		if (figures.size() == 1)
		{
			return figures.get(0);
		}

		int worst = 0;
		int best = 0;
		for (int i = 1; i < figures.size(); i++)
		{
			// The worst rate leaves the most players still without the pet
			worst = figures.get(i).rawLogProbabilityStillDry() > figures.get(worst).rawLogProbabilityStillDry() ? i : worst;
			best = figures.get(i).rawLogProbabilityStillDry() < figures.get(best).rawLogProbabilityStillDry() ? i : best;
		}

		DrynessResult worstResult = figures.get(worst);
		String assumption = "No method chosen, so this assumes all " + formatXp(progress.getXp(pet.getSkill()).orElse(0))
			+ " " + skillName(pet.getSkill()) + " XP came from " + figureSources.get(worst).getLabel()
			+ ", the worst rate for this pet, so you are at least this dry. All of it from "
			+ figureSources.get(best).getLabel() + " instead would leave "
			+ formatPercent(Math.exp(figures.get(best).rawLogProbabilityStillDry()))
			+ " still without it. Choose the method you trained with for a sharper estimate.";
		return DrynessResult.figure(worstResult.rawLogProbabilityStillDry(), worstResult.rawExpectedDrops(),
			worstResult.getAttempts().map(TieredValue::getValue).orElse(null), Confidence.ESTIMATED, assumption,
			"xp:" + pet.getSkill(), 0, true);
	}

	static String formatPercent(double fraction)
	{
		double percent = fraction * 100;
		if (percent > 0 && percent < 0.1)
		{
			return "<0.1%";
		}
		return String.format(Locale.ROOT, "%.1f%%", percent);
	}

	/**
	 * The XP-derived sources of a pet, which the player picks between because they all consume the
	 * same skill XP.
	 */
	public static List<PetSource> xpDerivedSources(Pet pet)
	{
		return pet.getSources().stream().filter(s -> isXpDerived(s.getRateModel())).collect(java.util.stream.Collectors.toList());
	}

	static boolean isXpDerived(RateModel model)
	{
		return model == RateModel.SKILL_LEVEL_SCALED || model == RateModel.STATIC_IGNORES_FORMULA;
	}

	private static DrynessResult estimateCounted(PetSource source, PlayerProgress progress,
		@Nullable Double denominator, String unit, String suffix)
	{
		if (denominator == null)
		{
			return unverifiedRate(source);
		}
		Optional<Count> count = count(source, progress);
		if (count.isEmpty())
		{
			return noCount(source, unit);
		}

		double p = DrynessCalculator.probabilityOf(denominator);
		Count c = count.get();
		String assumption = c.describe(unit) + " at 1/" + formatRate(denominator) + " (" + source.getLabel() + ")." + suffix
			+ (c.confidence == Confidence.EXACT ? countWarning(source) : "");
		return DrynessResult.figure(
			DrynessCalculator.logProbabilityStillDry(c.value, p),
			DrynessCalculator.expectedDrops(c.value, p),
			(double) c.value, c.confidence, assumption, pool(source), 0);
	}

	private static DrynessResult estimateContribution(PetSource source, PlayerProgress progress)
	{
		ContributionRange range = source.isVerified() ? source.getContributionRange() : null;
		if (range == null)
		{
			return unverifiedRate(source);
		}
		Optional<Count> count = count(source, progress);
		if (count.isEmpty())
		{
			return noCount(source, "attempts");
		}

		int rarest = range.getRarestDenominator();
		double p = DrynessCalculator.probabilityOf(rarest);
		Count c = count.get();
		String assumption = c.describe("attempts") + " at 1/" + formatInt(rarest) + " (" + source.getLabel() + ")."
			+ " The rate varies with contribution between 1/" + formatInt(range.getCommonestDenominator())
			+ " and 1/" + formatInt(rarest) + "; the rarest end is used so this never overstates dryness."
			+ (c.confidence == Confidence.EXACT ? countWarning(source) : "");
		return DrynessResult.figure(
			DrynessCalculator.logProbabilityStillDry(c.value, p),
			DrynessCalculator.expectedDrops(c.value, p),
			(double) c.value, c.confidence, assumption, pool(source), 0);
	}

	private static DrynessResult estimateFromXp(Pet pet, PetSource source, PlayerProgress progress)
	{
		boolean levelScaled = source.getRateModel() == RateModel.SKILL_LEVEL_SCALED;
		Double rate = verifiedRate(source, levelScaled
			? (source.getBaseChance() == null ? null : source.getBaseChance().doubleValue())
			: source.getFlatRate());
		if (rate == null)
		{
			return unverifiedRate(source);
		}

		String skill = pet.getSkill();
		if (skill == null)
		{
			return DrynessResult.unknown(source.getLabel() + ": the dataset does not name a skill for this pet.");
		}

		Optional<HuntMethod> method = pet.getMethodFor(source.getId());
		Double xpPerAction = method.filter(HuntMethod::isVerified).map(HuntMethod::getXpPerAction).orElse(null);
		if (xpPerAction == null)
		{
			return DrynessResult.unknown(source.getLabel() + ": XP per action is not yet verified in the dataset, "
				+ "so XP cannot be converted into attempts.");
		}

		OptionalLong xp = progress.getXp(skill);
		if (xp.isEmpty())
		{
			return DrynessResult.unknown(source.getLabel() + ": " + skillName(skill) + " XP is not available yet. Log in to read it.");
		}

		Integer minLevel = source.getMinLevel();
		long countedXp = minLevel == null ? xp.getAsLong() : Math.max(0, xp.getAsLong() - XpTable.xpForLevel(minLevel));
		String xpClause = minLevel == null
			? "all " + formatXp(countedXp) + " " + skillName(skill) + " XP"
			: "all " + formatXp(countedXp) + " " + skillName(skill) + " XP earned from level " + minLevel + " onward";

		double logPDry;
		double expectedDrops;
		double actions;
		String rateClause;
		if (levelScaled)
		{
			LevelBandIntegrator.Result r = LevelBandIntegrator.integrate(xp.getAsLong(), source.getBaseChance(), xpPerAction, minLevel, 0);
			logPDry = r.getLogProbabilityStillDry();
			expectedDrops = r.getExpectedDrops();
			actions = r.getActions();
			rateClause = ", with each level's XP evaluated at the rate for that level.";
		}
		else
		{
			XpTable.requireValidXp(xp.getAsLong());
			actions = countedXp / xpPerAction;
			double p = DrynessCalculator.probabilityOf(rate);
			logPDry = DrynessCalculator.logProbabilityStillDry(actions, p);
			expectedDrops = DrynessCalculator.expectedDrops(actions, p);
			rateClause = ", at a fixed 1/" + formatRate(rate) + " per action that does not change with level.";
		}

		String assumption = "Assumes " + xpClause + " came from " + source.getLabel() + " at "
			+ formatDecimal(xpPerAction) + " XP each (~" + formatInt(Math.round(actions)) + " actions)" + rateClause;
		return DrynessResult.figure(logPDry, expectedDrops, actions, Confidence.ESTIMATED, assumption,
			"xp:" + skill, 0);
	}

	private static final class Count
	{
		final long value;
		final Confidence confidence;

		Count(long value, Confidence confidence)
		{
			this.value = value;
			this.confidence = confidence;
		}

		String describe(String unit)
		{
			return confidence == Confidence.EXACT
				? formatInt(value) + " " + unit
				: "Your manually entered count of " + formatInt(value) + " " + unit;
		}
	}

	/**
	 * Whether the player can enter their own attempt count for a source: it needs a per-attempt
	 * rate model, and either no game counter or a game counter with a warning that it can include
	 * attempts that never rolled the pet.
	 */
	public static boolean acceptsManualCount(PetSource source)
	{
		switch (source.getRateModel())
		{
			case FLAT_PER_KILL:
			case FLAT_PER_ROLL:
			case UNIQUE_CONDITIONAL:
			case CONTRIBUTION_SCALED:
				return source.getCounterKey() == null || source.getCountWarning() != null;
			default:
				return false;
		}
	}

	/**
	 * A counter read from the game normally wins over a manual entry, because live game state is
	 * the source of truth. When the counter carries a warning that it overcounts, the player's own
	 * count wins instead: they know how many attempts really rolled the pet.
	 */
	private static Optional<Count> count(PetSource source, PlayerProgress progress)
	{
		OptionalLong counter = progress.getCounter(source.getCounterKey());
		OptionalLong manual = progress.getManualCount(source.getId());
		boolean manualValid = manual.isPresent() && manual.getAsLong() >= 0;
		if (manualValid && source.getCountWarning() != null)
		{
			return Optional.of(new Count(manual.getAsLong(), Confidence.ESTIMATED));
		}
		if (counter.isPresent() && counter.getAsLong() >= 0)
		{
			return Optional.of(new Count(counter.getAsLong(), Confidence.EXACT));
		}
		if (manualValid)
		{
			return Optional.of(new Count(manual.getAsLong(), Confidence.ESTIMATED));
		}
		return Optional.empty();
	}

	/**
	 * Manual and automatic counts for the same counter describe the same attempts, so they share a pool.
	 */
	private static String pool(PetSource source)
	{
		return source.getCounterKey() != null ? "counter:" + source.getCounterKey() : "source:" + source.getId();
	}

	private static String countWarning(PetSource source)
	{
		return source.getCountWarning() == null ? "" : " Warning: " + source.getCountWarning();
	}

	@Nullable
	private static Double verifiedRate(PetSource source, @Nullable Double value)
	{
		return source.isVerified() ? value : null;
	}

	/**
	 * A rate denominator as written in the dataset: whole numbers with grouping, decimals as given.
	 */
	public static String formatRate(double denominator)
	{
		return denominator == Math.rint(denominator) ? formatInt((long) denominator) : formatDecimal(denominator);
	}

	private static DrynessResult unverifiedRate(PetSource source)
	{
		return DrynessResult.unknown(source.getLabel() + ": drop rate not yet verified in the dataset.");
	}

	private static DrynessResult noCount(PetSource source, String unit)
	{
		if (source.getCounterKey() == null && source.getCountWarning() != null)
		{
			// The log has a count, but it cannot stand in for this source's rolls
			return DrynessResult.unknown(source.getLabel() + ": " + source.getCountWarning() + " You can enter your own count.");
		}
		if (source.getCounterKey() == null)
		{
			return DrynessResult.unknown(source.getLabel() + ": the game has no " + unit
				+ " counter the plugin can read. You can enter your own count.");
		}
		return DrynessResult.unknown(source.getLabel() + ": no " + unit
			+ " count yet. Open its collection log page, or enter a count manually.");
	}

	static String skillName(@Nullable String skill)
	{
		if (skill == null || skill.isEmpty())
		{
			return "this skill";
		}
		return skill.charAt(0) + skill.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String formatXp(long xp)
	{
		if (xp >= 1_000_000)
		{
			return String.format(Locale.ROOT, "%.1fm", xp / 1_000_000.0);
		}
		if (xp >= 10_000)
		{
			return String.format(Locale.ROOT, "%.1fk", xp / 1_000.0);
		}
		return formatInt(xp);
	}

	private static String formatInt(long value)
	{
		return String.format(Locale.ROOT, "%,d", value);
	}

	private static String formatDecimal(double value)
	{
		return value == Math.rint(value)
			? formatInt((long) value)
			: String.format(Locale.ROOT, "%,.2f", value).replaceAll("0+$", "");
	}
}
