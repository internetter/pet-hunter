package com.pethunter.math;

import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Probabilities here are arbitrary fixtures, not game data.
 */
public class DrynessCombinerTest
{
	private static final double EPS = 1e-12;

	private static DrynessResult figure(double pDry, Confidence confidence, String pool)
	{
		return DrynessResult.figure(Math.log(pDry), 0.25, 10.0, confidence, "fixture " + pool, pool, 0);
	}

	@Test
	public void singleFigurePassesThroughUnchanged()
	{
		DrynessResult only = figure(0.8, Confidence.EXACT, "counter:a");

		assertSame(only, DrynessCombiner.combine(List.of(only)));
	}

	@Test
	public void independentSourcesMultiply()
	{
		DrynessResult result = DrynessCombiner.combine(List.of(
			figure(0.8, Confidence.EXACT, "counter:a"),
			figure(0.5, Confidence.EXACT, "counter:b")));

		assertEquals(0.4, result.getProbabilityStillDry().orElseThrow().getValue(), EPS);
		assertEquals(0.5, result.getDropRateMultiple().orElseThrow().getValue(), EPS);
		assertEquals(Confidence.EXACT, result.getConfidence().orElseThrow());
		assertFalse(result.isUpperBound());
	}

	@Test
	public void combinedTierIsTheWeakest()
	{
		DrynessResult result = DrynessCombiner.combine(List.of(
			figure(0.8, Confidence.EXACT, "counter:a"),
			figure(0.5, Confidence.ESTIMATED, "xp:MINING")));

		assertEquals(Confidence.ESTIMATED, result.getProbabilityStillDry().orElseThrow().getConfidence());
	}

	@Test
	public void knownPlusUnknownIsALabelledUpperBound()
	{
		DrynessResult known = figure(0.8, Confidence.EXACT, "counter:a");
		DrynessResult unknown = DrynessResult.unknown("Other boss: no kills count yet.");

		DrynessResult result = DrynessCombiner.combine(List.of(known, unknown));

		assertEquals(DrynessResult.Status.FIGURE, result.getStatus());
		// The unknown factor is <= 1 and left out, so the shown P(still dry) can only be too high
		assertEquals(0.8, result.getProbabilityStillDry().orElseThrow().getValue(), EPS);
		assertTrue(result.isUpperBound());
		assertEquals(1, result.getUnknownSourceCount());
		String assumption = result.getProbabilityStillDry().orElseThrow().getAssumption();
		assertTrue(assumption, assumption.contains("Upper bound: 1 of 2 sources could not be counted"));
		assertTrue(assumption, assumption.contains("Other boss: no kills count yet."));
		assertTrue(assumption, assumption.contains("at least this dry"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void sameAttemptPoolIsRejected()
	{
		DrynessCombiner.combine(List.of(
			figure(0.8, Confidence.ESTIMATED, "xp:FISHING"),
			figure(0.5, Confidence.ESTIMATED, "xp:FISHING")));
	}

	@Test
	public void allUnknownIsUnknown()
	{
		DrynessResult result = DrynessCombiner.combine(List.of(
			DrynessResult.unknown("a"), DrynessResult.unknown("b")));

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
		assertEquals(2, result.getUnknownSourceCount());
		assertFalse(result.getProbabilityStillDry().isPresent());
	}

	@Test
	public void noSourcesIsUnknown()
	{
		assertEquals(DrynessResult.Status.UNKNOWN, DrynessCombiner.combine(List.of()).getStatus());
	}

	@Test
	public void onlyNotApplicableStaysNotApplicable()
	{
		DrynessResult result = DrynessCombiner.combine(List.of(DrynessResult.notApplicable("quest reward")));

		assertEquals(DrynessResult.Status.NOT_APPLICABLE, result.getStatus());
		assertFalse(result.getProbabilityStillDry().isPresent());
	}

	@Test
	public void notApplicableSourceDoesNotDiluteAFigure()
	{
		DrynessResult known = figure(0.8, Confidence.EXACT, "counter:a");

		DrynessResult result = DrynessCombiner.combine(List.of(known, DrynessResult.notApplicable("also purchasable")));

		assertSame(known, result);
		assertFalse(result.isUpperBound());
	}
}
