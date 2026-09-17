package com.pethunter.math;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * The dryness outcome for one source or one whole pet.
 *
 * <p>Exactly one of three states:
 * <ul>
 * <li>{@link Status#FIGURE}: numbers are available, each exposed only as a {@link TieredValue}.</li>
 * <li>{@link Status#UNKNOWN}: no number, and an explanation of what the player can do about it.</li>
 * <li>{@link Status#NOT_APPLICABLE}: the pet has no randomness (ONE_OFF), so dryness is meaningless.
 * This is distinct from zero, one hundred, or NaN.</li>
 * </ul>
 */
@ToString
@EqualsAndHashCode
public final class DrynessResult
{
	public enum Status
	{
		FIGURE,
		UNKNOWN,
		NOT_APPLICABLE
	}

	private final Status status;
	@Nullable
	private final Confidence confidence;
	private final double logProbabilityStillDry;
	private final double expectedDrops;
	@Nullable
	private final Double attempts;
	private final String explanation;
	@Nullable
	private final String attemptPool;
	private final int unknownSourceCount;

	private DrynessResult(Status status, @Nullable Confidence confidence, double logProbabilityStillDry,
		double expectedDrops, @Nullable Double attempts, String explanation, @Nullable String attemptPool,
		int unknownSourceCount)
	{
		if (explanation == null || explanation.isBlank())
		{
			throw new IllegalArgumentException("every result needs an explanation");
		}
		this.status = status;
		this.confidence = confidence;
		this.logProbabilityStillDry = logProbabilityStillDry;
		this.expectedDrops = expectedDrops;
		this.attempts = attempts;
		this.explanation = explanation;
		this.attemptPool = attemptPool;
		this.unknownSourceCount = unknownSourceCount;
	}

	/**
	 * @param attemptPool identifies the stream of attempts this figure consumes (a counter, a
	 *                    skill's XP, a manual entry). Two figures from the same pool must never be
	 *                    multiplied together, because that would count the same attempts twice
	 */
	static DrynessResult figure(double logProbabilityStillDry, double expectedDrops, @Nullable Double attempts,
		Confidence confidence, String assumption, String attemptPool, int unknownSourceCount)
	{
		Objects.requireNonNull(confidence, "confidence");
		if (confidence == Confidence.UNKNOWN)
		{
			throw new IllegalArgumentException("a figure cannot be UNKNOWN");
		}
		if (Double.isNaN(logProbabilityStillDry) || logProbabilityStillDry > 0)
		{
			throw new IllegalArgumentException("log probability must be <= 0: " + logProbabilityStillDry);
		}
		if (!(expectedDrops >= 0) || Double.isInfinite(expectedDrops))
		{
			throw new IllegalArgumentException("expected drops must be finite and non-negative: " + expectedDrops);
		}
		return new DrynessResult(Status.FIGURE, confidence, logProbabilityStillDry, expectedDrops, attempts,
			assumption, attemptPool, unknownSourceCount);
	}

	static DrynessResult unknown(String reason)
	{
		return unknown(reason, 1);
	}

	static DrynessResult unknown(String reason, int unknownSourceCount)
	{
		return new DrynessResult(Status.UNKNOWN, Confidence.UNKNOWN, 0, 0, null, reason, null, unknownSourceCount);
	}

	static DrynessResult notApplicable(String reason)
	{
		return new DrynessResult(Status.NOT_APPLICABLE, null, 0, 0, null, reason, null, 0);
	}

	public Status getStatus()
	{
		return status;
	}

	public boolean hasFigure()
	{
		return status == Status.FIGURE;
	}

	/**
	 * @return the tier, or empty for NOT_APPLICABLE, which has no tier because it has no dryness
	 */
	public Optional<Confidence> getConfidence()
	{
		return Optional.ofNullable(confidence);
	}

	/**
	 * Share of players who would still be without the pet at this count. When
	 * {@link #isUpperBound()}, the true figure is lower: the player is at least this dry.
	 */
	public Optional<TieredValue> getProbabilityStillDry()
	{
		return tiered(Math.exp(logProbabilityStillDry));
	}

	/**
	 * Natural log of {@link #getProbabilityStillDry()}, for display and sorting when the linear
	 * probability underflows to zero.
	 */
	public Optional<TieredValue> getLogProbabilityStillDry()
	{
		return tiered(logProbabilityStillDry);
	}

	/**
	 * Attempts divided by expected attempts: the "times the drop rate" figure.
	 */
	public Optional<TieredValue> getDropRateMultiple()
	{
		return tiered(expectedDrops);
	}

	/**
	 * Attempt count behind a single-source figure. Empty for combined figures, whose attempts are
	 * in different units.
	 */
	public Optional<TieredValue> getAttempts()
	{
		return attempts == null ? Optional.empty() : tiered(attempts);
	}

	/**
	 * For a figure, the assumption it rests on. Otherwise, why there is no figure.
	 */
	public String getExplanation()
	{
		return explanation;
	}

	public Optional<String> getAttemptPool()
	{
		return Optional.ofNullable(attemptPool);
	}

	/**
	 * Sources that could not be counted. On a figure, any value above zero makes it a bound.
	 */
	public int getUnknownSourceCount()
	{
		return unknownSourceCount;
	}

	/**
	 * True when some sources could not be counted, so {@link #getProbabilityStillDry()} is an upper
	 * bound and the player is at least as dry as shown.
	 */
	public boolean isUpperBound()
	{
		return status == Status.FIGURE && unknownSourceCount > 0;
	}

	double rawLogProbabilityStillDry()
	{
		return logProbabilityStillDry;
	}

	double rawExpectedDrops()
	{
		return expectedDrops;
	}

	private Optional<TieredValue> tiered(double value)
	{
		return status == Status.FIGURE ? Optional.of(new TieredValue(value, confidence, explanation)) : Optional.empty();
	}
}
