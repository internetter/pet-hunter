package com.pethunter.ui;

import com.pethunter.data.Pet;
import com.pethunter.data.PetCategory;
import com.pethunter.data.PetSource;
import com.pethunter.data.RateModel;
import com.pethunter.math.DrynessResult;
import com.pethunter.math.PlayerProgress;
import com.pethunter.math.SourceEstimator;
import java.util.List;

/**
 * Builds panel entries through the real estimator. Rates, URLs and counts are arbitrary fixtures,
 * not game data.
 */
final class UiFixtures
{
	static final List<String> FIXTURE_CITATION = List.of("https://fixture.invalid/not-a-real-source");

	private UiFixtures()
	{
	}

	static Pet pet(String id, String name, PetCategory category, String skill, PetSource... sources)
	{
		return Pet.builder().id(id).name(name).category(category).skill(skill).sources(List.of(sources)).build();
	}

	static PetSource killSource(String petId, String label, Integer flatRate)
	{
		return PetSource.builder()
			.id(petId + ".kills")
			.label(label)
			.rateModel(RateModel.FLAT_PER_KILL)
			.flatRate(flatRate)
			.counterKey(petId + "_kc")
			.verified(flatRate != null)
			.citations(flatRate != null ? FIXTURE_CITATION : List.of())
			.build();
	}

	static PetSource oneOff(String petId)
	{
		return PetSource.builder().id(petId + ".reward").label("Quest reward").rateModel(RateModel.ONE_OFF).verified(true).build();
	}

	/**
	 * A boss pet with an EXACT figure from {@code kills} at 1/{@code flatRate}.
	 */
	static PetEntry figureEntry(String id, String name, int flatRate, long kills)
	{
		Pet pet = pet(id, name, PetCategory.BOSS, null, killSource(id, name + " kills", flatRate));
		DrynessResult result = SourceEstimator.estimatePet(pet, PlayerProgress.builder().counter(id + "_kc", kills).build(), null);
		return new PetEntry(pet, false, result);
	}

	static PetEntry unknownEntry(String id, String name, PetCategory category, String skill)
	{
		Pet pet = pet(id, name, category, skill, killSource(id, name + " kills", null));
		return new PetEntry(pet, false, SourceEstimator.estimatePet(pet, PlayerProgress.empty(), null));
	}

	static PetEntry oneOffEntry(String id, String name)
	{
		Pet pet = pet(id, name, PetCategory.QUEST, null, oneOff(id));
		return new PetEntry(pet, false, SourceEstimator.estimatePet(pet, PlayerProgress.empty(), null));
	}

	static PetEntry obtained(PetEntry entry)
	{
		return new PetEntry(entry.getPet(), true, entry.getDryness());
	}
}
