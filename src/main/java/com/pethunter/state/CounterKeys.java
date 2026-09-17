package com.pethunter.state;

import java.util.Locale;

/**
 * Builds the counter keys that dataset sources reference through counterKey.
 *
 * <p>A key is the collection log counter label in snake case, e.g. "Callisto kills" becomes
 * "callisto_kills". A "Your Callisto kill count is" chat message maps to the same key, so both
 * sources of truth update one counter.
 */
public final class CounterKeys
{
	private CounterKeys()
	{
	}

	public static String fromLabel(String label)
	{
		return slug(Text.removeTags(label));
	}

	public static String fromKillCountActivity(String activity)
	{
		return slug(Text.removeTags(activity) + " kills");
	}

	static String slug(String text)
	{
		return text.toLowerCase(Locale.ROOT)
			.replace("'", "")
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
	}

	/**
	 * Tag stripping without depending on client utilities, so parsing stays unit-testable.
	 */
	static final class Text
	{
		private Text()
		{
		}

		static String removeTags(String text)
		{
			return text == null ? "" : text.replaceAll("<[^>]*>", "").trim();
		}
	}
}
