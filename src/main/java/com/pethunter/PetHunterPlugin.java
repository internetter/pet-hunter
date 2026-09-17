package com.pethunter;

import com.google.gson.Gson;
import com.pethunter.data.PetRepository;
import com.pethunter.ui.PetHunterPanel;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Pet Hunter",
	description = "Tracks pet progress and estimates dryness with labelled confidence",
	tags = {"pet", "pets", "collection", "dryness", "skilling", "boss"}
)
public class PetHunterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	private PetHunterPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		PetRepository repository = PetRepository.loadBundled(gson);
		log.debug("Pet Hunter loaded {} pets", repository.getPets().size());

		panel = new PetHunterPanel(repository, (itemId, label) -> itemManager.getImage(itemId).addTo(label));
		panel.setLoggedIn(client.getGameState() == GameState.LOGGED_IN);

		navButton = NavigationButton.builder()
			.tooltip("Pet Hunter")
			.icon(ImageUtil.loadImageResource(PetHunterPlugin.class, "panel_icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		PetHunterPanel current = panel;
		if (current == null)
		{
			return;
		}
		boolean loggedIn = event.getGameState() == GameState.LOGGED_IN;
		SwingUtilities.invokeLater(() -> current.setLoggedIn(loggedIn));
	}
}
