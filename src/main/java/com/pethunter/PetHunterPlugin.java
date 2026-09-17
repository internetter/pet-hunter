package com.pethunter;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.pethunter.data.PetRepository;
import com.pethunter.state.AccountState;
import com.pethunter.state.AccountStateService;
import com.pethunter.state.CollectionLogPage;
import com.pethunter.state.CollectionLogReader;
import com.pethunter.state.GameIds;
import com.pethunter.state.ProgressTracker;
import com.pethunter.state.RsProfileStore;
import com.pethunter.state.SkillXpTracker;
import com.pethunter.ui.PetHunterPanel;
import com.pethunter.ui.PetListModel;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
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
	description = "Lists every pet, marks the ones you have, and estimates how dry you are on the rest",
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
	private ClientThread clientThread;

	@Inject
	private Gson gson;

	@Inject
	private PetHunterConfig config;

	private PetHunterPanel panel;
	private NavigationButton navButton;
	private AccountStateService accountState;
	private RsProfileStore profileStore;
	private SkillXpTracker skillXp;
	private ProgressTracker tracker;

	@Override
	protected void startUp()
	{
		PetRepository repository = PetRepository.loadBundled(gson);
		log.debug("Pet Hunter loaded {} pets", repository.getPets().size());

		skillXp = new SkillXpTracker();
		profileStore = new RsProfileStore(configManager);
		accountState = new AccountStateService(profileStore, gson);
		accountState.reload();
		tracker = new ProgressTracker(repository, accountState, System::currentTimeMillis);
		importRecordedKillCounts();

		panel = new PetHunterPanel(repository, (itemId, label) -> itemManager.getImage(itemId).addTo(label),
			this::onManualCount, this::onMethodChosen);
		panel.setLoggedIn(client.getGameState() == GameState.LOGGED_IN);
		panel.setView(config.filter(), config.grouping(), config.sort());
		panel.setViewListener(this::onViewChanged);
		panel.update(accountState.snapshot(), skillXp.snapshot());

		navButton = NavigationButton.builder()
			.tooltip("Pet Hunter")
			.icon(ImageUtil.loadImageResource(PetHunterPlugin.class, "panel_icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Provides
	PetHunterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PetHunterConfig.class);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		tracker = null;
		accountState = null;
		profileStore = null;
		skillXp = null;
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
	public void onGameTick(GameTick event)
	{
		// Only does work for the few ticks after a collection log page is drawn
		if (tracker == null || !tracker.isWaitingForCounters())
		{
			return;
		}
		if (tracker.onCollectionLogTick(CollectionLogReader.read(client)))
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
	public void onStatChanged(StatChanged event)
	{
		SkillXpTracker xp = skillXp;
		if (xp != null && xp.record(event.getSkill().name(), event.getXp()))
		{
			pushStateToPanel();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		if (accountState == null || skillXp == null)
		{
			return;
		}
		// XP belongs to the character that just left, so never show it for the next one
		skillXp.clear();
		accountState.reload();
		importRecordedKillCounts();
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
		if (skillXp == null)
		{
			return;
		}
		if (loggedIn)
		{
			// StatChanged only fires as XP changes, so skills not trained this session would be
			// missing. Read them all once, on the client thread.
			clientThread.invokeLater(this::readAllSkillXp);
		}
		else
		{
			skillXp.clear();
			pushStateToPanel();
		}
		SwingUtilities.invokeLater(() -> current.setLoggedIn(loggedIn));
	}

	private void onManualCount(String sourceId, Long count)
	{
		AccountStateService service = accountState;
		if (service != null && service.setManualCount(sourceId, count))
		{
			pushStateToPanel();
		}
	}

	/** RuneLite's chat commands plugin stores kill counts in this config group. */
	private static final String RUNELITE_KILLCOUNT_GROUP = "killcount";

	private void importRecordedKillCounts()
	{
		ProgressTracker current = tracker;
		if (current != null && current.importRecordedKillCounts(profileStore.readNumbersFromGroup(RUNELITE_KILLCOUNT_GROUP)))
		{
			pushStateToPanel();
		}
	}

	private void readAllSkillXp()
	{
		SkillXpTracker xp = skillXp;
		if (xp == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Map<String, Long> all = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				all.put(skill.name(), (long) client.getSkillExperience(skill));
			}
		}
		if (xp.recordAll(all))
		{
			pushStateToPanel();
		}
	}

	private void onViewChanged(PetListModel.Filter filter, PetListModel.Grouping grouping, PetListModel.SortOrder sort)
	{
		configManager.setConfiguration(PetHunterConfig.GROUP, "filter", filter);
		configManager.setConfiguration(PetHunterConfig.GROUP, "grouping", grouping);
		configManager.setConfiguration(PetHunterConfig.GROUP, "sort", sort);
	}

	private void onMethodChosen(String petId, String sourceId)
	{
		AccountStateService service = accountState;
		if (service != null && service.setMethodOverride(petId, sourceId))
		{
			pushStateToPanel();
		}
	}

	private void pushStateToPanel()
	{
		PetHunterPanel current = panel;
		AccountStateService service = accountState;
		SkillXpTracker xp = skillXp;
		if (current == null || service == null || xp == null)
		{
			return;
		}
		AccountState snapshot = service.snapshot();
		Map<String, Long> xpSnapshot = xp.snapshot();
		SwingUtilities.invokeLater(() -> current.update(snapshot, xpSnapshot));
	}
}
