package com.pethunter.ui;

import com.pethunter.data.ContributionRange;
import com.pethunter.data.PetCategory;
import com.pethunter.data.PetSource;
import com.pethunter.data.RateModel;
import com.pethunter.math.Confidence;
import com.pethunter.math.TieredValue;
import static com.pethunter.ui.UiFixtures.figureEntry;
import static com.pethunter.ui.UiFixtures.obtained;
import static com.pethunter.ui.UiFixtures.oneOffEntry;
import static com.pethunter.ui.UiFixtures.unknownEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Rates here are arbitrary fixtures, not game data.
 */
public class DrynessFormatTest
{
	@Test
	public void exactFigureIsPlain()
	{
		assertEquals("36.8%", DrynessFormat.probability(new TieredValue(0.3678, Confidence.EXACT, "kills"), false));
	}

	@Test
	public void estimatedFigureCarriesTilde()
	{
		assertEquals("~36.8%", DrynessFormat.probability(new TieredValue(0.3678, Confidence.ESTIMATED, "xp"), false));
	}

	@Test
	public void upperBoundIsMarked()
	{
		assertEquals("<=~36.8%", DrynessFormat.probability(new TieredValue(0.3678, Confidence.ESTIMATED, "xp"), true));
	}

	@Test
	public void extremesNeverRoundToAMisleadingZeroOrHundred()
	{
		assertEquals("<0.1%", DrynessFormat.probability(new TieredValue(1e-9, Confidence.EXACT, "kills"), false));
		assertEquals(">99.9%", DrynessFormat.probability(new TieredValue(0.99999, Confidence.EXACT, "kills"), false));
		assertEquals("100.0%", DrynessFormat.probability(new TieredValue(1.0, Confidence.EXACT, "no kills yet"), false));
		assertEquals("0.0%", DrynessFormat.probability(new TieredValue(0.0, Confidence.EXACT, "underflow"), false));
	}

	@Test
	public void multiple()
	{
		assertEquals("~2.30x", DrynessFormat.multiple(new TieredValue(2.3, Confidence.ESTIMATED, "xp")));
		assertEquals("0.50x", DrynessFormat.multiple(new TieredValue(0.5, Confidence.EXACT, "kills")));
	}

	@Test
	public void rowStatusPerState()
	{
		// 1/100 at 100 kills: (99/100)^100 = 36.6%
		assertEquals("36.6%", DrynessFormat.rowStatus(figureEntry("a", "A", 100, 100)));
		assertEquals("UNKNOWN", DrynessFormat.rowStatus(unknownEntry("b", "B", PetCategory.BOSS, null)));
		assertEquals("No drop chance", DrynessFormat.rowStatus(oneOffEntry("c", "C")));
		assertEquals("Obtained", DrynessFormat.rowStatus(obtained(figureEntry("d", "D", 100, 1))));
	}

	@Test
	public void figureFromAWarnedCountIsMarked()
	{
		PetSource warned = PetSource.builder().id("w.kills").label("Group boss").rateModel(RateModel.FLAT_PER_KILL)
			.flatRate(100).counterKey("w_kc").countWarning("Group kills overstate dryness.")
			.verified(true).citations(UiFixtures.FIXTURE_CITATION).build();
		com.pethunter.data.Pet pet = UiFixtures.pet("w", "Warned", PetCategory.BOSS, null, warned);
		com.pethunter.math.PlayerProgress progress = com.pethunter.math.PlayerProgress.builder().counter("w_kc", 100L).build();
		PetEntry entry = new PetEntry(pet, false, com.pethunter.math.SourceEstimator.estimatePet(pet, progress, null), progress);

		assertTrue(DrynessFormat.hasCountWarning(entry));
		assertEquals("36.6% !", DrynessFormat.rowStatus(entry));
		assertTrue(DrynessFormat.explanation(entry).contains("Warning: Group kills overstate dryness."));

		// No marker without a figure built from that count
		PetEntry noCount = new PetEntry(pet, false, com.pethunter.math.SourceEstimator.estimatePet(pet,
			com.pethunter.math.PlayerProgress.empty(), null), com.pethunter.math.PlayerProgress.empty());
		assertFalse(DrynessFormat.hasCountWarning(noCount));
		assertFalse(DrynessFormat.hasCountWarning(figureEntry("a", "A", 100, 100)));
	}

	@Test
	public void explanationStatesTierAndAssumption()
	{
		String text = DrynessFormat.explanation(figureEntry("a", "A", 100, 100));

		assertTrue(text, text.startsWith("36.6% of players would still be without this pet at your count (1.00x the drop rate)."));
		assertTrue(text, text.contains("Exact: based on a count read from the game."));
		assertTrue(text, text.contains("100 kills at 1/100"));
	}

	@Test
	public void unknownExplanationIsTheReasonWithNoNumber()
	{
		String text = DrynessFormat.explanation(unknownEntry("b", "Boss", PetCategory.BOSS, null));

		assertTrue(text, text.contains("not yet verified"));
		assertFalse(text, text.matches(".*\\d+(\\.\\d+)?%.*"));
	}

	@Test
	public void unverifiedRatesRenderAsUnknown()
	{
		PetSource unverified = PetSource.builder().id("p.a").label("A").rateModel(RateModel.SKILL_LEVEL_SCALED).build();

		assertEquals("UNKNOWN", DrynessFormat.rate(unverified));
		assertEquals("UNKNOWN", DrynessFormat.rate(UiFixtures.killSource("p", "Kills", null)));
	}

	@Test
	public void verifiedRatesRenderPerModel()
	{
		assertEquals("1/5,000", DrynessFormat.rate(UiFixtures.killSource("p", "Kills", 5_000)));
		assertEquals("1/(300,000 - level x 25)", DrynessFormat.rate(PetSource.builder().id("p.a").label("A")
			.rateModel(RateModel.SKILL_LEVEL_SCALED).baseChance(300_000).verified(true).build()));
		assertEquals("1/2,500 to 1/5,000", DrynessFormat.rate(PetSource.builder().id("p.b").label("B")
			.rateModel(RateModel.CONTRIBUTION_SCALED).contributionRange(new ContributionRange(5_000, 2_500)).verified(true).build()));
		assertEquals("No drop chance", DrynessFormat.rate(UiFixtures.oneOff("p")));
	}
}
