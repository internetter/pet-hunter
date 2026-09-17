package com.pethunter.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PetRepositoryTest
{
	private static final Gson GSON = new Gson();

	private static PetRepository load(String json)
	{
		// Single quotes keep the fixtures readable; they are swapped for JSON double quotes here.
		return PetRepository.load(GSON, new StringReader(json.replace('\'', '"')));
	}

	private static String pet(String id, String sourcesJson, String extra)
	{
		return "{'id':'" + id + "','name':'" + id + "','category':'BOSS','obtainableOn':['MAIN'],"
			+ "'sources':[" + sourcesJson + "]" + extra + "}";
	}

	private static String source(String id, String rateModel)
	{
		return "{'id':'" + id + "','label':'x','rateModel':'" + rateModel + "','baseChance':null,"
			+ "'flatRate':null,'verified':false,'sources':[]}";
	}

	private static String dataset(String... pets)
	{
		return "{'schemaVersion':1,'pets':[" + String.join(",", pets) + "]}";
	}

	@Test
	public void bundledDatasetLoadsWithoutSkippingAnything() throws IOException
	{
		JsonObject raw;
		try (Reader reader = new InputStreamReader(
			PetRepository.class.getResourceAsStream(PetRepository.BUNDLED_RESOURCE), StandardCharsets.UTF_8))
		{
			raw = GSON.fromJson(reader, JsonObject.class);
		}
		int rawSources = 0;
		int rawMethods = 0;
		for (JsonElement pet : raw.getAsJsonArray("pets"))
		{
			rawSources += pet.getAsJsonObject().getAsJsonArray("sources").size();
			JsonElement methods = pet.getAsJsonObject().get("methods");
			rawMethods += methods == null ? 0 : methods.getAsJsonArray().size();
		}

		PetRepository repo = PetRepository.loadBundled(GSON);

		assertFalse(repo.isEmpty());
		assertEquals(raw.getAsJsonArray("pets").size(), repo.getPets().size());
		assertEquals(rawSources, repo.getPets().stream().mapToInt(p -> p.getSources().size()).sum());
		assertEquals(rawMethods, repo.getPets().stream().mapToInt(p -> p.getMethods().size()).sum());
	}

	@Test
	public void loadsSeedShapes() throws IOException
	{
		PetRepository repo;
		try (Reader reader = new InputStreamReader(
			PetRepositoryTest.class.getResourceAsStream("/com/pethunter/fixtures/seed-dataset.json"), StandardCharsets.UTF_8))
		{
			repo = PetRepository.load(GSON, reader);
		}

		assertEquals(5, repo.getPets().size());

		Pet rockGolem = repo.getPet("rock_golem").orElseThrow();
		assertEquals(PetCategory.SKILLING, rockGolem.getCategory());
		assertEquals("MINING", rockGolem.getSkill());
		assertEquals(2, rockGolem.getSources().size());
		assertEquals(2, rockGolem.getMethods().size());
		assertTrue(rockGolem.getMethodFor("rock_golem.volcanic_mine").orElseThrow().getXpEfficient());

		PetSource minnows = repo.getSource("heron.minnows").orElseThrow();
		assertEquals(RateModel.STATIC_IGNORES_FORMULA, minnows.getRateModel());
		assertEquals(Integer.valueOf(977778), minnows.getFlatRate());
		assertTrue(minnows.isVerified());
		assertEquals(1, minnows.getCitations().size());

		ContributionRange trawler = repo.getSource("heron.fishing_trawler").orElseThrow().getContributionRange();
		assertEquals(5000, trawler.getAtMinimumContribution());
		assertEquals(2500, trawler.getAtMaximumContribution());
		assertEquals(5000, trawler.getRarestDenominator());

		assertEquals("heron", repo.getPetForSource("heron.minnows").orElseThrow().getId());
		assertEquals(RateModel.ONE_OFF,
			repo.getSource("example_one_off_pet.acquisition").orElseThrow().getRateModel());
	}

	@Test
	public void nullRateLoadsAsNull()
	{
		PetRepository repo = load(dataset(pet("p", source("p.a", "FLAT_PER_KILL"), "")));

		PetSource source = repo.getSource("p.a").orElseThrow();
		assertNull(source.getFlatRate());
		assertNull(source.getBaseChance());
		assertNull(source.getContributionRange());
		assertFalse(source.isVerified());
	}

	@Test
	public void missingMethodsLoadsAsEmptyList()
	{
		PetRepository repo = load(dataset(pet("p", source("p.a", "FLAT_PER_KILL"), "")));

		assertTrue(repo.getPet("p").orElseThrow().getMethods().isEmpty());
	}

	@Test
	public void unrecognisedRateModelSkipsOnlyThatSource()
	{
		PetRepository repo = load(dataset(
			pet("p", source("p.future", "SOME_FUTURE_MODEL") + "," + source("p.kills", "FLAT_PER_KILL"),
				",'methods':[{'id':'p.future','verified':false,'sources':[]}]"),
			pet("q", source("q.kills", "FLAT_PER_KILL"), "")));

		assertEquals(2, repo.getPets().size());
		Pet p = repo.getPet("p").orElseThrow();
		assertEquals(1, p.getSources().size());
		assertEquals("p.kills", p.getSources().get(0).getId());
		assertFalse(repo.getSource("p.future").isPresent());
		// The method for the skipped source has nothing to apply to, so it goes too
		assertTrue(p.getMethods().isEmpty());
	}

	@Test
	public void petWhoseOnlySourceIsSkippedStillLoads()
	{
		PetRepository repo = load(dataset(pet("p", source("p.future", "SOME_FUTURE_MODEL"), "")));

		assertTrue(repo.getPet("p").orElseThrow().getSources().isEmpty());
	}

	@Test
	public void wrongTypedRateSkipsSource()
	{
		String bad = "{'id':'p.bad','label':'x','rateModel':'FLAT_PER_KILL','flatRate':'5000','verified':true,'sources':[]}";
		String fractional = "{'id':'p.frac','label':'x','rateModel':'FLAT_PER_KILL','flatRate':12.5,'verified':true,'sources':[]}";
		PetRepository repo = load(dataset(pet("p", bad + "," + fractional + "," + source("p.ok", "FLAT_PER_KILL"), "")));

		assertEquals(1, repo.getPet("p").orElseThrow().getSources().size());
		assertTrue(repo.getSource("p.ok").isPresent());
	}

	@Test
	public void petMissingIdIsSkipped()
	{
		PetRepository repo = load(dataset("{'name':'nameless','sources':[]}", pet("p", source("p.a", "ONE_OFF"), "")));

		assertEquals(1, repo.getPets().size());
	}

	@Test
	public void duplicatePetIdKeepsFirst()
	{
		PetRepository repo = load(dataset(pet("p", source("p.a", "ONE_OFF"), ""), pet("p", source("p.b", "ONE_OFF"), "")));

		assertEquals(1, repo.getPets().size());
		assertTrue(repo.getSource("p.a").isPresent());
		assertFalse(repo.getSource("p.b").isPresent());
	}

	@Test
	public void unknownCategoryAndAccountTypeDegradeGracefully()
	{
		PetRepository repo = load(dataset("{'id':'p','name':'p','category':'NEW_THING','obtainableOn':['MAIN','LEAGUES'],"
			+ "'sources':[" + source("p.a", "ONE_OFF") + "]}"));

		Pet p = repo.getPet("p").orElseThrow();
		assertEquals(PetCategory.OTHER, p.getCategory());
		assertEquals(1, p.getObtainableOn().size());
	}

	@Test
	public void emptyDatasetLoads()
	{
		assertTrue(load(dataset()).isEmpty());
	}

	@Test
	public void malformedJsonYieldsEmptyRepository()
	{
		assertTrue(load("{'pets': [").isEmpty());
		assertTrue(load("[]").isEmpty());
		assertTrue(load("{'schemaVersion':1}").isEmpty());
		assertTrue(load("").isEmpty());
	}
}
