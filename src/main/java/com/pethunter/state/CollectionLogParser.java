package com.pethunter.state;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Interprets a {@link CollectionLogPage}. Pure; see {@link GameIds} for the verified layout.
 */
public final class CollectionLogParser
{
	private static final Pattern COUNTER_LINE = Pattern.compile("^(.+?):\\s*<col=[0-9a-fA-F]{6}>([0-9,]+)</col>$");
	private static final Pattern OBTAINED_LINE = Pattern.compile("^Obtained:\\s*<col=[0-9a-fA-F]{6}>([0-9,]+)/([0-9,]+)</col>$");

	private CollectionLogParser()
	{
	}

	@Value
	public static class Result
	{
		String title;
		/** Item ids drawn as obtained. Missing items are never listed: a scrape can only add. */
		Set<Integer> obtainedItemIds;
		/** Counter key to value, e.g. "callisto_kills" to 20. */
		Map<String, Long> counters;
		/** True when the item grid held as many items as the header says the page has. */
		boolean complete;
	}

	/**
	 * @return null when the page has no title or no items, which happens while the interface is
	 * still being drawn; such a page must not be treated as a sync
	 */
	@Nullable
	public static Result parse(CollectionLogPage page)
	{
		List<String> lines = page.getHeaderLines();
		if (lines.isEmpty() || page.getItems().isEmpty())
		{
			return null;
		}
		String title = CounterKeys.Text.removeTags(lines.get(0));
		if (title.isEmpty())
		{
			return null;
		}

		Integer declaredTotal = null;
		Map<String, Long> counters = new LinkedHashMap<>();
		for (String line : lines.subList(1, lines.size()))
		{
			Matcher obtained = OBTAINED_LINE.matcher(line.trim());
			if (obtained.matches())
			{
				declaredTotal = parseNumber(obtained.group(2)).intValue();
				continue;
			}
			Matcher counter = COUNTER_LINE.matcher(line.trim());
			if (counter.matches())
			{
				counters.put(CounterKeys.fromLabel(counter.group(1)), parseNumber(counter.group(2)));
			}
		}

		Set<Integer> obtainedIds = new LinkedHashSet<>();
		for (CollectionLogPage.Item item : page.getItems())
		{
			if (item.getOpacity() == GameIds.COLLECTION_ITEM_OBTAINED_OPACITY)
			{
				obtainedIds.add(item.getItemId());
			}
		}

		boolean complete = declaredTotal != null && declaredTotal == page.getItems().size();
		return new Result(title, Set.copyOf(obtainedIds), Map.copyOf(counters), complete);
	}

	static Long parseNumber(String digits)
	{
		return Long.parseLong(digits.replace(",", ""));
	}
}
