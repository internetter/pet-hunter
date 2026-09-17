package com.pethunter.math;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TieredValueTest
{
	@Test
	public void carriesTierAndAssumption()
	{
		TieredValue value = new TieredValue(0.5, Confidence.ESTIMATED, "assumes something");
		assertEquals(Confidence.ESTIMATED, value.getConfidence());
		assertEquals("assumes something", value.getAssumption());
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownCannotCarryANumber()
	{
		new TieredValue(0.5, Confidence.UNKNOWN, "assumes something");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNaN()
	{
		new TieredValue(Double.NaN, Confidence.EXACT, "kills");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsBlankAssumption()
	{
		new TieredValue(0.5, Confidence.EXACT, " ");
	}

	@Test(expected = NullPointerException.class)
	public void rejectsMissingTier()
	{
		new TieredValue(0.5, null, "kills");
	}

	@Test
	public void weakestTier()
	{
		assertEquals(Confidence.ESTIMATED, Confidence.weakest(Confidence.EXACT, Confidence.ESTIMATED));
		assertEquals(Confidence.ESTIMATED, Confidence.weakest(Confidence.ESTIMATED, Confidence.EXACT));
		assertEquals(Confidence.UNKNOWN, Confidence.weakest(Confidence.UNKNOWN, Confidence.EXACT));
		assertEquals(Confidence.EXACT, Confidence.weakest(Confidence.EXACT, Confidence.EXACT));
	}
}
