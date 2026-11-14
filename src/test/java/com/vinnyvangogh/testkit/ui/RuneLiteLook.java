package com.vinnyvangogh.testkit.ui;

import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * RuneLite's own look and feel ({@link RuneLiteLAF}, the dark FlatLaf theme the
 * client installs at startup), so a panel under test is built, sized and drawn
 * the way players see it. Install before creating any component.
 */
public final class RuneLiteLook
{
	private static boolean installed;

	private RuneLiteLook()
	{
	}

	/** Idempotent; call on the Swing thread. */
	public static synchronized void install()
	{
		if (!installed)
		{
			RuneLiteLAF.setup();
			installed = true;
		}
	}
}
