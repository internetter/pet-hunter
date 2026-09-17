package com.pethunter.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * The dataset assertions from docs/DATA.md "Validation" that JSON Schema cannot express.
 * Assumes the document has already passed schema validation.
 *
 * <p>These rules enforce the project's golden rule: no rate without a source. Do not weaken one
 * to get a commit through.
 */
final class DatasetRules
{
	/** 99 * 25: at or below this, 1/(baseChance - level * 25) is not a probability at level 99. */
	static final int MIN_EXCLUSIVE_BASE_CHANCE = 99 * 25;

	private static final String[] SOURCE_RATE_FIELDS = {"baseChance", "flatRate", "contributionRange"};
	private static final String[] METHOD_RATE_FIELDS = {"xpPerAction", "actionsPerHour"};

	private static final Map<String, String> RATE_FIELD_BY_MODEL = Map.of(
		"FLAT_PER_KILL", "flatRate",
		"FLAT_PER_ROLL", "flatRate",
		"STATIC_IGNORES_FORMULA", "flatRate",
		"UNIQUE_CONDITIONAL", "flatRate",
		"SKILL_LEVEL_SCALED", "baseChance",
		"CONTRIBUTION_SCALED", "contributionRange");

	private static final Set<String> SKILL_MODELS = Set.of("SKILL_LEVEL_SCALED", "STATIC_IGNORES_FORMULA");

	private final List<String> errors = new ArrayList<>();
	private final Set<String> petIds = new HashSet<>();
	private final Set<String> sourceIds = new HashSet<>();
	private final Set<String> methodIds = new HashSet<>();

	private DatasetRules()
	{
	}

	static List<String> check(JsonObject dataset)
	{
		DatasetRules rules = new DatasetRules();
		JsonArray pets = dataset.getAsJsonArray("pets");
		for (int i = 0; i < pets.size(); i++)
		{
			rules.checkPet(pets.get(i).getAsJsonObject(), "/pets/" + i);
		}
		return rules.errors;
	}

	private void checkPet(JsonObject pet, String path)
	{
		String petId = pet.get("id").getAsString();
		if (!petIds.add(petId))
		{
			error(1, path + "/id", "duplicate pet id \"" + petId + "\"");
		}

		boolean needsSkill = false;
		Set<String> sourceIdsOnPet = new HashSet<>();
		JsonArray sources = pet.getAsJsonArray("sources");
		for (int i = 0; i < sources.size(); i++)
		{
			JsonObject source = sources.get(i).getAsJsonObject();
			String sourcePath = path + "/sources/" + i;
			String sourceId = source.get("id").getAsString();
			sourceIdsOnPet.add(sourceId);
			checkChildId(sourceId, petId, sourceIds, "source", sourcePath);
			checkSource(source, sourcePath);
			needsSkill |= SKILL_MODELS.contains(source.get("rateModel").getAsString());
		}

		checkSkill(pet, needsSkill, path);

		JsonArray methods = pet.has("methods") ? pet.getAsJsonArray("methods") : new JsonArray();
		for (int i = 0; i < methods.size(); i++)
		{
			JsonObject method = methods.get(i).getAsJsonObject();
			String methodPath = path + "/methods/" + i;
			String methodId = method.get("id").getAsString();
			checkChildId(methodId, petId, methodIds, "method", methodPath);
			if (!sourceIdsOnPet.contains(methodId))
			{
				error(5, methodPath + "/id", "method \"" + methodId + "\" matches no source on pet \"" + petId + "\"");
			}
			checkCitations(method, methodPath);
			for (String field : METHOD_RATE_FIELDS)
			{
				if (isSet(method, field) && !isVerified(method))
				{
					error(3, methodPath + "/" + field, "is set but verified is false");
				}
			}
		}
	}

