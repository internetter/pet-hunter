package com.pethunter.math;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class XpTableTest
{
	@Test
	public void knownThresholds()
	{
		assertEquals(0, XpTable.xpForLevel(1));
		assertEquals(83, XpTable.xpForLevel(2));
		assertEquals(174, XpTable.xpForLevel(3));
		assertEquals(1_154, XpTable.xpForLevel(10));
		assertEquals(101_333, XpTable.xpForLevel(50));
		assertEquals(6_517_253, XpTable.xpForLevel(92));
		assertEquals(13_034_431, XpTable.xpForLevel(99));
	}

	@Test
	public void levelForXpAtBoundaries()
	{
		assertEquals(1, XpTable.levelForXp(0));
		assertEquals(1, XpTable.levelForXp(82));
		assertEquals(2, XpTable.levelForXp(83));
		assertEquals(98, XpTable.levelForXp(13_034_430));
		assertEquals(99, XpTable.levelForXp(13_034_431));
	}

	@Test
	public void levelCapsAt99UpTo200mXp()
	{
		assertEquals(99, XpTable.levelForXp(50_000_000));
		assertEquals(99, XpTable.levelForXp(XpTable.MAX_XP));
	}

	@Test
	public void everyLevelRoundTrips()
	{
		for (int level = 1; level <= 99; level++)
		{
			assertEquals(level, XpTable.levelForXp(XpTable.xpForLevel(level)));
			if (level > 1)
			{
				assertEquals(level - 1, XpTable.levelForXp(XpTable.xpForLevel(level) - 1));
			}
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsLevelZero()
	{
		XpTable.xpForLevel(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsLevel100()
	{
		XpTable.xpForLevel(100);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeXp()
	{
		XpTable.levelForXp(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsXpAbove200m()
	{
		XpTable.levelForXp(XpTable.MAX_XP + 1L);
	}
}
