package com.pethunter.ui;

import com.pethunter.data.Pet;
import com.pethunter.math.DrynessResult;
import com.pethunter.math.PlayerProgress;
import lombok.Value;

/**
 * One pet as the panel sees it: the dataset entry, whether the player has it, its dryness, and the
 * progress it was computed from.
 */
@Value
public class PetEntry
{
	Pet pet;
	boolean obtained;
	DrynessResult dryness;
	PlayerProgress progress;
	/** The XP-derived source in use for this pet, or null when none is chosen. */
	@javax.annotation.Nullable
	String assumedSourceId;

	public PetEntry(Pet pet, boolean obtained, DrynessResult dryness)
	{
		this(pet, obtained, dryness, PlayerProgress.empty(), null);
	}

	public PetEntry(Pet pet, boolean obtained, DrynessResult dryness, PlayerProgress progress,
		@javax.annotation.Nullable String assumedSourceId)
	{
		this.assumedSourceId = assumedSourceId;
		this.pet = pet;
		this.obtained = obtained;
		this.dryness = dryness;
		this.progress = progress;
	}
}
