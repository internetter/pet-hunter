package com.pethunter.ui;

import com.pethunter.data.Pet;
import com.pethunter.math.DrynessResult;
import lombok.Value;

/**
 * One pet as the panel sees it: the dataset entry, whether the player has it, and its dryness.
 */
@Value
public class PetEntry
{
	Pet pet;
	boolean obtained;
	DrynessResult dryness;
}
