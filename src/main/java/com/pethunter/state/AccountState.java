package com.pethunter.state;

import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * An immutable snapshot of one account's persisted progress. See docs/DESIGN.md section 6.
 */
@Value
public class AccountState
{
	@Value
	public static class Counter
	{
		long value;
		long lastSeenEpochMillis;
	}

	public static final AccountState EMPTY = new AccountState(false, Set.of(), Map.of(), null, null);

	/** False when no account is logged in, so nothing has been loaded. */
	boolean loaded;
	Set<String> obtainedPetIds;
	Map<String, Counter> counters;
	/** When the All Pets page was last read in full, or null if never. */
	@Nullable
	Long lastLogSyncEpochMillis;
	/** When an unidentified pet arrived (chat message without a name), or null. */
	@Nullable
	Long pendingPetEpochMillis;
}
