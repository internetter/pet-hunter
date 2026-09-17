package com.pethunter.state;

import com.google.gson.Gson;
import com.pethunter.data.PetRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import static com.pethunter.state.CollectionLogParserTest.page;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * P2 exit criteria: obtaining a pet (a simulated chat message) marks it obtained, it survives a
 * relog, and ownership never regresses on a failed or partial scrape.
 *
 * <p>Uses the bundled dataset's real pet names and item ids.
 */
public class ProgressTrackerTest
{
	private static final Gson GSON = new Gson();
	private static final PetRepository PETS = PetRepository.loadBundled(GSON);

	private final AtomicLong clock = new AtomicLong(1_000);
	private InMemoryProfileStore store;
	private AccountStateService state;
	private ProgressTracker tracker;

	@Before
	public void logIn()
	{
		store = new InMemoryProfileStore();
		store.logIn("main");
		state = new AccountStateService(store, GSON);
		state.reload();
		tracker = new ProgressTracker(PETS, state, clock::get);
	}

	/** Simulates the client restarting and the same account logging back in. */
	private AccountState relog()
	{
		store.logOut();
		state = new AccountStateService(store, GSON);
		state.reload();
		assertFalse(state.snapshot().isLoaded());
		store.logIn("main");
		state.reload();
		tracker = new ProgressTracker(PETS, state, clock::get);
		return state.snapshot();
	}

	@Test
	public void collectionLogChatMessageMarksPetObtainedAndItSurvivesRelog()
	{
		assertTrue(tracker.onChatMessage("New item added to your collection log: <col=ef1020>Baby Mole</col>"));
		assertEquals(Set.of("baby_mole"), state.snapshot().getObtainedPetIds());

		assertEquals(Set.of("baby_mole"), relog().getObtainedPetIds());
	}

	@Test
	public void collectionLogChatMatchesPetNamesIgnoringCase()
	{
		tracker.onChatMessage("New item added to your collection log: Pet kraken");

		assertEquals(Set.of("pet_kraken"), state.snapshot().getObtainedPetIds());
	}

	@Test
	public void nonPetCollectionLogItemsAreIgnored()
	{
		assertFalse(tracker.onChatMessage("New item added to your collection log: Dragon pickaxe"));
		assertTrue(state.snapshot().getObtainedPetIds().isEmpty());
	}

	@Test
	public void unnamedPetArrivalIsPendingUntilTheLogIsSynced()
	{
		clock.set(5_000);
		tracker.onChatMessage("<col=ef1020>You have a funny feeling like you're being followed.</col>");
		assertEquals(Long.valueOf(5_000), state.snapshot().getPendingPetEpochMillis());
		assertEquals(Long.valueOf(5_000), relog().getPendingPetEpochMillis());

		tracker.onCollectionLogPage(page(List.of("All Pets", "Obtained: <col=ffff00>1/2</col>"), 12646, 0, 13262, 175));

		AccountState after = state.snapshot();
		assertNull(after.getPendingPetEpochMillis());
		assertEquals(Set.of("baby_mole"), after.getObtainedPetIds());
		assertNull(relog().getPendingPetEpochMillis());
	}

	@Test
	public void duplicatePetRollChangesNothing()
	{
		assertFalse(tracker.onChatMessage("You have a funny feeling like you would have been followed..."));
		assertNull(state.snapshot().getPendingPetEpochMillis());
	}

	@Test
	public void allPetsPageRecordsOwnershipAndSyncTime()
	{
		clock.set(42_000);
		// 12655 Pet Kraken and 12921 Pet Snakeling obtained, 13262 Abyssal orphan missing
		tracker.onCollectionLogPage(page(List.of("All Pets", "Obtained: <col=ffff00>2/3</col>"), 12655, 0, 12921, 0, 13262, 175));

		AccountState snapshot = relog();
		assertEquals(Set.of("pet_kraken", "pet_snakeling"), snapshot.getObtainedPetIds());
		assertEquals(Long.valueOf(42_000), snapshot.getLastLogSyncEpochMillis());
	}

	@Test
	public void incompleteAllPetsPageStillAddsOwnershipButIsNotASync()
	{
		tracker.onCollectionLogPage(page(List.of("All Pets", "Obtained: <col=ffff00>7/71</col>"), 12655, 0));

		assertEquals(Set.of("pet_kraken"), state.snapshot().getObtainedPetIds());
		assertNull(state.snapshot().getLastLogSyncEpochMillis());
	}

