package com.pethunter.state;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.client.config.ConfigManager;

/**
 * {@link ProfileStore} backed by RuneLite's per-account RS profile configuration.
 */
public class RsProfileStore implements ProfileStore
{
	/** Config group for all persisted state. Never rename without a migration. */
	public static final String CONFIG_GROUP = "pethunter";

	private final ConfigManager configManager;

	public RsProfileStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	@Override
	public boolean isAvailable()
	{
		return configManager.getRSProfileKey() != null;
	}

	@Nullable
	@Override
	public String get(String key)
	{
		return configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
	}

	@Override
	public Map<String, Long> readNumbersFromGroup(String group)
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return Map.of();
		}
		Map<String, Long> values = new HashMap<>();
		for (String key : configManager.getRSProfileConfigurationKeys(group, profile, ""))
		{
			// Keys arrive fully qualified, e.g. "killcount.rsprofile.AyCN0gnf.vorkath"
			String name = key.substring(key.lastIndexOf('.') + 1);
			String value = configManager.getRSProfileConfiguration(group, name);
			if (value == null)
			{
				continue;
			}
			try
			{
				values.put(name, Long.parseLong(value.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// Not a count; the group may hold other things
			}
		}
		return values;
	}

	@Override
	public void set(String key, String value)
	{
		configManager.setRSProfileConfiguration(CONFIG_GROUP, key, value);
	}
}
