package com.pethunter.math;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Integrates a level-scaled skilling pet chance over a player's XP history, one level band at a
 * time, so XP earned at low levels is evaluated at the worse rate that applied then.
 * See docs/DESIGN.md section 4.2.
 *
 * <p>The rate at level L is 1 / (baseChance - L * 25), with L the unboosted level capped at 99.
 * At 200m XP the level-99 denominator is divided by 15.
 */
public final class LevelBandIntegrator
{
	static final int LEVEL_MULTIPLIER = 25;
	static final double MAX_XP_DIVISOR = 15.0;

	private LevelBandIntegrator()
	{
	}

	@Value
	public static class Band
	{
		/** Level the band was evaluated at. */
		int level;
		/** True for the terminal band of actions performed at 200m XP. */
		boolean at200mXp;
		/** XP earned in this band; zero for the 200m band, where XP no longer accrues. */
		double xp;
		double actions;
		double denominator;
	}

	@Value
	public static class Result
	{
		double logProbabilityStillDry;
		double expectedDrops;
		double actions;
		List<Band> bands;
	}

	/**
	 * The per-action rate denominator for a level-scaled source.
	 *
	 * @param level unboosted level; values above 99 are clamped to 99 because the rate stops
	 *              improving there
	 * @param at200mXp whether the player has 200m XP in the skill
	 */
	public static double denominatorAt(int baseChance, int level, boolean at200mXp)
	{
		if (level < XpTable.MIN_LEVEL)
		{
			throw new IllegalArgumentException("level must be at least 1: " + level);
		}
		int clamped = Math.min(level, XpTable.MAX_LEVEL);
		double denominator = baseChance - clamped * LEVEL_MULTIPLIER;
		if (at200mXp)
		{
			// The multiplier applies to the final denominator, not to baseChance
			denominator /= MAX_XP_DIVISOR;
		}
		if (!(denominator >= 1.0))
		{
			throw new IllegalArgumentException("baseChance " + baseChance + " gives a rate denominator below 1 at level "
				+ clamped + (at200mXp ? " with 200m XP" : ""));
		}
		return denominator;
	}

	/**
	 * @param xp            current XP in the skill, 0..200m
	 * @param baseChance    B in 1 / (B - level * 25)
	 * @param xpPerAction   XP per action under the assumed method
	 * @param minLevel      level below which this source produced no rolls, or null
	 * @param actionsAt200m actions performed after reaching 200m XP. The client cannot observe
	 *                      these retrospectively because XP stops at 200m, so pass 0 unless the
	 *                      count comes from somewhere else
	 */
	public static Result integrate(long xp, int baseChance, double xpPerAction, @Nullable Integer minLevel,
		double actionsAt200m)
	{
		XpTable.requireValidXp(xp);
		if (!(xpPerAction > 0) || Double.isInfinite(xpPerAction))
		{
			throw new IllegalArgumentException("xpPerAction must be positive and finite: " + xpPerAction);
		}
		if (minLevel != null && (minLevel < XpTable.MIN_LEVEL || minLevel > XpTable.MAX_LEVEL))
		{
			throw new IllegalArgumentException("minLevel must be in 1..99: " + minLevel);
		}
		if (!(actionsAt200m >= 0) || Double.isInfinite(actionsAt200m))
		{
			throw new IllegalArgumentException("actionsAt200m must be finite and non-negative: " + actionsAt200m);
		}
		if (actionsAt200m > 0 && xp < XpTable.MAX_XP)
		{
			throw new IllegalArgumentException("actionsAt200m requires 200m XP");
		}
		// The level-99 denominator is the smallest, so this rejects a bad baseChance even when the
		// bands that would expose it happen to hold no XP
		denominatorAt(baseChance, XpTable.MAX_LEVEL, false);

		int startLevel = minLevel == null ? XpTable.MIN_LEVEL : minLevel;
		int currentLevel = XpTable.levelForXp(xp);

		List<Band> bands = new ArrayList<>();
		double logPDry = 0;
		double expectedDrops = 0;
		double totalActions = 0;

		for (int level = startLevel; level <= currentLevel; level++)
		{
			long bandStart = XpTable.xpForLevel(level);
			// Level 99 has no next threshold: its band runs all the way to current XP
			long bandEnd = level == XpTable.MAX_LEVEL ? xp : Math.min(xp, XpTable.xpForLevel(level + 1));
			long xpInBand = bandEnd - bandStart;
			if (xpInBand <= 0)
			{
				continue;
			}

			double actions = xpInBand / xpPerAction;
			double denominator = denominatorAt(baseChance, level, false);
			double p = DrynessCalculator.probabilityOf(denominator);
			logPDry += DrynessCalculator.logProbabilityStillDry(actions, p);
			expectedDrops += DrynessCalculator.expectedDrops(actions, p);
			totalActions += actions;
			bands.add(new Band(level, false, xpInBand, actions, denominator));
		}

		if (actionsAt200m > 0)
		{
			double denominator = denominatorAt(baseChance, XpTable.MAX_LEVEL, true);
			double p = DrynessCalculator.probabilityOf(denominator);
			logPDry += DrynessCalculator.logProbabilityStillDry(actionsAt200m, p);
			expectedDrops += DrynessCalculator.expectedDrops(actionsAt200m, p);
			totalActions += actionsAt200m;
			bands.add(new Band(XpTable.MAX_LEVEL, true, 0, actionsAt200m, denominator));
		}

		return new Result(logPDry, expectedDrops, totalActions, List.copyOf(bands));
	}
}
