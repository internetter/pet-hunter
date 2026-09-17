package com.pethunter.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;

/**
 * Reads JSON strictly: no lenient syntax, no trailing content, and duplicate object keys are an
 * error. Gson's own tree parser is lenient and silently keeps the last duplicate key, which could
 * hide a second, conflicting rate in a hand-edited dataset.
 *
 * <p>Numbers are kept as {@link BigDecimal} so integer checks see the literal value.
 */
final class StrictJson
{
	private StrictJson()
	{
	}

	static JsonElement parse(Reader reader) throws IOException
	{
		JsonReader json = new JsonReader(reader);
		json.setLenient(false);
		JsonElement root = read(json, "");
		if (json.peek() != JsonToken.END_DOCUMENT)
		{
			throw new IOException("Unexpected content after the JSON document");
		}
		return root;
	}

	private static JsonElement read(JsonReader json, String path) throws IOException
	{
		switch (json.peek())
		{
			case BEGIN_OBJECT:
			{
				JsonObject obj = new JsonObject();
				json.beginObject();
				while (json.hasNext())
				{
					String key = json.nextName();
					if (obj.has(key))
					{
						throw new IOException("Duplicate key \"" + key + "\" at " + (path.isEmpty() ? "/" : path));
					}
					obj.add(key, read(json, path + "/" + key));
				}
				json.endObject();
				return obj;
			}
			case BEGIN_ARRAY:
			{
				JsonArray array = new JsonArray();
				json.beginArray();
				int index = 0;
				while (json.hasNext())
				{
					array.add(read(json, path + "/" + index++));
				}
				json.endArray();
				return array;
			}
			case STRING:
				return new JsonPrimitive(json.nextString());
			case NUMBER:
				return new JsonPrimitive(new BigDecimal(json.nextString()));
			case BOOLEAN:
				return new JsonPrimitive(json.nextBoolean());
			case NULL:
				json.nextNull();
				return JsonNull.INSTANCE;
			default:
				throw new IOException("Unexpected token " + json.peek() + " at " + path);
		}
	}
}
