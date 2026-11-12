# Changelog

## Unreleased

### Live board: last seen for everyone

- Offline members read *last seen 2 hours ago on world 302* (Discord's own
  relative time, so it stays current without edits) instead of just
  *offline*. Members without the plugin get theirs from when a plugin user
  last saw them online in the Group channel.
- When the last plugin user logs out, the board's final edit keeps every
  member's last seen and ends with *Nobody with GIM Utilities is online, so
  these may be out of date (as of ...)*.
- Last seen is kept in the group cache, so it survives a restart.

### Online status for members without the plugin

- Who is online, and on which world, now comes from the game's own Ironman
  Group channel (the in-game Group tab), so members who don't run the plugin
  show too. The Friends list is the fallback before the channel loads.
- Live board: *online (world 615)* or *offline* for every member, with a
  subtle *(no plugin)* tag for members whose client never reported. *unknown*
  only before the game has said.
- The Group overlay no longer says *Nobody else online* while the game shows
  others online. Sidebar cards show *Online - W615 (no plugin)* or *Offline*.
- Online posts: logins and logouts of members without the plugin are posted
  too, with the same offline grace, by one plugin user only (the first by name
  of those online in the group's party).

### One Discord post per skill's level streak

- New setting **Combine level-ups within (minutes)**, default 30, 0 turns it
  off. The first level-up in a skill posts as before; more levels in that
  skill within the window edit that same post in place: *Prayer 23 → 25 (+2)
  in 4 min*, with a fresh screenshot when screenshots are on.
- A 99, a total-level milestone, maxing and a combat level still get their own
  post. The overlay and the Log still show every level.
- At most one edit every 10 seconds per post; quicker levels fold into the
  next edit. A logout or the window running out starts a new post.
- If the post was deleted or the webhook changed, the next update is posted as
  a new message, and that one is edited from then on. Message ids are kept in
  memory only.
- One action that gains several levels (low Prayer from bones) reads from the
  level before it: *Prayer 3 → 6 (+3)*, not *5 → 6*.
- After three edits in a row lost to Discord or the network, or a post pushed
  out of a full queue, the streak ends and the next level posts fresh.
- Level-up posts use the Progress poster name.

### The Log shows every group event

- The sidebar Log lists every event the overlay and Discord know about,
  yours and members', from the same events: an event is logged even when its
  Discord and overlay switches are off. Every kind is detected while the
  plugin runs, for the Log.
- Search (member, item, place, text), category chips (Drops, Deaths,
  Storage, Progress, Group, Activities), a member picker and a time range.
- "Since you last played" stays on top; the rest is grouped by day. Each row
  has the item or skill icon, member, short line and time; hover for the
  full line and value (*1.5M (1,501,600) gp*); right-click to copy.
- Kept in the group cache file under a new `feed` key: the newest 2,000
  events, none older than 30 days. A cache from before starts the feed from
  its drops and storage logs.

### Amounts read like the game

- Quantities and gp values use the game's stack text everywhere: *Coins -
  450K in group storage*, *took 40K Coins*, *13.2K*, *10M* (RuneLite's
  `QuantityFormatter.quantityToStackSize`). The panel, chat lines, `::find`,
  overlays and storage alerts all read this way.
- The panel shows the exact amount in a tooltip (*450,001*) on item icons,
  Find rows, storage and log lines, and bank and storage totals.
- Discord keeps every digit after the short form: *450K (450,001)*, in
  descriptions, plain posts, the Value field and daily digests (titles carry
  no amounts).
- Item icons show the stack's own picture: a big coin pile for big stacks.
  The panel's Find rows now draw the stack too, and Discord thumbnails use
  the coin pile for the amount.

### Online status: a live board

- New setting **Online status**: *Posts*, *Live board* or *Off*. It replaces
  the "Online and offline" switch; an install that had it ticked stays on
  *Posts*, one that used Discord without it is *Off*, and a new install starts
  on *Posts*: a member opts into *Live board*.
- *Live board* keeps one message in the Group channel listing each member:
  online (with the world) or offline (last seen, kept current by Discord). It
  is edited on each login, logout and hop, at most once every 10 seconds and
  never in quiet hours. If the message is deleted, the next change posts a new
  one. Only members whose clients run the plugin are reported; the others show
  *unknown*. The webhook and the message id stay on your client: neither is
  sent to the group.
- Each member with *Live board* and a Group webhook keeps their own board, so
  only one member sharing a Group webhook should turn it on.
- The board's posts and edits share the Discord queue with every other post
  (at most 20 a minute) and use the Group poster name.

### Slayer posts fixed

- Checking the enchanted gem no longer posts *task done*: a task is done only
  on the line that sends you back to a Slayer master (RuneLite's own rule).
- A task from a master's dialogue now posts (it posted nothing), as does a
  partner's: *New Slayer task: 19 x Icefiends (Turael)*. The gem's check never
  posts the task you already have.
- A finished task reads *Slayer task done: Icefiends (streak 1, 2 points)*.

### Online and offline posts

- New setting **Offline grace (minutes)**, 0 to 10, default 2. A logout waits
  that long; logging back in within it (a disconnect, a quick relog) posts
  nothing for either. A world hop is never a logout, whatever the grace.
- The offline post says *Offline* with your name, how long you played and
  when you logged off: *GroupShip: Offline* / *offline, played 1h 12m on
  world 303, logged off at 1:35 PM*. It used to say *You: Online*.
- At most one online and offline pair per 10 minutes: a flaky connection
  posts nothing until it settles.
- A client closed during the grace posts the offline on the next start only
  when that is within 15 minutes; never a stale one hours later.

### Daily digest is in addition to instant posts

- Ticking a daily digest category no longer holds that category's posts.
  Instant posts follow "Discord: which events" exactly as set; each ticked
  digest category adds one summary a day at the digest hour, listing every
  event of that category since the last digest, ticked for instant posts or
  not. Untick a category's instant events to make it digest-only.
- The digest switches are now "Include in daily digest: Drops" and so on.
  Setting keys are unchanged.
- A webhook field that is set but is not a Discord webhook link now gets one
  game chat line naming the field (never the link), on login or a settings
  change. Surrounding whitespace and quotes are stripped before checking, so
  a link pasted with its quotes works.

### Discord Easter eggs

- New **Easter eggs** switch in the Discord section, off by default. When on,
  some posts get one short funny line under the facts: a 0 gp death, a death
  that lost only a spade (and clue items), a pet, a Bones-only "valuable"
  drop, about ten well-known drops, and big group storage visits.
- **Legend at (gp)** and **Freeloader at (gp)** (default 1M each, 0 turns it
  off) set when a deposit or withdrawal gets its line. Both fire at exactly
  the value: 10K taken with Freeloader at 10K gets a line. Each has four
  rotating phrasings, picked the same way on every client.
- A death that cost 0 gp no longer posts "lost about 0 gp". In a death's post
  the line is left out; on its own it posts nothing with Easter eggs off, and
  only the egg line with them on. The daily digest follows the same rule.
- The lines are all in `EasterEggLines.java`; a new egg is one table row.

### One Discord post per moment

- A group storage visit is one Discord post. With a big deposit or withdrawal
  it is titled "Big storage withdrawal", "Big storage deposit" or "Big storage
  visit" (both), lists every item moved with the big stacks marked, and shows
  the visit's total value. With nothing big it is the "Group storage visit"
  summary, when that is switched on. "Storage minimum hit" and "Took the last
  one" from the same visit are lines in that post, not posts of their own.
- The same rule elsewhere: a kill's drop, untradeable unique, collection log
  slot and pet; a tick's level up, 99, XP milestone, total level and combat
  level; a death, its Hardcore group life and the value it cost. Each is one
  post, titled after the biggest of them, with the rest as fields and one
  screenshot taken when the moment began.
- Discord only: banners, the group box and chat are unchanged.

### Windows launcher in CI

- `Run-GimUtilities.cmd` takes an opt-in `GU_BUILD_ONLY=1` that builds
  without opening RuneLite. Unset, it behaves exactly as before.
- New `windows-launcher` workflow runs the launcher on `windows-latest` the
  way a player would (no Java, no checkout, both the committed `.cmd` and the
  `gim-test-1` release asset) and runs the launcher tests on Windows
  PowerShell 5.1 and PowerShell 7.

### Group events, part A

- One event model (`GroupEvent`, now typed) feeds two outputs, each with its
  own switch per event, all off by default: a **Discord webhook** and
  **in-game banners** plus an optional **group box**.
- Events: valuable drop (with kill count), pet, new collection log slot,
  untradeable unique, death (where, killer, world, Hardcore group life lost),
  big storage deposit / withdrawal, storage visit summary, storage minimum
  hit, took the last one, level up, 99s and max total, XP milestones (1M, 10M,
  200M), combat level, total level every 100, quest complete, quest points
  every 25 and every quest done, online / offline (a hop or quick relog is
  not posted), back after N days, Barrows chest with set progress.
- Discord: main webhook plus optional per-category webhooks, embeds or plain
  text, optional screenshot, mention string for big events, quiet hours.
  At most 20 posts a minute, 50 waiting, Retry-After honoured; the URL is a
  secret field and never logged.
- *Share group events* sends members your non-drop events for their banners;
  a 1.0.0 client ignores them.

## 1.0.0 - 2026-11-04

First Plugin Hub release.

### From the in-game test (2026-11-04)

- **Copy group key / Paste group key.** The key travels outside the game (e.g.
  Discord): *Copy group key* puts it on the computer's clipboard, *Paste group
  key* saves one and joins. Nothing writes to the game chatbox.
- **Joining stays a click.** *Join group sync* once per session (and a paste
  joins once). "Auto-rejoin for parties" is a rejected RuneLite feature.
- Member cards: HP / Prayer / Run / Spec as minimap-style orbs with the
  game's icons; worn items in the equipment tab's layout beside a 4x7
  inventory; a drawn collapse arrow; the header's online count includes you.
- `::find` answers name the search, read from your side ("15 in your bank",
  "140 in group storage", "1 equipped by Sam") and colour each kind of place;
  new *::find colours* settings (item names uncoloured unless turned on). The
  Find tab reads the same way.
- Log tab: group storage changes by member, yours included, above drops.
- Banks tab: each card says whose bank it is, and a filtered result stays at
  the top.
- Fixes: text past the panel's right edge (column alignment), the Group tab
  drawing blank (item stats read off the client thread), the Leave button cut
  off; a tab that fails to draw now says so.

### Round 2 (Board in-game test, 2026-11-04)

- **No Party panel step.** GIM Utilities joins the group's RuneLite party
  itself through the core `PartyService` when you click **Join group sync**;
  the Party plugin can stay off (and its overlays hidden). It asks for member
  names itself (`PartyDataService`, `CHARACTER_NAME`), which nothing did with
  the Party plugin off. Joining waits for the click: auto-joining parties is
  a rejected Plugin Hub feature.
- **Group key.** 128 random bits, never derived from public facts, shared
  with *Copy group key* / *Paste group key* (see 1.0.0 above). A `[GIMU] <key>`
  line someone types in group chat is still read from group chat only, saved
  and hidden. *Group key* in the settings (a password field) is another way
  to paste it; *New key* rotates it. New settings: *Sync with my group* (on),
  *Group key*.
- A party you joined yourself is never replaced (*In another party; GIM sync
  paused*, with a *Switch* button), and on shutdown only a party GIM
  Utilities joined is left. Nothing is sent to any other party.
- Only names on the in-game group roster (`ClanSettings` for
  `ClanID.GROUP_IRONMAN`) are shown; anyone else is ignored and listed as
  *Not in your group*.
- Offline members keep their last world, place, stats and items, saved across
  restarts: *Offline - last seen W303, Draynor, 2h ago*.
- Group storage: the last contents are shown as a snapshot with when and by
  whom, even when nothing moved; changes are logged on each Save and on close
  ("You deposited 5 Trout"), whether or not sync is on.
- One search engine for the Find tab and `::find`: any word or part of the
  name, any case, with shorthands (`ppot`, `dwh`, `brew`, ...). `::find guam`
  now finds Grimy guam leaf, Guam leaf and Guam potion (unf).
- Side panel redesign: a sync header and five tabs (Group, Banks, Storage,
  Find, Log). Member cards with HP / Prayer / Run / Spec bars, a skills icon
  grid in the skills tab's order and item icon grids; banks and storage as
  filterable icon grids with value; Find as icon rows. Every empty state says
  what to do next.

Split out of Build Guard (VinnyVanGogh/build-guard-plugin, up to its PR #5)
as a plugin of its own, so a group can use the group features without Build
Guard's build and XP warnings.

- New identity: plugin name *GIM Utilities*, package
  `com.vinnyvangogh.groupironman`, config group `gimutilities`, party message
  `VvgGroupIronmanMessage`, saved data in `.runelite/gim-utilities/`. None of
  these overlap Build Guard's, so both plugins run side by side without
  reading each other's settings or decoding each other's messages. Settings
  and saved data are not carried over from Build Guard.
- Not named "Group Ironman" and not config group `groupironman`: the Hub
  plugin *Group Iron Man* (slug `group-ironman`) already has that name and
  uses that group.
- Kept from Build Guard: member banks, the group storage log, live stats with
  boosts, online / last seen and place names, inventories and equipment, rune
  pouch, quiver and seed vault, drops and collection log ("Since you last
  played"), "Who has it?", `::find`, storage minimums and world map markers.
- Removed, staying in Build Guard: build rules, the attack-style warning,
  sharing build rules / at-risk lines / the gear-name list with the party,
  and the quest and lamp engine with its bundled quest and lamp data.
- `::find chat command` is now **off by default**, like every other switch:
  a fresh install does nothing until it is turned on.
- Windows double-click launcher `tools/windows/Run-GimUtilities.cmd`, ported
  from Build Guard's: finds or installs a SHA-256-pinned Java 11, downloads
  this repo's `gim-test-1` tag (or uses the checkout it sits in), and runs
  the client. Keeps everything in `%LOCALAPPDATA%\GimUtilities`.
- Built against RuneLite 1.13.5 (was 1.12.39, inherited from Build Guard).
  On 1.12.39 `./gradlew run` exited with code 255 about 12 seconds in.
