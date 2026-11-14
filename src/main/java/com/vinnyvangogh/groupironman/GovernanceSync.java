package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link GroupGovernance} wired to the party and the config, without the
 * client: {@link GroupService} hands it a {@link Store} (the plugin's config
 * group), a send (party messages) and names it has already checked against the
 * in-game roster.
 *
 * <p><b>When it sends.</b> Never on its own timer: whatever changed is marked
 * and {@link #flush} sends it on the next game tick while connected. A member
 * joining (their {@code UserSync}) marks everything, which is how a member who
 * was offline picks up the latest roles and settings: any member online
 * resends them, and the signatures make it safe for anyone to relay.
 *
 * <p><b>What it keeps.</b> This client's key pair, the roles and the settings,
 * each under its own config key (no file of its own). Members' announces are
 * kept in memory only. The leader's shared webhooks ({@link GroupWebhooks})
 * are never put in the config: they go to {@code cache} (the group cache file)
 * and come back through {@link #restoreWebhooks}.
 */
@Slf4j
final class GovernanceSync
{
	static final String KEY_IDENTITY = "memberIdentity";
	static final String KEY_ROLES = "groupRoles";
	static final String KEY_SETTINGS = "groupSettings";

	/** The plugin's config group, as far as this needs it. */
	interface Store
	{
		String get(String key);

		/** {@code null} unsets the key. */
		void set(String key, String value);
	}

	private final Gson gson;
	private final Store store;
	private final Consumer<String> send;
	private final Runnable onChange;
	private final GroupGovernance governance;
	private final Consumer<String> cache;

	private boolean rolesDirty;
	private boolean settingsDirty;
	private boolean webhooksDirty;
	private boolean announceDirty = true;
	private String lastAnnounce;
	private String group = "";

	GovernanceSync(Gson gson, Store store, Consumer<String> send, Runnable onChange)
	{
		this(gson, store, send, onChange, json ->
		{
		});
	}

	/** {@code cache} keeps the leader's webhooks document between sessions, outside the config; null clears it. */
	GovernanceSync(Gson gson, Store store, Consumer<String> send, Runnable onChange, Consumer<String> cache)
	{
		this.gson = gson;
		this.store = store;
		this.send = send;
		this.onChange = onChange;
		this.cache = cache;
		GroupSigner.Identity id = GroupSigner.Identity.load(store.get(KEY_IDENTITY));
		if (id == null)
		{
			id = GroupSigner.Identity.create();
			store.set(KEY_IDENTITY, id.save());
		}
		governance = new GroupGovernance(id);
	}

	GroupGovernance governance()
	{
		return governance;
	}

	/** The in-game group, once known or when it changes: loads what was kept for it. */
	synchronized void group(String name)
	{
		String key = GroupMembers.key(name == null ? "" : name);
		if (key.equals(group))
		{
			return;
		}
		group = key;
		governance.group(name);
		GovernanceCodec.Message roles = GovernanceCodec.decode(gson, store.get(KEY_ROLES));
		GovernanceCodec.Message settings = GovernanceCodec.decode(gson, store.get(KEY_SETTINGS));
		governance.load(roles == null ? null : roles.roles, settings == null ? null : settings.settings);
		onChange.run();
	}

	/** A cheap look before decoding: every party message passes through here. */
	static boolean isGovernance(String payload)
	{
		return payload != null && (payload.contains("\"k\":\"" + GovernanceCodec.ROLES + "\"")
			|| payload.contains("\"k\":\"" + GovernanceCodec.SETTINGS + "\"")
			|| payload.contains("\"k\":\"" + GovernanceCodec.MEMBER + "\"")
			|| payload.contains("\"k\":\"" + GovernanceCodec.WEBHOOKS + "\""));
	}

	/** The webhooks document the group cache file held, once it is read. */
	void restoreWebhooks(String json)
	{
		GovernanceCodec.Message m = GovernanceCodec.decode(gson, json);
		if (m != null && GovernanceCodec.WEBHOOKS.equals(m.kind))
		{
			act(() ->
			{
				governance.receiveWebhooks(m.webhooks);
				return true;
			});
		}
	}

	/** A governance message from {@code name}, a roster member. False when it is not one. */
	boolean receive(String name, String payload)
	{
		GovernanceCodec.Message m = GovernanceCodec.decode(gson, payload);
		if (m == null)
		{
			return false;
		}
		GroupRoles rolesBefore = governance.roles();
		GroupSettings settingsBefore = governance.settings();
		GroupWebhooks webhooksBefore = governance.webhooks();
		GroupGovernance.Verdict v = apply(name, m);
		if (v == GroupGovernance.Verdict.REFUSED)
		{
			// The kind and the sender only: never the document, which may hold webhook URLs.
			log.debug("refused group {} from {}", m.kind, name);
		}
		keep(rolesBefore, settingsBefore, webhooksBefore);
		onChange.run();
		return true;
	}

	private GroupGovernance.Verdict apply(String name, GovernanceCodec.Message m)
	{
		switch (m.kind)
		{
			case GovernanceCodec.MEMBER:
				governance.receiveMember(name, m.memberKey, m.prefs);
				return null;
			case GovernanceCodec.ROLES:
				return governance.receiveRoles(m.roles);
			case GovernanceCodec.WEBHOOKS:
				return governance.receiveWebhooks(m.webhooks);
			default:
				return governance.receiveSettings(m.settings);
		}
	}

	/** Someone joined: they may have missed all of it. */
	synchronized void resendAll()
	{
		rolesDirty = true;
		settingsDirty = true;
		webhooksDirty = true;
		announceDirty = true;
	}

	/** This member's opt-outs, re-announced only when they changed. */
	synchronized void prefs(MemberPrefs prefs)
	{
		if (!announcement(prefs).equals(lastAnnounce))
		{
			announceDirty = true;
		}
	}

	/** Game tick, while connected to the group's party: sends whatever changed. */
	void flush(MemberPrefs prefs)
	{
		boolean announce;
		boolean roles;
		boolean settings;
		boolean webhooks;
		synchronized (this)
		{
			announce = announceDirty;
			roles = rolesDirty;
			settings = settingsDirty;
			webhooks = webhooksDirty;
			announceDirty = false;
			rolesDirty = false;
			settingsDirty = false;
			webhooksDirty = false;
		}
		if (announce)
		{
			lastAnnounce = announcement(prefs);
			send.accept(lastAnnounce);
		}
		GroupRoles r = governance.roles();
		if (roles && r != null)
		{
			send.accept(GovernanceCodec.encode(gson, r));
		}
		GroupSettings s = governance.settings();
		// After the roles, so a member who had neither can check the settings at once.
		if (settings && s != null)
		{
			send.accept(GovernanceCodec.encode(gson, s));
		}
		// Only once the leader has shared: a group that never did sends no webhooks document at all.
		GroupWebhooks w = governance.webhooks();
		if (webhooks && w != null)
		{
			send.accept(GovernanceCodec.encode(gson, w));
		}
	}

	private String announcement(MemberPrefs prefs)
	{
		return GovernanceCodec.encodeMember(gson, governance.publicKey(), prefs);
	}

	// ---- This player's actions; false when refused ----

	boolean claim(String self, long now)
	{
		return act(() -> governance.claim(self, now) != null);
	}

	boolean setEditor(String self, String name, boolean editor, long now)
	{
		return act(() -> governance.setEditor(self, name, editor, now) != null);
	}

	boolean save(String self, Map<String, Long> values, long now)
	{
		return act(() -> governance.save(self, values, now) != null);
	}

	/** The leader's "Share webhooks with the group", or a leader's webhook edit while it is on. */
	boolean shareWebhooks(String self, boolean share, Webhooks own, long now)
	{
		return act(() -> governance.shareWebhooks(self, share, own, now));
	}

	void forgetLeader()
	{
		act(() ->
		{
			governance.forgetLeader();
			return true;
		});
	}

	private boolean act(BooleanSupplier action)
	{
		GroupRoles rolesBefore = governance.roles();
		GroupSettings settingsBefore = governance.settings();
		GroupWebhooks webhooksBefore = governance.webhooks();
		boolean ok = action.getAsBoolean();
		if (ok)
		{
			// An unchanged document is the same object, so keep() writes and marks only what changed.
			keep(rolesBefore, settingsBefore, webhooksBefore);
			onChange.run();
		}
		return ok;
	}

	/** Writes, and marks for sending, whatever is no longer what it was. */
	private synchronized void keep(GroupRoles rolesBefore, GroupSettings settingsBefore, GroupWebhooks webhooksBefore)
	{
		GroupWebhooks w = governance.webhooks();
		if (w != webhooksBefore)
		{
			cache.accept(w == null ? null : GovernanceCodec.encode(gson, w));
			webhooksDirty |= w != null;
		}
		GroupRoles r = governance.roles();
		if (r != rolesBefore)
		{
			store.set(KEY_ROLES, r == null ? null : GovernanceCodec.encode(gson, r));
			rolesDirty |= r != null;
		}
		GroupSettings s = governance.settings();
		if (s != settingsBefore)
		{
			store.set(KEY_SETTINGS, s == null ? null : GovernanceCodec.encode(gson, s));
			settingsDirty |= s != null;
		}
	}
}
