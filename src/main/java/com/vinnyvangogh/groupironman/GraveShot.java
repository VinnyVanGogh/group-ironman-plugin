package com.vinnyvangogh.groupironman;

/**
 * The death's screenshot, held in memory for the gravestone it belongs to, so
 * the "Gravestone expiring" post can show where the player died. Nothing here
 * is written anywhere: a client restart loses it, and the warning then goes
 * out without a picture.
 *
 * <p>Each death is a new grave. A frame that finishes encoding after its grave
 * was replaced or freed is dropped, so a slow encoder never pins an old image.
 * The frame encoder calls {@link #keep} off the client thread; everything else
 * is the client thread.
 */
final class GraveShot
{
	/** What the warning post attaches: {@code picture} false means none; a null {@code png} means the next frame. */
	static final class Choice
	{
		static final Choice NOTHING = new Choice(false, null);
		static final Choice NEXT_FRAME = new Choice(true, null);

		final boolean picture;
		final byte[] png;

		private Choice(boolean picture, byte[] png)
		{
			this.picture = picture;
			this.png = png;
		}
	}

	private int grave;
	private boolean asked;
	private volatile int live;
	private volatile byte[] png;

	/** A death: the previous grave's image, if any, is let go. Returns the new grave's key for {@link #keep}. */
	synchronized int died()
	{
		png = null;
		asked = false;
		live = ++grave;
		return grave;
	}

	/** The death's frame was requested for {@code key}'s grave; true the first time, so it is only asked once. */
	synchronized boolean ask(int key)
	{
		if (key == 0 || key != live || asked)
		{
			return false;
		}
		asked = true;
		return true;
	}

	/** The death's frame, encoded. Kept only while that grave is still the live one. */
	synchronized void keep(int key, byte[] frame)
	{
		if (key == live && live != 0 && frame != null)
		{
			png = frame;
		}
	}

	/** The grave expired, was looted, or the player logged out: the image goes. */
	synchronized void free()
	{
		png = null;
		live = 0;
	}

	/** The live grave's key; 0 when there is none. */
	int live()
	{
		return live;
	}

	/** True while an image is held. */
	boolean holding()
	{
		return png != null;
	}

	/** The picture for the warning post, by the setting. {@code screenshots}: "Attach a screenshot" is on. */
	Choice choose(GraveScreenshot mode, boolean screenshots)
	{
		if (!screenshots || mode == GraveScreenshot.NONE)
		{
			return Choice.NOTHING;
		}
		if (mode == GraveScreenshot.CURRENT)
		{
			return Choice.NEXT_FRAME;
		}
		byte[] held = png;
		return held == null ? Choice.NOTHING : new Choice(true, held);
	}
}
