package com.pethunter.data;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
public class Pet
{
	String id;
	String name;
	@Nullable
	Integer itemId;
	PetCategory category;
	/** RuneLite Skill enum name, e.g. "MINING". Null for non-skilling pets. */
	@Nullable
	String skill;
	@Nullable
	String collectionLogPage;
	List<AccountType> obtainableOn;
	List<PetSource> sources;
	List<HuntMethod> methods;

	@Builder
	public Pet(String id, String name, @Nullable Integer itemId, PetCategory category, @Nullable String skill,
		@Nullable String collectionLogPage, @Nullable List<AccountType> obtainableOn,
		@Nullable List<PetSource> sources, @Nullable List<HuntMethod> methods)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.itemId = itemId;
		this.category = category == null ? PetCategory.OTHER : category;
		this.skill = skill;
		this.collectionLogPage = collectionLogPage;
		this.obtainableOn = obtainableOn == null ? List.of() : List.copyOf(obtainableOn);
		this.sources = sources == null ? List.of() : List.copyOf(sources);
		this.methods = methods == null ? List.of() : List.copyOf(methods);
	}

	/**
	 * The method that applies to the given source, if the dataset defines one.
	 */
	public Optional<HuntMethod> getMethodFor(String sourceId)
	{
		return methods.stream().filter(m -> m.getId().equals(sourceId)).findFirst();
	}
}
