package com.pethunter.state;

import com.pethunter.data.Pet;
import com.pethunter.data.PetRepository;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns collection log pages and chat messages into ownership and kill count updates. Holds no
 * game references, so the whole event-to-persistence path is unit tested with plain values.
 *
 * <p>Combines what docs/DESIGN.md calls OwnershipTracker and KillCountTracker: both are fed by the
 * same two events, and splitting them would duplicate the event handling without separating any
 * real concern.
 */
@Slf4j
public class ProgressTracker
{
	/** Title of the collection log page that lists every pet, verified in-game 2026-09-17. */
	static final String ALL_PETS_PAGE = "All Pets";

	private final AccountStateService state;
	private final LongSupplier clock;
	private final Map<Integer, String> petIdByItemId = new HashMap<>();
	private final Map<String, String> petIdByName = new HashMap<>();

	public ProgressTracker(PetRepository repository, AccountStateService state, LongSupplier clock)
	{
		this.state = state;
		this.clock = clock;
		for (Pet pet : repository.getPets())
		{
			if (pet.getItemId() != null)
			{
				petIdByItemId.put(pet.getItemId(), pet.getId());
			}
			petIdByName.put(pet.getName().toLowerCase(Locale.ROOT), pet.getId());
		}
	}

	private final CounterSettler counterSettler = new CounterSettler();

	/**
	 * Handles a page as it is first drawn: records obtained pets and the sync immediately, and
	 * starts waiting for the page's counters to settle (see {@link CounterSettler}).
	 *
	 * @return true if anything the panel shows changed
	 */
	public boolean onCollectionLogPage(CollectionLogPage page)
	{
		CollectionLogParser.Result result = CollectionLogParser.parse(page);
		if (result == null)
		{
			log.debug("Ignoring collection log page that is not fully drawn");
			return false;
		}
		counterSettler.start(result.getTitle());
		return recordOwnership(result);
	}

	/**
	 * @return true while a page's counters are still being confirmed, so the caller knows to keep
	 * reading the page on game ticks
	 */
	public boolean isWaitingForCounters()
	{
		return counterSettler.isWaiting();
	}

	/**
	 * Feeds the page as currently drawn on a game tick, or null if the log is closed.
	 *
	 * @return true if settled counters were recorded and changed what the panel shows
	 */
	public boolean onCollectionLogTick(CollectionLogPage page)
	{
		CollectionLogParser.Result result = page == null ? null : CollectionLogParser.parse(page);
		Map<String, Long> settled = counterSettler.onTick(result).orElse(null);
		if (settled == null)
		{
			return false;
		}
		log.debug("Read collection log page {} counters {}", result.getTitle(), settled);
		return state.recordCounters(settled, clock.getAsLong());
	}

	private boolean recordOwnership(CollectionLogParser.Result result)
	{

		long now = clock.getAsLong();
		Set<String> pets = new LinkedHashSet<>();
		for (int itemId : result.getObtainedItemIds())
		{
			String petId = petIdByItemId.get(itemId);
			if (petId != null)
			{
				pets.add(petId);
			}
		}

		boolean changed = state.markObtained(pets, now);
		if (ALL_PETS_PAGE.equals(result.getTitle()) && result.isComplete())
		{
			changed |= state.markLogSynced(now);
		}
		log.debug("Read collection log page {}: {} pets obtained", result.getTitle(), pets.size());
		return changed;
	}

	/**
	 * @return true if anything the panel shows changed
	 */
	public boolean onChatMessage(String message)
	{
		ChatParser.Event event = ChatParser.parse(message).orElse(null);
		if (event == null)
		{
			return false;
		}

		long now = clock.getAsLong();
		switch (event.getKind())
		{
			case PET_RECEIVED:
				return state.markPendingPet(now);
			case COLLECTION_LOG_ITEM:
				String petId = petIdByName.get(event.getSubject().toLowerCase(Locale.ROOT));
				return petId != null && state.markObtained(Set.of(petId), now);
			case KILL_COUNT:
				return state.recordCounters(Map.of(event.getSubject(), event.getCount()), now);
			default:
				return false;
		}
	}
}
