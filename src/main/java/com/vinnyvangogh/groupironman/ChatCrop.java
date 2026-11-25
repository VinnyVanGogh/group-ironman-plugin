package com.vinnyvangogh.groupironman;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;

/**
 * Chat-only events (a chat line is the whole story) post a picture of the chat box instead of the whole screen
 * (Board 2026-11-19). The crop is a sub-rectangle of the frame that would have been posted, so it never shows
 * anything the full screenshot would not.
 *
 * <p>Drops, uniques, raid uniques, Barrows, wave activities and gravestones keep the full screen: the drop's
 * inventory, the scene or the interface is the point.
 */
final class ChatCrop
{
	/** Kinds whose evidence is a chat line (or the chat-box dialog); everything else keeps the full screen. */
	private static final Set<GroupEvent.Type> CHAT_ONLY = EnumSet.of(
		GroupEvent.Type.CLOG, GroupEvent.Type.PET, GroupEvent.Type.LEVEL_UP, GroupEvent.Type.MAXED,
		GroupEvent.Type.XP_MILESTONE, GroupEvent.Type.COMBAT_LEVEL, GroupEvent.Type.TOTAL_LEVEL,
		GroupEvent.Type.QUEST, GroupEvent.Type.QUEST_POINTS, GroupEvent.Type.DIARY, GroupEvent.Type.COMBAT_TASK,
		GroupEvent.Type.CLUE, GroupEvent.Type.KC_MILESTONE, GroupEvent.Type.PERSONAL_BEST,
		GroupEvent.Type.SLAYER, GroupEvent.Type.FARMING, GroupEvent.Type.BIRDHOUSE, GroupEvent.Type.DRY_STREAK,
		GroupEvent.Type.DEATH, GroupEvent.Type.DEATH_VALUE, GroupEvent.Type.HC_LIFE_LOST);

	private ChatCrop()
	{
	}

	static boolean chatOnly(GroupEvent.Type type)
	{
		return CHAT_ONLY.contains(type);
	}

	/**
	 * The chat box in the frame's pixels, or null to keep the whole frame: chat hidden or collapsed (empty or
	 * missing bounds), or bounds that fall outside the frame. {@code real} is the client's unstretched size;
	 * a stretched frame is that size scaled, so the bounds scale with it.
	 */
	static Rectangle region(Rectangle chat, Dimension real, int imageWidth, int imageHeight)
	{
		if (chat == null || real == null || real.width <= 0 || real.height <= 0 || imageWidth <= 0
			|| imageHeight <= 0 || chat.width <= 0 || chat.height <= 0)
		{
			return null;
		}
		double sx = (double) imageWidth / real.width;
		double sy = (double) imageHeight / real.height;
		int x = (int) Math.floor(chat.x * sx);
		int y = (int) Math.floor(chat.y * sy);
		int right = (int) Math.ceil((chat.x + chat.width) * sx);
		int bottom = (int) Math.ceil((chat.y + chat.height) * sy);
		Rectangle r = new Rectangle(x, y, right - x, bottom - y).intersection(
			new Rectangle(0, 0, imageWidth, imageHeight));
		return r.isEmpty() || r.width < 2 || r.height < 2 ? null : r;
	}

	/** The frame cropped to {@code chat}, or the frame itself when there is nothing sensible to crop to. */
	static BufferedImage crop(BufferedImage frame, Rectangle chat, Dimension real)
	{
		Rectangle r = region(chat, real, frame.getWidth(), frame.getHeight());
		if (r == null)
		{
			return frame;
		}
		BufferedImage out = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_RGB);
		out.getGraphics().drawImage(frame, 0, 0, r.width, r.height, r.x, r.y, r.x + r.width, r.y + r.height, null);
		return out;
	}
}
