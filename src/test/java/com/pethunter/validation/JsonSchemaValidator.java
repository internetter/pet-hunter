package com.pethunter.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a JSON document against the subset of JSON Schema draft-07 that pets.schema.json
 * uses. This exists so the build needs no new dependency.
 *
 * <p>Any keyword outside the supported set is reported as an error rather than ignored, so a
 * schema edit can never silently disable a check. Extend {@link #SUPPORTED} and implement the
 * keyword before using it in the schema.
 */
final class JsonSchemaValidator
{
	private static final Set<String> ANNOTATIONS = Set.of("$schema", "title", "description");

	private static final Set<String> SUPPORTED = Set.of(
		"type", "enum", "const", "$ref", "definitions",
		"properties", "required", "additionalProperties",
		"items", "minItems", "maxItems",
		"pattern", "format",
		"minimum", "maximum", "exclusiveMinimum");

	private final JsonObject rootSchema;
	private final List<String> errors = new ArrayList<>();

	private JsonSchemaValidator(JsonObject rootSchema)
	{
		this.rootSchema = rootSchema;
	}

	static List<String> validate(JsonElement document, JsonElement schema)
	{
		if (!schema.isJsonObject())
		{
			return List.of("schema: root must be an object");
		}
		JsonSchemaValidator validator = new JsonSchemaValidator(schema.getAsJsonObject());
		validator.checkKeywords(schema.getAsJsonObject(), "#");
		if (!validator.errors.isEmpty())
		{
			// A schema we cannot fully interpret must not produce a partial pass
			return validator.errors;
		}
		validator.check(document, schema.getAsJsonObject(), "", "#");
		return validator.errors;
	}

	/**
	 * Walks the whole schema, including branches no document value reaches, and reports every
	 * keyword this validator does not implement.
	 */
	private void checkKeywords(JsonObject schema, String schemaPath)
	{
		for (Map.Entry<String, JsonElement> entry : schema.entrySet())
		{
			String keyword = entry.getKey();
			JsonElement child = entry.getValue();
			if (!ANNOTATIONS.contains(keyword) && !SUPPORTED.contains(keyword))
			{
				errors.add("schema " + schemaPath + ": unsupported keyword \"" + keyword + "\"");
			}
			else if (("properties".equals(keyword) || "definitions".equals(keyword)) && child.isJsonObject())
			{
				for (Map.Entry<String, JsonElement> named : child.getAsJsonObject().entrySet())
				{
					if (named.getValue().isJsonObject())
					{
						checkKeywords(named.getValue().getAsJsonObject(), schemaPath + "/" + keyword + "/" + named.getKey());
					}
				}
			}
			else if (("items".equals(keyword) || "additionalProperties".equals(keyword)) && child.isJsonObject())
			{
				checkKeywords(child.getAsJsonObject(), schemaPath + "/" + keyword);
			}
		}
	}

	private void check(JsonElement value, JsonObject schema, String path, String schemaPath)
	{
		if (schema.has("$ref"))
		{
			String ref = schema.get("$ref").getAsString();
			JsonObject target = resolve(ref);
			if (target == null)
			{
				errors.add("schema " + schemaPath + ": cannot resolve $ref " + ref);
				return;
			}
			check(value, target, path, ref);
		}

		if (schema.has("type") && !matchesType(value, schema.get("type")))
		{
			errors.add(at(path) + "expected type " + schema.get("type") + " but found " + describe(value));
			// Remaining keywords assume the declared type
			return;
		}

		if (schema.has("enum") && !contains(schema.getAsJsonArray("enum"), value))
		{
			errors.add(at(path) + describe(value) + " is not one of " + schema.get("enum"));
		}

		if (schema.has("const") && !jsonEquals(schema.get("const"), value))
		{
			errors.add(at(path) + "must equal " + schema.get("const"));
		}

		if (value.isJsonObject())
		{
			checkObject(value.getAsJsonObject(), schema, path, schemaPath);
		}
		else if (value.isJsonArray())
		{
			checkArray(value.getAsJsonArray(), schema, path, schemaPath);
		}
		else if (value.isJsonPrimitive())
		{
			JsonPrimitive primitive = value.getAsJsonPrimitive();
			if (primitive.isString())
			{
				checkString(primitive.getAsString(), schema, path);
			}
			else if (primitive.isNumber())
			{
				checkNumber(new BigDecimal(primitive.getAsString()), schema, path);
			}
		}
	}

	private void checkObject(JsonObject obj, JsonObject schema, String path, String schemaPath)
	{
		if (schema.has("required"))
		{
			for (JsonElement key : schema.getAsJsonArray("required"))
			{
				if (!obj.has(key.getAsString()))
				{
					errors.add(at(path) + "missing required property \"" + key.getAsString() + "\"");
				}
			}
		}

		JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
		JsonElement additional = schema.get("additionalProperties");

		for (Map.Entry<String, JsonElement> entry : obj.entrySet())
		{
			String childPath = path + "/" + entry.getKey();
			if (properties.has(entry.getKey()))
			{
				check(entry.getValue(), properties.getAsJsonObject(entry.getKey()), childPath,
					schemaPath + "/properties/" + entry.getKey());
			}
			else if (additional != null)
			{
				if (additional.isJsonPrimitive() && !additional.getAsBoolean())
				{
					errors.add(at(path) + "property \"" + entry.getKey() + "\" is not allowed");
				}
				else if (additional.isJsonObject())
				{
					check(entry.getValue(), additional.getAsJsonObject(), childPath, schemaPath + "/additionalProperties");
				}
			}
		}
	}

	private void checkArray(JsonArray array, JsonObject schema, String path, String schemaPath)
	{
		if (schema.has("minItems") && array.size() < schema.get("minItems").getAsInt())
		{
			errors.add(at(path) + "must have at least " + schema.get("minItems") + " items");
		}
		if (schema.has("maxItems") && array.size() > schema.get("maxItems").getAsInt())
		{
			errors.add(at(path) + "must have at most " + schema.get("maxItems") + " items");
		}
		if (schema.has("items"))
		{
			if (!schema.get("items").isJsonObject())
			{
				errors.add("schema " + schemaPath + ": only a single schema is supported for \"items\"");
				return;
			}
			for (int i = 0; i < array.size(); i++)
			{
				check(array.get(i), schema.getAsJsonObject("items"), path + "/" + i, schemaPath + "/items");
			}
		}
	}

	private void checkString(String value, JsonObject schema, String path)
	{
		if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").getAsString()).matcher(value).find())
		{
			errors.add(at(path) + "\"" + value + "\" does not match pattern " + schema.get("pattern").getAsString());
		}
		if (schema.has("format"))
		{
			String format = schema.get("format").getAsString();
			if (!"uri".equals(format))
			{
				errors.add(at(path) + "unsupported format \"" + format + "\"");
			}
			else if (!isAbsoluteUri(value))
			{
				errors.add(at(path) + "\"" + value + "\" is not an absolute URI");
			}
		}
	}

	private void checkNumber(BigDecimal value, JsonObject schema, String path)
	{
		if (schema.has("minimum") && value.compareTo(schema.get("minimum").getAsBigDecimal()) < 0)
		{
			errors.add(at(path) + value + " is below the minimum " + schema.get("minimum"));
		}
		if (schema.has("maximum") && value.compareTo(schema.get("maximum").getAsBigDecimal()) > 0)
		{
			errors.add(at(path) + value + " is above the maximum " + schema.get("maximum"));
		}
		if (schema.has("exclusiveMinimum") && value.compareTo(schema.get("exclusiveMinimum").getAsBigDecimal()) <= 0)
		{
			errors.add(at(path) + value + " must be greater than " + schema.get("exclusiveMinimum"));
		}
	}

	private JsonObject resolve(String ref)
	{
		if (!ref.startsWith("#"))
		{
			return null;
		}
		JsonElement current = rootSchema;
		for (String token : ref.substring(1).split("/"))
		{
			if (token.isEmpty())
			{
				continue;
			}
			if (!current.isJsonObject() || !current.getAsJsonObject().has(token))
			{
				return null;
			}
			current = current.getAsJsonObject().get(token);
		}
		return current.isJsonObject() ? current.getAsJsonObject() : null;
	}

	private static boolean matchesType(JsonElement value, JsonElement type)
	{
		if (type.isJsonArray())
		{
			for (JsonElement t : type.getAsJsonArray())
			{
				if (matchesType(value, t.getAsString()))
				{
					return true;
				}
			}
			return false;
		}
		return matchesType(value, type.getAsString());
	}

	private static boolean matchesType(JsonElement value, String type)
	{
		switch (type)
		{
			case "null":
				return value.isJsonNull();
			case "object":
				return value.isJsonObject();
			case "array":
				return value.isJsonArray();
			case "string":
				return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
			case "boolean":
				return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
			case "number":
				return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
			case "integer":
				return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
					&& isIntegral(new BigDecimal(value.getAsString()));
			default:
				throw new IllegalArgumentException("Unsupported JSON Schema type " + type);
		}
	}

	private static boolean isIntegral(BigDecimal value)
	{
		return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
	}

	private static boolean contains(JsonArray values, JsonElement value)
	{
		for (JsonElement candidate : values)
		{
			if (jsonEquals(candidate, value))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean jsonEquals(JsonElement a, JsonElement b)
	{
		if (a.isJsonPrimitive() && b.isJsonPrimitive()
			&& a.getAsJsonPrimitive().isNumber() && b.getAsJsonPrimitive().isNumber())
		{
			return new BigDecimal(a.getAsString()).compareTo(new BigDecimal(b.getAsString())) == 0;
		}
		return a.equals(b);
	}

	private static boolean isAbsoluteUri(String value)
	{
		try
		{
			return new URI(value).isAbsolute();
		}
		catch (URISyntaxException e)
		{
			return false;
		}
	}

	private static String describe(JsonElement value)
	{
		if (value.isJsonNull())
		{
			return "null";
		}
		if (value.isJsonObject())
		{
			return "object";
		}
		if (value.isJsonArray())
		{
			return "array";
		}
		return value.toString();
	}

	private static String at(String path)
	{
		return (path.isEmpty() ? "/" : path) + ": ";
	}
}
