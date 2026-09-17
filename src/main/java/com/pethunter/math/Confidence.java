package com.pethunter.math;

/**
 * How much a figure can be trusted. See docs/DESIGN.md section 3.
 *
 * <p>Declared strongest first; {@link #weakest} relies on that order.
 */
public enum Confidence
{
	/** Attempt count read from a real counter (collection log KC, chat KC). */
	EXACT,
	/** Attempt count derived from XP under a stated method assumption, or entered by the user. */
	ESTIMATED,
	/** No attempt count or no verified rate. Never accompanied by a number. */
	UNKNOWN;

	public static Confidence weakest(Confidence a, Confidence b)
	{
		return a.ordinal() >= b.ordinal() ? a : b;
	}
}
