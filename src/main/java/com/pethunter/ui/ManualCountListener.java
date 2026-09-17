package com.pethunter.ui;

import javax.annotation.Nullable;

/**
 * Receives attempt counts the player types into the panel. Called on the Swing thread.
 */
@FunctionalInterface
public interface ManualCountListener
{
	ManualCountListener NONE = (sourceId, count) ->
	{
	};

	/**
	 * @param count the entered count, or null when the player clears it
	 */
	void onManualCount(String sourceId, @Nullable Long count);
}
