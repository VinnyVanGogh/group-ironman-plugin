package com.vinnyvangogh.groupironman;

/**
 * Whose name a Discord post goes out under. Public, like every setting type: RuneLite reads settings through a
 * proxy that cannot see a package-private type, and the client black-screens (ConfigKeysTest).
 */
public enum DiscordPosterName
{
	/**
	 * No {@code username} is sent, so Discord shows the name and avatar set on the webhook. The label is short
	 * because the dropdown is as wide as its longest label, and a long one squeezed "Poster name" to "P...".
	 * Only the label: the setting is saved under the constant's name, so changing it never resets anyone.
	 */
	WEBHOOK("Webhook's own"),
	/**
	 * The webhook's RuneScape-flavoured default name ({@link Webhook#defaultName}). The constant keeps its first
	 * name, GIM_UTILITIES, because that is what a saved choice is stored under.
	 */
	GIM_UTILITIES("Default name"),
	/** The webhook's Custom box; a blank or refused box uses the default name. */
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
