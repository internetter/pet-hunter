package com.pethunter.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Every baseChance and xpPerAction in this file is an arbitrary fixture chosen to make the
 * arithmetic checkable. None of them is a real game rate.
 */
public class LevelBandIntegratorTest
{
	private static final double EPS = 1e-12;

	/**
	 * Hand-computed two-band example.
	 *
	 * <pre>
	 * Fixture: baseChance B = 3000, xpPerAction = 1, current XP = 150, no minLevel.
	 *
	 * XP table: level 1 starts at 0 XP, level 2 at 83 XP, level 3 at 174 XP.
	 * 150 XP is level 2, so there are two bands:
	 *
	 *   Band L=1: XP 0..83    -> 83 XP -> 83 actions
	 *             denominator = 3000 - 1*25 = 2975, rate 1/2975
	 *   Band L=2: XP 83..150  -> 67 XP -> 67 actions
	 *             denominator = 3000 - 2*25 = 2950, rate 1/2950
	 *
	 * P(still dry) = (2974/2975)^83 * (2949/2950)^67
	 *              = 0.972481866779 * 0.977540344581
	 *              = 0.950640259150
	 *
	 * In log space, as the integrator computes it:
	 *   83 * ln(1 - 1/2975) = 83 * -3.361909596297518e-4 = -2.790384964926940e-2
	 *   67 * ln(1 - 1/2950) = 67 * -3.390405185892658e-4 = -2.271571474548081e-2
	 *   sum = -5.061956439475021e-2, exp(sum) = 0.950640259150
	 *
	 * Expected drops ("times the drop rate") = 83/2975 + 67/2950
	 *                                        = 0.027899159664 + 0.022711864407
	 *                                        = 0.050611024071
	 *
	 * For contrast, the naive approach of evaluating all 150 actions at the current level
	 * gives (2949/2950)^150 = 0.950415446583, i.e. it overstates dryness.
	 * </pre>
	 */
	@Test
	public void handComputedTwoBandExample()
	{
		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(150, 3000, 1.0, null, 0);

		assertEquals(2, result.getBands().size());
		LevelBandIntegrator.Band band1 = result.getBands().get(0);
		assertEquals(1, band1.getLevel());
		assertEquals(83.0, band1.getActions(), 0.0);
		assertEquals(2975.0, band1.getDenominator(), 0.0);
		LevelBandIntegrator.Band band2 = result.getBands().get(1);
		assertEquals(2, band2.getLevel());
		assertEquals(67.0, band2.getActions(), 0.0);
		assertEquals(2950.0, band2.getDenominator(), 0.0);

		assertEquals(-5.061956439475021e-2, result.getLogProbabilityStillDry(), EPS);
		assertEquals(0.950640259150, Math.exp(result.getLogProbabilityStillDry()), EPS);
		assertEquals(Math.pow(2974.0 / 2975, 83) * Math.pow(2949.0 / 2950, 67),
			Math.exp(result.getLogProbabilityStillDry()), EPS);
		assertEquals(0.050611024071, result.getExpectedDrops(), EPS);
		assertEquals(150.0, result.getActions(), 0.0);

		double naive = Math.pow(2949.0 / 2950, 150);
		assertEquals(0.950415446583, naive, EPS);
		assertTrue("banding must show the player less dry than the naive estimate",
			Math.exp(result.getLogProbabilityStillDry()) > naive);
	}

	@Test
	public void zeroXpMeansZeroRolls()
	{
		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(0, 3000, 1.0, null, 0);

		assertEquals(0.0, result.getLogProbabilityStillDry(), 0.0);
		assertEquals(0.0, result.getExpectedDrops(), 0.0);
		assertEquals(0.0, result.getActions(), 0.0);
		assertTrue(result.getBands().isEmpty());
	}

	@Test
	public void levelOneOnly()
	{
		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(50, 3000, 10.0, null, 0);

		assertEquals(1, result.getBands().size());
		assertEquals(5.0, result.getActions(), 0.0);
		assertEquals(5 * Math.log1p(-1.0 / 2975), result.getLogProbabilityStillDry(), EPS);
	}

	@Test
	public void fractionalActionsAreKept()
	{
		// 150 XP at 40 XP per action is 3.75 actions; flooring would silently drop progress
		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(150, 3000, 40.0, null, 0);

		assertEquals(3.75, result.getActions(), EPS);
	}

