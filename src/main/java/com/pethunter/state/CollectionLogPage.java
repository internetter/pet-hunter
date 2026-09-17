package com.pethunter.state;

import java.util.List;
import lombok.Value;

/**
 * A plain snapshot of one collection log page as drawn, copied off the client so everything that
 * interprets it can run and be tested without game state.
 */
@Value
public class CollectionLogPage
{
	@Value
	public static class Item
	{
		int itemId;
		int opacity;
	}

	/** Text of each header line in order, tags left intact. */
	List<String> headerLines;
	List<Item> items;
}
