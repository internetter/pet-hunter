package com.pethunter.state;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CounterSettlerTest
{
	private static CollectionLogParser.Result result(String title, long kills)
	{
		return new CollectionLogParser.Result(title, Set.of(), Map.of("kills", kills), true);
	}

	@Test
	public void settlesOnTwoMatchingTicks()
	{
		CounterSettler settler = new CounterSettler();
		settler.start("Vorkath");

		assertEquals(Optional.empty(), settler.onTick(result("Vorkath", 1_312)));
		assertEquals(Optional.of(Map.of("kills", 1_312L)), settler.onTick(result("Vorkath", 1_312)));
		assertFalse("settles once only", settler.isWaiting());
	}

	@Test
	public void changingValueRestartsTheWait()
	{
		CounterSettler settler = new CounterSettler();
		settler.start("Vorkath");

		settler.onTick(result("Vorkath", 106));
		assertEquals(Optional.empty(), settler.onTick(result("Vorkath", 1_312)));
		assertEquals(Optional.of(Map.of("kills", 1_312L)), settler.onTick(result("Vorkath", 1_312)));
	}

	@Test
	public void differentPageOrClosedLogCancels()
	{
		CounterSettler settler = new CounterSettler();
		settler.start("Vorkath");
		settler.onTick(result("Vorkath", 1));
		assertEquals(Optional.empty(), settler.onTick(result("The Whisperer", 1)));
		assertFalse(settler.isWaiting());

		settler.start("Vorkath");
		assertEquals(Optional.empty(), settler.onTick(null));
		assertFalse(settler.isWaiting());
	}

	@Test
	public void givesUpOnAPageThatNeverSettles()
	{
		CounterSettler settler = new CounterSettler();
		settler.start("Vorkath");
		for (int tick = 0; tick < CounterSettler.MAX_TICKS; tick++)
		{
			assertTrue(settler.isWaiting());
			assertEquals(Optional.empty(), settler.onTick(result("Vorkath", tick)));
		}
		settler.onTick(result("Vorkath", 999));
		assertFalse(settler.isWaiting());
	}

	@Test
	public void ticksWithoutAPageDoNothing()
	{
		assertEquals(Optional.empty(), new CounterSettler().onTick(result("Vorkath", 1)));
	}
}
