package com.pethunter.data;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * One way a pet can be rolled, with the rate that applies to it.
 * Rates are null unless verified against a cited source.
 */
@Value
public class PetSource
{
	String id;
	String label;
	RateModel rateModel;
	@Nullable
	Integer baseChance;
	@Nullable
	Integer flatRate;
	@Nullable
	ContributionRange contributionRange;
	@Nullable
	Integer minLevel;
	@Nullable
	String counterKey;
	/**
	 * Why the attempt count may include attempts that never rolled the pet, e.g. group kills where
	 * only the MVP rolls. Shown as a warning wherever the count is used.
	 */
	@Nullable
	String countWarning;
	boolean verified;
	/** URLs backing this entry's numbers. Named "sources" in pets.json. */
	List<String> citations;
	@Nullable
	String notes;

	@Builder
	public PetSource(String id, String label, RateModel rateModel, @Nullable Integer baseChance,
		@Nullable Integer flatRate, @Nullable ContributionRange contributionRange, @Nullable Integer minLevel,
		@Nullable String counterKey, @Nullable String countWarning, boolean verified, @Nullable List<String> citations,
		@Nullable String notes)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.label = label == null ? id : label;
		this.rateModel = Objects.requireNonNull(rateModel, "rateModel");
		this.baseChance = baseChance;
		this.flatRate = flatRate;
		this.contributionRange = contributionRange;
		this.minLevel = minLevel;
		this.counterKey = counterKey;
		this.countWarning = countWarning;
		this.verified = verified;
		this.citations = citations == null ? List.of() : List.copyOf(citations);
		this.notes = notes;
	}
}
