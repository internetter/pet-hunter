package com.pethunter.state;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/**
 * Copies the currently drawn collection log page off the client. Must run on the client thread.
 * Reads only; never interacts with the interface.
 */
public final class CollectionLogReader
{
	private CollectionLogReader()
	{
	}

	@Nullable
	public static CollectionLogPage read(Client client)
	{
		Widget header = client.getWidget(GameIds.COLLECTION_HEADER_TEXT);
		Widget items = client.getWidget(GameIds.COLLECTION_ITEMS);
		if (header == null || items == null)
		{
			return null;
		}

		List<String> lines = new ArrayList<>();
		for (Widget child : children(header))
		{
			if (child != null && child.getText() != null && !child.getText().isEmpty())
			{
				lines.add(child.getText());
			}
		}

		List<CollectionLogPage.Item> pageItems = new ArrayList<>();
		for (Widget child : children(items))
		{
			if (child != null && child.getItemId() > 0)
			{
				pageItems.add(new CollectionLogPage.Item(child.getItemId(), child.getOpacity()));
			}
		}

		return new CollectionLogPage(List.copyOf(lines), List.copyOf(pageItems));
	}

	private static Widget[] children(Widget widget)
	{
		Widget[] dynamic = widget.getDynamicChildren();
		if (dynamic != null && dynamic.length > 0)
		{
			return dynamic;
		}
		Widget[] statics = widget.getChildren();
		return statics == null ? new Widget[0] : statics;
	}
}
