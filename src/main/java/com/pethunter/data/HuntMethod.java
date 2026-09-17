package com.pethunter.data;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * How a player performs a {@link PetSource}: throughput and XP figures used to turn skill XP
 * into an action count and to compare methods. Shares its id with the source it applies to.
 */
@Value
public class HuntMethod
{
	String id;
	@Nullable
	Double actionsPerHour;
	@Nullable
	Double xpPerAction;
	List<String> requirements;
	@Nullable
	Boolean xpEfficient;
	boolean verified;
	/** URLs backing this entry's numbers. Named "sources" in pets.json. */
	List<String> citations;
	@Nullable
	String notes;

	@Builder
	public HuntMethod(String id, @Nullable Double actionsPerHour, @Nullable Double xpPerAction,
		@Nullable List<String> requirements, @Nullable Boolean xpEfficient, boolean verified,
		@Nullable List<String> citations, @Nullable String notes)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.actionsPerHour = actionsPerHour;
		this.xpPerAction = xpPerAction;
		this.requirements = requirements == null ? List.of() : List.copyOf(requirements);
		this.xpEfficient = xpEfficient;
		this.verified = verified;
		this.citations = citations == null ? List.of() : List.copyOf(citations);
		this.notes = notes;
	}
}
