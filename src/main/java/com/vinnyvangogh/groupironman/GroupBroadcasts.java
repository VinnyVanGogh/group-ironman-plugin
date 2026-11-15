package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game's own Group channel broadcasts ({@code ChatMessageType.CLAN_GIM_MESSAGE}) as group events, so members
 * without the plugin get posted too (Board 2026-11-14). Pure: the engine decides who is a plugin user and who posts.
 *
 * <p><b>Sources.</b> The level-up and quest lines are the Board's own captures ("GroupOtt has reached Herblore level
 * 21.", "... has completed a quest: Black Knights' Fortress"). The pet line is Dink's
 * {@code PetNotifier.CLAN_REGEX} (pajlads/DinkPlugin, which reads {@code CLAN_GIM_MESSAGE} in
 * {@code DinkPlugin.onChatMessage}). The rest follow the game's clan broadcast wording as remembered and are
 * <b>unverified</b>: a line the game words differently is simply not posted, never posted wrong, and each needs one
 * in-game check (BRIEF.md, Phase 0 #27).
 *
 * <p>The chatbox shows a broadcast as "[GroupName] Alex has ..."; the bracketed group name is the sender, not part
 * of the message, but a leading one is tolerated in case a client passes it through.
 */
final class GroupBroadcasts
{
	/** The footer's note on a post made from a broadcast. */
	static final String SOURCE = "from the game's group broadcast";
	/** How long a posted broadcast is remembered, so a restart or a late claim never posts it again. */
	static final long REMEMBER_MS = 24L * 60 * 60 * 1000;
	/** Most broadcasts remembered at once; a busy group makes a few dozen a day. */
	static final int MAX_SEEN = 500;

	/** A display name: letters, digits, spaces, hyphens and underscores, at most 12 (the game's own rule). */
	private static final String NAME = "(?<m>[A-Za-z0-9 _-]{1,12})";
	private static final Pattern GROUP_TAG = Pattern.compile("^\\[[^]]*]\\s*");
	private static final Pattern NUMBER = Pattern.compile("[\\d,]+");

	private static final Map<Pattern, BiFunction<Matcher, Long, GroupEvent.Builder>> LINES = new LinkedHashMap<>();

	static
	{
		// Combat first: "has reached combat level 100" would otherwise read as a skill called "combat".
		line(NAME + " has reached combat level (?<n>\\d+)", (m, at) -> of(GroupEvent.Type.COMBAT_LEVEL, m, at)
			.number(number(m.group("n"))));
		line(NAME + " has reached a total level of (?<n>[\\d,]+)", (m, at) -> of(GroupEvent.Type.TOTAL_LEVEL, m, at)
			.number(number(m.group("n"))));
		line(NAME + " has reached the highest possible (?<s>[A-Za-z]+) level of 99", (m, at) ->
			of(GroupEvent.Type.MAXED, m, at).text(m.group("s")).number(ProgressTracker.MAX_LEVEL));
		line(NAME + " has reached (?<s>[A-Za-z]+) level (?<n>\\d+)", GroupBroadcasts::level);
		line(NAME + " has reached (?<n>[\\d,]+) XP in (?<s>[A-Za-z]+)", (m, at) ->
			of(GroupEvent.Type.XP_MILESTONE, m, at).text(m.group("s")).number(number(m.group("n"))));
		line(NAME + " has completed a quest: (?<q>.+)", (m, at) -> of(GroupEvent.Type.QUEST, m, at)
			.text(m.group("q")));
		line(NAME + " has completed an? (?<t>\\w+) combat task: (?<c>.+?)(?: \\(\\d+ points?\\))?", (m, at) ->
			of(GroupEvent.Type.COMBAT_TASK, m, at).text(m.group("c")).detail(m.group("t")));
		line(NAME + " has completed the (?<d>.+?) diary", (m, at) -> of(GroupEvent.Type.DIARY, m, at)
			.text(m.group("d")));
		line(NAME + " has achieved a new (?<b>.+?) personal best: (?<t>[\\d:.]+)", (m, at) ->
			of(GroupEvent.Type.PERSONAL_BEST, m, at).text(m.group("b") + " " + m.group("t")));
		line(NAME + " received a new collection log item: (?<i>.+?)(?: \\(\\d+/\\d+\\))?", (m, at) ->
			of(GroupEvent.Type.CLOG, m, at).text(m.group("i")));
		line(NAME + " (?:has a funny feeling like .+ followed|feels something weird sneaking into .+ backpack"
			+ "|feels like .+ acquired something special): (?<p>.+?)(?: at .+)?", (m, at) ->
			of(GroupEvent.Type.PET, m, at).text(m.group("p")));
		line(NAME + " has been defeated by (?<k>.+?)(?: in .+?)?(?: and lost .+)?", (m, at) ->
			of(GroupEvent.Type.DEATH, m, at).text(m.group("k")));
		line(NAME + " has died(?: .*)?", (m, at) -> of(GroupEvent.Type.DEATH, m, at));
	}

	private static final Pattern DROP = Pattern.compile("^" + NAME
		+ " received a drop: (?:(?<q>[\\d,]+) x )?(?<i>.+?)(?: \\([\\d,]+ coins\\))?$");

	private GroupBroadcasts()
	{
	}

	private static void line(String regex, BiFunction<Matcher, Long, GroupEvent.Builder> build)
	{
		LINES.put(Pattern.compile("^" + regex + "$"), build);
	}

	private static GroupEvent.Builder of(GroupEvent.Type type, Matcher m, long at)
	{
		return GroupEvent.of(type, at, m.group("m")).broadcast(true);
	}

	/** A 99 by the plain level line is the skill's 99, as the plugin's own tracker files it. */
	private static GroupEvent.Builder level(Matcher m, long at)
	{
		long n = number(m.group("n"));
		GroupEvent.Type type = n >= ProgressTracker.MAX_LEVEL ? GroupEvent.Type.MAXED : GroupEvent.Type.LEVEL_UP;
		return of(type, m, at).text(m.group("s")).number(n);
	}

	/**
	 * The event a broadcast line stands for, under the member it names; null for any other line. A drop needs its
	 * item's id from {@code itemId} (null when unknown, and then no event: a drop is its items).
	 */
	static GroupEvent parse(String message, long at, Function<String, Integer> itemId)
	{
		String text = clean(message);
		if (text.isEmpty())
		{
			return null;
		}
		GroupEvent drop = drop(text, at, itemId);
		if (drop != null)
		{
			return drop;
		}
		for (Map.Entry<Pattern, BiFunction<Matcher, Long, GroupEvent.Builder>> e : LINES.entrySet())
		{
			Matcher m = e.getKey().matcher(text);
			if (m.matches())
			{
				GroupEvent event = e.getValue().apply(m, at).build();
				return event.isValid() ? event : null;
			}
		}
		return null;
	}

	private static GroupEvent drop(String text, long at, Function<String, Integer> itemId)
	{
		Matcher m = DROP.matcher(text);
		if (!m.matches())
		{
			return null;
		}
		Integer id = itemId.apply(m.group("i").trim());
		long quantity = m.group("q") == null ? 1 : number(m.group("q"));
		if (id == null || quantity <= 0)
		{
			return null;
		}
		return of(GroupEvent.Type.DROP, m, at)
			.items(Collections.singletonMap(id, (int) Math.min(Integer.MAX_VALUE, quantity))).build();
	}

	/** Tags off, the game's non-breaking spaces as spaces, a leading "[Group]" and the closing full stop gone. */
	static String clean(String message)
	{
		String text = EventText.plain(message).replace(' ', ' ').trim();
		text = GROUP_TAG.matcher(text).replaceFirst("");
		return text.endsWith(".") ? text.substring(0, text.length() - 1).trim() : text;
	}

	private static long number(String digits)
	{
		Matcher m = NUMBER.matcher(digits == null ? "" : digits);
		if (!m.find())
		{
			return 0;
		}
		try
		{
			return Long.parseLong(m.group().replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** What one client does with a broadcast. */
	enum Action
	{
		/** Not a broadcast, or a plugin user's own (their client posts it). */
		SKIP,
		/**
		 * Log and overlays only: another plugin user posts it, it was posted already, or posting for members without
		 * the plugin is off.
		 */
		SHOW,
		/** Show it and post it: this client is the one poster. */
		POST
	}

	/**
	 * The rule (Board 2026-11-14): a member heard from over the group sync ({@code pluginUser}, the same rule as the
	 * board's "(no plugin)") posts their own events; for the rest, only the client that {@code posts} (the group
	 * setting is on and it {@link ChannelPosts#leads}) posts, and never one {@code seen} already holds.
	 */
	static Action decide(GroupEvent event, Predicate<String> pluginUser, boolean posts, Seen seen, long now)
	{
		if (event == null || pluginUser.test(event.member))
		{
			return Action.SKIP;
		}
		// Seen before (posted, or claimed by the poster ahead of this client's own line): shown, never posted again.
		boolean fresh = seen.add(key(event), now);
		return posts && fresh ? Action.POST : Action.SHOW;
	}

	/**
	 * What makes two sightings the same broadcast: the member, the kind, what it is about and the minute. Every
	 * plugin user sees the same line within a second or two, so they agree on it; a later identical line (the same
	 * drop again next minute) is a new one.
	 */
	static String key(GroupEvent e)
	{
		return (GroupMembers.key(e.member) + "|" + e.type.code + "|" + e.text.toLowerCase(Locale.ROOT) + "|"
			+ e.number + "|" + e.items.keySet() + "|" + e.at / 60_000L).replaceAll("[\\t\\n\\r]", " ");
	}

	/**
	 * The broadcasts this client has posted, or heard another plugin user post, in the last day. Saved so a restart
	 * does not post again, and fed by the party's "posted" claims so two clients never both post one.
	 * Not thread-safe: the engine holds it on the client thread.
	 */
	static final class Seen
	{
		private final LinkedHashMap<String, Long> keys = new LinkedHashMap<>();

		/** True the first time {@code key} is seen in {@link #REMEMBER_MS}; false for a repeat. */
		boolean add(String key, long now)
		{
			prune(now);
			if (key == null || key.isEmpty() || keys.containsKey(key))
			{
				return false;
			}
			keys.put(key, now);
			while (keys.size() > MAX_SEEN)
			{
				keys.remove(keys.keySet().iterator().next());
			}
			return true;
		}

		private void prune(long now)
		{
			keys.values().removeIf(at -> now - at > REMEMBER_MS || at > now + REMEMBER_MS);
		}

		/** One "time TAB key" line per broadcast. */
		String save()
		{
			StringBuilder out = new StringBuilder();
			keys.forEach((k, at) -> out.append(at).append('\t').append(k).append('\n'));
			return out.toString();
		}

		/** What {@link #save} wrote; a bad line is skipped, an empty or missing text is none. */
		static Seen load(String text, long now)
		{
			Seen seen = new Seen();
			if (text == null)
			{
				return seen;
			}
			for (String line : text.split("\n"))
			{
				int tab = line.indexOf('\t');
				long at = tab > 0 ? number(line.substring(0, tab)) : 0;
				if (at > 0)
				{
					seen.keys.put(line.substring(tab + 1), at);
				}
			}
			seen.prune(now);
			return seen;
		}
	}
}