	@Test
	public void ownershipNeverRegressesOnAFailedOrContradictoryScrape()
	{
		tracker.onCollectionLogPage(page(List.of("All Pets", "Obtained: <col=ffff00>1/1</col>"), 12655, 0));
		assertEquals(Set.of("pet_kraken"), state.snapshot().getObtainedPetIds());

		// Page not drawn yet: no header, no items
		tracker.onCollectionLogPage(page(List.of()));
		// Header drawn but items not yet
		tracker.onCollectionLogPage(page(List.of("All Pets", "Obtained: <col=ffff00>0/1</col>")));
		// A page that shows the pet as missing, as a broken or stale draw might
		tracker.onCollectionLogPage(page(List.of("All Pets", "Obtained: <col=ffff00>0/1</col>"), 12655, 175));
		// Garbage chat
		tracker.onChatMessage("New item added to your collection log: ");

		assertEquals(Set.of("pet_kraken"), state.snapshot().getObtainedPetIds());
		assertEquals(Set.of("pet_kraken"), relog().getObtainedPetIds());
	}

	/** Draws a page and leaves it open for the ticks needed to trust its counters. */
	private void viewPage(CollectionLogPage page)
	{
		tracker.onCollectionLogPage(page);
		tracker.onCollectionLogTick(page);
		tracker.onCollectionLogTick(page);
	}

	private static CollectionLogPage vorkathPage(String kills)
	{
		return page(List.of("Vorkath", "Obtained: <col=ffff00>0/1</col>", "Vorkath kills: <col=ffffff>" + kills + "</col>"), 21992, 175);
	}

	@Test
	public void staleCounterDrawnWhilePagingQuicklyIsNotRecorded()
	{
		// Real sequence from 2026-09-17: Vorkath's label appeared with another page's value (106)
		// before its own value (1,312) was drawn
		tracker.onCollectionLogPage(vorkathPage("106"));
		tracker.onCollectionLogTick(vorkathPage("1,312"));
		tracker.onCollectionLogTick(vorkathPage("1,312"));

		assertEquals(1_312L, relog().getCounters().get("vorkath_kills").getValue());
	}

	@Test
	public void leavingAPageBeforeItSettlesRecordsNoCounters()
	{
		tracker.onCollectionLogPage(vorkathPage("106"));
		tracker.onCollectionLogTick(vorkathPage("106"));
		// Moved to the next page before a second matching tick
		CollectionLogPage whisperer = page(List.of("The Whisperer", "Obtained: <col=ffff00>0/1</col>", "Whisperer kills: <col=ffffff>106</col>"), 28246, 175);
		tracker.onCollectionLogTick(whisperer);

		assertFalse(tracker.isWaitingForCounters());
		assertTrue(state.snapshot().getCounters().isEmpty());
	}

	@Test
	public void closingTheLogBeforeItSettlesRecordsNoCounters()
	{
		tracker.onCollectionLogPage(vorkathPage("1,312"));
		tracker.onCollectionLogTick(null);
		tracker.onCollectionLogTick(vorkathPage("1,312"));

		assertTrue(state.snapshot().getCounters().isEmpty());
	}

	@Test
	public void ownershipIsRecordedImmediatelyWithoutWaitingForCounters()
	{
		tracker.onCollectionLogPage(page(List.of("Vorkath", "Obtained: <col=ffff00>1/1</col>", "Vorkath kills: <col=ffffff>1</col>"), 21992, 0));

		assertEquals(Set.of("vorki"), state.snapshot().getObtainedPetIds());
		assertTrue(state.snapshot().getCounters().isEmpty());
	}

	@Test
	public void killCountsFromPageAndChatShareAKeyAndLatestWins()
	{
		clock.set(1_000);
		viewPage(page(List.of("Callisto and Artio", "Obtained: <col=ffff00>0/1</col>",
			"Callisto kills: <col=ffffff>20</col>", "Artio kills: <col=ffffff>0</col>"), 13178, 175));
		clock.set(2_000);
		tracker.onChatMessage("Your Callisto kill count is: <col=ff0000>21</col>.");

		Map<String, AccountState.Counter> counters = relog().getCounters();
		assertEquals(new AccountState.Counter(21, 2_000), counters.get("callisto_kills"));
		assertEquals(new AccountState.Counter(0, 1_000), counters.get("artio_kills"));
	}

