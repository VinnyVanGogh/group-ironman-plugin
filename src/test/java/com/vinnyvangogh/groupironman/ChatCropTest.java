package com.vinnyvangogh.groupironman;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChatCropTest
{
	private static final Dimension FIXED = new Dimension(765, 503);

	@Test
	public void dropsKeepTheFullScreen()
	{
		assertFalse(ChatCrop.chatOnly(GroupEvent.Type.DROP));
		assertFalse(ChatCrop.chatOnly(GroupEvent.Type.UNIQUE));
		assertFalse(ChatCrop.chatOnly(GroupEvent.Type.RAID_UNIQUE));
		assertFalse(ChatCrop.chatOnly(GroupEvent.Type.BARROWS));
		assertFalse(ChatCrop.chatOnly(GroupEvent.Type.GRAVESTONE_EXPIRING));
		assertFalse(ChatCrop.chatOnly(GroupEvent.Type.ACTIVITY_WAVE));
	}

	@Test
	public void chatEventsAreCropped()
	{
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.LEVEL_UP));
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.QUEST));
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.DIARY));
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.COMBAT_TASK));
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.CLOG));
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.PET));
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.DEATH));
		assertTrue(ChatCrop.chatOnly(GroupEvent.Type.CLUE));
	}

	@Test
	public void fixedModeIsOneToOne()
	{
		Rectangle r = ChatCrop.region(new Rectangle(0, 338, 519, 165), FIXED, 765, 503);
		assertEquals(new Rectangle(0, 338, 519, 165), r);
	}

	@Test
	public void resizableChatAtTheBottomLeft()
	{
		Dimension real = new Dimension(1600, 900);
		assertEquals(new Rectangle(0, 744, 519, 156),
			ChatCrop.region(new Rectangle(0, 744, 519, 156), real, 1600, 900));
	}

	@Test
	public void stretchedFrameScalesTheBounds()
	{
		// A 765x503 client drawn at twice the size.
		Rectangle r = ChatCrop.region(new Rectangle(0, 338, 519, 165), FIXED, 1530, 1006);
		assertEquals(new Rectangle(0, 676, 1038, 330), r);
	}

	@Test
	public void boundsPastTheFrameAreClamped()
	{
		Rectangle r = ChatCrop.region(new Rectangle(700, 480, 200, 100), FIXED, 765, 503);
		assertEquals(new Rectangle(700, 480, 65, 23), r);
	}

	@Test
	public void hiddenOrCollapsedChatFallsBackToFullScreen()
	{
		assertNull(ChatCrop.region(null, FIXED, 765, 503));
		assertNull(ChatCrop.region(new Rectangle(0, 338, 0, 0), FIXED, 765, 503));
		assertNull(ChatCrop.region(new Rectangle(0, 338, 519, 0), FIXED, 765, 503));
		assertNull(ChatCrop.region(new Rectangle(900, 600, 100, 100), FIXED, 765, 503));
		assertNull(ChatCrop.region(new Rectangle(0, 338, 519, 165), null, 765, 503));
	}

	@Test
	public void cropIsTheChatPixelsOnly()
	{
		BufferedImage frame = new BufferedImage(765, 503, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 503; y++)
		{
			for (int x = 0; x < 765; x++)
			{
				frame.setRGB(x, y, y >= 338 && x < 519 ? 0x00FF00 : 0xFF0000);
			}
		}
		BufferedImage out = ChatCrop.crop(frame, new Rectangle(0, 338, 519, 165), FIXED);
		assertEquals(519, out.getWidth());
		assertEquals(165, out.getHeight());
		assertEquals(0x00FF00, out.getRGB(0, 0) & 0xFFFFFF);
		assertEquals(0x00FF00, out.getRGB(518, 164) & 0xFFFFFF);
	}

	@Test
	public void noBoundsReturnsTheSameFrame()
	{
		BufferedImage frame = new BufferedImage(765, 503, BufferedImage.TYPE_INT_RGB);
		assertSame(frame, ChatCrop.crop(frame, null, FIXED));
	}
}
