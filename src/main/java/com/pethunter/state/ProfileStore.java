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

	void set(String key, String value);
}
