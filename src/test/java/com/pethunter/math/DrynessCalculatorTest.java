package com.pethunter.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Rates here are arbitrary fixtures, not game data.
 */
public class DrynessCalculatorTest
{
	private static final double DENOMINATOR = 5_000;
	private static final double P = 1.0 / DENOMINATOR;

	@Test
	public void zeroAttemptsIsCertainlyStillDry()
	{
		assertEquals(0.0, DrynessCalculator.logProbabilityStillDry(0, P), 0.0);
		assertEquals(1.0, DrynessCalculator.probabilityStillDry(0, P), 0.0);
		assertEquals(0.0, DrynessCalculator.expectedDrops(0, P), 0.0);
	}

	@Test
	public void oneAttempt()
	{
		assertEquals(1.0 - P, DrynessCalculator.probabilityStillDry(1, P), 1e-15);
		assertEquals(P, DrynessCalculator.expectedDrops(1, P), 1e-15);
	}

	@Test
	public void atTheDropRate()
	{
		// (1 - 1/5000)^5000 = 0.36784...; tends to 1/e as the rate gets rarer
		assertEquals(Math.pow(1.0 - P, DENOMINATOR), DrynessCalculator.probabilityStillDry(DENOMINATOR, P), 1e-12);
		assertEquals(0.3678, DrynessCalculator.probabilityStillDry(DENOMINATOR, P), 1e-4);
		assertEquals(1.0, DrynessCalculator.expectedDrops(DENOMINATOR, P), 1e-12);
	}

	@Test
	public void atFiveTimesTheDropRate()
	{
		// (1 - 1/5000)^25000 = 0.006730...; close to e^-5 = 0.006738
		assertEquals(Math.pow(1.0 - P, 5 * DENOMINATOR), DrynessCalculator.probabilityStillDry(5 * DENOMINATOR, P), 1e-12);
		assertEquals(0.00673, DrynessCalculator.probabilityStillDry(5 * DENOMINATOR, P), 1e-5);
		assertEquals(5.0, DrynessCalculator.expectedDrops(5 * DENOMINATOR, P), 1e-12);
	}

	@Test
	public void logSpaceStaysFiniteAndAccurateAtVeryLargeCounts()
	{
		double attempts = 1e12;
		double p = 1e-6;

		double log = DrynessCalculator.logProbabilityStillDry(attempts, p);

		// ln(1 - 1e-6) = -1.0000005000003333e-6, times 1e12
		assertEquals(-1.0000005000003333e6, log, 1e-3);
		assertTrue(Double.isFinite(log));
		// The linear probability underflows, but to a clean zero, never NaN
		assertEquals(0.0, DrynessCalculator.probabilityStillDry(attempts, p), 0.0);
	}

	@Test
	public void tinyRateKeepsPrecision()
	{
		// Naively 1 - 1e-17 == 1.0 in double arithmetic, which would report no progress at all
		assertEquals(-1e-7, DrynessCalculator.logProbabilityStillDry(1e10, 1e-17), 1e-20);
	}

	@Test
	public void certainRate()
	{
		assertEquals(Double.NEGATIVE_INFINITY, DrynessCalculator.logProbabilityStillDry(1, 1.0), 0.0);
		assertEquals(0.0, DrynessCalculator.probabilityStillDry(1, 1.0), 0.0);
		assertEquals(0.0, DrynessCalculator.logProbabilityStillDry(0, 1.0), 0.0);
	}

	@Test
	public void probabilityOfDenominator()
	{
		assertEquals(0.0002, DrynessCalculator.probabilityOf(5_000), 0.0);
		assertEquals(1.0, DrynessCalculator.probabilityOf(1), 0.0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsZeroProbability()
	{
		DrynessCalculator.logProbabilityStillDry(10, 0.0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNaNAttempts()
	{
		DrynessCalculator.logProbabilityStillDry(Double.NaN, P);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeAttempts()
	{
		DrynessCalculator.expectedDrops(-1, P);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsDenominatorBelowOne()
	{
		DrynessCalculator.probabilityOf(0.5);
	}
}
