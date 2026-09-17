package com.pethunter.math;

import com.google.gson.Gson;
import com.pethunter.data.HuntMethod;
import com.pethunter.data.Pet;
import com.pethunter.data.PetCategory;
import com.pethunter.data.PetRepository;
import com.pethunter.data.PetSource;
import com.pethunter.data.RateModel;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Seed entries come from the frozen seed fixture. Every other rate, XP-per-action and URL here is
 * an arbitrary fixture, not game data; fixture URLs use the reserved .invalid TLD.
 */
public class SourceEstimatorTest
{
	private static final double EPS = 1e-12;
	private static final List<String> FIXTURE_CITATION = List.of("https://fixture.invalid/not-a-real-source");

	private static PetRepository seed;

	@BeforeClass
	public static void loadSeed() throws IOException
	{
		try (Reader reader = new InputStreamReader(
			SourceEstimatorTest.class.getResourceAsStream("/com/pethunter/fixtures/seed-dataset.json"), StandardCharsets.UTF_8))
		{
			seed = PetRepository.load(new Gson(), reader);
		}
	}

	private static Pet seedPet(String id)
	{
		return seed.getPet(id).orElseThrow();
	}

	private static PetSource flatSource(RateModel model, Integer flatRate, String counterKey)
	{
		return PetSource.builder()
			.id("fixture_pet.source")
			.label("Fixture source")
			.rateModel(model)
			.flatRate(flatRate)
			.counterKey(counterKey)
			.verified(flatRate != null)
			.citations(flatRate != null ? FIXTURE_CITATION : List.of())
			.build();
	}

	private static Pet petWith(String skill, PetSource source, HuntMethod method)
	{
		return Pet.builder()
			.id("fixture_pet")
			.name("Fixture pet")
			.category(skill == null ? PetCategory.BOSS : PetCategory.SKILLING)
			.skill(skill)
			.sources(List.of(source))
			.methods(method == null ? List.of() : List.of(method))
			.build();
	}

	private static HuntMethod method(String id, Double xpPerAction)
	{
		return HuntMethod.builder()
			.id(id)
			.xpPerAction(xpPerAction)
			.verified(xpPerAction != null)
			.citations(xpPerAction != null ? FIXTURE_CITATION : List.of())
			.build();
	}

	/**
	 * The seed heron with a fixture method attached to its minnows source, since the seed has none.
	 */
	private static Pet heronWithMinnowsMethod(double xpPerAction)
	{
		Pet heron = seedPet("heron");
		return Pet.builder()
			.id(heron.getId())
			.name(heron.getName())
			.category(heron.getCategory())
			.skill(heron.getSkill())
			.obtainableOn(heron.getObtainableOn())
			.sources(heron.getSources())
			.methods(List.of(method("heron.minnows", xpPerAction)))
			.build();
	}

	// ---- ONE_OFF ----

	@Test
	public void oneOffProducesNoDrynessFigure()
	{
		Pet pet = seedPet("example_one_off_pet");
		PlayerProgress progress = PlayerProgress.builder()
			.counter("anything", 100L)
			.manualCount("example_one_off_pet.acquisition", 5L)
			.build();

		for (DrynessResult result : List.of(
			SourceEstimator.estimate(pet, pet.getSources().get(0), progress),
			SourceEstimator.estimatePet(pet, progress, null)))
		{
			assertEquals(DrynessResult.Status.NOT_APPLICABLE, result.getStatus());
			assertFalse("no probability, so no 0, 100 or NaN", result.getProbabilityStillDry().isPresent());
			assertFalse(result.getDropRateMultiple().isPresent());
			assertFalse(result.getLogProbabilityStillDry().isPresent());
			assertFalse("not applicable has no tier at all", result.getConfidence().isPresent());
			assertFalse(result.isUpperBound());
		}
	}

	// ---- Counted flat sources ----

