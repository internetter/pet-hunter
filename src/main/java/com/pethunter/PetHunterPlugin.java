package com.pethunter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Pet Hunter",
	description = "Tracks pet progress and estimates dryness with labelled confidence",
	tags = {"pet", "pets", "collection", "dryness", "skilling", "boss"}
)
public class PetHunterPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.debug("Pet Hunter started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Pet Hunter stopped");
	}
}
