package com.pethunter.state;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Recognises the chat messages that matter for pets and kill counts. Pure; the strings are in
 * {@link GameIds} with their verification notes.
 */
public final class ChatParser
{
	private static final Pattern KILL_COUNT = Pattern.compile(GameIds.KILL_COUNT_REGEX);

	private ChatParser()
	{
	}

	public enum Kind
	{
		/** A new pet arrived; the message does not say which. */
		PET_RECEIVED,
		/** A pet roll hit while the player already owns that pet. Nothing new to record. */
		PET_DUPLICATE,
		/** Names an item newly added to the collection log. */
		COLLECTION_LOG_ITEM,
		KILL_COUNT
	}

	@Value
	public static class Event
	{
		Kind kind;
		/** Item name for COLLECTION_LOG_ITEM, counter key for KILL_COUNT, otherwise null. */
		String subject;
		/** Count for KILL_COUNT, otherwise 0. */
		long count;
	}

	public static Optional<Event> parse(String rawMessage)
	{
		if (rawMessage == null)
		{
			return Optional.empty();
		}

		String plain = CounterKeys.Text.removeTags(rawMessage);
		// The duplicate message shares a prefix with nothing else, but check it before the
		// following message in case a future wording overlaps
		if (plain.startsWith(GameIds.PET_DUPLICATE_MESSAGE))
		{
			return Optional.of(new Event(Kind.PET_DUPLICATE, null, 0));
		}
		if (plain.startsWith(GameIds.PET_FOLLOWING_MESSAGE) || plain.startsWith(GameIds.PET_BACKPACK_MESSAGE))
		{
			return Optional.of(new Event(Kind.PET_RECEIVED, null, 0));
		}
		if (plain.startsWith(GameIds.COLLECTION_LOG_ITEM_PREFIX))
		{
			String item = plain.substring(GameIds.COLLECTION_LOG_ITEM_PREFIX.length()).trim();
			return item.isEmpty() ? Optional.empty() : Optional.of(new Event(Kind.COLLECTION_LOG_ITEM, item, 0));
		}

		Matcher killCount = KILL_COUNT.matcher(rawMessage);
		if (killCount.find())
		{
			return Optional.of(new Event(Kind.KILL_COUNT, CounterKeys.fromKillCountActivity(killCount.group(1)),
				CollectionLogParser.parseNumber(killCount.group(2))));
		}
		return Optional.empty();
	}
}
