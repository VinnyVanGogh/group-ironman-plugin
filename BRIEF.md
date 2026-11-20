# GIM Utilities — research & build brief

The authoritative record of what was decided and why. Findings go **in this
file**, under the phase that produced them, so nothing has to be re-derived
later. If a claim here is a measurement, say what was measured and how.

## What this plugin is

Opt-in sharing for a Group Ironman team over RuneLite Party: member banks, the
group storage log, live stats with boosts, where each member is and when they
were last seen, inventories, equipment, rune pouch, quiver and seed vault,
drops and collection log slots, a "Who has it?" search and `::find`, storage
minimums and world map markers.

It was split out of Build Guard on 2026-11-04 (Board decision) so a group can
use these without Build Guard's build and XP warnings. This repo starts as a
copy of `build-guard-plugin` with full history through its PR #5; the Phase 0
findings for the group features below were made there and are carried over
unchanged.

## What this plugin is not

Not a build or XP guard (that stays in Build Guard), not a website companion,
and not a server. Group data goes only through RuneLite's `PartyService`; the
one network call of its own is the optional Discord webhook (#18).

---

## Phase 0 — compliance

Read, in order:

1. `AGENTS.md` in this repo (the forbidden-features list at the bottom).
2. <https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features>
3. <https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1>

### The rule every feature shares

Each member's own client sends that member's own data, only after they turn
the matching switch on, only to the party they joined, through RuneLite's own
`PartyService` relay. No server or database of ours exists, and no HTTP client
either. *Nearest rules:* Data & Privacy, "no crowdsourcing data about other
players" and "no exposing player information over HTTP": nothing is collected
about anyone who has not opted in, and nothing goes over HTTP. *Precedent:*
the base client's `party` plugin (`StatusUpdate`: HP, prayer, run, special
attack, exact location) and the Hub plugin **Party Panel** (inventory, worn
equipment, prayers and skill levels between party members).

### Findings carried over from Build Guard (2026-11-03)

Numbering kept from Build Guard's brief so commit messages and tests that cite
them still resolve.

**8. Member banks, last seen — allowed, with precedent.** Sent on bank close
as item id and count, no slots or tabs. A member choosing to show their own
bank to their own team. *Precedent:* the Hub plugin **Group Ironmen Tracker**
shares members' banks, group storage, inventories, equipment and skills
(through its own website, with the third-party-server warning); this plugin
does the same subset with no server at all. Its README was not re-read in
this run: cite it again before submitting.

**9. Group storage change log — allowed.** A diff of what the local player's
own client showed on open and on close. Only visits by members running the
plugin are logged, and the panel says so. Precedent as #8.

**10. Group stats with boosts — allowed, with precedent.** The base `party`
plugin shares HP, prayer, run energy and special attack; Party Panel shares
skill levels.

**11. "Who has it?" search and `::find` — allowed.** A search over data the
members already chose to share. It adds no data. `::find` is a `::` command,
which RuneLite's `ChatInputManager` turns into a `CommandExecuted` event and
never sends to the game; the answer is local game messages only.

**12. Low-stock alerts — allowed, with precedent.** A desktop notification via
RuneLite's `Notifier`, which the base `idlenotifier` and many Hub plugins use.
It informs; it does not act.

**13. Inventory and equipment per member — allowed.** Precedent: Group
Ironmen Tracker; Party Panel shows members' gear.

**14. Rune pouch, Dizana's quiver, seed vault — allowed.** Read from the
local player's own varbits and varps (as the base `runepouch` plugin does) and
the seed vault container when the player opens it. Precedent: Group Ironmen
Tracker shares all three.

**15. Drops and new collection log slots, "since you last played" —
allowed.** Drops from `NpcLootReceived`, which the client's own `LootManager`
posts; collection log slots from the game's own chat line. Both are the
player's own. Precedent: the base loot tracker and Hub plugins such as Dink
report the same events.

**16. Where each member is, and world map markers — allowed, with
precedent.** The base `party` plugin already shares exact location. Markers
use `WorldMapPointManager`, the API the base client's own markers use.

**17. Online / last seen — allowed.** Derived from when a member's client
last sent anything. Adds no data.

Every switch is **off by default**, `::find` included, and each opens only its
own feature (`GroupOutboxTest`, `PluginIdentityTest.everySwitchDefaultsToOff`).

### Phase 0 addendum — the split (2026-11-04)

