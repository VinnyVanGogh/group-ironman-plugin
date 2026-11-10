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
| Saved data | `gimutilities.groupCache` (config value; was `.runelite/gim-utilities/` until 2026-11-09) | Build Guard's is `.runelite/build-guard/`. |
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
| 10 | Gravestone expiring | Tick count, `GRAVESTONE_VISIBLE` | Own grave only. Idle pauses are not modelled, so it warns early, never late | Gameval name; meaning assumed |
| 11 | Hardcore life lost | `GIM_HARDCORE_DEATHS`, `GIM_GROUPSIZE` | Own group's own varbits | Gameval names; [one life per player](https://oldschool.runescape.wiki/w/Group_Ironman_Mode) |
| 12 | Wilderness, skulled | `INSIDE_WILDERNESS`, own skull icon | PvP Restrictions: this player's own state only, nothing about any opponent. Situational overlays are display only | Gameval name |
| 30 | Diary tier | `*_DIARY_*_COMPLETE`, `ATJUN_*_DONE` | Own progress | Gameval names; `ATJUN_HARD_DONE` not confirmed |
| 31 | Combat task | "you've completed an X combat task" | Own progress. Tier is the task's tier, not the overall rank | Unverified wording |
| 32 | KC milestone | Kill count line (#20) | Not an attack counter: a total after the kill | As #20 |
| 33 | Personal best | "(new personal best)" | Own time, after the fight | Unverified wording |
| 36 | Weekly recap | This player's own tally | **Corrected from the brief:** #18 forbids posting another player's data, so each member posts their own week, not one group total | Pure logic |
| 37 | Daily digest | Held own lines, per category | Sent only while logged in | Pure logic |
| 39 | Slayer | "You've completed N tasks", "new Slayer assignment" | Own task | Unverified wording |
| 40 | Farming runs ready (herbs, trees) | `FARMING_TRANSMIT_*`, read only in the patch's own region | Own patches only. *Precedent:* the base client's Time Tracking plugin reports the same. Fruit trees and other patch types not included | Time Tracking source, master, read 2026-11-09 (THIRD_PARTY_NOTICES.md). Not checked in game |
| 41 | Bird houses, Hespori | `BIRDHOUSE_TRANSMIT_*` on Fossil Island's ground, `FARMING_TRANSMIT_J` | Own patches only, as #40 | As #40 |

Crop ready times use Time Tracking's formula without its learned per-account
offset, so a crop's post can come late but not early. Bird houses use Time
Tracking's 50 minute average. A crop that catches disease out of sight is still
reported, as it is in Time Tracking.

### Out of scope — do not implement, do not propose workarounds

- **Reflection**, and therefore any integration that reads another plugin's
  state. If integration is ever wanted, the channel is
  `net.runelite.client.events.PluginMessage`.
- **Every network call** of our own but the optional Discord webhook the
  player sets (#18), and any server. Group Ironmen Tracker needs a website;
  this plugin's point is that it does not.
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

### Phase 0 addendum — saved data moves into config (2026-11-09)

The Hub bot on plugin-hub#20149 held the plugin for file i/o: "Plugins
performing file i/o (such as with java.io or java.nio) are subject to
additional review by maintainers. If possible, use the RuneLite Filepath API
instead." The flagged commit already wrote under `RuneLite.RUNELITE_DIR`, so
that path alone does not clear it.

**Group cache in ConfigManager — allowed, with precedent.** What the player
knows does not change; only where the same data is kept. *Nearest rules:*
Data & Privacy, "no exposing player information over HTTP" and "no
crowdsourcing data about other players": members' data is still only what they
chose to share with their own group. The value leaves the machine only through
RuneLite's own profile sync, if the player turned that on for the profile.
*Precedent:* the base client's Bank Tags plugin keeps player data in config
(`TagManager`: `configManager.getConfiguration` / `setConfiguration`).

**Global config, not RS profile.** `getRSProfileConfiguration` returns null
until a profile key exists, and the cache loads at `startUp`, usually before
login. The old file was per install too.

---

## Phase 0.5 — measure before building

Party message size: `GroupCodec` splits payloads to a budget of its own,
because RuneLite 1.12.39's `WSClient` imposes no limit and the relay's is not
published (see `GroupCodec`'s javadoc for what was read).

---

## Phase 3 — quality bar

- Pure logic separated from client access, and unit tested.
- `startUp` / `shutDown` symmetric: everything registered is unregistered.
- No unused config keys, fields or imports.

---

## Phase 4 — getting it installed

See the `runelite-hub-submit` skill. Not yet: the display name above is open,
and `icon.png` is still the scaffold's placeholder.
