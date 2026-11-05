package com.vinnyvangogh.groupironman;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;

/**
 * The group key: 128 random bits as 32 hex characters, generated once by the
 * first member to set up sync and shared with the others in group chat.
 *
 * <p>It is the only secret in the party passphrase. Everything else a group
 * has (its name, member names, join dates) is on the hiscores, so a passphrase
 * built only from those could be rebuilt by anyone. Party data is relayed to
 * everyone in the party, so this key is what keeps it inside the group.
 *
 * <p>Never logged and never sent anywhere except as part of the passphrase
 * handed to RuneLite's {@code PartyService}, and in the share line the player
 * sends to their own group chat themselves.
 */
final class GroupKey
{
	static final int HEX_LENGTH = 32;
	/** The tag a share line starts with, e.g. {@code [GIMU] 0123...}. */
	static final String TAG = "[GIMU]";

	private static final Pattern KEY = Pattern.compile("[0-9a-f]{" + HEX_LENGTH + "}");
	private static final Pattern SHARE_LINE = Pattern.compile("^\\s*\\[GIMU]\\s*([0-9a-fA-F]{" + HEX_LENGTH + "})\\s*$");
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private GroupKey()
	{
	}

	/** A new key from a {@link SecureRandom}. */
	static String generate()
	{
		return generate(new SecureRandom());
	}

	static String generate(Random random)
	{
		byte[] bytes = new byte[HEX_LENGTH / 2];
		random.nextBytes(bytes);
		StringBuilder out = new StringBuilder(HEX_LENGTH);
		for (byte b : bytes)
		{
			out.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
		}
		return out.toString();
	}

	/** The key as stored: trimmed and lower-case, or "" when {@code text} is not a key. */
	static String normalize(String text)
	{
		if (text == null)
		{
			return "";
		}
		String key = text.trim().toLowerCase(Locale.ROOT);
		return KEY.matcher(key).matches() ? key : "";
	}

	static boolean isValid(String text)
	{
		return !normalize(text).isEmpty();
	}

	/** What the player pastes into group chat. */
	static String shareLine(String key)
	{
		return TAG + " " + normalize(key);
	}

	/** The key in a share line, or "" when {@code message} is not exactly one. */
	static String parseShareLine(String message)
	{
		if (message == null)
		{
			return "";
		}
		Matcher m = SHARE_LINE.matcher(message);
		return m.matches() ? m.group(1).toLowerCase(Locale.ROOT) : "";
	}

	/** True when {@code message} looks like a share line, valid or not. */
	static boolean looksLikeShareLine(String message)
	{
		return message != null && message.trim().startsWith(TAG);
	}

	/**
	 * The key in a chat line, read from group chat only: every member of a
	 * Group Ironman group is in it and nobody else is. "" for any other channel.
	 */
	static String keyFrom(ChatMessageType type, String message)
	{
		return type == ChatMessageType.CLAN_GIM_CHAT ? parseShareLine(message) : "";
	}

	/** Whether to drop a chat line from this player's chatbox: share lines in group chat, ours and others'. */
	static boolean hide(ChatMessageType type, String message)
	{
		return type == ChatMessageType.CLAN_GIM_CHAT && looksLikeShareLine(message);
	}

	/**
	 * The RuneLite party passphrase for {@code group} and {@code key}, or null
	 * without a valid key. The group name only keeps two groups that somehow
	 * share a key apart; the key is the secret.
	 */
	static String passphrase(String group, String key)
	{
		String k = normalize(key);
		if (k.isEmpty())
		{
			return null;
		}
		String g = group == null ? "" : group.trim().toLowerCase(Locale.ROOT).replace(' ', ' ');
		return "gimu:" + g + ":" + k;
	}
}
