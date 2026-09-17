package com.pethunter.state;

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
	public void set(String key, String value)
	{
		configManager.setRSProfileConfiguration(CONFIG_GROUP, key, value);
	}
}
