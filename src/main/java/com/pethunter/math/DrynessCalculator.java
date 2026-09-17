package com.pethunter.math;

/**
 * Binomial dryness for a fixed per-attempt probability, computed in log space so very large
 * attempt counts and very rare rates keep their precision. See docs/DESIGN.md section 4.1.
 */
public final class DrynessCalculator
{
	private DrynessCalculator()
	{
	}

	/**
	 * @return ln P(still dry) = attempts * ln(1 - p). Zero attempts gives 0 (certainly still dry).
	 * A rate of exactly 1 with at least one attempt gives negative infinity.
	 */
	public static double logProbabilityStillDry(double attempts, double probability)
	{
		requireAttempts(attempts);
		requireProbability(probability);
		if (attempts == 0)
		{
			return 0.0;
		}
		if (probability == 1.0)
		{
			return Double.NEGATIVE_INFINITY;
		}
		// log1p keeps precision where 1 - p rounds to 1 in double arithmetic
		return attempts * Math.log1p(-probability);
	}

	/**
	 * @return (1 - p)^attempts, the share of players still without the drop at this count
	 */
	public static double probabilityStillDry(double attempts, double probability)
	{
		return Math.exp(logProbabilityStillDry(attempts, probability));
	}

	/**
	 * @return attempts * p, the "times the drop rate" figure (attempts / expected attempts)
	 */
	public static double expectedDrops(double attempts, double probability)
	{
		requireAttempts(attempts);
		requireProbability(probability);
		return attempts * probability;
	}

	/**
	 * @return 1 / denominator for a "1 in N" rate
	 */
	public static double probabilityOf(double denominator)
	{
		if (!(denominator >= 1.0) || Double.isInfinite(denominator))
		{
			throw new IllegalArgumentException("rate denominator must be finite and at least 1: " + denominator);
		}
		return 1.0 / denominator;
	}

	private static void requireAttempts(double attempts)
	{
		if (!(attempts >= 0) || Double.isInfinite(attempts))
		{
			throw new IllegalArgumentException("attempts must be finite and non-negative: " + attempts);
		}
	}

	private static void requireProbability(double probability)
	{
		if (!(probability > 0.0 && probability <= 1.0))
		{
			throw new IllegalArgumentException("probability must be in (0, 1]: " + probability);
		}
	}
}
