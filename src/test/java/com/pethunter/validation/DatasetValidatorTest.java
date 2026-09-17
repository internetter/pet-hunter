package com.pethunter.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Each test breaks a frozen copy of the seed dataset in exactly one way and asserts the validator
 * catches it. The copy is used rather than the live pets.json so these tests stay stable while the
 * dataset grows; the live file is checked by the validateDataset task. The schema is the live one.
 *
 * <p>Fixture URLs use the reserved .invalid TLD and fixture numbers are arbitrary: none of this is
 * game data.
 */
public class DatasetValidatorTest
{
	private static final String FIXTURE_URL = "https://fixture.invalid/not-a-real-source";

	private static Reader resource(String path)
	{
		return new InputStreamReader(DatasetValidatorTest.class.getResourceAsStream(path), StandardCharsets.UTF_8);
	}

	private static Reader schemaReader()
	{
		return resource("/com/pethunter/data/pets.schema.json");
	}

	private static JsonObject seed() throws IOException
	{
		return StrictJson.parse(resource("/com/pethunter/fixtures/seed-dataset.json")).getAsJsonObject();
	}

	private static JsonObject schema() throws IOException
	{
		return StrictJson.parse(schemaReader()).getAsJsonObject();
	}

	private static List<String> validate(JsonElement dataset, JsonElement schema)
	{
		return DatasetValidator.validate(new StringReader(dataset.toString()), new StringReader(schema.toString()));
	}

	private static List<String> validateSeedWith(Consumer<JsonObject> mutation) throws IOException
	{
		JsonObject dataset = seed();
		mutation.accept(dataset);
		return validate(dataset, schema());
	}

	private static JsonObject pet(JsonObject dataset, String id)
	{
		for (JsonElement pet : dataset.getAsJsonArray("pets"))
		{
			if (pet.getAsJsonObject().get("id").getAsString().equals(id))
			{
				return pet.getAsJsonObject();
			}
		}
		throw new AssertionError("no pet " + id);
	}

	private static JsonObject source(JsonObject dataset, String sourceId)
	{
		String petId = sourceId.substring(0, sourceId.indexOf('.'));
		for (JsonElement source : pet(dataset, petId).getAsJsonArray("sources"))
		{
			if (source.getAsJsonObject().get("id").getAsString().equals(sourceId))
			{
				return source.getAsJsonObject();
			}
		}
		throw new AssertionError("no source " + sourceId);
	}

	private static JsonArray citations(String... urls)
	{
		JsonArray array = new JsonArray();
		for (String url : urls)
		{
			array.add(url);
		}
		return array;
	}

	private static void assertSingleError(List<String> errors, String expectedFragment)
	{
		assertEquals("expected exactly one error but got " + errors, 1, errors.size());
		assertTrue("expected \"" + expectedFragment + "\" in " + errors, errors.get(0).contains(expectedFragment));
	}

	@Test
	public void seedDatasetIsValid() throws IOException
	{
		assertEquals(List.of(), validate(seed(), schema()));
	}

	// ---- JSON and schema ----

	@Test
	public void rejectsDuplicateJsonKeys()
	{
		List<String> errors = DatasetValidator.validate(
			new StringReader("{\"schemaVersion\":1,\"schemaVersion\":1,\"pets\":[]}"), schemaReader());

		assertSingleError(errors, "Duplicate key \"schemaVersion\"");
	}

	@Test
	public void rejectsTrailingCommaAndOtherLenientSyntax()
	{
		List<String> errors = DatasetValidator.validate(
			new StringReader("{\"schemaVersion\":1,\"pets\":[],}"), schemaReader());

		assertSingleError(errors, "dataset is not valid JSON");
	}

	@Test
	public void rejectsUnknownPetProperty() throws IOException
	{
		assertSingleError(validateSeedWith(d -> pet(d, "heron").addProperty("dropRate", 5000)),
			"property \"dropRate\" is not allowed");
	}