	@Test
	public void flatPerKillFromGameCounterIsExact()
	{
		PetSource source = flatSource(RateModel.FLAT_PER_KILL, 3_000, "fixture_kc");
		Pet pet = petWith(null, source, null);

		DrynessResult result = SourceEstimator.estimate(pet, source, PlayerProgress.builder().counter("fixture_kc", 412L).build());

		TieredValue pDry = result.getProbabilityStillDry().orElseThrow();
		assertEquals(Confidence.EXACT, pDry.getConfidence());
		assertEquals(Math.pow(2_999.0 / 3_000, 412), pDry.getValue(), EPS);
		assertEquals(412.0 / 3_000, result.getDropRateMultiple().orElseThrow().getValue(), EPS);
		assertEquals(412.0, result.getAttempts().orElseThrow().getValue(), 0.0);
		assertTrue(pDry.getAssumption(), pDry.getAssumption().contains("412 kills at 1/3,000"));
		assertFalse(result.isUpperBound());
	}

	@Test
	public void manualCountIsEstimatedAndSaysSo()
	{
		PetSource source = flatSource(RateModel.FLAT_PER_ROLL, 3_000, null);
		Pet pet = petWith(null, source, null);

		DrynessResult result = SourceEstimator.estimate(pet, source,
			PlayerProgress.builder().manualCount("fixture_pet.source", 12L).build());

		TieredValue pDry = result.getProbabilityStillDry().orElseThrow();
		assertEquals(Confidence.ESTIMATED, pDry.getConfidence());
		assertTrue(pDry.getAssumption(), pDry.getAssumption().contains("manually entered count of 12 reward rolls"));
	}

	@Test
	public void gameCounterWinsOverManualCount()
	{
		PetSource source = flatSource(RateModel.FLAT_PER_KILL, 3_000, "fixture_kc");
		Pet pet = petWith(null, source, null);

		DrynessResult result = SourceEstimator.estimate(pet, source, PlayerProgress.builder()
			.counter("fixture_kc", 50L)
			.manualCount("fixture_pet.source", 9_999L)
			.build());

		assertEquals(50.0, result.getAttempts().orElseThrow().getValue(), 0.0);
		assertEquals(Confidence.EXACT, result.getConfidence().orElseThrow());
	}

	@Test
	public void missingCountIsUnknownWithACallToAction()
	{
		PetSource withCounter = flatSource(RateModel.FLAT_PER_KILL, 3_000, "fixture_kc");
		DrynessResult noKc = SourceEstimator.estimate(petWith(null, withCounter, null), withCounter, PlayerProgress.empty());
		assertEquals(DrynessResult.Status.UNKNOWN, noKc.getStatus());
		assertEquals(Confidence.UNKNOWN, noKc.getConfidence().orElseThrow());
		assertFalse(noKc.getProbabilityStillDry().isPresent());
		assertTrue(noKc.getExplanation(), noKc.getExplanation().contains("collection log"));

		PetSource noCounter = flatSource(RateModel.FLAT_PER_KILL, 3_000, null);
		DrynessResult manualOnly = SourceEstimator.estimate(petWith(null, noCounter, null), noCounter, PlayerProgress.empty());
		assertTrue(manualOnly.getExplanation(), manualOnly.getExplanation().contains("no kills counter the plugin can read"));
	}

	@Test
	public void countWarningIsCarriedIntoTheAssumption()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture boss").rateModel(RateModel.FLAT_PER_KILL)
			.flatRate(1_500).counterKey("fixture_kc").countWarning("Group kills overstate dryness.")
			.verified(true).citations(FIXTURE_CITATION).build();

		DrynessResult result = SourceEstimator.estimate(petWith(null, source, null), source,
			PlayerProgress.builder().counter("fixture_kc", 20L).build());

