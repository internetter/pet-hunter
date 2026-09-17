package com.pethunter.state;

import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;

/**
 * Every game identifier and chat string this plugin depends on, each with what it was verified
 * against. Anything marked UNVERIFIED must be confirmed in-game before behaviour relies on it.
 */
public final class GameIds
{
	private GameIds()
	{
	}

	/**
	 * Script that draws a collection log page. Verified: ScriptID.COLLECTION_DRAW_LIST = 2731 in
	 * runelite-api 1.12.39, and observed firing with the page fully populated in-game (2026-09-17).
	 */
	public static final int COLLECTION_DRAW_LIST_SCRIPT = ScriptID.COLLECTION_DRAW_LIST;

	/**
	 * Header lines of the open page. Verified in-game 2026-09-17 (runelite-api 1.12.39): children
	 * are, in order, the page title, "Obtained: &lt;col=..&gt;x/y&lt;/col&gt;", an optional
	 * "Personal Best: ..." line, and one "&lt;Label&gt;: &lt;col=..&gt;n&lt;/col&gt;" line per counter,
	 * e.g. "Callisto kills" and "Artio kills" on the Callisto and Artio page.
	 */
	public static final int COLLECTION_HEADER_TEXT = InterfaceID.Collection.HEADER_TEXT;

	/**
	 * Item grid of the open page. Verified in-game 2026-09-17 (runelite-api 1.12.39): component 37,
	 * ITEMS_CONTENTS, holds one dynamic child per item. (InterfaceID.Collection.ITEMS is empty.)
	 */
	public static final int COLLECTION_ITEMS = InterfaceID.Collection.ITEMS_CONTENTS;

	/**
	 * Verified in-game 2026-09-17: obtained items are drawn at opacity 0 and missing items at 175.
	 * The All Pets page showed exactly 7 items at 0 with a header of "Obtained: 7/71".
	 */
	public static final int COLLECTION_ITEM_OBTAINED_OPACITY = 0;

	/**
	 * Verified: same literals are used by RuneLite 1.12.39's bundled plugins (string constants in
	 * client-1.12.39.jar, 2026-09-17), and match https://oldschool.runescape.wiki/w/Pet.
	 * Not yet observed in-game by this plugin.
	 */
	public static final String PET_FOLLOWING_MESSAGE = "You have a funny feeling like you're being followed";
	public static final String PET_BACKPACK_MESSAGE = "You feel something weird sneaking into your backpack";
	public static final String PET_DUPLICATE_MESSAGE = "You have a funny feeling like you would have been followed";

	/**
	 * Verified: literal "New item added to your collection log: " in RuneLite 1.12.39's bundled
	 * plugins (2026-09-17). Only sent when the player enables the in-game collection log
	 * notification. Not yet observed in-game by this plugin.
	 */
	public static final String COLLECTION_LOG_ITEM_PREFIX = "New item added to your collection log: ";

	/**
	 * Verified: pattern "Your (.+) (?:kill|success) count is: ?&lt;col=[0-9a-f]{6}&gt;([0-9,]+)&lt;/col&gt;"
	 * in RuneLite 1.12.39's chat commands plugin (2026-09-17). Only "kill" counts are recorded;
	 * other wordings (success, completions, raids) are ignored until their collection log labels
	 * are confirmed. Not yet observed in-game by this plugin.
	 */
	public static final String KILL_COUNT_REGEX = "Your (.+) kill count is: ?<col=[0-9a-f]{6}>([0-9,]+)</col>";
}
