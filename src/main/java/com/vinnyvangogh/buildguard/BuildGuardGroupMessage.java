package com.vinnyvangogh.buildguard;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * The second message Build Guard sends over RuneLite Party: banks, carried
 * items, group storage changes and live stats.
 *
 * <p>A separate class from {@link BuildGuardPartyMessage} on purpose. The
 * relay routes by {@code getSimpleName()}, so a member still on the first
 * group build never receives these and cannot misread them as a build. The
 * body is one versioned JSON string read by {@link GroupCodec}.
 *
 * <p><b>The class name is the wire contract.</b> Do not rename it.
 */
public class BuildGuardGroupMessage extends PartyMemberMessage
{
	private String payload;

	public BuildGuardGroupMessage()
	{
	}

	BuildGuardGroupMessage(String payload)
	{
		this.payload = payload;
	}

	public String getPayload()
	{
		return payload;
	}
}
