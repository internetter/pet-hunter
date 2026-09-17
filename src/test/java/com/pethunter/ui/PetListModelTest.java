package com.pethunter.ui;

import com.pethunter.data.PetCategory;
import static com.pethunter.ui.PetListModel.Filter;
import static com.pethunter.ui.PetListModel.Group;
import static com.pethunter.ui.PetListModel.Grouping;
import static com.pethunter.ui.PetListModel.SortOrder;
import static com.pethunter.ui.UiFixtures.figureEntry;
import static com.pethunter.ui.UiFixtures.obtained;
import static com.pethunter.ui.UiFixtures.oneOffEntry;
import static com.pethunter.ui.UiFixtures.unknownEntry;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PetListModelTest
{
	// P(still dry): 1/100 rate at 400 kills = 1.8% (very dry); at 100 kills = 36.6% (dry); at 10 kills = 90.4% (on rate)
	private final PetEntry veryDry = figureEntry("very_dry", "Zeta", 100, 400);
	private final PetEntry dry = figureEntry("dry", "Beta", 100, 100);
	private final PetEntry onRate = figureEntry("on_rate", "Alpha", 100, 10);
	private final PetEntry unknownSkilling = unknownEntry("unknown_skill", "Gamma", PetCategory.SKILLING, "MINING");
	private final PetEntry unknownBoss = unknownEntry("unknown_boss", "Delta", PetCategory.BOSS, null);
	private final PetEntry oneOff = oneOffEntry("one_off", "Epsilon");
	private final PetEntry have = obtained(figureEntry("have", "Aardvark", 100, 1));

	private final List<PetEntry> all = List.of(veryDry, dry, onRate, unknownSkilling, unknownBoss, oneOff, have);

	private static List<String> names(List<Group> groups)
	{
		return groups.stream().flatMap(g -> g.getEntries().stream()).map(e -> e.getPet().getName()).collect(Collectors.toList());
	}

	private static List<String> titles(List<Group> groups)
	{
		return groups.stream().map(Group::getTitle).collect(Collectors.toList());
	}

	@Test
	public void driestFirstThenUnknownThenNoDropChanceThenObtained()
	{
		List<Group> groups = PetListModel.build(all, Filter.ALL, Grouping.DRYNESS, SortOrder.DRYNESS, "");

		assertEquals(List.of("Zeta", "Beta", "Alpha", "Delta", "Gamma", "Epsilon", "Aardvark"), names(groups));
	}

	@Test
	public void dryGroupingBucketsByShareStillWithoutIt()
	{
		List<Group> groups = PetListModel.build(all, Filter.ALL, Grouping.DRYNESS, SortOrder.ALPHABETICAL, "");

		assertEquals(List.of(
			"Very dry: under 10% still without it",
			"Dry: 10% to 50% still without it",
			"On rate: over 50% still without it",
			"Dryness unknown",
			"No drop chance",
			"Obtained"), titles(groups));
		assertEquals(List.of("Delta", "Gamma"), groups.get(3).getEntries().stream().map(e -> e.getPet().getName()).collect(Collectors.toList()));
	}

	@Test
	public void alphabeticalSort()
	{
		List<Group> groups = PetListModel.build(all, Filter.ALL, Grouping.DRYNESS, SortOrder.ALPHABETICAL, "");
		List<Group> flat = PetListModel.build(List.of(veryDry, dry, onRate), Filter.ALL, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "");

		assertEquals(List.of("Alpha", "Beta", "Zeta"), names(flat));
		assertTrue(names(groups).containsAll(List.of("Aardvark", "Epsilon")));
	}

	@Test
	public void expectedHoursFallsBackToAlphabeticalUntilMethodDataExists()
	{
		List<Group> groups = PetListModel.build(List.of(veryDry, dry, onRate), Filter.ALL, Grouping.SOURCE_TYPE, SortOrder.EXPECTED_HOURS, "");

		assertEquals(List.of("Alpha", "Beta", "Zeta"), names(groups));
	}

	@Test
	public void filters()
	{
		assertEquals(List.of("Aardvark"), names(PetListModel.build(all, Filter.OBTAINED, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "")));
		assertEquals(6, names(PetListModel.build(all, Filter.MISSING, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "")).size());
		assertEquals(7, names(PetListModel.build(all, Filter.ALL, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "")).size());
	}

	@Test
	public void searchMatchesNameOrSourceCaseInsensitively()
	{
		assertEquals(List.of("Gamma"), names(PetListModel.build(all, Filter.ALL, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "  gAmMa ")));
		// Source labels are "<name> kills"
		assertEquals(List.of("Beta"), names(PetListModel.build(all, Filter.ALL, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "beta kills")));
		assertEquals(List.of(), PetListModel.build(all, Filter.ALL, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "no such pet"));
	}

	@Test
	public void groupBySourceTypeFollowsCategoryOrderAndSkipsEmptyGroups()
	{
		List<Group> groups = PetListModel.build(all, Filter.ALL, Grouping.SOURCE_TYPE, SortOrder.ALPHABETICAL, "");

		assertEquals(List.of("Skilling", "Bosses", "Quests"), titles(groups));
	}

	@Test
	public void groupBySkillPutsPetsWithoutASkillLast()
	{
		List<Group> groups = PetListModel.build(all, Filter.ALL, Grouping.SKILL, SortOrder.ALPHABETICAL, "");

		assertEquals(List.of("Mining", "No skill"), titles(groups));
		assertEquals(List.of("Gamma"), groups.get(0).getEntries().stream().map(e -> e.getPet().getName()).collect(Collectors.toList()));
	}

	@Test
	public void emptyInputGivesNoGroups()
	{
		assertTrue(PetListModel.build(List.of(), Filter.ALL, Grouping.DRYNESS, SortOrder.DRYNESS, null).isEmpty());
	}

	@Test
	public void sourceSummary()
	{
		assertEquals("Beta kills", PetListModel.sourceSummary(dry.getPet()));
		assertEquals("No sources in dataset",
			PetListModel.sourceSummary(UiFixtures.pet("x", "X", PetCategory.OTHER, null)));
		assertEquals("A kills +1 more", PetListModel.sourceSummary(UiFixtures.pet("x", "X", PetCategory.BOSS, null,
			UiFixtures.killSource("a", "A kills", null), UiFixtures.killSource("b", "B kills", null))));
	}
}