	private void checkChildId(String id, String petId, Set<String> seen, String kind, String path)
	{
		if (!id.startsWith(petId + "."))
		{
			error(1, path + "/id", kind + " id \"" + id + "\" must be prefixed with its pet id \"" + petId + ".\"");
		}
		if (!seen.add(id))
		{
			error(1, path + "/id", "duplicate " + kind + " id \"" + id + "\"");
		}
	}

	private void checkSource(JsonObject source, String path)
	{
		String model = source.get("rateModel").getAsString();

		if ("ONE_OFF".equals(model))
		{
			for (String field : SOURCE_RATE_FIELDS)
			{
				if (isSet(source, field))
				{
					error(4, path + "/" + field, "ONE_OFF sources carry no rate, so this must be null");
				}
			}
			return;
		}

		checkCitations(source, path);

		String rateField = RATE_FIELD_BY_MODEL.get(model);
		for (String field : SOURCE_RATE_FIELDS)
		{
			if (!isSet(source, field))
			{
				continue;
			}
			if (!isVerified(source))
			{
				error(3, path + "/" + field, "is set but verified is false");
			}
			if (!field.equals(rateField))
			{
				error(4, path + "/" + field, model + " sources use " + rateField + ", so this must be null");
			}
		}
		if (isVerified(source) && !isSet(source, rateField))
		{
			error(4, path + "/" + rateField, "verified " + model + " source must populate " + rateField);
		}

		if (isSet(source, "baseChance") && source.get("baseChance").getAsBigDecimal()
			.compareTo(BigDecimal.valueOf(MIN_EXCLUSIVE_BASE_CHANCE)) <= 0)
		{
			error(7, path + "/baseChance", "must be greater than " + MIN_EXCLUSIVE_BASE_CHANCE);
		}
		if (isSet(source, "contributionRange"))
		{
			for (JsonElement value : source.getAsJsonArray("contributionRange"))
			{
				if (value.getAsBigDecimal().compareTo(BigDecimal.ONE) < 0)
				{
					error(7, path + "/contributionRange", "values must be at least 1");
				}
			}
		}
	}

	private void checkCitations(JsonObject obj, String path)
	{
		JsonArray citations = obj.getAsJsonArray("sources");
		if (isVerified(obj) && citations.size() == 0)
		{
			error(2, path + "/sources", "verified is true but no source URL is given");
		}
		for (int i = 0; i < citations.size(); i++)
		{
			String url = citations.get(i).getAsString();
			if (!isHttpUrl(url))
			{
				error(2, path + "/sources/" + i, "\"" + url + "\" is not an http(s) URL");
			}
		}
	}

	private void checkSkill(JsonObject pet, boolean needsSkill, String path)
	{
		String skill = isSet(pet, "skill") ? pet.get("skill").getAsString() : null;
		if (skill == null)
		{
			if (needsSkill)
			{
				error(6, path + "/skill", "required because a source is SKILL_LEVEL_SCALED or STATIC_IGNORES_FORMULA");
			}
			return;
		}
		if (!isRealSkill(skill))
		{
			error(6, path + "/skill", "\"" + skill + "\" is not a RuneLite Skill");
		}
	}

	@SuppressWarnings("deprecation")
	private static boolean isRealSkill(String name)
	{
		try
		{
			return Skill.valueOf(name) != Skill.OVERALL;
		}
		catch (IllegalArgumentException e)
		{
			return false;
		}
	}

	private static boolean isHttpUrl(String value)
	{
		try
		{
			URI uri = new URI(value);
			return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
		}
		catch (URISyntaxException e)
		{
			return false;
		}
	}

	private static boolean isSet(JsonObject obj, String field)
	{
		return obj.has(field) && !obj.get(field).isJsonNull();
	}

	private static boolean isVerified(JsonObject obj)
	{
		return obj.get("verified").getAsBoolean();
	}

	private void error(int rule, String path, String message)
	{
		errors.add("[rule " + rule + "] " + path + ": " + message);
	}
}
