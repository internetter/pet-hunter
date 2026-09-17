package com.pethunter.ui;

import com.pethunter.data.Pet;
import com.pethunter.data.PetRepository;
import com.pethunter.math.PlayerProgress;
import com.pethunter.math.SourceEstimator;
import com.pethunter.state.AccountState;
import java.awt.BorderLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * The sidebar panel. All methods must be called on the Swing event dispatch thread.
 */
public class PetHunterPanel extends PluginPanel
{
	static final String EMPTY_DATASET = "No pets could be loaded. The bundled dataset may be damaged; try reinstalling the plugin.";
	static final String NO_MATCHES = "No pets match the current filter and search.";
	static final String NOT_SYNCED = "Pets not synced yet. Open your collection log in-game and view the All Pets page (under Other).";
	static final String NOT_LOADED = "Log in to load this account's pets.";
	static final String PENDING_PET = "You received a pet! Open the All Pets page of your collection log to record which one.";

	private static final DateTimeFormatter SYNC_TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
	static final String LOGGED_OUT = "You are logged out. Log in to read your progress.";
	static final String ATTRIBUTION = "Rates from the Old School RuneScape Wiki (CC BY-NC-SA 3.0). Figures are rough guides, not facts; hover to see what each one rests on.";

	private final List<Pet> pets;
	private final PetIconLoader icons;
	private final ManualCountListener manualCounts;
	private final MethodChoiceListener methodChoices;

