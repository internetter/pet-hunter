package com.pethunter.state;

import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Names on the left are as RuneLite's chat commands plugin stores them, taken from a real profile.
 */
public class KillCountImportTest
{
	private static final Set<String> DATASET = Set.of(
		"vorkath_kills", "tztok_jad_kills", "herbiboar_harvests", "gauntlet_completion_count", "vetion_kills");

	@Test
	public void matchesActivityNamesToCounterKeys()
	{
		Map<String, Long> matched = KillCountImport.match(
			Map.of("vorkath", 1_312L, "tztok-jad", 23L, "herbiboar", 667L, "gauntlet", 7L, "vet'ion", 20L),
			DATASET, Set.of());

		assertEquals(Map.of("vorkath_kills", 1_312L, "tztok_jad_kills", 23L, "herbiboar_harvests", 667L,
			"gauntlet_completion_count", 7L, "vetion_kills", 20L), matched);
	}

	@Test
	public void skipsCountersTheDatasetDoesNotUseOrThatAreAlreadyKnown()
	{
		Map<String, Long> matched = KillCountImport.match(
			Map.of("vorkath", 1_312L, "mimic", 1L, "herbiboar", 667L),
			DATASET, Set.of("vorkath_kills"));

		assertEquals(Map.of("herbiboar_harvests", 667L), matched);
	}

	@Test
	public void ignoresNonsense()
	{
		java.util.Map<String, Long> input = new java.util.HashMap<>();
		input.put("vorkath", -5L);
		input.put("herbiboar", null);
		input.put(null, 3L);

		assertEquals(Map.of(), KillCountImport.match(input, DATASET, Set.of()));
	}
}
