package com.pethunter.state;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the logged-in account's progress and writes every change straight through to the
 * {@link ProfileStore}. Thread-safe: game events arrive on the client thread and the panel reads
 * snapshots from the Swing thread.
 *
 * <p>Ownership is additive and sticky: nothing in this class can remove an obtained pet.
 */
@Slf4j
public class AccountStateService
{
	static final String KEY_OBTAINED = "obtainedPetIds";
	// "killCounts" held values from before counter settling (2026-09-17) and is abandoned, not migrated
	static final String KEY_COUNTERS = "counters";
	static final String KEY_LAST_SYNC = "lastLogSyncEpoch";
	static final String KEY_PENDING_PET = "pendingPetEpoch";

	private static final Type STRING_LIST = new TypeToken<List<String>>()
	{
	}.getType();
	private static final Type COUNTER_MAP = new TypeToken<Map<String, AccountState.Counter>>()
	{
	}.getType();

	private final ProfileStore store;
	private final Gson gson;

	private boolean loaded;
	private final Set<String> obtained = new TreeSet<>();
	private final Map<String, AccountState.Counter> counters = new HashMap<>();
	private Long lastSync;
	private Long pendingPet;

	public AccountStateService(ProfileStore store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
	}

	/**
	 * Replaces in-memory state with the active profile's, or clears it when no profile is active.
	 * Call whenever the RS profile changes.
	 */
	public synchronized void reload()
	{
		obtained.clear();
		counters.clear();
		lastSync = null;
		pendingPet = null;
		loaded = store.isAvailable();
		if (!loaded)
		{
			return;
		}

		List<String> ids = read(KEY_OBTAINED, STRING_LIST);
		if (ids != null)
		{
			obtained.addAll(ids);
		}
		Map<String, AccountState.Counter> storedCounters = read(KEY_COUNTERS, COUNTER_MAP);
		if (storedCounters != null)
		{
			storedCounters.forEach((key, counter) ->
			{
				if (key != null && counter != null)
				{
					counters.put(key, counter);
				}
			});
		}
		lastSync = readLong(KEY_LAST_SYNC);
		pendingPet = readLong(KEY_PENDING_PET);
	}

	public synchronized AccountState snapshot()
	{
		if (!loaded)
		{
			return AccountState.EMPTY;
		}
		return new AccountState(true, Set.copyOf(obtained), Map.copyOf(counters), lastSync, pendingPet);
	}

	/**
	 * @return true if any pet was newly recorded
	 */
	public synchronized boolean markObtained(Set<String> petIds, long nowEpochMillis)
	{
		if (!loaded || !obtained.addAll(petIds))
		{
			return false;
		}
		// A newly identified pet resolves an earlier unnamed arrival
		pendingPet = null;
		write(KEY_OBTAINED, gson.toJson(obtained));
		write(KEY_PENDING_PET, "");
		return true;
	}

	/**
	 * Records counters observed now. Live game state wins, so a newer observation replaces an
	 * older one even if the value is lower.
	 *
	 * @return true if any stored counter changed
	 */
	public synchronized boolean recordCounters(Map<String, Long> observed, long nowEpochMillis)
	{
		if (!loaded || observed.isEmpty())
		{
			return false;
		}
		boolean changed = false;
		for (Map.Entry<String, Long> entry : observed.entrySet())
		{
			if (entry.getValue() == null || entry.getValue() < 0)
			{
				continue;
			}
			AccountState.Counter previous = counters.get(entry.getKey());
			if (previous == null || previous.getValue() != entry.getValue())
			{
				changed = true;
			}
			counters.put(entry.getKey(), new AccountState.Counter(entry.getValue(), nowEpochMillis));
		}
		write(KEY_COUNTERS, gson.toJson(counters));
		return changed;
	}

	/**
	 * @return false when no account is loaded, so nothing was recorded
	 */
	public synchronized boolean markLogSynced(long nowEpochMillis)
	{
		if (!loaded)
		{
			return false;
		}
		lastSync = nowEpochMillis;
		// The All Pets page lists every owned pet, so any unnamed arrival is now accounted for
		pendingPet = null;
		write(KEY_LAST_SYNC, Long.toString(nowEpochMillis));
		write(KEY_PENDING_PET, "");
		return true;
	}

	/**
	 * @return false when no account is loaded, so nothing was recorded
	 */
	public synchronized boolean markPendingPet(long nowEpochMillis)
	{
		if (!loaded)
		{
			return false;
		}
		pendingPet = nowEpochMillis;
		write(KEY_PENDING_PET, Long.toString(nowEpochMillis));
		return true;
	}

	private void write(String key, String value)
	{
		if (store.isAvailable())
		{
			store.set(key, value);
		}
	}

	@Nullable
	private <T> T read(String key, Type type)
	{
		String json = store.get(key);
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, type);
		}
		catch (JsonParseException e)
		{
			log.warn("Ignoring unreadable stored value for {}", key);
			return null;
		}
	}

	@Nullable
	private Long readLong(String key)
	{
		String value = store.get(key);
		if (value == null || value.isEmpty())
		{
			return null;
		}
		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException e)
		{
			log.warn("Ignoring unreadable stored value for {}", key);
			return null;
		}
	}
}
