package com.pethunter.state;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player's current XP per skill, as read from the client. Live game state, so it is never
 * persisted: the client supplies it again on every login.
 *
 * <p>Thread-safe: XP arrives on the client thread and the panel reads snapshots from the Swing
 * thread.
 */
public class SkillXpTracker
{
	private final Map<String, Long> xpBySkill = new ConcurrentHashMap<>();

	/**
	 * @param skill RuneLite Skill name, e.g. "MINING"
	 * @return true if the stored XP changed
	 */
	public boolean record(String skill, long xp)
	{
		if (skill == null || xp < 0)
		{
			return false;
		}
		Long previous = xpBySkill.put(skill, xp);
		return previous == null || previous != xp;
	}

	/**
	 * Drops everything, for logout or an account switch, so one character's XP is never shown for
	 * another.
	 */
	public void clear()
	{
		xpBySkill.clear();
	}

	public Map<String, Long> snapshot()
	{
		return Map.copyOf(new HashMap<>(xpBySkill));
	}
}
