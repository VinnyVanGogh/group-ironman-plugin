package com.vinnyvangogh.groupironman;

/**
 * The sidebar button's lifecycle: added on startUp, removed on shutDown, and
 * never touched by a config change. With every switch off the panel shows a
 * notice instead of disappearing (Board 2026-11-13: a friend with every switch
 * off saw no panel at all and took the plugin for broken).
 */
final class SidebarButton
{
	static final String OFF_TEXT = "Everything is off. Turn on what you want to share.";
	static final String OFF_BUTTON = "Open GIM Utilities settings";

	private final Runnable add;
	private final Runnable remove;
	private boolean shown;

	SidebarButton(Runnable add, Runnable remove)
	{
		this.add = add;
		this.remove = remove;
	}

	void startUp()
	{
		if (!shown)
		{
			add.run();
			shown = true;
		}
	}

	void shutDown()
	{
		if (shown)
		{
			remove.run();
			shown = false;
		}
	}

	boolean shown()
	{
		return shown;
	}

	/** Whether the panel leads with the "everything is off" notice. */
	static boolean offNotice(boolean anyOn)
	{
		return !anyOn;
	}
}