**Removed, and why.** Build Guard's own Phase 0 items 1 to 7 (build rules,
quest verdicts, quest-start warning, lamp advisor, attack-style warning, and
sharing build rules / at-risk lines / a gear-name list with the party) are
build features and stay in Build Guard. The gear-name list (#7) is covered
here by #13, which shares counts as well as names. Nothing in the kept code
read the quest or lamp data, so `quests.json` and `lamps.json` went too.

**Identity, chosen so it collides with nothing.** Checked 2026-11-04 against
`runelite/plugin-hub` `master` (the `plugins/` tree) and the manifests' source
repos:

| What | Value | Why not the obvious one |
| --- | --- | --- |
| Config group | `gimutilities` | `groupironman` is already the `@ConfigGroup` of the Hub plugin **Group Iron Man** (Spedwards, slug `group-ironman`, `GroupIronManConfig.java:9` at `8700097`). Sharing it would read and write that plugin's settings. |
| Party message type | `VvgGroupIronmanMessage` | The relay routes by simple class name, so the name is shared by every plugin on the client. Build Guard uses `BuildGuardGroupMessage`; a generic `GroupIronmanMessage` could meet another group plugin's. A GitHub-wide code search to prove the generic name free was held at the gate, so the author prefix makes it moot. |
| Saved data | `.runelite/gim-utilities/` | Build Guard's is `.runelite/build-guard/`. |
| Display name | `GIM Utilities` (Board, 2026-11-04) | "Group Ironman" **clashes on the Hub**: slug `group-ironman` exists, display name "Group Iron Man", and 20+ `gim-*` slugs exist. No `gim-util*` slug does. |

### Phase 0 addendum — round 2, no Party panel (2026-11-04)

Board asked for the plugin to stand on its own ("auto hidden party"), with a
random group key shared in group chat. Checked against `runelite/runelite` at
tag `runelite-parent-1.13.5` before any code:

- **`PartyService` is core.** `party/PartyService.java:61-62` is a Guice
  `@Singleton`; `changeParty(String)` at :151 closes the socket, hashes the
  passphrase and connects. Nothing in it references the Party plugin, so it
  works with that plugin off. Passphrase to id is
  `sha256(utf8).asLong() & Long.MAX_VALUE` (:308-312), no length limit.
  RuneLite itself logs the passphrase at debug level (:160); our code never
  logs the key.
- **Names only travel when asked for.** `PartyDataService.java:285` sends
  `CHARACTER_NAME` only while some plugin subscribes; the Party plugin does at
  `PartyPlugin.java:210`. With it off, member names stay `<unknown>`, so GIM
  Utilities subscribes itself (and unsubscribes on shutdown).
- **`PartyPlugin.shutDown` does not leave the party** and removes its
  overlays (:228): with the plugin off, its run-energy overlay is hidden.
- **Auto-joining is rejected.** Wiki "Rejected or Rolled-Back Features":
  "Auto-rejoin for parties: Would put undue strain on the party servers."
  Precedent of rejection: plugin-hub PR #19833 *Party Smarty*
  (`PartyService.changeParty` on login), closed 2026-10-31 by a maintainer:
  "Auto joining parties is a rejected feature." **So every join is the
  player's click** ("Join group sync"). Leaving our own party on shutdown or
  when sync is turned off is not a join.
- **Typing into the chatbox is rejected.** Same page: "Plugins which
  programmatically insert text into the user's chatbox input for any reason
  ... This is considered to be autotyping." So *Copy share line* uses the
  clipboard; the player pastes and presses Enter.
- **Reading and hiding chat lines is allowed.** Precedent: the base chat
  filter plugin drops lines through the `chatFilterCheck` script callback
  (`ChatFilterPlugin.java:177-257`); GIM Utilities drops only `[GIMU]` lines
  in `CLAN_GIM_CHAT`.
- **Roster.** `client.getClanSettings(ClanID.GROUP_IRONMAN).getMembers()`
  (fallback `getClanChannel(ClanID.GROUP_IRONMAN)`), runelite-api 1.13.5. No
  hiscores or Wise Old Man calls: the in-game roster is authoritative and
  needs no network (and no third-party-server warning).
- **Why a random key.** Group name, member names and join dates are on the
  hiscores; a passphrase built from them could be rebuilt by anyone, and
  party data is relayed to everyone in the party.
- **Panel layout** after Quest Helper (`Zoinkwiz/quest-helper`,
  `panel/QuestHelperPanel.java`): `IconTextField` search bar, `CardLayout`
  views, sections collapsed from their header, a `JScrollPane` around it all
  (here, `PluginPanel`'s own).

### Phase 0 addendum — group events, Discord and overlays, part A (2026-11-04)

Board, 2026-11-04 (RUNELITE-85): every one of the 50 listed event ideas, each
its own switch, all off by default; a Discord webhook and in-game overlays set
up together. Part A is the 20 "easy" kinds (`GroupEvent.Type`).

**18. Discord webhook — allowed, with precedent; needs the third-party
disclosure.** *Nearest rules:* AGENTS.md Config: a setting that sends anything
to a third-party server is off by default and carries the warning
"This feature submits your IP address to a 3rd-party server not controlled or
verified by RuneLite developers" (here on *Post to Discord*); Data & Privacy,
"no exposing player information over HTTP" and "no crowdsourcing data about
other players": each client posts **only its own player's events**
(`EventRouter`, own only), to a webhook that player pasted, and nothing about
any other player is ever posted. *Precedent:* Hub plugins **Dink**
(`pajlads/DinkPlugin`, loot, deaths, levels, clog, quests to a webhook),
**Discord Loot Logger** (`Adam-/runelite-plugins` `discord-loot-logger`) and
**GIM Bank Discord** (`Boredska/gim-bank-discord`, group storage changes to a
webhook). HTTP is RuneLite's injected `OkHttpClient` with `enqueue`, never the
client thread (AGENTS.md HTTP & JSON).

*The Hub `warning=` line — the brief's assumption corrected.* Checked
2026-11-04: a `warning=` line belongs in the **plugin-hub manifest**
(`plugins/<slug>` in `runelite/plugin-hub`), not in this repo's
`runelite-plugin.properties`, whose documented fields are `displayName`,
`author`, `description`, `tags`, `plugins`, `version`, `build`
(`runelite/plugin-hub` README). And the cited webhook plugins carry **none**:
`plugins/discord-loot-logger`, `plugins/gim-bank-discord` and `plugins/dink`
are `repository=` and `commit=` only. Manifests that do carry one point at a
fixed or user-entered third-party site, e.g. `plugins/clan-chat-warnings`:
"This plugin can fetch and post text to 3rd party websites not controlled or
verified by the RuneLite Developers. This setting is optional, and only
accesses the user-inputed site." So: no line was added to
`runelite-plugin.properties`; when the next Hub update is opened (not
plugin-hub#20149, which stays untouched), propose in `plugins/gim-utilities`:
`warning=This plugin can post your own events to a Discord webhook you enter. This is optional, off by default, and sends your IP address to Discord, a 3rd party not controlled or verified by the RuneLite Developers.`
and let the reviewer decide, since Dink ships without one.

**19. Event banners and the group box — allowed.** Overlays of information
the client already has or that members chose to share (Data & Privacy, as
#15). No boss mechanics, no PvP information. *Precedent:* the base client's
loot and XP drop overlays; Party Panel. Later "situational" banners (only in
the Wilderness, only in a boss room) filter where a banner shows, never add
combat information: `ToastQueue.visible` takes that filter.

**20. Event detection — allowed.** Every kind is read from the player's own
client: `NpcLootReceived`, `ActorDeath` for the local player, `StatChanged`,
the QP varp, the quest scroll and Barrows reward interfaces, and the game's
own chat lines (pet, kill count, collection log), the patterns the base
screenshot plugin uses (`ScreenshotPlugin.java`, runelite master: pet
messages ~:111-113, kill count ~:102, quest title ~:286). Storage events are
derived from the storage change each member already shares (#9), so they
never cross the party themselves.

### Phase 0 addendum — medium events, part C (2026-11-09)

Board, 2026-11-04. Every kind is the player's own, off by default, one Discord
and one overlay switch each, and is read only from chat lines, varbits and
events the game gives this client. No reflection and no network beyond #18.

**Status of the evidence.** RuneLite's Time Tracking sources were read for #40
and #41. The chat wording was not checked against RuneLite core or Dink sources.
So "unverified" below means written from memory, never checked against source
or the live game. A wrong line fails silent. Names
marked gameval were read from `runelite-api` 1.13.5; what each value means is
assumed from its name.

| # | Kind | Detected from | Nearest rule and note | Evidence |
| --- | --- | --- | --- | --- |
| 4 | Raid unique | "found something special", Chambers' "Special loot:" lines | Data & Privacy, #18: only when the line names this player; team members outside the group are counted, never named | Unverified wording |
| 5 | Clue reward, counts by tier | "You have completed N tier Treasure Trails", reward container | As #20 | Unverified wording |
| 7 | Dry streak | Kill count line, drop names | Adds no game information; display of past kills | Wiki rates read 2026-11-09, Vorkath and Zulrah only (`DryStreak`) |
| 9 | Value lost on death, gravestone | `ActorDeath`, carried items before and after | Own items only | [Gravestone](https://oldschool.runescape.wiki/w/Gravestone): 15 min, paused logged out |
| 10 | Gravestone expiring | Tick count, `GRAVESTONE_VISIBLE` | Own grave only. Idle pauses are not modelled, so it warns early, never late. No warning unless the death lost something and the varbit was seen on (Board 2026-11-10: no grave, no warning) | Gameval name; meaning assumed, so the warning now depends on it: confirm in game |
| 11 | Hardcore life lost | `GIM_HARDCORE_DEATHS`, `GIM_GROUPSIZE` | Own group's own varbits | Gameval names; [one life per player](https://oldschool.runescape.wiki/w/Group_Ironman_Mode) |
| 12 | Wilderness, skulled | `INSIDE_WILDERNESS`, own skull icon | PvP Restrictions: this player's own state only, nothing about any opponent. Situational overlays are display only | Gameval name |
| 30 | Diary tier | `*_DIARY_*_COMPLETE`, `ATJUN_*_DONE` | Own progress | Gameval names; `ATJUN_HARD_DONE` not confirmed |
| 31 | Combat task | "you've completed an X combat task" | Own progress. Tier is the task's tier, not the overall rank | Unverified wording |
| 32 | KC milestone | Kill count line (#20) | Not an attack counter: a total after the kill | As #20 |
| 33 | Personal best | "(new personal best)" | Own time, after the fight | Unverified wording |
| 36 | Weekly recap | This player's own tally | **Corrected from the brief:** #18 forbids posting another player's data, so each member posts their own week, not one group total | Pure logic |
| 37 | Daily digest | Own lines per category, a summary on top of instant posts (never holds them; Board 2026-11-10) | Sent only while logged in | Pure logic |
| 39 | Slayer | "You've completed N tasks", "new Slayer assignment" | Own task | Unverified wording |
| 40 | Farming runs ready (herbs, trees) | `FARMING_TRANSMIT_*`, read only in the patch's own region | Own patches only. *Precedent:* the base client's Time Tracking plugin reports the same. Fruit trees and other patch types not included | Time Tracking source, master, read 2026-11-09 (THIRD_PARTY_NOTICES.md). Not checked in game |
| 41 | Bird houses, Hespori | `BIRDHOUSE_TRANSMIT_*` on Fossil Island's ground, `FARMING_TRANSMIT_J` | Own patches only, as #40 | As #40 |

Crop ready times use Time Tracking's formula without its learned per-account
offset, so a crop's post can come late but not early. Bird houses use Time
Tracking's 50 minute average. A crop that catches disease out of sight is still
reported, as it is in Time Tracking.

### Phase 0 addendum — optional server sync (2026-11-11, RUNELITE-117)

Board, 2026-11-11: an optional server of our own (Cloudflare Workers + D1)
for group sync without Party, a web settings page, Discord bot settings and a
group dashboard. Design and threat model: `docs/plans/gim-server.md`. The
server lives in its own repo; the plugin side lives on the long-lived
`server-sync` branch until the Board merges it. **None of #22–#25 is on
`main`.** Numbers start at #22 because open PR #19 takes #21.

**22. Group sync through our own server — allowed, with precedent, under
conditions.** *Nearest rules:* Data & Privacy, "no exposing player
information over HTTP" and "no crowdsourcing data about other players".
*Verdict* (runelite-compliance, Board session 2026-11-11): allowed **only
if** (a) it is opt-in (`serverSync`, off by default, with the third-party
warning in its description); (b) a group's data is readable only by that
group: a group secret derived from the group key, per-member tokens, no
public pages, no lookups by player name and no cross-group queries; and
(c) nothing about non-members is stored. Each member's client sends only its
own player's data, the same subset Party carries today (#8–#17).
*Precedent:* Hub plugin **Group Ironmen Tracker**
(`christoabrown/group-ironmen-tracker`, 21,557 installs) syncs banks,
storage, stats and location to its own server and site, without Party. Its
Hub manifest carries `warning=This plugin submits your IP address, and may
submit various account data, to a 3rd party server not controlled or
verified by the RuneLite Developers.` Expect the same warning on ours, and
document it in the README and the Hub PR. Connecting on login without a click
is an HTTP call, not a party join or a game action, so the "joining a party
without a click" line below does not cover it. Plugin rules still hold:
injected `OkHttpClient` with `enqueue` (or its `newWebSocket`), never the
client thread, no reflection, `Filepath` for local files (main `6c7036a`).

**23. Settings changed remotely (web page, Discord) — allowed, no precedent.**
No rule was found that covers it, and no precedent was found. *Reasoning:*
the settings change only what this plugin displays and posts. None of them
touches game input, menus, combat information or other players' data.
Applying them is a second opt-in per member (`serverAllowRemoteSettings`, off
by default). An allowlist excludes the sync opt-ins, the group key and every
webhook URL; webhook URLs never leave the client. Revisit if a reviewer
objects.

**24. Group web dashboard — allowed, with precedent.** It shows a group only
its own shared data (#22 conditions). *Precedent:* Group Ironmen Tracker's
site.

**25. Discord bot that edits settings — allowed.** It runs outside the game
client (HTTP interactions on the Worker) and never posts game data. Its
commands are limited to Manage Server by default and re-checked on the
server. Same reasoning as #23.

### Phase 0 addendum — hiscore levels for members without the plugin (2026-11-11)

Board in-game test, 2026-11-11: the Group tab should show something for a
roster member who never ran GIM Utilities, and for one never seen online.

**26. Hiscore lookup of your own group's members — allowed, with
precedent.** *The feature, as what the player now knows:* the public hiscore
skill levels of the people on their own in-game Group Ironman roster. *Nearest
rules:* Data & Privacy, "no crowdsourcing data about other players" and "no
exposing player information over HTTP". Neither applies: the levels are
already public on Jagex's own hiscores, the lookup is a read from Jagex
(`secure.runescape.com`) by RuneLite's own `HiscoreClient`, nothing about any
player is sent anywhere, and the result never leaves this client: it is not
relayed over Party (#8–#17) or posted to Discord (#18). Only names on the
in-game roster (`GroupMembers`) are looked up, never a typed or arbitrary
name, so this is not a lookup tool. Not PvP scouting: group members cannot be
attacked by each other, and hiscore levels say nothing about a fight.
*Precedent:* the base client's **Hiscore** plugin
(`net.runelite.client.plugins.hiscore`) looks up any player's levels with the
same `HiscoreClient` (runelite-client 1.13.5, `HiscoreClient.lookupAsync`,
read with `javap` 2026-11-11); its right-click *Lookup* runs on players the
user meets. Group Ironmen Tracker (#8, #22) shows each member's skills on its
site; whether it reads them from the hiscores was not checked in this run.

*Conditions this build keeps* (`HiscoreBook`, `GroupService.lookUpHiscores`):
- RuneLite's injected `HiscoreClient` only, no HTTP client of our own; its
  `lookupAsync` runs on OkHttp's threads, never the client thread.
- Only members who share no stats, never this player. Shared plugin data
  always wins over hiscores in the panel (`PanelModel.members`).
- At most one lookup per member every 6 hours (Board), started only on this
  player's login or the member coming online in the Group channel. A failed
  lookup retries on its own after 15 min, doubling to at most 6 h: no storm.
  Failure shows "Hiscores unavailable".
- Results are kept in the group cache with their time ("From hiscores, 2h
  ago"). Run and special attack are never on the hiscores: for an online
  member they show 100%, dimmed, "assumed, not shared" (Board). Items are
  never invented: a member who never shared them shows none.
- Endpoint: `HiscoreEndpoint.NORMAL`. RuneLite 1.13.5 has no Group Ironman
  endpoint (`IRONMAN`, `HARDCORE_IRONMAN`, `ULTIMATE_IRONMAN` only), and the
  main hiscores rank every account type. *Unverified:* that every group
  member is on the main hiscores; a skill below the ranking threshold shows as
  missing, not as level 1.
- No new switch: the lookup sends nothing about anyone and goes to Jagex, not
  a third party, so the third-party warning does not apply. It runs only
  while some GIM Utilities feature is on (`anyOn`). If the Board wants it
  switchable, that is a new key, off by default, frozen at first release.

This amends the roster line in round 2 ("no hiscores or Wise Old Man
calls"): the roster still comes only from the game; hiscores add levels, not
members.

### Phase 0 addendum — group broadcasts for members without the plugin (2026-11-14)

Board, 2026-11-14: members online without the plugin still produce the game's
own Group channel broadcasts ("[GroupPures] GroupOtt has reached Herblore
level 21.", "... has completed a quest: Black Knights' Fortress"); post those
to Discord too.

**27. Posting the game's Group broadcasts to the group's Discord — allowed,
with precedent.** *The feature, as what the player now knows:* nothing new.
Every member already sees each broadcast in their own chatbox; this copies
the line, as a group event, into the group's own Discord channel. *Nearest
rules:* Data & Privacy, "no crowdsourcing data about other players" and "no
exposing player information over HTTP". Neither applies beyond what #18
already settled: the source is this player's own Group channel
(`ChatMessageType.CLAN_GIM_MESSAGE`), the members are their own group, and the
destination is the group's own webhook, opted into by its members. Nothing is
sent to any server of ours. *Precedent:* **Dink** (pajlads/DinkPlugin,
`DinkPlugin.onChatMessage`) routes `CLAN_MESSAGE`, `CLAN_GUEST_MESSAGE` and
`CLAN_GIM_MESSAGE` to `PetNotifier.onClanNotification` and posts other clan
members' pets from their broadcasts to a Discord webhook (`CLAN_REGEX`, read
2026-11-14).

*Conditions this build keeps* (`GroupBroadcasts`, `GroupEventEngine.groupBroadcast`):
- Only `CLAN_GIM_MESSAGE` lines, parsed with fixed patterns; no chat is sent
  anywhere but the configured webhook, and only events the group's per-kind
  Discord switches allow, after "Post every N levels" and the drop minimum.
  Quiet hours hold them like every other post.
- A member heard from over the group sync posts their own events and is
  skipped (the board's "(no plugin)" rule), so nothing is posted twice.
- One plugin user posts each line: the leader while online and in the party,
  else the first by name (`ChannelPosts.leads`). It tells the party with a
  `posted` claim (the line's key, nothing of its own) and keeps posted keys a
  day, so a restart or a second client never posts it again.
- The group setting "Post events for members without the plugin" (default on)
  turns it off for everyone. The poster's own "don't post my events" applies
  to their own events only: members without the plugin cannot opt out in-game.
- Posts say "from the game's group broadcast" in the footer. No screenshot is
  attached: the poster's screen is not the member's moment.
- *Unverified:* the wording of every line but the Board's two captures and
  Dink's pet line. A line worded differently is simply not posted.

### Phase 0 addendum — group settings and roles (2026-11-10)

Board, 2026-11-10: group-synced settings with a leader and editors, and
per-member opt-outs.

**21. Shared settings, signed roles — allowed.** *Nearest rules:* Data &
Privacy, "no exposing player information over HTTP" and "no crowdsourcing
data about other players": what travels is the group's own preferences
(numbers and switches) and each member's own posting opt-outs, only to the
members' own party, through `PartyService` like every other kind (#8 to
#17). No new network call, and no webhook URL ever leaves its owner's
client (`GroupSettingsSchemaTest`, `GovernanceSyncTest`). *Forbidden
language features:* none. Signatures are ECDSA P-256 from the JDK's own
`java.security` (no library, no reflection, no JNI); `GroupKey` already uses
`SecureRandom` from the same package. *Precedent:* the base client's
`party` plugin already relays member-chosen state; Discord-posting Hub
plugins (#18) carry per-event switches, here shared instead of typed five
times. Each member still posts only their own events (#18).

**Trust model.** Member names are the ones `GroupService.sender` checks
against the in-game roster. The leader's key is pinned on first accept (a
claim is accepted only when the claimant's own announce shows the same key);
later roles must be signed by it; settings must be signed by the leader or a
current editor with the key the roles hold. A member who controls their own
client can still refuse to apply the group's settings to themselves: that is
their client, and their posts only. Recovery when a leader loses their
RuneLite config is "Forget leader" on each client, then a new claim.

**Storage.** Kept in the `gimutilities` config group under keys with no
`@ConfigItem` (`memberIdentity`, `groupRoles`, `groupSettings`): no new file
I/O (Board rule).

### Phase 0 addendum — Shopping tab requests (2026-11-14, RUNELITE-149)

Board, 2026-11-14: each member keeps their own named request lists ("Fire
cape": 500 sharks, 20 prayer potions) that the group fills; anyone can say
"I'll get it"; an item closes when the group holds the amount.

**28. Requests and claims — allowed.** *Nearest rules:* Data & Privacy, "no
crowdsourcing data about other players" and "no exposing player information
over HTTP": a request is text a member typed for their own group, and the
counts it shows are the item counts members already share with each other
(#8 to #17, the same `Holdings` the shopping list reads). It travels only to
the members' own party, as two more kinds on the shopping list's messages
(`req`, `rqc`), through `PartyService`; nothing new goes over HTTP.
*Input Restrictions:* nothing is automated. "I'll get it" is a note to the
group; no item is moved, withdrawn, bought or traded for anyone, and the
plugin never touches the trade screen. *Discord:* one more opt-in kind
(`discordRequest`, off), posted only by the member whose action it was
(#18): the owner for "made" and "done", the claimer for a claim.
*Forbidden language features:* none. *Precedent:* the base client's `party`
plugin relays member-chosen state; the shopping list (#8 to #17 data,
RUNELITE-136) already shows these same counts.

**Trust model.** A request is edited only by its owner: each client refuses a
revision naming a different owner than the one it holds
(`RequestsTest.aRevisionNamingAnotherOwnerIsIgnored`). A member who edits
their own client can still forge their own claims; that is their own word to
their own group, as every other kind.

### Phase 0 addendum — "Path to" with Shortest Path (2026-11-14)

Board, 2026-11-14: shopping lines and requested items should let you "run to
the items, the way Quest Path does".

**29. "Path to" through Shortest Path — allowed, with precedent.** A button
on each line still to get, on each requested item, and "Path to next" on each
list, hands one tile to the Shortest Path plugin, which draws a route.
*Nearest rules:* Input Restrictions ("no automating input", no walking for
the player): nothing moves the player; Shortest Path only draws a line, and
the player walks it. Inter-plugin: the **Out of scope** rule below (no
reflection; "the channel is `PluginMessage`"). The message is
`PluginMessage("shortestpath", "path", {"target": Set<WorldPoint>})` and
`PluginMessage("shortestpath", "clear", {})`, posted on RuneLite's own
`EventBus`: no reflection, no dependency on Shortest Path, nothing reads its
state but "is a plugin of that class loaded and on" through
`PluginManager`. *Precedent:* Shortest Path documents this API for other
plugins (`docs/Cross-plugin-communication.md` in `Skretzo/shortest-path`,
read 2026-11-14: "Every message uses the namespace `shortestpath`", `path`
"shows a path", `clear` "removes the shown path"); Quest Path
(questpath-plugin `80cdafb`, `ShortestPathBridge`) and Combat Vitals post the
same messages. The Board also named RuneLite's Clue Scroll plugin; that was
not re-checked here. *Forbidden language features:* none.

Where it paths, and the sources: the shops are the quest data's shop table
(`QuestData.shop`, `quest-items.json` from questpath-plugin `ee638ff`), the
same table and reachability check the shop alert uses (`ShopSources`,
`ShopVisits`): a shop this player cannot be served at is never a target.
Drops are not targets (the data names a monster's region, not a tile). For an
item the group holds in group storage, the nearest bank: group storage
opens "via the regular Bank interface" (OSRS Wiki, "Group storage", read
2026-11-14). Bank tiles: `banks.tsv`, one ungated tile per bank from Shortest
Path's `destinations/game_features/bank.tsv` (THIRD_PARTY_NOTICES.md).
Nearest is straight-line steps from the player's tile on the last game tick,
another floor costing 50 more; "Path to next" walks greedy nearest-next.

The path clears when its item is done, its list is removed, its request
closes or the plugin is turned off; "Path to next" moves on to the nearest
remaining stop once the current one has nothing left to get. With Shortest
Path missing or off, the button says so and sends nothing.

Not verifiable from here: that the line is drawn in a running client
(`EventBus#post` is fire-and-forget; Quest Path's bridge doc).

### Research — which group storage actions save (2026-11-14)

Bug (Board 2026-11-14): 1,000 logs deposited, the window left without Save,
the game kept nothing, yet the deposit was logged and posted to Discord.
The close was treated as a save (`StorageSession.close` diffed and committed).

What the client receives, with sources:

- **The storage container is a working copy.** The only group-storage
  inventory in RuneLite's gameval is `InventoryID.INV_GROUP_TEMP = 659`
  (runelite-api `gameval/InventoryID.java`); there is no separate "saved"
  container. Its contents while open include unsaved moves.
- **Saving shows the game's loading modal.** `InterfaceID.LOADING_ICON_MODAL
  = 293` (runelite-api `gameval/InterfaceID.java`); child 1 reads
  "Saving..." while the game saves group storage. The Group Ironmen Tracker
  plugin on the Plugin Hub commits the shared bank only on that text
  (`christoabrown/group-ironmen-tracker`, `GroupIronmenTrackerPlugin.java`,
  `onGameTick`: `GROUP_STORAGE_LOADER = 293`, child 1, `"saving..."` then
  `commitTransaction()`). This is both the evidence and the precedent.
- **Closing does not reliably save.** The Board's test (walk away: not
  saved). Players report closing saves for some accounts and not others
  (r/ironscape, "Group ironman shared storage 'save' help", 2024), so no
  close path can be assumed to save. Whichever path does save shows
  "Saving...", so the loader covers it without guessing.
- **Saves can fail.** "Unable to save changes to group storage. Please try
  again later" (r/2007scape reports, 2021-2023). The OSRS Wiki "Group
  storage" page says nothing about saving (checked 2026-11-14).
- No varbit or varp for "unsaved changes" was found in gameval (unverified
  beyond a name search of the published `VarbitID` source).

What the plugin does now:

1. Commits on the Save button (as before) and on the first tick
   "Saving..." shows, also up to 5 s after the window closed (a save the
   close itself makes). Each save posts only what moved since the last.
2. Closing records nothing.
3. Truth check: the first contents the server sends on the next open are
   what the game kept. If they differ from the last snapshot committed, they
   replace it silently (an empty change: no log line, no post, nothing sent).

Not verifiable from here: the "Saving..." text in today's client and
whether X / Escape / logout save on the Board's account. In-game check:
deposit, walk away, reopen (nothing logged); deposit, Save (one post).

### Out of scope — do not implement, do not propose workarounds

- **Reflection**, and therefore any integration that reads another plugin's
  state. If integration is ever wanted, the channel is
  `net.runelite.client.events.PluginMessage`.
- **Every network call** of our own but the optional Discord webhook the
  player sets (#18), and any server, **on `main`**. Group Ironmen Tracker
  needs a website; this plugin's point is that it does not. *Amended
  2026-11-11 (Board):* the opt-in server sync in #22–#25 is the one exception,
  on the `server-sync` branch only, until the Board merges it. *Amended
  2026-11-11 (Board):* reading roster members' public levels through
  RuneLite's own `HiscoreClient` (#26) is allowed on `main`; it sends nothing.
- **Automating anything**: moving items, withdrawing, walking. Input
  Restrictions.
- **Joining a party without a click**, on login or on a timer, and **putting
  text into the chatbox input** (the share line goes on the clipboard).

### The display name (settled)

"Group Ironman" clashes with the existing Hub plugin "Group Iron Man" (slug
`group-ironman`). Candidates proposed on 2026-11-04 were **GIM Party Share**
(`gim-party-share`) and **Group Iron Party** (`group-iron-party`); the Board
chose **GIM Utilities** with config group `gimutilities`. The message type
does not depend on the display name.

---

### Phase 0 addendum — shared bests, quests and Compare (2026-11-17)

Board, 2026-11-16 and 2026-11-17 (task-c3ec10ad): members' boss bests, quests,
quest points and diaries visible to the group; a Compare tab; a Personal bests
section under Activities. Our own design. The ideas were checked as examples
only; nothing was taken from another plugin's code.

**30. Sharing own boss bests, quests, quest points and diaries — allowed.**
*Nearest rules:* Data & Privacy ("no sending identifying data to third
parties"; here only to the player's own party, under the existing *Share group
events* switch, off by default, the same switch the PB and quest events use).
Read only from the player's own game chat ("Personal best:" lines), `Quest`
states, the `QP` varp and diary varbits the client already exposes: no
reflection, no widget scraping, no network. *Precedent:* the base Hiscore and
Quest List plugins read the same states; this plugin's existing
`PERSONAL_BEST`, `QUEST` and `DIARY` events read the same chat lines and
varbits. Payloads are bounded and sanitised on receipt (`PersonalBests.of`,
`AchievementState.of`, at most 100 members). A beaten best is posted to the
group feed and Discord by the existing *New personal best* event, the member's
own client posting it once, so nothing is posted twice.

**31. Compare tab — allowed.** Reads only what members share and the public
hiscores. *Nearest rules:* Data & Privacy, and "no automating input": nothing
acts for the player. Players and Leaderboard use shared levels, QP and bests; a
figure nobody shared reads "?", never a loss. *Group* looks up your members and
up to five typed names with RuneLite's own `HiscoreClient` (precedent: the base
Hiscore plugin, `HiscorePlugin`; and item 26 above for members), off the client
thread, only when the button is pressed. RuneLite's `HiscoreEndpoint` has no
group-ranking endpoint, so "where the group sits" and "the group one place
above" are not available; the tab compares against any group the player types.

**32. Who benefits most — allowed.** Reads item stats from `ItemManager`
(`ItemStats`) and members' shared worn and carried items; nothing leaves the
client. RuneLite carries no level requirements, so none are claimed: the tab
says when requirements are unknown and "can use it now" then means only that
nothing is known to block it. Stats and item categories are the *Compare:
stats that count* and *Compare: item categories* settings.

Not done here, on purpose: boss-page overlays (this plugin has no boss pages),
and group-synced Compare settings (task-5760e864 owns the group settings
schema; the two keys are per-player for now).

---

## Phase 0.5 — measure before building

Party message size: `GroupCodec` splits payloads to a budget of its own,
because RuneLite 1.12.39's `WSClient` imposes no limit and the relay's is not
published (see `GroupCodec`'s javadoc for what was read).

**Prayer on/off, poison / venom / disease (RUNELITE-229, 2026-11-18) —
allowed, with precedent.** Each member's own client sends its own prayer switch
and poison state under the existing stats switch, to their own party. *Nearest
rule:* Data & Privacy, no crowdsourcing about other players; nothing is read
about anyone who has not opted in, and it is teammates' status, not PvP
scouting. *Precedent:* the base `party` plugin shares prayer and HP between
party members; the base `poison` plugin reads the same `VarPlayerID.POISON`
varp. Read via `net.runelite.api.gameval` only (`VarbitID.PRAYER_ALLACTIVE`,
`VarPlayerID.POISON`, `VarPlayerID.DISEASE`). Time left: poison is estimated
(varp counts down one per 30-tick hit, so hits x 18 s; not verified in game);
venom and disease have no duration in the client, so state only and the
tooltip says so. Log basket per-log counts and looting bag contents were
already shipped in RUNELITE-222 (#85) and render in the slot tooltip.

**Group vitals HUD (RUNELITE-215, 2026-11-19) — allowed, with precedent.** An
overlay beside your character (or in a corner) with one card per online group
member: HP, prayer, special attack, run energy, world, and a marker for the
Wilderness or a running activity. *Nearest rule:* Data & Privacy, no
crowdsourcing about other players. Cards come only from the group's own sync
(each member's client sending their own stats under "Share stats") and the
game's Group channel; nothing is read about anyone else on screen, so no PvP
target scouting. *Precedent:* the base `party` plugin's HUD draws party
members' HP, prayer, special attack and run energy the same way. Not built:
an "in combat" marker for other members (not in the stats payload; only your
own combat state is used, for "only in combat"). The Wilderness marker is
from the member's shared tile, not a new datum.

---

## Phase 3 — quality bar

- Pure logic separated from client access, and unit tested.
- `startUp` / `shutDown` symmetric: everything registered is unregistered.
- No unused config keys, fields or imports.

---

## Phase 4 — getting it installed

See the `runelite-hub-submit` skill. Not yet: the display name above is open,
and `icon.png` is still the scaffold's placeholder.
