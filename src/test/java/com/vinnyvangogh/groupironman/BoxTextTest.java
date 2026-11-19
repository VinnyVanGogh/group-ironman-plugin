package com.vinnyvangogh.groupironman;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BoxTextTest
{
	private static final long NOW = EventFixtures.NOW;
	private final FakeItems items = new FakeItems();

	private static GroupEvent level(String member, String skill, long level)
	{
		return GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW - 30_000, member).text(skill).number(level).build();
	}

	private BoxText.Line line(GroupEvent e, BoxSize size, boolean icons)
	{
		return BoxText.format(e, items, size, icons, BoxTimes.SHORT, NOW);
	}

	@Test
	public void shortTimes()
	{
		assertEquals("now", BoostText.shortAgo(NOW, NOW - 59_000));
		assertEquals("now", BoostText.shortAgo(NOW, NOW + 5_000));
		assertEquals("8m", BoostText.shortAgo(NOW, NOW - TimeUnit.MINUTES.toMillis(8) - 5_000));
		assertEquals("2h", BoostText.shortAgo(NOW, NOW - TimeUnit.MINUTES.toMillis(150)));
		assertEquals("3d", BoostText.shortAgo(NOW, NOW - TimeUnit.HOURS.toMillis(80)));
	}

	@Test
	public void timeModes()
	{
		long then = NOW - TimeUnit.MINUTES.toMillis(8);
		assertEquals("8m", BoxTimes.SHORT.of(NOW, then));
		assertEquals("8m ago", BoxTimes.FULL.of(NOW, then));
		assertEquals("", BoxTimes.OFF.of(NOW, then));
	}

	@Test
	public void levelUpIsTheSkillIconAndTheLevel()
	{
		BoxText.Line withIcon = line(level("Member 1", "Slayer", 38), BoxSize.MEDIUM, true);
		assertEquals("Slayer", withIcon.icon.skill);
		assertEquals("Member 1", withIcon.member);
		assertEquals("38", withIcon.detail);
		assertEquals("now", withIcon.ago);
		BoxText.Line textOnly = line(level("Member 1", "Slayer", 38), BoxSize.MEDIUM, false);
		assertNull(textOnly.icon);
		assertEquals("Slayer 38", textOnly.detail);
	}

	@Test
	public void onlineIsADotAndTheWorld()
	{
		GroupEvent on = GroupEvent.of(GroupEvent.Type.ONLINE, NOW, "Member 2").text("online").world(480).build();
		BoxText.Line l = line(on, BoxSize.SMALL, true);
		assertEquals(BoxText.Glyph.ONLINE, l.icon.glyph);
		assertEquals("online W480", l.detail);
		assertEquals("W480", line(on, BoxSize.COMPACT, true).detail);
		assertEquals("online W480", line(on, BoxSize.COMPACT, false).detail);
		GroupEvent off = GroupEvent.of(GroupEvent.Type.ONLINE, NOW, "Member 2").text("offline").build();
		assertEquals(BoxText.Glyph.OFFLINE, line(off, BoxSize.SMALL, true).icon.glyph);
		assertEquals("offline", line(off, BoxSize.SMALL, true).detail);
	}

	@Test
	public void aDropShowsItsTopItemAndTheSizeSetsTheDetail()
	{
		GroupEvent drop = GroupEvent.drop(NOW, "Member 3", "Vorkath",
			EventFixtures.items(FakeItems.WHIP, 1, FakeItems.COINS, 40));
		BoxText.Line compact = line(drop, BoxSize.COMPACT, true);
		assertEquals(FakeItems.WHIP, compact.icon.itemId);
		assertEquals(items.amount(1_500_040), compact.detail);
		assertEquals("Abyssal whip", line(drop, BoxSize.MEDIUM, true).detail);
		assertEquals("Abyssal whip " + items.amount(1_500_040), line(drop, BoxSize.LARGE, true).detail);
		assertEquals("Abyssal whip " + items.amount(1_500_040), line(drop, BoxSize.MEDIUM, false).detail);
	}

	private String form(GroupEvent.Type type, String text, long number, boolean textOnly)
	{
		GroupEvent e = GroupEvent.of(type, NOW, "Member 1").text(text).number(number).build();
		return line(e, BoxSize.MEDIUM, !textOnly).detail;
	}

	@Test
	public void eachKindHasItsShortForm()
	{
		assertEquals("Varrock Elite", form(GroupEvent.Type.DIARY, "Varrock Elite", 0, false));
		assertEquals("diary: Varrock Elite", form(GroupEvent.Type.DIARY, "Varrock Elite", 0, true));
		assertEquals("Dragon Slayer II", form(GroupEvent.Type.QUEST, "Dragon Slayer II", 0, false));
		assertEquals("quest: Dragon Slayer II", form(GroupEvent.Type.QUEST, "Dragon Slayer II", 0, true));
		assertEquals("Twisted Bow", form(GroupEvent.Type.CLOG, "Twisted Bow", 0, false));
		assertEquals("log: Twisted Bow", form(GroupEvent.Type.CLOG, "Twisted Bow", 0, true));
		assertEquals("Vorkath 1:12", form(GroupEvent.Type.PERSONAL_BEST, "Vorkath 1:12", 0, false));
		assertEquals("PB: Vorkath 1:12", form(GroupEvent.Type.PERSONAL_BEST, "Vorkath 1:12", 0, true));
		assertEquals("19 Icefiends", form(GroupEvent.Type.SLAYER, "Icefiends", 19, false));
		assertEquals("Slayer 19 Icefiends", form(GroupEvent.Type.SLAYER, "Icefiends", 19, true));
		assertEquals("done Icefiends", form(GroupEvent.Type.SLAYER, "Icefiends", 0, false));
		assertEquals("pet", form(GroupEvent.Type.PET, "", 0, false));
		assertEquals("died", form(GroupEvent.Type.DEATH, "", 0, false));
		assertEquals("life lost", form(GroupEvent.Type.HC_LIFE_LOST, "", 0, false));
		assertEquals("-" + items.amount(1_200_000), form(GroupEvent.Type.DEATH_VALUE, "", 1_200_000, false));
		assertEquals("lost " + items.amount(1_200_000), form(GroupEvent.Type.DEATH_VALUE, "", 1_200_000, true));
		assertEquals("back 12d", form(GroupEvent.Type.BACK, "", 12, false));
		assertEquals("99", form(GroupEvent.Type.MAXED, "Slayer", 99, false));
		assertEquals("99 Slayer", form(GroupEvent.Type.MAXED, "Slayer", 99, true));
		assertEquals("2277", form(GroupEvent.Type.TOTAL_LEVEL, "", 2277, false));
		assertEquals("total 2277", form(GroupEvent.Type.TOTAL_LEVEL, "", 2277, true));
	}

	@Test
	public void sizeChangesWordingOnlyWhereTheTextHasMoreToSay()
	{
		GroupEvent combat = GroupEvent.of(GroupEvent.Type.COMBAT_LEVEL, NOW, "Member 1").number(120).build();
		assertEquals("cmb 120", line(combat, BoxSize.COMPACT, true).detail);
		assertEquals("combat 120", line(combat, BoxSize.WIDE, true).detail);
		GroupEvent pet = GroupEvent.of(GroupEvent.Type.PET, NOW, "Member 1").text("Tiny tempor").build();
		assertEquals("pet", line(pet, BoxSize.COMPACT, true).detail);
		assertEquals("pet Tiny tempor", line(pet, BoxSize.MEDIUM, true).detail);
	}

	@Test
	public void everyTypeAtEverySizeHasAMemberAndWords()
	{
		for (GroupEvent.Type type : GroupEvent.Type.values())
		{
			GroupEvent e = EventFixtures.sample(type);
			for (BoxSize size : BoxSize.values())
			{
				for (boolean icons : new boolean[] {true, false})
				{
					BoxText.Line l = line(e, size, icons);
					String where = type + " " + size + " icons=" + icons;
					assertEquals(where, e.member, l.member);
					assertFalse(where + " has no words", l.detail.isEmpty());
					assertTrue(where + " time " + l.ago, l.ago.matches("now|\\d+[mhd]"));
					if (!icons)
					{
						assertNull(where, l.icon);
					}
				}
			}
		}
	}

	@Test
	public void noIconTypesFallBackToTheFullSentence()
	{
		GroupEvent e = EventFixtures.sample(GroupEvent.Type.ANNOUNCEMENT);
		BoxText.Line l = line(e, BoxSize.MEDIUM, true);
		assertNull(l.icon);
		assertEquals(EventText.describe(e, items), l.detail);
	}

	@Test
	public void timesOffLeavesNoTime()
	{
		assertEquals("", BoxText.format(level("Member 1", "Slayer", 5), items, BoxSize.MEDIUM, true, BoxTimes.OFF,
			NOW).ago);
	}

	@Test
	public void theDefaultSizeIsTheWidthTheBoxAlwaysHad()
	{
		assertEquals(OverlayStyle.WIDTH, BoxSize.MEDIUM.width);
		assertTrue(BoxSize.COMPACT.width < BoxSize.SMALL.width);
		assertTrue(BoxSize.SMALL.width < BoxSize.MEDIUM.width);
		assertTrue(BoxSize.MEDIUM.width < BoxSize.WIDE.width);
		assertTrue(BoxSize.WIDE.width < BoxSize.LARGE.width);
	}

	@Test
	public void truncationCutsTheDetailNeverTheMemberOrTheTime()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		FontMetrics fm = g.getFontMetrics();
		int name = fm.stringWidth("GroupOtt");
		int time = fm.stringWidth("8m");
		int room = OverlayStyle.EventRow.detailRoom(160, 15, name, time);
		assertEquals(160 - 15 - name - 4 - time - 6, room);
		String cut = OverlayStyle.fit("Slayer 38 and a very long detail that cannot fit", fm, room);
		assertTrue(cut.endsWith(".."));
		assertTrue(fm.stringWidth(cut) <= room);
		assertEquals("38", OverlayStyle.fit("38", fm, room));
		// No time takes no room; a name that fills the line leaves none for the detail.
		assertEquals(160 - 15 - name - 4, OverlayStyle.EventRow.detailRoom(160, 15, name, 0));
		assertTrue(OverlayStyle.EventRow.detailRoom(100, 15, 90, time) <= 0);
	}
}
