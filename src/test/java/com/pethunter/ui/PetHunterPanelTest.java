package com.pethunter.ui;

import com.google.gson.Gson;
import com.pethunter.data.PetRepository;
import com.pethunter.state.AccountState;
import java.awt.Component;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * P1 exit criteria: the panel renders with an empty dataset and with a logged-out client without
 * throwing. Components are built and driven on the event dispatch thread, headless.
 */
public class PetHunterPanelTest
{
	private interface PanelAction
	{
		void run(PetHunterPanel panel) throws Exception;
	}

	private static void onEdt(PetRepository repository, PanelAction action) throws Exception
	{
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				action.run(new PetHunterPanel(repository, PetIconLoader.NONE));
			}
			catch (Throwable t)
			{
				failure.set(t);
			}
		});
		if (failure.get() instanceof Exception)
		{
			throw (Exception) failure.get();
		}
		if (failure.get() != null)
		{
			throw new AssertionError(failure.get());
		}
	}

	private static List<String> labelTexts(Component component)
	{
		List<String> texts = new ArrayList<>();
		if (component instanceof JLabel)
		{
			texts.add(((JLabel) component).getText());
		}
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents())
			{
				texts.addAll(labelTexts(child));
			}
		}
		return texts;
	}

	private static long rowCount(PetHunterPanel panel)
	{
		long count = 0;
		for (Component c : panel.getListPanel().getComponents())
		{
			if (c instanceof PetRow)
			{
				count++;
			}
		}
		return count;
	}

	@Test
	public void rendersEmptyDatasetWithMessage() throws Exception
	{
		onEdt(PetRepository.empty(), panel ->
		{
			assertTrue(panel.getEntries().isEmpty());
			assertEquals(0, rowCount(panel));
			assertTrue(labelTexts(panel.getListPanel()).stream().anyMatch(t -> t.contains("No pets could be loaded")));

			// Every control still works with nothing to show
			panel.getFilterBox().setSelectedItem(PetListModel.Filter.OBTAINED);
			panel.getGroupingBox().setSelectedItem(PetListModel.Grouping.DRYNESS);
			panel.getSortBox().setSelectedItem(PetListModel.SortOrder.EXPECTED_HOURS);
			panel.getSearchField().setText("anything");
			assertEquals(0, rowCount(panel));
		});
	}

	@Test
	public void rendersUnreadableDatasetAsEmpty() throws Exception
	{
		onEdt(PetRepository.load(new Gson(), new StringReader("{ not json")), panel -> assertEquals(0, rowCount(panel)));
	}

	@Test
	public void rendersLoggedOutWithTheFullBundledListAllUnknown() throws Exception
	{
		PetRepository bundled = PetRepository.loadBundled(new Gson());
		onEdt(bundled, panel ->
		{
			panel.setLoggedIn(false);
			assertTrue(panel.getLoggedOutLabel().isVisible());

			assertEquals(bundled.getPets().size(), panel.getEntries().size());
			assertEquals(bundled.getPets().size(), rowCount(panel));
			// No progress is read yet, so nothing may show a number
			assertTrue(panel.getEntries().stream().noneMatch(e -> e.getDryness().hasFigure()));
			assertTrue(labelTexts(panel.getListPanel()).stream().noneMatch(t -> t.matches(".*\\d+(\\.\\d+)?%.*")));

			panel.setLoggedIn(true);
			assertFalse(panel.getLoggedOutLabel().isVisible());
		});
	}

	@Test
	public void controlsFilterAndSearchTheList() throws Exception
	{
		PetRepository bundled = PetRepository.loadBundled(new Gson());
		onEdt(bundled, panel ->
		{
			panel.getSearchField().setText("rock golem");
			assertEquals(1, rowCount(panel));

			panel.getSearchField().setText("no pet is called this");
			assertEquals(0, rowCount(panel));
			assertTrue(labelTexts(panel.getListPanel()).stream().anyMatch(t -> t.contains("No pets match")));

			panel.getSearchField().setText("");
			panel.getFilterBox().setSelectedItem(PetListModel.Filter.OBTAINED);
			assertEquals(0, rowCount(panel));

			panel.getFilterBox().setSelectedItem(PetListModel.Filter.MISSING);
			for (PetListModel.Grouping grouping : PetListModel.Grouping.values())
			{
				for (PetListModel.SortOrder sort : PetListModel.SortOrder.values())
				{
					panel.getGroupingBox().setSelectedItem(grouping);
					panel.getSortBox().setSelectedItem(sort);
					assertEquals(bundled.getPets().size(), rowCount(panel));
				}
			}
		});
	}

	@Test
	public void showsAccountProgressAndClearsItForEmptyState() throws Exception
	{
		PetRepository bundled = PetRepository.loadBundled(new Gson());
		AccountState state = new AccountState(true, Set.of("pet_kraken", "beef"),
			Map.of("callisto_kills", new AccountState.Counter(20, 1L)), 1_700_000_000_000L, 1_700_000_100_000L);

		onEdt(bundled, panel ->
		{
			assertTrue(labelTexts(panel.getSyncLabel()).get(0).contains("Log in to load"));
			assertFalse(panel.getPendingPetLabel().isVisible());

			panel.update(state);

			assertTrue(labelTexts(panel).stream().anyMatch(t -> t.equals("2 / 71 pets obtained")));
			assertTrue(panel.getEntries().stream().filter(PetEntry::isObtained).map(e -> e.getPet().getId())
				.collect(Collectors.toSet()).containsAll(Set.of("pet_kraken", "beef")));
			assertTrue(labelTexts(panel.getSyncLabel()).get(0).contains("Pets last synced"));
			assertTrue(panel.getPendingPetLabel().isVisible());

			panel.getFilterBox().setSelectedItem(PetListModel.Filter.OBTAINED);
			assertEquals(2, rowCount(panel));

			panel.update(AccountState.EMPTY);
			assertEquals(0, rowCount(panel));
			assertFalse(panel.getPendingPetLabel().isVisible());
		});
	}

	@Test
	public void neverSyncedAccountSaysHowToSync() throws Exception
	{
		onEdt(PetRepository.loadBundled(new Gson()), panel ->
		{
			panel.update(new AccountState(true, Set.of(), Map.of(), null, null));
			assertTrue(labelTexts(panel.getSyncLabel()).get(0).contains("All Pets page"));
		});
	}

	@Test
	public void rowsExpandAndStayExpandedAcrossRebuilds() throws Exception
	{
		PetRepository bundled = PetRepository.loadBundled(new Gson());
		onEdt(bundled, panel ->
		{
			for (Component c : panel.getListPanel().getComponents())
			{
				if (c instanceof PetRow)
				{
					PetRow row = (PetRow) c;
					// Simulate the click handler for every row, so every detail panel is built once
					row.getComponent(0).dispatchEvent(new java.awt.event.MouseEvent(row.getComponent(0),
						java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
					assertTrue(row.isExpanded());
				}
			}

			panel.getSortBox().setSelectedItem(PetListModel.SortOrder.ALPHABETICAL);

			for (Component c : panel.getListPanel().getComponents())
			{
				if (c instanceof PetRow)
				{
					assertTrue(((PetRow) c).getPetId() + " collapsed after a rebuild", ((PetRow) c).isExpanded());
				}
			}
		});
	}
}
