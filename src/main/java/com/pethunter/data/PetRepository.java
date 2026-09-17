package com.pethunter.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads and indexes the pet dataset.
 *
 * <p>Loading never throws. The build validates the bundled dataset strictly, so anything this
 * class rejects at runtime indicates a broken build or a hand-edited jar: the offending entry is
 * logged and skipped, and the rest of the dataset still loads. An unreadable file yields an empty
 * repository, which the panel must render.
 */
@Slf4j
public final class PetRepository
{
	static final String BUNDLED_RESOURCE = "pets.json";

	private static final PetRepository EMPTY = new PetRepository(List.of());

	private final List<Pet> pets;
	private final Map<String, Pet> petsById;
	private final Map<String, PetSource> sourcesById;
	private final Map<String, Pet> petsBySourceId;

	private PetRepository(List<Pet> pets)
	{
		Map<String, Pet> byId = new LinkedHashMap<>();
		Map<String, PetSource> sources = new LinkedHashMap<>();
		Map<String, Pet> bySource = new LinkedHashMap<>();
		for (Pet pet : pets)
		{
			byId.put(pet.getId(), pet);
			for (PetSource source : pet.getSources())
			{
				sources.put(source.getId(), source);
				bySource.put(source.getId(), pet);
			}
		}
		this.pets = List.copyOf(pets);
		this.petsById = Collections.unmodifiableMap(byId);
		this.sourcesById = Collections.unmodifiableMap(sources);
		this.petsBySourceId = Collections.unmodifiableMap(bySource);
	}

	public static PetRepository empty()
	{
		return EMPTY;
	}