	@Test
	public void rejectsMissingRequiredProperty() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "heron.minnows").remove("verified")),
			"missing required property \"verified\"");
	}

	@Test
	public void rejectsUnknownRateModel() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "heron.minnows").addProperty("rateModel", "FLAT_PER_MINNOW")),
			"is not one of");
	}

	@Test
	public void rejectsFractionalIntegerRate() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "heron.minnows").addProperty("flatRate", 977778.5)),
			"expected type");
	}

	@Test
	public void rejectsUnsupportedSchemaKeywordEvenWhereNoDataReachesIt() throws IOException
	{
		JsonObject schema = schema();
		schema.getAsJsonObject("definitions").getAsJsonObject("method").getAsJsonObject("properties")
			.getAsJsonObject("xpPerAction").addProperty("multipleOf", 0.5);

		assertSingleError(validate(seed(), schema), "unsupported keyword \"multipleOf\"");
	}

	// ---- docs/DATA.md rules ----

	@Test
	public void rule1_duplicatePetId() throws IOException
	{
		List<String> errors = validateSeedWith(d -> d.getAsJsonArray("pets").add(pet(d, "heron").deepCopy()));

		assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.startsWith("[rule 1]") && e.contains("duplicate pet id")));
		assertTrue(errors.stream().allMatch(e -> e.startsWith("[rule 1]")));
	}

	@Test
	public void rule1_sourceIdMustCarryPetPrefix() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "example_boss_pet.kills").addProperty("id", "other_pet.kills")),
			"[rule 1]");
	}

	@Test
	public void rule1_duplicateSourceId() throws IOException
	{
		assertSingleError(validateSeedWith(d -> pet(d, "heron").getAsJsonArray("sources")
				.add(source(d, "heron.fishing_trawler").deepCopy())),
			"duplicate source id");
	}

	@Test
	public void rule1_methodIdMayEqualSourceId() throws IOException
	{
		// rock_golem's methods share ids with its sources; that must not count as a duplicate
		assertEquals(List.of(), validate(seed(), schema()));
	}

	@Test
	public void rule2_verifiedWithoutSourceUrl() throws IOException
	{
		assertSingleError(validateSeedWith(d ->
		{
			JsonObject s = source(d, "example_boss_pet.kills");
			s.addProperty("flatRate", 5000);
			s.addProperty("verified", true);
		}), "[rule 2]");
	}

	@Test
	public void rule2_sourceUrlMustBeHttp() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "heron.minnows").add("sources", citations("ftp://wiki/minnows"))),
			"[rule 2]");
	}

	@Test
	public void rule2_verifiedMethodWithoutSourceUrl() throws IOException
	{
		assertSingleError(validateSeedWith(d -> pet(d, "rock_golem").getAsJsonArray("methods").get(0).getAsJsonObject()
				.addProperty("verified", true)),
			"[rule 2]");
	}

	@Test
	public void rule3_rateWithoutVerification() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "rock_golem.gem_rocks_shilo").addProperty("baseChance", 300000)),
			"[rule 3]");
	}

	@Test
	public void rule3_xpPerActionWithoutVerification() throws IOException
	{
		assertSingleError(validateSeedWith(d -> pet(d, "rock_golem").getAsJsonArray("methods").get(0).getAsJsonObject()
				.addProperty("xpPerAction", 65)),
			"[rule 3]");
	}

	@Test
	public void rule3_contributionRangeCountsAsARate() throws IOException
	{
		assertSingleError(validateSeedWith(d ->
		{
			JsonObject s = source(d, "heron.fishing_trawler");
			s.addProperty("verified", false);
			s.add("sources", new JsonArray());
		}), "[rule 3]");
	}

	@Test
	public void rule4_wrongRateFieldForModel() throws IOException
	{
		List<String> errors = validateSeedWith(d ->
		{
			JsonObject s = source(d, "rock_golem.gem_rocks_shilo");
			s.addProperty("flatRate", 300000);
			s.addProperty("verified", true);
			s.add("sources", citations(FIXTURE_URL));
		});

		// Both halves of the mistake are reported: the wrong field is set and the right one is missing
		assertEquals(errors.toString(), 2, errors.size());
		assertTrue(errors.stream().allMatch(e -> e.startsWith("[rule 4]")));
		assertTrue(errors.stream().anyMatch(e -> e.contains("SKILL_LEVEL_SCALED sources use baseChance")));
		assertTrue(errors.stream().anyMatch(e -> e.contains("must populate baseChance")));
	}

	@Test
	public void rule4_verifiedSourceMustPopulateItsRate() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "heron.minnows").add("flatRate", JsonNull.INSTANCE)),
			"must populate flatRate");
	}

	@Test
	public void rule4_oneOffCarriesNoRate() throws IOException
	{
		assertSingleError(validateSeedWith(d -> source(d, "example_one_off_pet.acquisition").addProperty("flatRate", 1)),
			"ONE_OFF sources carry no rate");
	}

	@Test
	public void rule4_unverifiedSourceMayLeaveEveryRateNull() throws IOException
	{
		// The original 'exactly one of baseChance/flatRate' wording rejected this, the normal unpopulated state
		JsonObject gemRocks = source(seed(), "rock_golem.gem_rocks_shilo");
		assertTrue(gemRocks.get("baseChance").isJsonNull());
		assertTrue(gemRocks.get("flatRate").isJsonNull());
		assertEquals(List.of(), validate(seed(), schema()));
	}

	@Test
	public void rule5_methodWithoutMatchingSource() throws IOException
	{
		assertSingleError(validateSeedWith(d -> pet(d, "rock_golem").getAsJsonArray("methods").get(0).getAsJsonObject()
				.addProperty("id", "rock_golem.essence")),
			"[rule 5]");
	}

	@Test
	public void rule6_skillRequiredForSkillModels() throws IOException
	{
		assertSingleError(validateSeedWith(d -> pet(d, "heron").add("skill", JsonNull.INSTANCE)), "[rule 6]");
	}

	@Test
	public void rule6_skillMustBeARuneLiteSkill() throws IOException
	{
		assertSingleError(validateSeedWith(d -> pet(d, "heron").addProperty("skill", "Fishing")), "is not a RuneLite Skill");
		assertSingleError(validateSeedWith(d -> pet(d, "heron").addProperty("skill", "OVERALL")), "is not a RuneLite Skill");
	}

	@Test
	public void rule7_baseChanceMustExceed2475() throws IOException
	{
		assertSingleError(validateSeedWith(d ->
		{
			JsonObject s = source(d, "rock_golem.gem_rocks_shilo");
			s.addProperty("baseChance", 2475);
			s.addProperty("verified", true);
			s.add("sources", citations(FIXTURE_URL));
		}), "[rule 7]");
	}

	@Test
	public void rule7_contributionRangeValuesAtLeastOne() throws IOException
	{
		assertSingleError(validateSeedWith(d ->
		{
			JsonArray range = new JsonArray();
			range.add(new JsonPrimitive(0));
			range.add(new JsonPrimitive(2500));
			source(d, "heron.fishing_trawler").add("contributionRange", range);
		}), "[rule 7]");
	}
}
