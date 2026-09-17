package com.pethunter.ui;

import javax.annotation.Nullable;

/**
 * Receives the XP-derived source the player picks for a pet. Called on the Swing thread.
 */
@FunctionalInterface
public interface MethodChoiceListener
{
	MethodChoiceListener NONE = (petId, sourceId) ->
	{
	};

	/**
	 * @param sourceId the chosen source, or null when the player clears the choice
	 */
	void onMethodChosen(String petId, @Nullable String sourceId);
}
