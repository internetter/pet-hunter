package com.pethunter.data;

import lombok.Value;

/**
 * Rate denominators for a {@link RateModel#CONTRIBUTION_SCALED} source, at minimum and at
 * maximum contribution.
 */
@Value
public class ContributionRange
{
	int atMinimumContribution;
	int atMaximumContribution;

	/**
	 * The larger denominator, i.e. the rarer rate. Estimates without contribution data use this,
	 * so they never overstate how dry a player is.
	 */
	public int getRarestDenominator()
	{
		return Math.max(atMinimumContribution, atMaximumContribution);
	}

	/**
	 * The smaller denominator, i.e. the most common rate.
	 */
	public int getCommonestDenominator()
	{
		return Math.min(atMinimumContribution, atMaximumContribution);
	}
}
