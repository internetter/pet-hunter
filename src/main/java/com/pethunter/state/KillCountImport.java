package com.pethunter.state;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matches kill counts recorded by RuneLite's own chat commands plugin (the numbers behind
 * {@code !kc}) to this plugin's counter keys.
 *
 * <p>Both come from the same game messages, so a player who has killed a boss since installing
 * RuneLite often already has a count this plugin never saw. Importing them fills in counts before
 * the player has opened the matching collection log page.
 *
 * <p>Only counters the dataset actually uses are imported, and never over a count already
 * recorded here, which is at least as fresh.
 */
public final class KillCountImport
{
	/**
	 * Counter key endings, since RuneLite stores the activity name alone ("herbiboar") while the
	 * collection log labels it ("Herbiboar harvests").
	 */
	private static final List<String> SUFFIXES = List.of("_kills", "_harvests", "_completions", "_completion_count");

	private KillCountImport()
	{
	}

	/**
	 * @param runeliteCounts activity name to count, as RuneLite stores them
	 * @param datasetKeys    counter keys the dataset references
	 * @param alreadyKnown   counter keys this plugin has already recorded
	 * @return counts to record, keyed by this plugin's counter key
	 */
	public static Map<String, Long> match(Map<String, Long> runeliteCounts, Set<String> datasetKeys, Set<String> alreadyKnown)
	{
		Map<String, Long> matched = new HashMap<>();
		for (Map.Entry<String, Long> entry : runeliteCounts.entrySet())
		{
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)
			{
				continue;
			}
			String base = CounterKeys.slug(entry.getKey());
			for (String suffix : SUFFIXES)
			{
				String key = base + suffix;
				if (datasetKeys.contains(key) && !alreadyKnown.contains(key))
				{
					matched.put(key, entry.getValue());
					break;
				}
			}
		}
		return matched;
	}
}