	/**
	 * Loads the dataset bundled with the plugin.
	 */
	public static PetRepository loadBundled(Gson gson)
	{
		try (InputStream in = PetRepository.class.getResourceAsStream(BUNDLED_RESOURCE))
		{
			if (in == null)
			{
				log.error("Bundled pet dataset {} is missing", BUNDLED_RESOURCE);
				return empty();
			}
			return load(gson, new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.error("Unable to read bundled pet dataset", e);
			return empty();
		}
	}

	public static PetRepository load(Gson gson, Reader reader)
	{
		JsonElement root;
		try
		{
			root = gson.fromJson(reader, JsonElement.class);
		}
		catch (JsonParseException e)
		{
			log.error("Pet dataset is not valid JSON", e);
			return empty();
		}

		if (root == null || !root.isJsonObject())
		{
			log.error("Pet dataset root is not a JSON object");
			return empty();
		}

		JsonElement petsElement = root.getAsJsonObject().get("pets");
		if (petsElement == null || !petsElement.isJsonArray())
		{
			log.error("Pet dataset has no \"pets\" array");
			return empty();
		}

		List<Pet> pets = new ArrayList<>();
		Set<String> petIds = new HashSet<>();
		Set<String> sourceIds = new HashSet<>();
		int index = 0;
		for (JsonElement element : petsElement.getAsJsonArray())
		{
			Pet pet = parsePet(element, index++, sourceIds);
			if (pet == null)
			{
				continue;
			}
			if (!petIds.add(pet.getId()))
			{
				log.warn("Skipping duplicate pet id {}", pet.getId());
				continue;
			}
			pets.add(pet);
		}
		return new PetRepository(pets);
	}

	public List<Pet> getPets()
	{
		return pets;
	}

	public Optional<Pet> getPet(String petId)
	{
		return Optional.ofNullable(petsById.get(petId));
	}

	public Optional<PetSource> getSource(String sourceId)
	{
		return Optional.ofNullable(sourcesById.get(sourceId));
	}

	public Optional<Pet> getPetForSource(String sourceId)
	{
		return Optional.ofNullable(petsBySourceId.get(sourceId));
	}

	public boolean isEmpty()
	{
		return pets.isEmpty();
	}

	@Nullable
	private static Pet parsePet(JsonElement element, int index, Set<String> seenSourceIds)
	{
		try
		{
			JsonObject obj = element.getAsJsonObject();
			String id = requireString(obj, "id");
			String name = requireString(obj, "name");

			List<PetSource> sources = new ArrayList<>();
			Set<String> sourceIdsOnPet = new HashSet<>();
			for (JsonElement sourceElement : optArray(obj, "sources"))
			{
				PetSource source = parseSource(sourceElement, id);
				if (source == null)
				{
					continue;
				}
				if (!seenSourceIds.add(source.getId()))
				{
					log.warn("Skipping duplicate source id {} on pet {}", source.getId(), id);
					continue;
				}
				sourceIdsOnPet.add(source.getId());
				sources.add(source);
			}
			if (sources.isEmpty())
			{
				log.warn("Pet {} has no usable sources; it will render as UNKNOWN", id);
			}

			List<HuntMethod> methods = new ArrayList<>();
			Set<String> methodIds = new HashSet<>();
			for (JsonElement methodElement : optArray(obj, "methods"))
			{
				HuntMethod method = parseMethod(methodElement, id);
				if (method == null)
				{
					continue;
				}
				if (!sourceIdsOnPet.contains(method.getId()))
				{
					log.warn("Skipping method {} on pet {}: no matching source", method.getId(), id);
					continue;
				}
				if (!methodIds.add(method.getId()))
				{
					log.warn("Skipping duplicate method id {} on pet {}", method.getId(), id);
					continue;
				}
				methods.add(method);
			}

			return Pet.builder()
				.id(id)
				.name(name)
				.itemId(optInteger(obj, "itemId"))
				.category(parseCategory(optString(obj, "category"), id))
				.skill(optString(obj, "skill"))
				.collectionLogPage(optString(obj, "collectionLogPage"))
				.obtainableOn(parseAccountTypes(optArray(obj, "obtainableOn"), id))
				.sources(sources)
				.methods(methods)
				.build();
		}
		catch (RuntimeException e)
		{
			log.warn("Skipping malformed pet at index {}: {}", index, e.getMessage());
			return null;
		}
	}

	@Nullable
	private static PetSource parseSource(JsonElement element, String petId)
	{
		try
		{
			JsonObject obj = element.getAsJsonObject();
			String id = requireString(obj, "id");
			String rateModelName = requireString(obj, "rateModel");
			RateModel rateModel;
			try
			{
				rateModel = RateModel.valueOf(rateModelName);
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Skipping source {} on pet {}: unrecognised rateModel {}", id, petId, rateModelName);
				return null;
			}

			return PetSource.builder()
				.id(id)
				.label(optString(obj, "label"))
				.rateModel(rateModel)
				.baseChance(optInteger(obj, "baseChance"))
				.flatRate(optDouble(obj, "flatRate"))
				.contributionRange(parseContributionRange(obj))
				.minLevel(optInteger(obj, "minLevel"))
				.counterKey(optString(obj, "counterKey"))
				.countWarning(optString(obj, "countWarning"))
				.verified(optBoolean(obj, "verified"))
				.citations(parseStrings(optArray(obj, "sources")))
				.notes(optString(obj, "notes"))
				.build();
		}
		catch (RuntimeException e)
		{
			log.warn("Skipping malformed source on pet {}: {}", petId, e.getMessage());
			return null;
		}
	}

	@Nullable
	private static HuntMethod parseMethod(JsonElement element, String petId)
	{
		try
		{
			JsonObject obj = element.getAsJsonObject();
			return HuntMethod.builder()
				.id(requireString(obj, "id"))
				.actionsPerHour(optDouble(obj, "actionsPerHour"))
				.xpPerAction(optDouble(obj, "xpPerAction"))
				.requirements(parseStrings(optArray(obj, "requirements")))
				.xpEfficient(optNullableBoolean(obj, "xpEfficient"))
				.verified(optBoolean(obj, "verified"))
				.citations(parseStrings(optArray(obj, "sources")))
				.notes(optString(obj, "notes"))
				.build();
		}
		catch (RuntimeException e)
		{
			log.warn("Skipping malformed method on pet {}: {}", petId, e.getMessage());
			return null;
		}
	}

	@Nullable
	private static ContributionRange parseContributionRange(JsonObject obj)
	{
		JsonElement element = obj.get("contributionRange");
		if (element == null || element.isJsonNull())
		{
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		if (array.size() != 2)
		{
			throw new IllegalStateException("contributionRange must have exactly two values");
		}
		return new ContributionRange(asInteger(array.get(0), "contributionRange"),
			asInteger(array.get(1), "contributionRange"));
	}

	private static PetCategory parseCategory(@Nullable String name, String petId)
	{
		if (name != null)
		{
			try
			{
				return PetCategory.valueOf(name);
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Pet {} has unrecognised category {}; grouping it under OTHER", petId, name);
			}
		}
		return PetCategory.OTHER;
	}

	private static List<AccountType> parseAccountTypes(JsonArray array, String petId)
	{
		List<AccountType> types = new ArrayList<>();
		for (JsonElement element : array)
		{
			String name = element.getAsString();
			try
			{
				types.add(AccountType.valueOf(name));
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Pet {} has unrecognised account type {}; ignoring it", petId, name);
			}
		}
		return types;
	}

	private static List<String> parseStrings(JsonArray array)
	{
		List<String> values = new ArrayList<>(array.size());
		for (JsonElement element : array)
		{
			values.add(element.getAsJsonPrimitive().getAsString());
		}
		return values;
	}

	private static String requireString(JsonObject obj, String key)
	{
		String value = optString(obj, key);
		if (value == null)
		{
			throw new IllegalStateException("missing required field " + key);
		}
		return value;
	}

	@Nullable
	private static String optString(JsonObject obj, String key)
	{
		JsonElement element = obj.get(key);
		if (element == null || element.isJsonNull())
		{
			return null;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isString())
		{
			throw new IllegalStateException(key + " must be a string");
		}
		return primitive.getAsString();
	}

	@Nullable
	private static Integer optInteger(JsonObject obj, String key)
	{
		JsonElement element = obj.get(key);
		if (element == null || element.isJsonNull())
		{
			return null;
		}
		return asInteger(element, key);
	}

	private static int asInteger(JsonElement element, String key)
	{
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isNumber())
		{
			throw new IllegalStateException(key + " must be a number");
		}
		try
		{
			// intValueExact rejects fractions and overflow rather than truncating a rate
			return new BigDecimal(primitive.getAsString()).intValueExact();
		}
		catch (ArithmeticException | NumberFormatException e)
		{
			throw new IllegalStateException(key + " must be an integer");
		}
	}

	@Nullable
	private static Double optDouble(JsonObject obj, String key)
	{
		JsonElement element = obj.get(key);
		if (element == null || element.isJsonNull())
		{
			return null;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isNumber())
		{
			throw new IllegalStateException(key + " must be a number");
		}
		double value = primitive.getAsDouble();
		if (!Double.isFinite(value))
		{
			throw new IllegalStateException(key + " must be finite");
		}
		return value;
	}

	/**
	 * Missing or null is treated as false: an entry is unverified unless it says otherwise.
	 */
	private static boolean optBoolean(JsonObject obj, String key)
	{
		Boolean value = optNullableBoolean(obj, key);
		return value != null && value;
	}

	@Nullable
	private static Boolean optNullableBoolean(JsonObject obj, String key)
	{
		JsonElement element = obj.get(key);
		if (element == null || element.isJsonNull())
		{
			return null;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isBoolean())
		{
			throw new IllegalStateException(key + " must be a boolean");
		}
		return primitive.getAsBoolean();
	}

	private static JsonArray optArray(JsonObject obj, String key)
	{
		JsonElement element = obj.get(key);
		if (element == null || element.isJsonNull())
		{
			return new JsonArray();
		}
		if (!element.isJsonArray())
		{
			throw new IllegalStateException(key + " must be an array");
		}
		return element.getAsJsonArray();
	}
}
