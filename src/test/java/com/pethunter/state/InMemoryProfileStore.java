package com.pethunter.state;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ProfileStore} holding several profiles, one of which is active, to simulate logging in
 * and out and switching characters.
 */
class InMemoryProfileStore implements ProfileStore
{
	private final Map<String, Map<String, String>> profiles = new HashMap<>();
	private String activeProfile;

	void logIn(String profile)
	{
		activeProfile = profile;
		profiles.computeIfAbsent(profile, p -> new HashMap<>());
	}

	void logOut()
	{
		activeProfile = null;
	}

	Map<String, String> raw(String profile)
	{
		return profiles.get(profile);
	}

	@Override
	public boolean isAvailable()
	{
		return activeProfile != null;
	}

	/** Stands in for another plugin's config group, e.g. RuneLite's kill counts. */
	private final Map<String, Map<String, Long>> otherGroups = new HashMap<>();

	void putOtherGroup(String group, Map<String, Long> values)
	{
		otherGroups.put(group, values);
	}

	@Override
	public Map<String, Long> readNumbersFromGroup(String group)
	{
		return activeProfile == null ? Map.of() : otherGroups.getOrDefault(group, Map.of());
	}

	@Override
	public String get(String key)
	{
		return activeProfile == null ? null : profiles.get(activeProfile).get(key);
	}

	@Override
	public void set(String key, String value)
	{
		if (activeProfile == null)
		{
			throw new IllegalStateException("write while logged out");
		}
		profiles.get(activeProfile).put(key, value);
	}
}
