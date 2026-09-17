package com.pethunter.math;

import java.util.Objects;
import lombok.Value;

/**
 * A computed figure that cannot be separated from its confidence tier or the assumption behind
 * it. Everything the panel renders as a number should arrive as one of these.
 *
 * <p>There is deliberately no way to build an {@link Confidence#UNKNOWN} value: unknown means no
 * number.
 */
@Value
public class TieredValue
{
	double value;
	Confidence confidence;
	/** Plain-language statement of what the figure rests on, suitable for a tooltip. */
	String assumption;

	public TieredValue(double value, Confidence confidence, String assumption)
	{
		if (Double.isNaN(value))
		{
			throw new IllegalArgumentException("value must not be NaN");
		}
		Objects.requireNonNull(confidence, "confidence");
		if (confidence == Confidence.UNKNOWN)
		{
			throw new IllegalArgumentException("an UNKNOWN figure has no value");
		}
		if (assumption == null || assumption.isBlank())
		{
			throw new IllegalArgumentException("every figure needs an assumption");
		}
		this.value = value;
		this.confidence = confidence;
		this.assumption = assumption;
	}
}
