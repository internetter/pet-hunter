package com.pethunter.validation;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Build-time validation of the pet dataset: JSON Schema first, then the extra assertions in
 * docs/DATA.md. Run by the validateDataset Gradle task, which check (and so build) depends on.
 *
 * <p>Lives in the test source set so it never ships in the plugin.
 */
public final class DatasetValidator
{
	private DatasetValidator()
	{
	}

	public static void main(String[] args) throws IOException
	{
		if (args.length != 2)
		{
			System.err.println("usage: DatasetValidator <pets.json> <pets.schema.json>");
			System.exit(2);
		}

		Path dataset = Path.of(args[0]);
		Path schema = Path.of(args[1]);
		List<String> errors;
		try (Reader data = Files.newBufferedReader(dataset, StandardCharsets.UTF_8);
			Reader schemaReader = Files.newBufferedReader(schema, StandardCharsets.UTF_8))
		{
			errors = validate(data, schemaReader);
		}

		if (!errors.isEmpty())
		{
			System.err.println("Pet dataset validation FAILED for " + dataset + " (" + errors.size() + " error(s)):");
			errors.forEach(e -> System.err.println("  " + e));
			System.err.println("See docs/DATA.md \"Validation\". Do not weaken a rule to get a commit through.");
			System.exit(1);
		}
		System.out.println("Pet dataset valid: " + dataset);
	}

	/**
	 * @return every problem found; empty when the dataset is valid
	 */
	public static List<String> validate(Reader dataset, Reader schema)
	{
		JsonElement schemaJson;
		try
		{
			schemaJson = StrictJson.parse(schema);
		}
		catch (IOException | RuntimeException e)
		{
			return List.of("schema is not valid JSON: " + e.getMessage());
		}

		JsonElement datasetJson;
		try
		{
			datasetJson = StrictJson.parse(dataset);
		}
		catch (IOException | RuntimeException e)
		{
			return List.of("dataset is not valid JSON: " + e.getMessage());
		}

		List<String> errors = new ArrayList<>(JsonSchemaValidator.validate(datasetJson, schemaJson));
		if (errors.isEmpty())
		{
			// Rules assume schema-valid shapes, so only run them on a schema-valid document
			errors.addAll(DatasetRules.check(datasetJson.getAsJsonObject()));
		}
		return errors;
	}
}