	@Test
	public void clampsRateAtLevel99AndKeepsXpBeyondIt()
	{
		// B = 100,000. At level 99 and beyond the denominator is 100,000 - 99*25 = 97,525.
		// 50m XP is far past level 99 (13,034,431 XP); every XP point above that must still count
		// and must be evaluated at 97,525, not at some virtual-level rate.
		long xp = 50_000_000;
		int b = 100_000;
		double xpPerAction = 100;

		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(xp, b, xpPerAction, null, 0);

		LevelBandIntegrator.Band top = result.getBands().get(result.getBands().size() - 1);
		assertEquals(99, top.getLevel());
		assertEquals(97_525.0, top.getDenominator(), 0.0);
		assertEquals(xp - XpTable.xpForLevel(99), top.getXp(), 0.0);
		assertEquals(xp / xpPerAction, result.getActions(), 1e-6);

		double expected = 0;
		for (int level = 1; level < 99; level++)
		{
			double actions = (XpTable.xpForLevel(level + 1) - XpTable.xpForLevel(level)) / xpPerAction;
			expected += actions * Math.log1p(-1.0 / (b - level * 25));
		}
		expected += (xp - XpTable.xpForLevel(99)) / xpPerAction * Math.log1p(-1.0 / 97_525);
		assertEquals(expected, result.getLogProbabilityStillDry(), 1e-9);
	}

	@Test
	public void denominatorClampsLevelsAbove99()
	{
		assertEquals(LevelBandIntegrator.denominatorAt(100_000, 99, false),
			LevelBandIntegrator.denominatorAt(100_000, 120, false), 0.0);
	}

	@Test
	public void minLevelExcludesEarlierXp()
	{
		// minLevel 2: the 83 XP of level 1 produced no rolls. Only 67 actions at 1/2950 remain.
		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(150, 3000, 1.0, 2, 0);

		assertEquals(1, result.getBands().size());
		assertEquals(2, result.getBands().get(0).getLevel());
		assertEquals(67.0, result.getActions(), 0.0);
		assertEquals(67 * Math.log1p(-1.0 / 2950), result.getLogProbabilityStillDry(), EPS);
	}

	@Test
	public void minLevelAboveCurrentLevelMeansNoRolls()
	{
		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(150, 3000, 1.0, 40, 0);

		assertEquals(0.0, result.getActions(), 0.0);
		assertEquals(0.0, result.getLogProbabilityStillDry(), 0.0);
	}

	@Test
	public void maxXpDivisorAppliesToFinalDenominatorNotBase()
	{
		int b = 100_000;

		// Correct: (B - 99*25) / 15 = 97,525 / 15 = 6,501.666...
		double correct = LevelBandIntegrator.denominatorAt(b, 99, true);
		assertEquals(97_525.0 / 15, correct, EPS);

		// Wrong: B / 15 - 99*25 = 6,666.666... - 2,475 = 4,191.666...
		double dividedBase = b / 15.0 - 99 * 25;
		assertNotEquals(dividedBase, correct, 1.0);
	}

	@Test
	public void maxXpBandIsTerminalAndUsesDividedDenominator()
	{
		int b = 100_000;
		double xpPerAction = 100;
		LevelBandIntegrator.Result without = LevelBandIntegrator.integrate(XpTable.MAX_XP, b, xpPerAction, null, 0);
		LevelBandIntegrator.Result with = LevelBandIntegrator.integrate(XpTable.MAX_XP, b, xpPerAction, null, 1_000);

		LevelBandIntegrator.Band terminal = with.getBands().get(with.getBands().size() - 1);
		assertTrue(terminal.isAt200mXp());
		assertEquals(0.0, terminal.getXp(), 0.0);
		assertEquals(97_525.0 / 15, terminal.getDenominator(), EPS);
		assertEquals(without.getBands().size() + 1, with.getBands().size());
		assertEquals(without.getLogProbabilityStillDry() + 1_000 * Math.log1p(-15.0 / 97_525),
			with.getLogProbabilityStillDry(), 1e-9);
	}

	@Test
	public void noActionsAt200mAddsNoBand()
	{
		LevelBandIntegrator.Result result = LevelBandIntegrator.integrate(XpTable.MAX_XP, 100_000, 100, null, 0);

		assertFalse(result.getBands().get(result.getBands().size() - 1).isAt200mXp());
	}

	@Test(expected = IllegalArgumentException.class)
	public void actionsAt200mRequire200mXp()
	{
		LevelBandIntegrator.integrate(XpTable.MAX_XP - 1, 100_000, 100, null, 10);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsBaseChanceThatBreaksTheFormula()
	{
		// 2475 - 99*25 = 0: not a probability. Rejected even with no XP, when no band would expose it.
		LevelBandIntegrator.integrate(0, 2475, 1.0, null, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsZeroXpPerAction()
	{
		LevelBandIntegrator.integrate(150, 3000, 0.0, null, 0);
	}
}
