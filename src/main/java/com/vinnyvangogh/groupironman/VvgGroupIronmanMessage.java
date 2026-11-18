package com.vinnyvangogh.groupironman;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * The one message GIM Utilities sends over RuneLite Party: banks, carried
 * items, group storage changes, live stats, extras and drops.
 *
 * <p>The relay routes by {@code getSimpleName()}, so the class name is the
 * message's type id, shared by every plugin on the client. It is
 * author-prefixed so it cannot meet another plugin's message: not Build
 * Guard's {@code BuildGuardGroupMessage} (both plugins may run side by side,
 * and neither receives, decodes or answers the other's traffic), and not a
 * generic name another group plugin might also pick. The body is one
 * versioned JSON string read by {@link GroupCodec}.
 *
 * <p><b>The class name is the wire contract.</b> Do not rename it, and keep it
 * when the plugin's display name changes.
 */
public class VvgGroupIronmanMessage extends PartyMemberMessage
{
	private String payload;

	public VvgGroupIronmanMessage()
	{
	}

	VvgGroupIronmanMessage(String payload)
	{
		this.payload = payload;
	}

	public String getPayload()
	{
		return payload;
	}
}
