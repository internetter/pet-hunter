package com.pethunter.data;

/**
 * How a pet source's attempt count and per-attempt probability are derived.
 * See docs/DESIGN.md section 2.
 */
public enum RateModel
{
	/** Fixed 1/flatRate per kill. */
	FLAT_PER_KILL,
	/** Fixed 1/flatRate per reward roll or completion. */
	FLAT_PER_ROLL,
	/** 1/(baseChance - level * 25) per action. */
	SKILL_LEVEL_SCALED,
	/** Fixed 1/flatRate per action on a skilling activity; level does not affect it. */
	STATIC_IGNORES_FORMULA,
	/** Rate varies with minigame contribution across contributionRange. */
	CONTRIBUTION_SCALED,
	/** Fixed 1/flatRate per unique drop, not per completion. */
	UNIQUE_CONDITIONAL,
	/** Quest, achievement or purchase. No randomness, so no dryness. */
	ONE_OFF
}
