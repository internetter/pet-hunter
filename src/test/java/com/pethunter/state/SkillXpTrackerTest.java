package com.pethunter.state;

import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkillXpTrackerTest
{
	@Test
	public void recordsChangesOnly()
	{
		SkillXpTracker tracker = new SkillXpTracker();

		assertTrue(tracker.record("FISHING", 1_000));
		assertFalse("same XP is not a change", tracker.record("FISHING", 1_000));
		assertTrue(tracker.record("FISHING", 1_200));
		assertEquals(Map.of("FISHING", 1_200L), tracker.snapshot());
	}

	@Test
	public void rejectsNonsense()
	{
		SkillXpTracker tracker = new SkillXpTracker();

		assertFalse(tracker.record(null, 5));
		assertFalse(tracker.record("MINING", -1));
		assertTrue(tracker.snapshot().isEmpty());
	}

	@Test
	public void clearsOnAccountChange()
	{
		SkillXpTracker tracker = new SkillXpTracker();
		tracker.record("MINING", 500);

		tracker.clear();

		assertTrue(tracker.snapshot().isEmpty());
	}
}
