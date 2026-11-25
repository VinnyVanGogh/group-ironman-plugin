package com.vinnyvangogh.groupironman;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The "Group sync is off" banner, shown while this member could join the group's party but has not (Board
 * 2026-11-23). Joining stays the player's own click: the banner's right-click "Join group sync" entry does what
 * the panel button does. RuneLite rejects plugins that rejoin parties on their own, so nothing here joins by itself.
 */
final class SyncReminder extends OverlayPanel
{
	static final String TITLE = "Group sync is off - click to join";
	static final String JOIN = "Join group sync";
	static final String CHAT = "Group sync is off. Right-click the banner and pick 'Join group sync', or press it"
		+ " in the GIM Utilities panel.";

	/** Set on the client thread each tick; read by render. */
	private volatile boolean shown;

	SyncReminder()
	{
		setPosition(OverlayPosition.TOP_CENTER);
		OverlayStyle.frame(panelComponent, OverlayStyle.BANNER_WIDTH);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, JOIN, "GIM Utilities"));
	}

	/** Sync is on with a key and a known group, but this client is not in the group's party. */
	static boolean due(GroupSync.State state)
	{
		return state == GroupSync.State.READY || state == GroupSync.State.STALE;
	}

	void show(boolean on)
	{
		shown = on;
	}

	boolean shown()
	{
		return shown;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!shown)
		{
			return null;
		}
		panelComponent.getChildren().add(new OverlayStyle.Row(TITLE, OverlayStyle.ACCENT));
		panelComponent.getChildren().add(new OverlayStyle.Row("Right-click > " + JOIN, OverlayStyle.MUTED));
		return super.render(graphics);
	}
}
