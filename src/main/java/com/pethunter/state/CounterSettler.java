package com.pethunter.state;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Waits for a collection log page's counters to stop changing before they are trusted.
 *
 * <p>Observed in-game 2026-09-17: when paging quickly, a page's header shows the new title and
 * counter labels before the counter values are updated, so an immediate read can pair a label
 * with another page's number (Vorkath showed 106 instead of 1,312). Counters are therefore only
 * accepted once the same page has shown identical counters on two consecutive game ticks. A page
 * left before that is skipped, never recorded wrongly.
 *
 * <p>Pure and not thread-safe; drive it from the client thread.
 */
public class CounterSettler
{
	/** Give up on a page after this many ticks without two matching reads. */
	static final int MAX_TICKS = 10;

	private String title;
	private Map<String, Long> lastCounters;
	private int ticks;

	/**
	 * Starts waiting on a newly drawn page, replacing any page still waiting.
	 */
	public void start(String pageTitle)
	{
		title = pageTitle;
		lastCounters = null;
		ticks = 0;
	}

	public boolean isWaiting()
	{
		return title != null;
	}

	/**
	 * Feeds the page as read on a game tick.
	 *
	 * @param result the page currently drawn, or null if the collection log is closed or undrawn
	 * @return the settled counters, once, when two consecutive reads of the same page agree
	 */
	public Optional<Map<String, Long>> onTick(@Nullable CollectionLogParser.Result result)
	{
		if (title == null)
		{
			return Optional.empty();
		}
		if (result == null || !title.equals(result.getTitle()) || ++ticks > MAX_TICKS)
		{
			cancel();
			return Optional.empty();
		}
		if (lastCounters != null && Objects.equals(lastCounters, result.getCounters()))
		{
			cancel();
			return Optional.of(result.getCounters());
		}
		lastCounters = result.getCounters();
		return Optional.empty();
	}

	public void cancel()
	{
		title = null;
		lastCounters = null;
		ticks = 0;
	}
}
