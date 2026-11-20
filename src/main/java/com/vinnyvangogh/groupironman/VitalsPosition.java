package com.vinnyvangogh.groupironman;

/** Where the group vitals HUD sits. Public for the config proxy (see {@link DiscordStyle}). */
public enum VitalsPosition
{
	BESIDE_PLAYER("Beside your character"),
	TOP_LEFT("Top left"),
	TOP_RIGHT("Top right"),
	BOTTOM_LEFT("Bottom left"),
	BOTTOM_RIGHT("Bottom right");

	private final String label;

	VitalsPosition(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
