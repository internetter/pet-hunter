package com.pethunter;

import com.google.gson.Gson;
import com.pethunter.data.PetRepository;
import com.pethunter.state.AccountState;
import com.pethunter.state.AccountStateService;
import com.pethunter.state.CollectionLogPage;
import com.pethunter.state.CollectionLogReader;
import com.pethunter.state.GameIds;
import com.pethunter.state.ProgressTracker;
import com.pethunter.state.RsProfileStore;
import com.pethunter.ui.PetHunterPanel;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
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
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private PetHunterPanel panel;
	private NavigationButton navButton;
	private AccountStateService accountState;
	private ProgressTracker tracker;

	@Override
	protected void startUp()
	{
		PetRepository repository = PetRepository.loadBundled(gson);
		log.debug("Pet Hunter loaded {} pets", repository.getPets().size());

		accountState = new AccountStateService(new RsProfileStore(configManager), gson);
		accountState.reload();
		tracker = new ProgressTracker(repository, accountState, System::currentTimeMillis);

		panel = new PetHunterPanel(repository, (itemId, label) -> itemManager.getImage(itemId).addTo(label));
		panel.setLoggedIn(client.getGameState() == GameState.LOGGED_IN);
		panel.update(accountState.snapshot());

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
		tracker = null;
		accountState = null;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != GameIds.COLLECTION_DRAW_LIST_SCRIPT || tracker == null)
		{
			return;
		}
		CollectionLogPage page = CollectionLogReader.read(client);
		if (page != null && tracker.onCollectionLogPage(page))
		{
			pushStateToPanel();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (tracker == null || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}
		if (tracker.onChatMessage(event.getMessage()))
		{
			pushStateToPanel();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		if (accountState == null)
		{
			return;
		}
		accountState.reload();
		pushStateToPanel();
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

	private void pushStateToPanel()
	{
		PetHunterPanel current = panel;
		AccountStateService service = accountState;
		if (current == null || service == null)
		{
			return;
		}
		AccountState snapshot = service.snapshot();
		SwingUtilities.invokeLater(() -> current.update(snapshot));
	}
}
