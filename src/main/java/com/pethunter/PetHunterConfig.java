package com.pethunter;

import com.pethunter.ui.PetListModel;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Remembers how the player left the panel. These are set by the panel's controls rather than
 * edited by hand, so they are hidden from the settings screen.
 */
@ConfigGroup(PetHunterConfig.GROUP)
public interface PetHunterConfig extends Config
{
	String GROUP = "pethunter";

	@ConfigItem(keyName = "filter", name = "Filter", description = "Last used filter", hidden = true)
	default PetListModel.Filter filter()
	{
		return PetListModel.Filter.ALL;
	}

	@ConfigItem(keyName = "grouping", name = "Grouping", description = "Last used grouping", hidden = true)
	default PetListModel.Grouping grouping()
	{
		return PetListModel.Grouping.SOURCE_TYPE;
	}

	@ConfigItem(keyName = "sort", name = "Sort", description = "Last used sort order", hidden = true)
	default PetListModel.SortOrder sort()
	{
		return PetListModel.SortOrder.DRYNESS;
	}
}
