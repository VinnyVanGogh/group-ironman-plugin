package com.vinnyvangogh.buildguard;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * The one message Build Guard sends over RuneLite Party.
 *
 * <p><b>The class name is the wire contract.</b> RuneLite's party client
 * routes messages by {@code getSimpleName()}, so renaming this class makes
 * every other member's build stop understanding it. The body is a single
 * versioned JSON string, read by {@link PartyBuildCodec}, so the format can
 * grow without touching this class.
 *
 * <p>Public because RuneLite's websocket Gson and event bus construct and
 * dispatch it from outside this package.
 */
public class BuildGuardPartyMessage extends PartyMemberMessage
{
	private String payload;

	public BuildGuardPartyMessage()
	{
	}

	BuildGuardPartyMessage(String payload)
	{
		this.payload = payload;
	}

	public String getPayload()
	{
		return payload;
	}
}
