package com.pethunter.ui;

import javax.swing.JLabel;

/**
 * Puts an item's icon on a label. The plugin backs this with ItemManager; tests and an empty
 * client pass {@link #NONE}.
 */
@FunctionalInterface
public interface PetIconLoader
{
	PetIconLoader NONE = (itemId, label) ->
	{
	};

	void load(int itemId, JLabel target);
}