	private final JLabel obtainedLabel = smallLabel("", ColorScheme.TEXT_COLOR);
	private final JProgressBar progressBar = new JProgressBar();
	private final JLabel loggedOutLabel = wrappedLabel(LOGGED_OUT, ColorScheme.PROGRESS_ERROR_COLOR);
	private final JLabel syncLabel = wrappedLabel(NOT_LOADED, ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel pendingPetLabel = wrappedLabel(PENDING_PET, ColorScheme.BRAND_ORANGE);

	private final JComboBox<PetListModel.Filter> filterBox = new JComboBox<>(PetListModel.Filter.values());
	private final JComboBox<PetListModel.Grouping> groupingBox = new JComboBox<>(PetListModel.Grouping.values());
	private final JComboBox<PetListModel.SortOrder> sortBox = new JComboBox<>(PetListModel.SortOrder.values());
	private final IconTextField searchField = new IconTextField();

	private final JPanel listPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
	private ViewListener viewListener = ViewListener.NONE;
	private final Set<String> expandedPetIds = new HashSet<>();

	private List<PetEntry> entries = List.of();
	private AccountState state = AccountState.EMPTY;
	private Map<String, Long> xpBySkill = Map.of();

	public PetHunterPanel(PetRepository repository, PetIconLoader icons)
	{
		this(repository, icons, ManualCountListener.NONE, MethodChoiceListener.NONE);
	}

	public PetHunterPanel(PetRepository repository, PetIconLoader icons, ManualCountListener manualCounts,
		MethodChoiceListener methodChoices)
	{
		this.pets = repository.getPets();
		this.icons = icons;
		this.manualCounts = manualCounts;
		this.methodChoices = methodChoices;

		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel north = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		north.setOpaque(false);

		JLabel title = new JLabel("Pet Hunter");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		north.add(title);

		north.add(obtainedLabel);
		progressBar.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		progressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressBar.setBorderPainted(false);
		progressBar.setPreferredSize(new Dimension(0, 8));
		north.add(progressBar);
		north.add(loggedOutLabel);
		north.add(pendingPetLabel);
		north.add(syncLabel);

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PANEL_WIDTH - 16, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuildList();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuildList();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuildList();
			}
		});
		north.add(searchField);

		for (JComboBox<?> box : List.of(filterBox, groupingBox, sortBox))
		{
			box.setFont(FontManager.getRunescapeSmallFont());
			box.addActionListener(e ->
			{
				rebuildList();
				viewListener.onViewChanged((PetListModel.Filter) filterBox.getSelectedItem(),
					(PetListModel.Grouping) groupingBox.getSelectedItem(),
					(PetListModel.SortOrder) sortBox.getSelectedItem());
			});
			north.add(box);
		}

		add(north, BorderLayout.NORTH);

		listPanel.setOpaque(false);
		add(listPanel, BorderLayout.CENTER);

		add(wrappedLabel(ATTRIBUTION, ColorScheme.MEDIUM_GRAY_COLOR), BorderLayout.SOUTH);

		setLoggedIn(false);
		refreshEntries();
	}

	/**
	 * Restores the view the player last used, without reporting it back as a fresh choice.
	 */
	public void setView(PetListModel.Filter filter, PetListModel.Grouping grouping, PetListModel.SortOrder sort)
	{
		ViewListener previous = viewListener;
		viewListener = ViewListener.NONE;
		try
		{
			filterBox.setSelectedItem(filter);
			groupingBox.setSelectedItem(grouping);
			sortBox.setSelectedItem(sort);
		}
		finally
		{
			viewListener = previous;
		}
		rebuildList();
	}

	public void setViewListener(ViewListener listener)
	{
		this.viewListener = listener;
	}

	public void setLoggedIn(boolean loggedIn)
	{
		loggedOutLabel.setVisible(!loggedIn);
		revalidate();
		repaint();
	}

	/**
	 * Shows an account's progress. Pass {@link AccountState#EMPTY} when logged out, so the previous
	 * account's data is never left on screen.
	 */
	public void update(AccountState state)
	{
		update(state, xpBySkill);
	}

	/**
	 * Shows an account's progress together with its current skill XP.
	 */
	public void update(AccountState state, Map<String, Long> xpBySkill)
	{
		this.state = state;
		this.xpBySkill = Map.copyOf(xpBySkill);
		refreshEntries();
	}

	/**
	 * Recomputes every pet's dryness from the current account state.
	 */
	void refreshEntries()
	{
		PlayerProgress.PlayerProgressBuilder builder = PlayerProgress.builder();
		state.getCounters().forEach((key, counter) -> builder.counter(key, counter.getValue()));
		state.getManualCounts().forEach(builder::manualCount);
		xpBySkill.forEach(builder::xp);
		PlayerProgress progress = builder.build();

		List<PetEntry> computed = new ArrayList<>(pets.size());
		for (Pet pet : pets)
		{
			String assumed = state.getMethodOverrides().get(pet.getId());
			computed.add(new PetEntry(pet, state.getObtainedPetIds().contains(pet.getId()),
				SourceEstimator.estimatePet(pet, progress, assumed), progress, assumed));
		}
		entries = computed;

		long obtained = entries.stream().filter(PetEntry::isObtained).count();
		obtainedLabel.setText(obtained + " / " + entries.size() + " pets obtained");
		progressBar.setMaximum(Math.max(1, entries.size()));
		progressBar.setValue((int) obtained);

		syncLabel.setText(wrap(syncText(state)));
		pendingPetLabel.setVisible(state.getPendingPetEpochMillis() != null);

		rebuildList();
	}

	static String syncText(AccountState state)
	{
		if (!state.isLoaded())
		{
			return NOT_LOADED;
		}
		if (state.getLastLogSyncEpochMillis() == null)
		{
			return NOT_SYNCED;
		}
		return "Pets last synced " + SYNC_TIME.format(Instant.ofEpochMilli(state.getLastLogSyncEpochMillis()))
			+ ". Opening a collection log page updates it.";
	}

	void rebuildList()
	{
		listPanel.removeAll();

		if (entries.isEmpty())
		{
			listPanel.add(wrappedLabel(EMPTY_DATASET, ColorScheme.LIGHT_GRAY_COLOR));
		}
		else
		{
			List<PetListModel.Group> groups = PetListModel.build(entries,
				(PetListModel.Filter) filterBox.getSelectedItem(),
				(PetListModel.Grouping) groupingBox.getSelectedItem(),
				(PetListModel.SortOrder) sortBox.getSelectedItem(),
				searchField.getText());

			if (groups.isEmpty())
			{
				listPanel.add(wrappedLabel(NO_MATCHES, ColorScheme.LIGHT_GRAY_COLOR));
			}
			for (PetListModel.Group group : groups)
			{
				listPanel.add(groupHeader(group));
				for (PetEntry entry : group.getEntries())
				{
					listPanel.add(row(entry));
				}
			}
		}

		listPanel.revalidate();
		listPanel.repaint();
	}

	private Component row(PetEntry entry)
	{
		String petId = entry.getPet().getId();
		PetRow[] holder = new PetRow[1];
		holder[0] = new PetRow(entry, icons, manualCounts, methodChoices, expandedPetIds.contains(petId), () ->
		{
			if (holder[0].isExpanded())
			{
				expandedPetIds.add(petId);
			}
			else
			{
				expandedPetIds.remove(petId);
			}
		});
		return holder[0];
	}

	private static Component groupHeader(PetListModel.Group group)
	{
		JLabel label = new JLabel(group.getTitle() + " (" + group.getEntries().size() + ")");
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 2));
		return label;
	}

	// Package-private accessors for tests

	List<PetEntry> getEntries()
	{
		return entries;
	}

	JPanel getListPanel()
	{
		return listPanel;
	}

	JComboBox<PetListModel.Filter> getFilterBox()
	{
		return filterBox;
	}

	JComboBox<PetListModel.Grouping> getGroupingBox()
	{
		return groupingBox;
	}

	JComboBox<PetListModel.SortOrder> getSortBox()
	{
		return sortBox;
	}

	IconTextField getSearchField()
	{
		return searchField;
	}

	JLabel getLoggedOutLabel()
	{
		return loggedOutLabel;
	}

	private static JLabel smallLabel(String text, java.awt.Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	private static JLabel wrappedLabel(String text, java.awt.Color color)
	{
		return smallLabel(wrap(text), color);
	}

	private static String wrap(String text)
	{
		return "<html><body style='width:190px'>" + PetDetailPanel.escape(text) + "</body></html>";
	}

	JLabel getSyncLabel()
	{
		return syncLabel;
	}

	JLabel getPendingPetLabel()
	{
		return pendingPetLabel;
	}
}
