package com.vinnyvangogh.groupironman;

/**
 * Whose name a Discord post goes out under. Public, like every setting type: RuneLite reads settings through a
 * proxy that cannot see a package-private type, and the client black-screens (ConfigKeysTest).
 */
public enum DiscordPosterName
{
	/** No {@code username} is sent, so Discord shows the name and avatar set on the webhook. */
	WEBHOOK("Use the webhook's own name"),
	GIM_UTILITIES("GIM Utilities"),
	CUSTOM("Custom");

	private final String label;

	DiscordPosterName(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
