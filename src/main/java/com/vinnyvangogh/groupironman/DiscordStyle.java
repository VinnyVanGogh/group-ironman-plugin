package com.vinnyvangogh.groupironman;

/**
 * How a Discord post looks. Public, like every setting type: RuneLite reads
 * settings through a proxy that cannot see a package-private type, and the
 * client black-screens (ConfigKeysTest).
 */
public enum DiscordStyle
{
	EMBED("Rich embed"),
	PLAIN("Plain text");

	private final String label;

	DiscordStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
