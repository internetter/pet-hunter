package com.pethunter.ui;

/**
 * Reports the filter, grouping and sort the player picked, so they can be restored next session.
 * Called on the Swing thread.
 */
@FunctionalInterface
public interface ViewListener
{
	ViewListener NONE = (filter, grouping, sort) ->
	{
	};

	void onViewChanged(PetListModel.Filter filter, PetListModel.Grouping grouping, PetListModel.SortOrder sort);
}
