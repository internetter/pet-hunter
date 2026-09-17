package com.pethunter.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

/**
 * Message formats as used by RuneLite 1.12.39's bundled plugins; see {@link GameIds}.
 */
public class ChatParserTest
{
	private static ChatParser.Event parse(String message)
	{
		return ChatParser.parse(message).orElseThrow();
	}

	@Test
	public void petFollowingAndBackpackMessagesAreUnnamedArrivals()
	{
		assertEquals(ChatParser.Kind.PET_RECEIVED, parse("<col=ef1020>You have a funny feeling like you're being followed.</col>").getKind());
		assertEquals(ChatParser.Kind.PET_RECEIVED, parse("You feel something weird sneaking into your backpack.").getKind());
	}

	@Test
	public void duplicatePetRollIsNotAnArrival()
	{
		assertEquals(ChatParser.Kind.PET_DUPLICATE, parse("<col=ef1020>You have a funny feeling like you would have been followed...</col>").getKind());
	}

	@Test
	public void collectionLogMessageNamesTheItem()
	{
		ChatParser.Event event = parse("New item added to your collection log: <col=ef1020>Baby mole</col>");

		assertEquals(ChatParser.Kind.COLLECTION_LOG_ITEM, event.getKind());
		assertEquals("Baby mole", event.getSubject());
	}

	@Test
	public void killCountMapsToTheCollectionLogCounterKey()
	{
		ChatParser.Event event = parse("Your Callisto kill count is: <col=ff0000>1,021</col>.");

		assertEquals(ChatParser.Kind.KILL_COUNT, event.getKind());
		assertEquals("callisto_kills", event.getSubject());
		assertEquals(1_021L, event.getCount());
	}

	@Test
	public void unrelatedMessagesAreIgnored()
	{
		assertFalse(ChatParser.parse("Welcome to Old School RuneScape.").isPresent());
		assertFalse(ChatParser.parse("Your Tempoross success count is: <col=ff0000>12</col>.").isPresent());
		assertFalse(ChatParser.parse("New item added to your collection log: ").isPresent());
		assertFalse(ChatParser.parse(null).isPresent());
	}
}
