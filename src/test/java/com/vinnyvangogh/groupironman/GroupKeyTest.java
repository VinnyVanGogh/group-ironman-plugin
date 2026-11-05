package com.vinnyvangogh.groupironman;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GroupKeyTest
{
	private static final String KEY = "0123456789abcdef0123456789abcdef";

	@Test
	public void aKeyIs32HexCharactersAndNeverTheSameTwice()
	{
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 200; i++)
		{
			String key = GroupKey.generate();
			assertTrue(key, key.matches("[0-9a-f]{32}"));
			assertTrue("repeated " + key, seen.add(key));
		}
	}

	@Test
	public void theKeyComesFromTheRandomNotFromAnythingPublic()
	{
		// Same seed, same key; different seed, different key: nothing but the random goes in.
		assertEquals(GroupKey.generate(new Random(1)), GroupKey.generate(new Random(1)));
		assertNotEquals(GroupKey.generate(new Random(1)), GroupKey.generate(new Random(2)));
	}

	@Test
	public void passphraseIsStableAndDiffersPerGroupAndKey()
	{
		String other = "fedcba9876543210fedcba9876543210";
		assertEquals(GroupKey.passphrase("GroupShip", KEY), GroupKey.passphrase("groupship ", KEY.toUpperCase()));
		assertNotEquals(GroupKey.passphrase("GroupShip", KEY), GroupKey.passphrase("Other Group", KEY));
		assertNotEquals(GroupKey.passphrase("GroupShip", KEY), GroupKey.passphrase("GroupShip", other));
		assertNull("no key, no passphrase", GroupKey.passphrase("GroupShip", ""));
		assertNull(GroupKey.passphrase("GroupShip", "not a key"));
	}

	@Test
	public void theGroupNameAloneCannotRebuildThePassphrase()
	{
		// Everything public about a group is its name; without the key there is no passphrase at all.
		assertNull(GroupKey.passphrase("GroupShip", null));
		assertTrue(GroupKey.passphrase("GroupShip", KEY).contains(KEY));
	}

	@Test
	public void shareLineRoundTrips()
	{
		String line = GroupKey.shareLine(KEY);
		assertEquals("[GIMU] " + KEY, line);
		assertEquals(KEY, GroupKey.parseShareLine(line));
		assertEquals(KEY, GroupKey.parseShareLine("  [GIMU]" + KEY.toUpperCase() + " "));
		assertEquals("", GroupKey.parseShareLine("[GIMU] " + KEY + " and more"));
		assertEquals("", GroupKey.parseShareLine("[GIMU] 1234"));
		assertEquals("", GroupKey.parseShareLine("hello"));
	}

	@Test
	public void aKeyIsOnlyReadFromGroupChat()
	{
		String line = GroupKey.shareLine(KEY);
		assertEquals(KEY, GroupKey.keyFrom(ChatMessageType.CLAN_GIM_CHAT, line));
		for (ChatMessageType t : new ChatMessageType[]{ChatMessageType.PUBLICCHAT, ChatMessageType.PRIVATECHAT,
			ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT,
			ChatMessageType.GAMEMESSAGE, ChatMessageType.CLAN_GIM_MESSAGE})
		{
			assertEquals(t.name(), "", GroupKey.keyFrom(t, line));
		}
	}

	@Test
	public void shareLinesAreHiddenInGroupChatOnly()
	{
		// Ours and other members' look the same in the chatbox; both are hidden, even a broken one.
		assertTrue(GroupKey.hide(ChatMessageType.CLAN_GIM_CHAT, GroupKey.shareLine(KEY)));
		assertTrue(GroupKey.hide(ChatMessageType.CLAN_GIM_CHAT, "[GIMU] typo"));
		assertFalse(GroupKey.hide(ChatMessageType.CLAN_GIM_CHAT, "anyone got guams?"));
		assertFalse(GroupKey.hide(ChatMessageType.PUBLICCHAT, GroupKey.shareLine(KEY)));
	}
}
