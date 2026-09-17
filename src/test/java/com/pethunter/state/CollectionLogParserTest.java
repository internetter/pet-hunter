package com.pethunter.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Header lines and item opacities are copied from a real client session (2026-09-17).
 */
public class CollectionLogParserTest
{
	static CollectionLogPage page(List<String> header, int... itemIdOpacityPairs)
	{
		List<CollectionLogPage.Item> items = new ArrayList<>();
		for (int i = 0; i < itemIdOpacityPairs.length; i += 2)
		{
			items.add(new CollectionLogPage.Item(itemIdOpacityPairs[i], itemIdOpacityPairs[i + 1]));
		}
		return new CollectionLogPage(header, items);
	}

	@Test
	public void singleBossPage()
	{
		CollectionLogParser.Result result = CollectionLogParser.parse(page(
			List.of("Brutus", "Obtained: <col=0dc10d>4/4</col>", "Personal Best: <col=ffffff>0:01.20</col>", "Brutus kills: <col=ffffff>2,271</col>"),
			33124, 0, 33101, 0, 33091, 0, 33000, 0));

		assertEquals("Brutus", result.getTitle());
		assertEquals(Map.of("brutus_kills", 2_271L), result.getCounters());
		assertEquals(Set.of(33124, 33101, 33091, 33000), result.getObtainedItemIds());
		assertTrue(result.isComplete());
	}

	@Test
	public void multiBossPageRecordsEachCounterAndOnlyOpacityZeroItems()
	{
		CollectionLogParser.Result result = CollectionLogParser.parse(page(
			List.of("Callisto and Artio", "Obtained: <col=ffff00>2/6</col>", "Callisto kills: <col=ffffff>20</col>", "Artio kills: <col=ffffff>0</col>"),
			13178, 175, 12603, 175, 11920, 0, 12604, 175, 12605, 175, 27681, 0));

		assertEquals(Map.of("callisto_kills", 20L, "artio_kills", 0L), result.getCounters());
		assertEquals(Set.of(11920, 27681), result.getObtainedItemIds());
		assertFalse("the pet (13178) is at opacity 175, so it is not obtained", result.getObtainedItemIds().contains(13178));
		assertTrue(result.isComplete());
	}

	@Test
	public void pageWithoutCounters()
	{
		CollectionLogParser.Result result = CollectionLogParser.parse(page(
			List.of("All Pets", "Obtained: <col=ffff00>2/3</col>"),
			12655, 0, 12921, 0, 13262, 175));

		assertEquals("All Pets", result.getTitle());
		assertTrue(result.getCounters().isEmpty());
		assertEquals(Set.of(12655, 12921), result.getObtainedItemIds());
	}

	@Test
	public void itemCountDisagreeingWithHeaderIsIncomplete()
	{
		CollectionLogParser.Result result = CollectionLogParser.parse(page(
			List.of("All Pets", "Obtained: <col=ffff00>7/71</col>"), 12655, 0));

		assertFalse(result.isComplete());
		assertEquals(Set.of(12655), result.getObtainedItemIds());
	}

	@Test
	public void undrawnPagesAreIgnored()
	{
		assertNull(CollectionLogParser.parse(page(List.of(), 12655, 0)));
		assertNull(CollectionLogParser.parse(page(List.of("All Pets", "Obtained: <col=ffff00>0/71</col>"))));
		assertNull(CollectionLogParser.parse(page(List.of("<col=ffffff></col>"), 12655, 0)));
	}

	@Test
	public void counterKeys()
	{
		assertEquals("callisto_kills", CounterKeys.fromLabel("Callisto kills"));
		assertEquals("kril_tsutsaroth_kills", CounterKeys.fromLabel("K'ril Tsutsaroth kills"));
		assertEquals("tztok_jad_kills", CounterKeys.fromKillCountActivity("TzTok-Jad"));
		assertEquals("callisto_kills", CounterKeys.fromKillCountActivity("<col=ff0000>Callisto</col>"));
	}
}
