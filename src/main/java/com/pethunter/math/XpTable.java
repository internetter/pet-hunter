package com.pethunter.math;

/**
 * The standard Old School RuneScape experience curve for real (non-virtual) levels 1 to 99.
 */
public final class XpTable
{
	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 99;
	/** Skill XP stops accruing here. */
	public static final int MAX_XP = 200_000_000;

	/** Indexed by level; index 0 is unused. */
	private static final int[] XP_FOR_LEVEL = new int[MAX_LEVEL + 1];

	static
	{
		// xp(L) = floor( sum_{i=1}^{L-1} floor(i + 300 * 2^(i/7)) / 4 )
		int points = 0;
		for (int level = MIN_LEVEL; level < MAX_LEVEL; level++)
		{
			points += (int) Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0));
			XP_FOR_LEVEL[level + 1] = points / 4;
		}
	}

	private XpTable()
	{
	}

	/**
	 * @return the XP at which {@code level} is reached
	 * @throws IllegalArgumentException if level is outside 1..99
	 */
	public static int xpForLevel(int level)
	{
		if (level < MIN_LEVEL || level > MAX_LEVEL)
		{
			throw new IllegalArgumentException("level must be in " + MIN_LEVEL + ".." + MAX_LEVEL + ": " + level);
		}
		return XP_FOR_LEVEL[level];
	}

	/**
	 * @return the real level for {@code xp}, capped at 99
	 * @throws IllegalArgumentException if xp is negative or above 200m
	 */
	public static int levelForXp(long xp)
	{
		requireValidXp(xp);
		int low = MIN_LEVEL;
		int high = MAX_LEVEL;
		// Highest level whose threshold is <= xp
		while (low < high)
		{
			int mid = (low + high + 1) >>> 1;
			if (XP_FOR_LEVEL[mid] <= xp)
			{
				low = mid;
			}
			else
			{
				high = mid - 1;
			}
		}
		return low;
	}

	static void requireValidXp(long xp)
	{
		if (xp < 0 || xp > MAX_XP)
		{
			throw new IllegalArgumentException("xp must be in 0.." + MAX_XP + ": " + xp);
		}
	}
}
