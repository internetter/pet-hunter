package com.pethunter.math;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Combines independent sources for one pet: P(still dry overall) is the product of each source's
 * P(still dry). See docs/DESIGN.md section 4.3.
 *
 * <p>Sources without a figure are left out of the product. Leaving a factor of at most 1 out can
 * only raise the product, so the combined P(still dry) becomes an upper bound: the player is at
 * least as dry as shown. The result says so.
 */
public final class DrynessCombiner
{
	private DrynessCombiner()
	{
	}

	/**
	 * @throws IllegalArgumentException if two figures draw on the same attempt pool, which would
	 *                                  count the same attempts twice
	 */
	public static DrynessResult combine(List<DrynessResult> results)
	{
		List<DrynessResult> figures = new ArrayList<>();
		List<DrynessResult> unknowns = new ArrayList<>();
		List<DrynessResult> notApplicable = new ArrayList<>();
		for (DrynessResult result : results)
		{
			switch (result.getStatus())
			{
				case FIGURE:
					figures.add(result);
					break;
				case UNKNOWN:
					unknowns.add(result);
					break;
				default:
					notApplicable.add(result);
			}
		}

		int unknownCount = unknowns.stream().mapToInt(DrynessResult::getUnknownSourceCount).sum();

		if (figures.isEmpty())
		{
			if (!unknowns.isEmpty())
			{
				return DrynessResult.unknown(joinExplanations(unknowns), unknownCount);
			}
			if (!notApplicable.isEmpty())
			{
				return DrynessResult.notApplicable(joinExplanations(notApplicable));
			}
			return DrynessResult.unknown("No usable sources for this pet in the dataset yet.");
		}

		Set<String> pools = new HashSet<>();
		for (DrynessResult figure : figures)
		{
			String pool = figure.getAttemptPool().orElse(null);
			if (pool != null && !pools.add(pool))
			{
				throw new IllegalArgumentException("Two sources draw on the same attempts (" + pool
					+ "); combining them would count those attempts twice");
			}
		}

		if (figures.size() == 1 && unknowns.isEmpty())
		{
			return figures.get(0);
		}

		double logPDry = 0;
		double expectedDrops = 0;
		Confidence confidence = Confidence.EXACT;
		for (DrynessResult figure : figures)
		{
			logPDry += figure.rawLogProbabilityStillDry();
			expectedDrops += figure.rawExpectedDrops();
			confidence = Confidence.weakest(confidence, figure.getConfidence().orElseThrow());
			unknownCount += figure.getUnknownSourceCount();
		}

		String explanation = joinExplanations(figures);
		if (unknownCount > 0)
		{
			int total = figures.size() + unknownCount;
			explanation += " Upper bound: " + unknownCount + " of " + total + " sources could not be counted ("
				+ joinExplanations(unknowns) + "), so you are at least this dry.";
		}

		Double attempts = figures.size() == 1 ? figures.get(0).getAttempts().map(TieredValue::getValue).orElse(null) : null;
		String pool = figures.size() == 1 ? figures.get(0).getAttemptPool().orElse(null) : null;
		return DrynessResult.figure(logPDry, expectedDrops, attempts, confidence, explanation, pool, unknownCount);
	}

	private static String joinExplanations(List<DrynessResult> results)
	{
		return results.stream().map(DrynessResult::getExplanation).collect(Collectors.joining(" "));
	}
}