	@Test
	public void accountsNeverSeeEachOthersProgress()
	{
		tracker.onChatMessage("New item added to your collection log: Baby Mole");
		tracker.onChatMessage("Your Callisto kill count is: <col=ff0000>21</col>.");

		store.logIn("alt");
		state.reload();
		assertTrue(state.snapshot().isLoaded());
		assertTrue(state.snapshot().getObtainedPetIds().isEmpty());
		assertTrue(state.snapshot().getCounters().isEmpty());

		tracker.onChatMessage("New item added to your collection log: Pet kraken");
		store.logIn("main");
		state.reload();
		assertEquals(Set.of("baby_mole"), state.snapshot().getObtainedPetIds());
	}

	@Test
	public void manualCountsPersistPerAccountAndCanBeCleared()
	{
		assertTrue(state.setManualCount("olmlet.chambers_of_xeric_uniques", 14L));
		assertFalse("same value is not a change", state.setManualCount("olmlet.chambers_of_xeric_uniques", 14L));
		assertFalse("negative counts are rejected", state.setManualCount("scurry.scurrius", -1L));
		assertEquals(Map.of("olmlet.chambers_of_xeric_uniques", 14L), relog().getManualCounts());

		store.logIn("alt");
		state.reload();
		assertTrue(state.snapshot().getManualCounts().isEmpty());

		store.logIn("main");
		state.reload();
		assertTrue(state.setManualCount("olmlet.chambers_of_xeric_uniques", null));
		assertTrue(relog().getManualCounts().isEmpty());
	}

	@Test
	public void killCountsRecordedByRuneliteFillGapsButNeverOverwrite()
	{
		clock.set(1_000);
		viewPage(vorkathPage("1,312"));

		store.putOtherGroup("killcount", Map.of("vorkath", 999L, "herbiboar", 667L, "mimic", 1L));
		assertTrue(tracker.importRecordedKillCounts(store.readNumbersFromGroup("killcount")));

		Map<String, AccountState.Counter> counters = relog().getCounters();
		assertEquals("a count read here already is at least as fresh", 1_312L, counters.get("vorkath_kills").getValue());
		assertEquals(667L, counters.get("herbiboar_harvests").getValue());
		assertFalse("mimic is not a pet source", counters.containsKey("mimic_kills"));

		assertFalse("importing again changes nothing", tracker.importRecordedKillCounts(store.readNumbersFromGroup("killcount")));
	}

	@Test
	public void methodChoicePersistsPerAccount()
	{
		assertTrue(state.setMethodOverride("heron", "heron.minnows"));
		assertFalse(state.setMethodOverride("heron", "heron.minnows"));
		assertEquals(Map.of("heron", "heron.minnows"), relog().getMethodOverrides());

		store.logIn("alt");
		state.reload();
		assertTrue(state.snapshot().getMethodOverrides().isEmpty());

		store.logIn("main");
		state.reload();
		assertTrue(state.setMethodOverride("heron", null));
		assertTrue(relog().getMethodOverrides().isEmpty());
	}

	@Test
	public void eventsWhileLoggedOutAreDropped()
	{
		store.logOut();
		state.reload();

		assertFalse(tracker.onChatMessage("New item added to your collection log: Baby Mole"));
		assertFalse(tracker.onCollectionLogPage(page(List.of("All Pets", "Obtained: <col=ffff00>1/1</col>"), 12646, 0)));
		assertEquals(AccountState.EMPTY, state.snapshot());
	}

	@Test
	public void unreadableStoredValuesLoadAsEmptyInsteadOfThrowing()
	{
		store.set(AccountStateService.KEY_OBTAINED, "{not json");
		store.set(AccountStateService.KEY_COUNTERS, "[1,2,3]");
		store.set(AccountStateService.KEY_LAST_SYNC, "yesterday");

		state.reload();

		AccountState snapshot = state.snapshot();
		assertTrue(snapshot.isLoaded());
		assertTrue(snapshot.getObtainedPetIds().isEmpty());
		assertTrue(snapshot.getCounters().isEmpty());
		assertNull(snapshot.getLastLogSyncEpochMillis());

		// And it recovers on the next write
		tracker.onChatMessage("New item added to your collection log: Baby Mole");
		assertNotNull(store.raw("main").get(AccountStateService.KEY_OBTAINED));
		assertEquals(Set.of("baby_mole"), relog().getObtainedPetIds());
	}
}
