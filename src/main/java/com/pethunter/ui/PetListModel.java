package com.pethunter.ui;

import com.pethunter.data.Pet;
import com.pethunter.data.PetCategory;
import com.pethunter.data.PetSource;
import com.pethunter.math.DrynessResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Filtering, searching, grouping and sorting for the pet list. Pure: no Swing, so it is unit tested
 * directly and the panel only renders what this returns.
 */
public final class PetListModel
{
	private PetListModel()
	{
	}

	@RequiredArgsConstructor
	public enum Filter
	{
		ALL("All pets"),
		MISSING("Missing"),
		OBTAINED("Obtained");

		@Getter
		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@RequiredArgsConstructor
	public enum Grouping
	{
		SOURCE_TYPE("Group: source type"),
		SKILL("Group: skill"),
		DRYNESS("Group: dryness");

		@Getter
		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@RequiredArgsConstructor
	public enum SortOrder
	{
		DRYNESS("Sort: driest first"),
		ALPHABETICAL("Sort: A to Z");

		@Getter
		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Value
	public static class Group
	{
		String title;
		List<PetEntry> entries;
	}

	/** Upper limits of P(still dry) for the dryness buckets. */
	static final double VERY_DRY_BELOW = 0.10;
	static final double DRY_BELOW = 0.50;

	static final String NO_SKILL = "No skill";

	public static List<Group> build(List<PetEntry> entries, Filter filter, Grouping grouping, SortOrder sort, String search)
	{
		String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<PetEntry> visible = entries.stream()
			.filter(e -> matchesFilter(e, filter))
			.filter(e -> query.isEmpty() || matchesSearch(e.getPet(), query))
			.sorted(comparator(sort))
			.collect(Collectors.toList());

		Map<String, List<PetEntry>> groups = new LinkedHashMap<>();
		for (String title : groupOrder(grouping, visible))
		{
			groups.put(title, new ArrayList<>());
		}
		for (PetEntry entry : visible)
		{
			groups.get(groupTitle(grouping, entry)).add(entry);
		}

		return groups.entrySet().stream()
			.filter(g -> !g.getValue().isEmpty())
			.map(g -> new Group(g.getKey(), List.copyOf(g.getValue())))
			.collect(Collectors.toList());
	}

	/**
	 * A short description of where the pet comes from, for the collapsed row.
	 */
	public static String sourceSummary(Pet pet)
	{
		List<PetSource> sources = pet.getSources();
		if (sources.isEmpty())
		{
			return "No sources in dataset";
		}
		String first = sources.get(0).getLabel();
		return sources.size() == 1 ? first : first + " +" + (sources.size() - 1) + " more";
	}

	static boolean matchesFilter(PetEntry entry, Filter filter)
	{
		switch (filter)
		{
			case MISSING:
				return !entry.isObtained();
			case OBTAINED:
				return entry.isObtained();
			default:
				return true;
		}
	}

	static boolean matchesSearch(Pet pet, String query)
	{
		if (pet.getName().toLowerCase(Locale.ROOT).contains(query))
		{
			return true;
		}
		return pet.getSources().stream().anyMatch(s -> s.getLabel().toLowerCase(Locale.ROOT).contains(query));
	}

	private static Comparator<PetEntry> comparator(SortOrder sort)
	{
		Comparator<PetEntry> byName = Comparator.comparing(e -> e.getPet().getName().toLowerCase(Locale.ROOT));
		if (sort != SortOrder.DRYNESS)
		{
			return byName;
		}
		// Missing pets with a figure first, driest (lowest P(still dry)) at the top; then pets with
		// no figure; obtained pets last, since dryness no longer applies to them
		return Comparator.<PetEntry>comparingInt(PetListModel::drynessRank)
			.thenComparingDouble(PetListModel::logProbabilityOrZero)
			.thenComparing(byName);
	}

	private static int drynessRank(PetEntry entry)
	{
		if (entry.isObtained())
		{
			return 3;
		}
		switch (entry.getDryness().getStatus())
		{
			case FIGURE:
				return 0;
			case UNKNOWN:
				return 1;
			default:
				return 2;
		}
	}

	private static double logProbabilityOrZero(PetEntry entry)
	{
		return entry.getDryness().getLogProbabilityStillDry().map(v -> v.getValue()).orElse(0.0);
	}

	private static List<String> groupOrder(Grouping grouping, List<PetEntry> visible)
	{
		List<String> order = new ArrayList<>();
		switch (grouping)
		{
			case SOURCE_TYPE:
				for (PetCategory category : PetCategory.values())
				{
					order.add(categoryTitle(category));
				}
				break;
			case SKILL:
				visible.stream()
					.map(e -> e.getPet().getSkill())
					.filter(s -> s != null)
					.map(DrynessFormat::skillName)
					.distinct()
					.sorted()
					.forEach(order::add);
				order.add(NO_SKILL);
				break;
			case DRYNESS:
				for (DrynessBucket bucket : DrynessBucket.values())
				{
					order.add(bucket.title);
				}
				break;
		}
		return order;
	}

	private static String groupTitle(Grouping grouping, PetEntry entry)
	{
		switch (grouping)
		{
			case SKILL:
				String skill = entry.getPet().getSkill();
				return skill == null ? NO_SKILL : DrynessFormat.skillName(skill);
			case DRYNESS:
				return DrynessBucket.of(entry).title;
			default:
				return categoryTitle(entry.getPet().getCategory());
		}
	}

	static String categoryTitle(PetCategory category)
	{
		switch (category)
		{
			case BOSS:
				return "Bosses";
			case SKILLING:
				return "Skilling";
			case RAID:
				return "Raids";
			case MINIGAME:
				return "Minigames";
			case CLUE:
				return "Clue scrolls";
			case SLAYER:
				return "Slayer";
			case QUEST:
				return "Quests";
			default:
				return "Other";
		}
	}

	@RequiredArgsConstructor
	enum DrynessBucket
	{
		VERY_DRY("Very dry: under 10% still without it"),
		DRY("Dry: 10% to 50% still without it"),
		ON_RATE("On rate: over 50% still without it"),
		UNKNOWN("Dryness unknown"),
		NOT_APPLICABLE("No drop chance"),
		OBTAINED("Obtained");

		final String title;

		static DrynessBucket of(PetEntry entry)
		{
			if (entry.isObtained())
			{
				return OBTAINED;
			}
			DrynessResult dryness = entry.getDryness();
			switch (dryness.getStatus())
			{
				case NOT_APPLICABLE:
					return NOT_APPLICABLE;
				case UNKNOWN:
					return UNKNOWN;
				default:
					double pDry = dryness.getProbabilityStillDry().orElseThrow().getValue();
					if (pDry < VERY_DRY_BELOW)
					{
						return VERY_DRY;
					}
					return pDry < DRY_BELOW ? DRY : ON_RATE;
			}
		}
	}
}
