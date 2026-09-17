package com.pethunter.ui;

import com.pethunter.data.ContributionRange;
import com.pethunter.data.PetSource;
import com.pethunter.math.Confidence;
import com.pethunter.math.DrynessResult;
import com.pethunter.math.PlayerProgress;
import com.pethunter.math.TieredValue;
import java.util.Locale;
import java.util.OptionalLong;
import javax.annotation.Nullable;

/**
 * Text for every number the panel shows. Each formatter takes the tier with the value, so a figure
 * cannot be printed without its marker: EXACT is plain, ESTIMATED is prefixed with "~", and an
 * upper bound is prefixed with "&lt;=". UNKNOWN never has a number.
 */
public final class DrynessFormat
{
	static final String UNKNOWN = "UNKNOWN";
	static final String NOT_APPLICABLE = "No drop chance";
	static final String OBTAINED = "Obtained";

	private DrynessFormat()
	{
	}

	/**
	 * The short status shown at the right of a collapsed row.
	 */
	public static String rowStatus(PetEntry entry)
	{
		if (entry.isObtained())
		{
			return OBTAINED;
		}
		DrynessResult dryness = entry.getDryness();
		switch (dryness.getStatus())
		{
			case FIGURE:
				return probability(dryness.getProbabilityStillDry().orElseThrow(), dryness.isUpperBound());
			case NOT_APPLICABLE:
				return NOT_APPLICABLE;
			default:
				return UNKNOWN;
		}
	}

	/**
	 * Tooltip and detail text: what the status means and what it rests on.
	 */
	public static String explanation(PetEntry entry)
	{
		if (entry.isObtained())
		{
			return "You have this pet.";
		}
		DrynessResult dryness = entry.getDryness();
		if (!dryness.hasFigure())
		{
			return dryness.getExplanation();
		}
		TieredValue pDry = dryness.getProbabilityStillDry().orElseThrow();
		String share = probability(pDry, dryness.isUpperBound());
		String multiple = multiple(dryness.getDropRateMultiple().orElseThrow());
		return share + " of players would still be without this pet at your count (" + multiple + " the drop rate). "
			+ tierSentence(pDry.getConfidence()) + " " + pDry.getAssumption();
	}

	/**
	 * P(still dry) as a percentage with its tier marker.
	 */
	public static String probability(TieredValue value, boolean upperBound)
	{
		double percent = value.getValue() * 100;
		String number;
		if (percent > 0 && percent < 0.1)
		{
			number = "<0.1%";
		}
		else if (percent > 99.9 && percent < 100)
		{
			number = ">99.9%";
		}
		else
		{
			number = String.format(Locale.ROOT, "%.1f%%", percent);
		}
		return (upperBound ? "<=" : "") + marker(value.getConfidence()) + number;
	}

	/**
	 * Attempts relative to the drop rate, e.g. "~2.3x".
	 */
	public static String multiple(TieredValue value)
	{
		return marker(value.getConfidence()) + String.format(Locale.ROOT, "%.2fx", value.getValue());
	}

	/**
	 * The per-attempt rate a source uses, or UNKNOWN if it is not verified. Rates are cited dataset
	 * facts rather than estimates, so they carry no tier marker.
	 */
	public static String rate(PetSource source)
	{
		switch (source.getRateModel())
		{
			case ONE_OFF:
				return "No drop chance";
			case SKILL_LEVEL_SCALED:
				return source.isVerified() && source.getBaseChance() != null
					? "1/(" + integer(source.getBaseChance()) + " - level x 25)"
					: UNKNOWN;
			case CONTRIBUTION_SCALED:
				ContributionRange range = source.getContributionRange();
				return source.isVerified() && range != null
					? "1/" + integer(range.getCommonestDenominator()) + " to 1/" + integer(range.getRarestDenominator())
					: UNKNOWN;
			default:
				return source.isVerified() && source.getFlatRate() != null ? "1/" + integer(source.getFlatRate()) : UNKNOWN;
		}
	}

	/**
	 * The attempt count recorded for a source, labelled as exact because it was read from the game,
	 * or null when the source has no counter or nothing has been recorded.
	 */
	@Nullable
	public static String counter(PetSource source, PlayerProgress progress)
	{
		if (source.getCounterKey() == null)
		{
			return null;
		}
		OptionalLong value = progress.getCounter(source.getCounterKey());
		if (value.isEmpty())
		{
			return null;
		}
		return "Count: " + integer(value.getAsLong()) + " (exact, from the game)";
	}

	public static String rateModelDescription(PetSource source)
	{
		switch (source.getRateModel())
		{
			case FLAT_PER_KILL:
				return "per kill";
			case FLAT_PER_ROLL:
				return "per reward roll";
			case SKILL_LEVEL_SCALED:
				return "per action, improves with level";
			case STATIC_IGNORES_FORMULA:
				return "per action, fixed at every level";
			case CONTRIBUTION_SCALED:
				return "varies with contribution";
			case UNIQUE_CONDITIONAL:
				return "per unique drop";
			default:
				return "guaranteed";
		}
	}

	static String marker(Confidence confidence)
	{
		return confidence == Confidence.ESTIMATED ? "~" : "";
	}

	static String tierSentence(Confidence confidence)
	{
		return confidence == Confidence.EXACT
			? "Exact: based on a count read from the game."
			: "Estimate:";
	}

	static String skillName(String skill)
	{
		if (skill == null || skill.isEmpty())
		{
			return "";
		}
		return skill.charAt(0) + skill.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String integer(long value)
	{
		return String.format(Locale.ROOT, "%,d", value);
	}
}