		String assumption = result.getProbabilityStillDry().orElseThrow().getAssumption();
		assertTrue(assumption, assumption.endsWith("Warning: Group kills overstate dryness."));
	}

	@Test
	public void unusableCounterExplainsWhyInsteadOfClaimingNoCounterExists()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture boss").rateModel(RateModel.FLAT_PER_KILL)
			.flatRate(3_000).countWarning("Only the MVP rolls the pet.").verified(true).citations(FIXTURE_CITATION).build();

		DrynessResult result = SourceEstimator.estimate(petWith(null, source, null), source, PlayerProgress.empty());

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
		assertEquals("Fixture boss: Only the MVP rolls the pet. You can enter your own count.", result.getExplanation());
	}

	@Test
	public void manualCountReplacesAWarnedGameCounterAndDropsTheWarning()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture boss").rateModel(RateModel.FLAT_PER_KILL)
			.flatRate(3_000).counterKey("fixture_kc").countWarning("Group kills overstate dryness.")
			.verified(true).citations(FIXTURE_CITATION).build();
		Pet pet = petWith(null, source, null);

		DrynessResult result = SourceEstimator.estimate(pet, source, PlayerProgress.builder()
			.counter("fixture_kc", 176L)
			.manualCount("fixture_pet.source", 12L)
			.build());

		assertEquals(12.0, result.getAttempts().orElseThrow().getValue(), 0.0);
		assertEquals(Confidence.ESTIMATED, result.getConfidence().orElseThrow());
		assertFalse(result.getExplanation(), result.getExplanation().contains("Warning"));
		assertTrue(result.getExplanation().contains("manually entered count of 12 kills"));
	}

	@Test
	public void manualEntryIsOfferedOnlyWhereTheGameCannotSupplyATrustworthyCount()
	{
		assertTrue(SourceEstimator.acceptsManualCount(flatSource(RateModel.UNIQUE_CONDITIONAL, 53, null)));
		assertTrue(SourceEstimator.acceptsManualCount(PetSource.builder().id("p.a").label("a").rateModel(RateModel.FLAT_PER_KILL)
			.counterKey("kc").countWarning("MVP only").build()));
		assertFalse(SourceEstimator.acceptsManualCount(flatSource(RateModel.FLAT_PER_KILL, 3_000, "kc")));
		assertFalse(SourceEstimator.acceptsManualCount(PetSource.builder().id("p.b").label("b").rateModel(RateModel.SKILL_LEVEL_SCALED).build()));
		assertFalse(SourceEstimator.acceptsManualCount(PetSource.builder().id("p.c").label("c").rateModel(RateModel.ONE_OFF).build()));
	}

	@Test
	public void unverifiedRateIsUnknownEvenWithAKillCount()
	{
		Pet pet = seedPet("example_boss_pet");
		PlayerProgress progress = PlayerProgress.builder().counter("clog_page_kc_placeholder", 5_000L).build();

		DrynessResult result = SourceEstimator.estimate(pet, pet.getSources().get(0), progress);

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
		assertFalse(result.getProbabilityStillDry().isPresent());
		assertTrue(result.getExplanation().contains("not yet verified"));
	}

	@Test
	public void rateOnAnUnverifiedSourceIsNeverUsed()
	{
		// The build rejects this shape, but the estimator must not trust a rate that lacks verification
		PetSource sneaky = PetSource.builder().id("fixture_pet.source").label("x").rateModel(RateModel.FLAT_PER_KILL)
			.flatRate(3_000).counterKey("fixture_kc").verified(false).build();

		DrynessResult result = SourceEstimator.estimate(petWith(null, sneaky, null), sneaky,
			PlayerProgress.builder().counter("fixture_kc", 10L).build());

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
	}

	@Test
	public void uniqueConditionalCountsUniquesNotCompletions()
	{
		PetSource source = flatSource(RateModel.UNIQUE_CONDITIONAL, 50, null);

		DrynessResult result = SourceEstimator.estimate(petWith(null, source, null), source,
			PlayerProgress.builder().manualCount("fixture_pet.source", 20L).build());

		assertEquals(Math.pow(49.0 / 50, 20), result.getProbabilityStillDry().orElseThrow().getValue(), EPS);
		assertTrue(result.getExplanation().contains("20 unique drops"));
		assertTrue(result.getExplanation().contains("Rolled only when a unique is received"));
	}

	// ---- CONTRIBUTION_SCALED ----

	@Test
	public void contributionScaledUsesRarestRateAndNeedsACount()
	{
		Pet heron = seedPet("heron");
		PetSource trawler = seed.getSource("heron.fishing_trawler").orElseThrow();

		assertEquals(DrynessResult.Status.UNKNOWN, SourceEstimator.estimate(heron, trawler, PlayerProgress.empty()).getStatus());

		DrynessResult result = SourceEstimator.estimate(heron, trawler,
			PlayerProgress.builder().manualCount("heron.fishing_trawler", 40L).build());

		TieredValue pDry = result.getProbabilityStillDry().orElseThrow();
		assertEquals(Confidence.ESTIMATED, pDry.getConfidence());
		// Seed range is [5000, 2500]; the rarer 1/5000 is used so dryness is never overstated
		assertEquals(Math.pow(4_999.0 / 5_000, 40), pDry.getValue(), EPS);
		assertTrue(pDry.getAssumption(), pDry.getAssumption().contains("between 1/2,500 and 1/5,000"));
	}

	// ---- STATIC_IGNORES_FORMULA ----

	@Test
	public void staticRateDoesNotMoveWithLevel()
	{
		PetSource minnows = seed.getSource("heron.minnows").orElseThrow();

		double atLevel1 = SourceEstimator.perAttemptDenominator(minnows, 1, false).orElseThrow();
		double atLevel50 = SourceEstimator.perAttemptDenominator(minnows, 50, false).orElseThrow();
		double atLevel99 = SourceEstimator.perAttemptDenominator(minnows, 99, false).orElseThrow();

		assertEquals(977_778.0, atLevel1, 0.0);
		assertEquals(atLevel1, atLevel50, 0.0);
		assertEquals(atLevel1, atLevel99, 0.0);
	}

	@Test
	public void staticDrynessIgnoresTheLevelsTheXpWasEarnedAt()
	{
		// Fixture: 26 XP per action (not a real figure). 13,034,431 XP spans levels 1 to 99.
		Pet heron = heronWithMinnowsMethod(26.0);
		PetSource minnows = seed.getSource("heron.minnows").orElseThrow();
		long xp = XpTable.xpForLevel(99);

		DrynessResult result = SourceEstimator.estimate(heron, minnows, PlayerProgress.builder().xp("FISHING", xp).build());

		// A single flat evaluation of every action, with no level bands
		double actions = xp / 26.0;
		assertEquals(actions * Math.log1p(-1.0 / 977_778), result.getLogProbabilityStillDry().orElseThrow().getValue(), 1e-9);
		assertEquals(Confidence.ESTIMATED, result.getConfidence().orElseThrow());
		assertTrue(result.getExplanation(), result.getExplanation().contains("does not change with level"));

		// Contrast: a level-scaled source whose level-99 denominator is the same 977,778 is less dry,
		// because its early levels were evaluated at worse rates
		PetSource scaled = PetSource.builder().id("heron.minnows").label("Scaled contrast").rateModel(RateModel.SKILL_LEVEL_SCALED)
			.baseChance(977_778 + 99 * 25).verified(true).citations(FIXTURE_CITATION).build();
		DrynessResult scaledResult = SourceEstimator.estimate(heron, scaled, PlayerProgress.builder().xp("FISHING", xp).build());
		assertTrue(scaledResult.getProbabilityStillDry().orElseThrow().getValue() > result.getProbabilityStillDry().orElseThrow().getValue());
	}

	// ---- SKILL_LEVEL_SCALED ----

	@Test
	public void levelScaledMatchesTheIntegratorAndStatesItsAssumption()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture rocks").rateModel(RateModel.SKILL_LEVEL_SCALED)
			.baseChance(3_000).verified(true).citations(FIXTURE_CITATION).build();
		Pet pet = petWith("MINING", source, method("fixture_pet.source", 1.0));

		DrynessResult result = SourceEstimator.estimate(pet, source, PlayerProgress.builder().xp("MINING", 150L).build());

		// Same numbers as the hand-computed two-band example in LevelBandIntegratorTest
		TieredValue pDry = result.getProbabilityStillDry().orElseThrow();
		assertEquals(0.950640259150, pDry.getValue(), EPS);
		assertEquals(Confidence.ESTIMATED, pDry.getConfidence());
		assertEquals("xp:MINING", result.getAttemptPool().orElseThrow());
		assertTrue(pDry.getAssumption(), pDry.getAssumption().startsWith("Assumes all 150 Mining XP came from Fixture rocks at 1 XP each (~150 actions)"));
	}

	@Test
	public void levelScaledAssumptionMentionsMinLevel()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture rocks").rateModel(RateModel.SKILL_LEVEL_SCALED)
			.baseChance(3_000).minLevel(2).verified(true).citations(FIXTURE_CITATION).build();
		Pet pet = petWith("MINING", source, method("fixture_pet.source", 1.0));

		DrynessResult result = SourceEstimator.estimate(pet, source, PlayerProgress.builder().xp("MINING", 150L).build());

		assertEquals(67 * Math.log1p(-1.0 / 2_950), result.getLogProbabilityStillDry().orElseThrow().getValue(), EPS);
		assertTrue(result.getExplanation(), result.getExplanation().contains("all 67 Mining XP earned from level 2 onward"));
	}

	@Test
	public void levelScaledWithoutXpPerActionIsUnknown()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture rocks").rateModel(RateModel.SKILL_LEVEL_SCALED)
			.baseChance(3_000).verified(true).citations(FIXTURE_CITATION).build();

		DrynessResult noMethod = SourceEstimator.estimate(petWith("MINING", source, null), source,
			PlayerProgress.builder().xp("MINING", 150L).build());
		DrynessResult unverifiedMethod = SourceEstimator.estimate(petWith("MINING", source, method("fixture_pet.source", null)), source,
			PlayerProgress.builder().xp("MINING", 150L).build());

		assertEquals(DrynessResult.Status.UNKNOWN, noMethod.getStatus());
		assertEquals(DrynessResult.Status.UNKNOWN, unverifiedMethod.getStatus());
		assertTrue(noMethod.getExplanation().contains("XP per action"));
	}

	@Test
	public void levelScaledWithoutXpIsUnknown()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture rocks").rateModel(RateModel.SKILL_LEVEL_SCALED)
			.baseChance(3_000).verified(true).citations(FIXTURE_CITATION).build();

		DrynessResult result = SourceEstimator.estimate(petWith("MINING", source, method("fixture_pet.source", 1.0)), source,
			PlayerProgress.empty());

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
		assertTrue(result.getExplanation().contains("Mining XP is not available"));
	}

	@Test
	public void levelScaledAt200mXpDoesNotThrow()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture rocks").rateModel(RateModel.SKILL_LEVEL_SCALED)
			.baseChance(100_000).verified(true).citations(FIXTURE_CITATION).build();

		DrynessResult result = SourceEstimator.estimate(petWith("MINING", source, method("fixture_pet.source", 100.0)), source,
			PlayerProgress.builder().xp("MINING", (long) XpTable.MAX_XP).build());

		assertTrue(result.hasFigure());
		assertEquals(97_525.0 / 15, SourceEstimator.perAttemptDenominator(source, 99, true).orElseThrow(), EPS);
	}

	@Test
	public void invalidProgressDegradesToUnknownInsteadOfThrowing()
	{
		PetSource source = PetSource.builder().id("fixture_pet.source").label("Fixture rocks").rateModel(RateModel.SKILL_LEVEL_SCALED)
			.baseChance(3_000).verified(true).citations(FIXTURE_CITATION).build();

		DrynessResult result = SourceEstimator.estimate(petWith("MINING", source, method("fixture_pet.source", 1.0)), source,
			PlayerProgress.builder().xp("MINING", -5L).build());

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
	}

	// ---- Whole pets ----

	@Test
	public void heronCombinesChosenXpMethodWithIndependentTrawlerCount()
	{
		Pet heron = heronWithMinnowsMethod(26.0);
		long xp = 1_000_000;
		PlayerProgress progress = PlayerProgress.builder()
			.xp("FISHING", xp)
			.manualCount("heron.fishing_trawler", 40L)
			.build();

		DrynessResult result = SourceEstimator.estimatePet(heron, progress, "heron.minnows");

		double minnowsLog = (xp / 26.0) * Math.log1p(-1.0 / 977_778);
		double trawlerLog = 40 * Math.log1p(-1.0 / 5_000);
		assertEquals(minnowsLog + trawlerLog, result.getLogProbabilityStillDry().orElseThrow().getValue(), 1e-9);
		assertEquals(Confidence.ESTIMATED, result.getConfidence().orElseThrow());
		// generic_fishing is an alternative explanation of the same XP, not a missing source
		assertFalse(result.isUpperBound());
		assertFalse("combined figures mix units, so there is no single attempt count", result.getAttempts().isPresent());
	}

	@Test
	public void heronWithoutTrawlerCountIsAnUpperBound()
	{
		Pet heron = heronWithMinnowsMethod(26.0);
		long xp = 1_000_000;

		DrynessResult result = SourceEstimator.estimatePet(heron, PlayerProgress.builder().xp("FISHING", xp).build(), "heron.minnows");

		assertTrue(result.isUpperBound());
		assertEquals(1, result.getUnknownSourceCount());
		assertEquals((xp / 26.0) * Math.log1p(-1.0 / 977_778), result.getLogProbabilityStillDry().orElseThrow().getValue(), 1e-9);
		String explanation = result.getProbabilityStillDry().orElseThrow().getAssumption();
		assertTrue(explanation, explanation.contains("you are at least this dry"));
	}

	@Test
	public void withNoChoiceTheOnlyUsableXpSourceIsUsed()
	{
		// heron has three XP sources, but only minnows has both a verified rate and a verified method
		Pet heron = heronWithMinnowsMethod(26.0);
		long xp = 1_000_000;

		DrynessResult result = SourceEstimator.estimatePet(heron, PlayerProgress.builder().xp("FISHING", xp).build(), null);

		assertTrue(result.hasFigure());
		assertTrue(result.getExplanation(), result.getExplanation().contains("Minnows"));
	}

	@Test
	public void severalXpMethodsWithNoChoiceIsUnknownForThatPart()
	{
		// Two usable XP sources and no choice: the plugin must ask rather than pick one
		Pet heron = heronWithMinnowsMethod(26.0);
		PetSource secondUsable = PetSource.builder().id("heron.generic_fishing").label("Standard fishing methods")
			.rateModel(RateModel.SKILL_LEVEL_SCALED).baseChance(300_000).verified(true).citations(FIXTURE_CITATION).build();
		heron = Pet.builder().id(heron.getId()).name(heron.getName()).category(heron.getCategory()).skill(heron.getSkill())
			.sources(List.of(heron.getSources().get(0), heron.getSources().get(1), secondUsable))
			.methods(List.of(method("heron.minnows", 26.0), method("heron.generic_fishing", 40.0)))
			.build();

		DrynessResult result = SourceEstimator.estimatePet(heron, PlayerProgress.builder().xp("FISHING", 1_000_000L).build(), null);

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
		assertTrue(result.getExplanation(), result.getExplanation().contains("Choose which method you trained Fishing with"));
	}

	@Test
	public void choosingAnUnverifiedMethodIsUnknownNotAFallback()
	{
		Pet heron = heronWithMinnowsMethod(26.0);

		DrynessResult result = SourceEstimator.estimatePet(heron, PlayerProgress.builder().xp("FISHING", 1_000_000L).build(),
			"heron.generic_fishing");

		assertEquals(DrynessResult.Status.UNKNOWN, result.getStatus());
	}

	@Test
	public void wholeSeedDatasetWithNoProgressIsAllBlanksAndNeverThrows()
	{
		List<DrynessResult.Status> statuses = new ArrayList<>();
		for (Pet pet : seed.getPets())
		{
			DrynessResult result = SourceEstimator.estimatePet(pet, PlayerProgress.empty(), null);
			assertFalse(pet.getId() + " produced a number from no data", result.hasFigure());
			statuses.add(result.getStatus());
		}
		assertEquals(List.of(
			DrynessResult.Status.UNKNOWN,         // rock_golem
			DrynessResult.Status.UNKNOWN,         // heron
			DrynessResult.Status.UNKNOWN,         // example_boss_pet
			DrynessResult.Status.UNKNOWN,         // example_unique_conditional_pet
			DrynessResult.Status.NOT_APPLICABLE), // example_one_off_pet
			statuses);
	}

	@Test
	public void petWithNoSourcesIsUnknown()
	{
		Pet empty = Pet.builder().id("fixture_pet").name("Fixture").category(PetCategory.OTHER).build();

		assertEquals(DrynessResult.Status.UNKNOWN, SourceEstimator.estimatePet(empty, PlayerProgress.empty(), null).getStatus());
	}
}
