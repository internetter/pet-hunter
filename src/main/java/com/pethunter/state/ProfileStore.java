package com.pethunter.state;

import javax.annotation.Nullable;

/**
 * Key-value storage scoped to the logged-in RuneScape profile. The plugin backs this with
 * ConfigManager's RS profile configuration, which RuneLite keys by account hash and game mode, so
 * one character's data can never be read while another is logged in.
 */
public interface ProfileStore
{
	/**
	 * @return false when no profile is active (logged out)
	 */
	boolean isAvailable();

	@Nullable
	String get(String key);

	/**
	 * Reads another plugin's per-profile values, used to import kill counts RuneLite's chat
	 * commands plugin already recorded.
	 *
	 * @return activity name to value; empty when the group is absent or unreadable
	 */
	java.util.Map<String, Long> readNumbersFromGroup(String group);

	void set(String key, String value);
}
