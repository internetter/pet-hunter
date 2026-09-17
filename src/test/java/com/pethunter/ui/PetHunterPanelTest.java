package com.pethunter.ui;

import com.google.gson.Gson;
import com.pethunter.data.PetRepository;
import com.pethunter.math.Confidence;
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
	private static final char SINGLE_QUOTE = (char) 39;

	private interface PanelAction
	{
		void run(PetHunterPanel panel) throws Exception;
	}

	private static void onEdt(PetRepository repository, PanelAction action) throws Exception
	{
		onEdt(repository, ManualCountListener.NONE, action);
	}

	private static void onEdt(PetRepository repository, ManualCountListener manualCounts, PanelAction action) throws Exception
	{
		onEdt(repository, manualCounts, MethodChoiceListener.NONE, action);
	}

	private static void onEdt(PetRepository repository, ManualCountListener manualCounts, MethodChoiceListener methodChoices,
		PanelAction action) throws Exception
	{
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				action.run(new PetHunterPanel(repository, PetIconLoader.NONE, manualCounts, methodChoices));
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
			Map.of("callisto_kills", new AccountState.Counter(20, 1L)), Map.of(), Map.of(), 1_700_000_000_000L, 1_700_000_100_000L);

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
			panel.update(new AccountState(true, Set.of(), Map.of(), Map.of(), Map.of(), null, null));
			assertTrue(labelTexts(panel.getSyncLabel()).get(0).contains("All Pets page"));
		});
	}

	private static <T extends Component> T findNamed(Component root, String name, Class<T> type)
	{
		if (type.isInstance(root) && name.equals(root.getName()))
		{
			return type.cast(root);
		}
		if (root instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) root).getComponents())
			{
				T found = findNamed(child, name, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void manualCountEntryReportsValidCountsAndRejectsTypos() throws Exception
	{
		PetRepository bundled = PetRepository.loadBundled(new Gson());
		List<String> received = new ArrayList<>();
		onEdt(bundled, (sourceId, count) -> received.add(sourceId + "=" + count), panel ->
		{
			panel.getSearchField().setText("olmlet");
			PetRow row = (PetRow) java.util.Arrays.stream(panel.getListPanel().getComponents())
				.filter(c -> c instanceof PetRow).findFirst().orElseThrow();
			row.setExpanded(true);

			javax.swing.JTextField field = findNamed(row, "manualCount:olmlet.chambers_of_xeric_uniques", javax.swing.JTextField.class);
			javax.swing.JButton save = findNamed(row, "saveManualCount:olmlet.chambers_of_xeric_uniques", javax.swing.JButton.class);
			assertTrue(field != null && save != null);
			assertTrue(labelTexts(row).stream().anyMatch(t -> t.contains("The game cannot confirm it")));

			field.setText("12x");
			save.doClick();
			assertTrue("typos are not submitted", received.isEmpty());

			field.setText("1,234");
			save.doClick();
			assertEquals(List.of("olmlet.chambers_of_xeric_uniques=1234"), received);
		});
	}

	@Test
	public void manualCountEntryIsNotOfferedForTrustworthyGameCounters() throws Exception
	{
		onEdt(PetRepository.loadBundled(new Gson()), panel ->
		{
			panel.getSearchField().setText("vorki");
			PetRow row = (PetRow) java.util.Arrays.stream(panel.getListPanel().getComponents())
				.filter(c -> c instanceof PetRow).findFirst().orElseThrow();
			row.setExpanded(true);
			assertEquals(null, findNamed(row, "manualCount:vorki.vorkath", javax.swing.JTextField.class));
		});
	}

	@Test
	public void manualCountParsing()
	{
		assertEquals(Long.valueOf(1_234), PetDetailPanel.parseCount(" 1,234 "));
		assertEquals(Long.valueOf(0), PetDetailPanel.parseCount("0"));
		assertEquals(null, PetDetailPanel.parseCount(""));
		assertEquals(null, PetDetailPanel.parseCount("-5"));
		assertEquals(null, PetDetailPanel.parseCount("12.5"));
		assertEquals(null, PetDetailPanel.parseCount("99999999999"));
	}

	/** A skilling pet with two XP-derived methods, so the chooser has something to choose between. */
	private static PetRepository twoMethodSkillingPet()
	{
		String json = "{'schemaVersion':1,'pets':[{'id':'fixture_pet','name':'Fixture pet','category':'SKILLING',"
			+ "'skill':'FISHING','obtainableOn':['MAIN'],'sources':["
			+ "{'id':'fixture_pet.fast','label':'Fast method','rateModel':'SKILL_LEVEL_SCALED','baseChance':100000,"
			+ "'verified':true,'sources':['https://fixture.invalid/x']},"
			+ "{'id':'fixture_pet.slow','label':'Slow method','rateModel':'SKILL_LEVEL_SCALED','baseChance':300000,"
			+ "'verified':true,'sources':['https://fixture.invalid/x']}],"
			+ "'methods':[{'id':'fixture_pet.fast','xpPerAction':50,'verified':true,'sources':['https://fixture.invalid/x']},"
			+ "{'id':'fixture_pet.slow','xpPerAction':100,'verified':true,'sources':['https://fixture.invalid/x']}]}]}";
		return PetRepository.load(new Gson(), new StringReader(json.replace(SINGLE_QUOTE, '"')));
	}

	@Test
	public void methodChoiceDrivesTheEstimateAndIsReported() throws Exception
	{
		PetRepository repository = twoMethodSkillingPet();
		List<String> chosen = new ArrayList<>();
		AccountState withChoice = new AccountState(true, Set.of(), Map.of(), Map.of(),
			Map.of("fixture_pet", "fixture_pet.slow"), 1L, null);

		onEdt(repository, ManualCountListener.NONE, (petId, sourceId) -> chosen.add(petId + "=" + sourceId), panel ->
		{
			// Two usable methods and no choice: the panel must ask instead of guessing
			panel.update(new AccountState(true, Set.of(), Map.of(), Map.of(), Map.of(), 1L, null), Map.of("FISHING", 1_000_000L));
			assertFalse(panel.getEntries().get(0).getDryness().hasFigure());
			assertTrue(panel.getEntries().get(0).getDryness().getExplanation().contains("Choose which method"));

			PetRow row = (PetRow) panel.getListPanel().getComponents()[1];
			row.setExpanded(true);
			javax.swing.JComboBox<?> box = findNamed(row, "method:fixture_pet", javax.swing.JComboBox.class);
			assertEquals(PetDetailPanel.NO_CHOICE, box.getSelectedItem());
			box.setSelectedItem("Fast method");
			assertEquals(List.of("fixture_pet=fixture_pet.fast"), chosen);

			// With a choice stored, the estimate appears and names that method
			panel.update(withChoice, Map.of("FISHING", 1_000_000L));
			PetEntry entry = panel.getEntries().get(0);
			assertTrue(entry.getDryness().hasFigure());
			assertTrue(entry.getDryness().getExplanation(), entry.getDryness().getExplanation().contains("Slow method"));
			assertEquals(Confidence.ESTIMATED, entry.getDryness().getConfidence().orElseThrow());

			// Without XP there is no estimate at all
			panel.update(withChoice, Map.of());
			assertFalse(panel.getEntries().get(0).getDryness().hasFigure());
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
