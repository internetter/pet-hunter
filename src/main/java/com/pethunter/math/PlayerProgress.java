package com.pethunter.math;

import java.util.Map;
import java.util.OptionalLong;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Everything the math layer knows about a player, as plain values. Game state is read elsewhere
 * and copied in here, so estimation never touches the client.
 */
@Value
@Builder
public class PlayerProgress
{
	/** Attempt counts read from the game (collection log KC, chat KC), keyed by counterKey. EXACT. */
	@Singular
	Map<String, Long> counters;

	/**
	 * Attempt counts the player typed in, keyed by source id rather than counterKey, because
	 * the sources that need manual entry are exactly the ones whose counterKey is null. ESTIMATED.
	 */
	@Singular
	Map<String, Long> manualCounts;

	/** Current XP keyed by RuneLite Skill name, e.g. "MINING". */
	@Singular("xp")
	Map<String, Long> xpBySkill;

	public static PlayerProgress empty()
	{
		return builder().build();
	}

	public OptionalLong getCounter(String counterKey)
	{
		return optional(counters, counterKey);
	}

	public OptionalLong getManualCount(String sourceId)
	{
		return optional(manualCounts, sourceId);
	}

	public OptionalLong getXp(String skill)
	{
		return optional(xpBySkill, skill);
	}

	private static OptionalLong optional(Map<String, Long> map, String key)
	{
		Long value = key == null ? null : map.get(key);
		return value == null ? OptionalLong.empty() : OptionalLong.of(value);
	}
}
