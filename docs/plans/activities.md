# Live Bossing & Minigame Progress (`RUNELITE-134`)

> **Phase 0 compliance + architecture & slice plan (docs only — no code in this PR).**
> Every activity and data point below has been checked with `runelite-compliance` against [Jagex's Third-Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1), RuneLite's [Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features), and [`AGENTS.md`](../../AGENTS.md) before any UI or wire design.

---

## 0. Goal, Spec Interview & Open Design Defaults

### Goal
Let a Group Ironman team see, **live and granularly**, what each opted-in member is doing across bossing, raids, skilling bosses, and wave/round minigames — e.g. *"Fight Caves — wave 42/63, 38m"* or *"Tempoross (solo) — 64% energy, 5 permits"* — across the sidebar panel, Group tab member cards, in-game group box overlay, Discord milestone webhooks (`Progress` and `Deaths`), and the Discord live status board, while remaining 100% Plugin Hub compliant and wire-compatible with `gim-test-2` clients.

### Spec interview (carried forward)
- **Q: Which activities?**
  **A (Board, 2026-11-13):** Fight Caves + Inferno, Fortis Colosseum, raids progress (CoX / ToB / ToA), and other wave/round minigames (Barbarian Assault wave + role, Pest Control, Wintertodt & Tempoross with granular solo-vs-mass and points/energy/round progress, Gauntlet prep vs boss & corrupted vs normal, plus comparable wave/round activities).
- **Q: Where should it show?**
  **A (Board, 2026-11-13):** Group tab member card, in-game group box overlay, Discord milestones (start, every N waves, death with wave, completion into Progress or Deaths; each kind its own toggle and group setting), Discord live board, and a new sidebar panel tab (*"maybe a new bossing and minigames tab, one for each, or something like that"*).

### Recommended defaults for open design choices
No blocking questions remain before Slice A; the choices left open in the brief have clear defaults grounded in existing `GroupIronmanPanel` and `GroupIronmanConfig` constraints (the Board can override any of these when signing Slice A):

| Choice | Recommended default | Why | Alternative |
| --- | --- | --- | --- |
| **Sidebar tab count** | **One `Activities` tab** with filter pills `All \| Bossing \| Minigames`, *Live now* cards at top, and *Recent runs* below | `GroupIronmanPanel` uses a 3-column `GridLayout(0, 3, 2, 2)` (`GroupIronmanPanel.java:308`). For a 2–5 player GIM group, one tab shows everyone's active runs at a glance (e.g. two members in ToA + one in solo Tempoross) instead of splitting a small roster across two mostly-empty tabs. | Two tabs (`Bossing`, `Minigames`) making 8 tabs across 3 header rows. |
| **Default wave milestone cadence (`eventWaveEvery`)** | **`10` waves** (`Post every N waves = 10`, plus final boss wave: Jad 63, Zuk 69, Sol Heredit 12, BA Queen 10) | Fight Caves (63) posts 6 milestones instead of 63; Inferno (69) posts 7. Short ladders (Colosseum 12 waves, BA 10 waves) post wave 10 + boss wave without spamming Discord. Configurable `0–100` (`0` = off) in `Thresholds` and synced in `GroupSettingsSchema`. | Separate thresholds per activity (adds config clutter in `Thresholds`). |
| **Discord milestones default state** | **All four new toggles (`as`, `aw`, `ac`, `ad`) default `false`** in `GroupIronmanConfig` and `GroupSettingsSchema` | Matches every existing `discord*` and `overlay*` toggle in `GroupIronmanConfig.java:500-1164` (`false` by default; leader or player opts in). | Default `ac` / `ad` to `true` once webhooks are configured. |
| **Activity death vs plain `DEATH` (`dt`)** | When `ACTIVITY_DEATH` (`ad`) fires for an `ActorDeath` inside a tracked activity, **suppress the duplicate plain `DEATH` (`dt`) post for that same tick** (fall back to `dt` when `ad` is disabled) | Prevents `Death's Office` (`Webhook.DEATHS`) from receiving two embeds for a single raid/Colosseum death while still recording safe-death wipes (Fight Caves, Inferno, BA, Colosseum *Doom*) that `dt` misses or lacks wave context for. | Emit both `dt` and `ad` independently. |

---

## 1. Phase 0 Compliance Review (`runelite-compliance`)

Using `runelite-compliance` to check every activity, data field, and output surface against [Jagex's Third-Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1), RuneLite's [Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features), and [`AGENTS.md`](../../AGENTS.md).

### 1.1 Ruling on the blanket policy: *"New high-end PvM boss plugins are not accepted"*

[`AGENTS.md`](../../AGENTS.md) and RuneLite's *Rejected or Rolled-Back Features* wiki state:

> *"New high-end PvM boss plugins are not accepted as a blanket policy."*

**Plain answer: Sharing coarse raid, Inferno, Fortis Colosseum, or Gauntlet status and milestones with your own opted-in Group Ironman party does NOT fall under this ban — provided the plugin gives zero combat/mechanical assistance inside the encounter.**

Why it falls outside the rule, and the precedents that establish it:

1. **What the blanket rule targets:** The policy rejects plugins whose purpose is helping a player **fight or solve** high-end PvM encounters — boss attack/tick counters, prayer-switch helpers, projectile/hazard indicators, safe-spot/line-of-sight solvers, or boss fight simulators (`AGENTS.md`, *Boss & Combat Restrictions*).
2. **What GIM Utilities does instead:** It reads coarse **where-and-how-far telemetry** (current region/room name, wave number already printed in game chat, modifier names already chosen by the player, elapsed time, and completion/wipe summary) and shares that status line with opted-in teammates over RuneLite's own `PartyService` and the player's own Discord webhook. The player fighting Zuk, Sol Heredit, Olm, Verzik, Wardens, or Hunllef gets **zero** tactical information they did not already see in vanilla OSRS, and zero combat overlays in the arena.
3. **Direct precedents in the base client and accepted Plugin Hub plugins:**
   - **RuneLite `DiscordPlugin` + `DiscordGameEventType` (base client):** `runelite-client/.../plugins/discord/DiscordGameEventType.java` ships in RuneLite itself and broadcasts Rich Presence status for *TzHaar Fight Cave*, *The Inferno*, *Fortis Colosseum*, *Chambers of Xeric*, *Theatre of Blood*, *Tombs of Amascut*, *The Gauntlet*, *Wintertodt*, *Tempoross*, *Barbarian Assault*, *Pest Control*, *Guardians of the Rift*, *Zalcano*, *Barrows*, and *Hallowed Sepulchre*.
   - **RuneLite `PartyPlugin` (`party`, base client):** `runelite-client/.../plugins/party/PartyPlugin.java` shares live party member status, HP/prayer/spec/world/location, and raid state over `PartyService` (`WSClient`).
   - **RuneLite `RaidsPlugin` (`raids`, base client):** Tracks CoX `IN_RAID`, `RAID_STATE`, `RAID_PARTY_SIZE`, and points (`runelite-client/.../plugins/raids/RaidsPlugin.java`).
   - **Accepted Plugin Hub plugins:**
     - **Dink** (`pajlads/DinkPlugin`): Accepted Hub plugin posting raid completions, Fight Caves/Inferno/Colosseum waves and completions, Gauntlet completions, Barrows/Moons chests, and PvM deaths (with region/boss context) to Discord webhooks.
     - **Party Panel** (`TheStonedTurtle/Party-Panel`) & **Group Ironmen Tracker** (`GroupIronmen/group-ironmen-plugin`): Accepted Hub plugins syncing group/party member status, locations, stats, and activity state to teammates.
     - **Tombs of Amascut** (`LlemonDuck/tombs-of-amascut`): Accepted Hub plugin syncing raid level/invocation and room/party state over RuneLite `PartyService`.
     - **Fortis Colosseum** (`LlemonDuck/fortis-colosseum`): Accepted Hub plugin reading Colosseum wave numbers, glory, and selected modifiers/handicaps (note: Colosseum was grandfathered for its own dedicated plugin at release, whereas GIM Utilities only reads wave + chosen handicap names + glory for group status, with zero spawn/LoS overlays).

**Hard compliance guardrail enforced across all slices:**
GIM Utilities must **never** render in-arena combat overlays (no tile markers, no NPC highlights, no tick/attack/prayer timers, no LoS calculators), must **never** use reflection (`Class.forName`) to read state from `RaidsPlugin`, `Tombs of Amascut`, or `Fortis Colosseum`, and must **never** pre-scout unentered CoX rooms.

### 1.2 Per-item compliance verdicts

Each row states the feature in terms of **what information reaches the player/group** (per `runelite-compliance` step 2), not UI layout.

| # | Activity / Item | Information reaching the group | Verdict | Nearest rule (`AGENTS.md`) | Precedent | Required narrowing / conditions |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | **Fight Caves** — wave `1–63`, elapsed time, Jad phase, completion KC/time, death/exit wave | Teammates see which Fight Caves wave a groupmate is currently on, how long they have been inside, and whether they cleared Jad or wiped on wave `N`. | **Allowed, with precedent** | Boss & Combat: *No attack counters / prayer indicators* in wave minigames | Base `DiscordGameEventType` (`region 9551`); Hub **Dink** (`KillCountNotifier`, `DeathNotifier`); Hub **Fight Cave Waves** (wave counter only) | Read wave strictly from `"Wave: N"` chat message + region `9551`; never track Jad attack styles or spawn rotations. Opt-in via `groupShareEvents`. |
| 2 | **The Inferno** — wave `1–69`, Triple Jads (68) / Zuk (69) label, elapsed time, completion KC/time, death/exit wave | Teammates see which Inferno wave (`1–69`) a groupmate has reached, time elapsed, and completion or wipe wave. | **Allowed, with precedent** | Boss & Combat: *Blanket high-end PvM boss policy* & *no boss mechanic indicators* | Base `DiscordGameEventType` (`region 9043`); Hub **Dink** (Inferno KC, PB, and safe-death webhook exceptions) | Wave number from `"Wave: N"` chat only (`68` labeled Triple Jads, `69` TzKal-Zuk because wave identity is fixed). Zero nibbler/shield/healer/set timers. |
| 3 | **Fortis Colosseum** — wave `1–12` (Sol Heredit on 12), active chosen modifiers, Glory, elapsed time, claim-loot / completion / death with wave & modifiers | Teammates see that a groupmate is on Colosseum wave `1–12`, which modifiers they already selected at the pillar, current Glory, and whether they claimed loot, beat Sol Heredit, or died (and to which modifiers/wave). | **Allowed, with precedent** | Boss & Combat: *Blanket high-end PvM boss policy* & *no advance hazard warnings* | Base `DiscordGameEventType` (`region 7216`); Hub **Fortis Colosseum** (`LlemonDuck/fortis-colosseum` modifier & wave tracking); Hub **Dink** | Read only already-active modifiers selected by the player + wave chat + glory varp. Never display reinforcement timers, solar flare/totem helpers, or LoS tiles. |
| 4 | **Chambers of Xeric (Normal & CM)** — floor/Olm stage, party size, solo vs group, personal & party points, raid deaths, completion KC/time | Teammates see a groupmate is in CoX (Normal or CM), party size + GIM mates present, current floor (`Upper` / `Middle` / `Lower` / `Great Olm`) or active boss HUD name, live points, raid deaths, and completion. | **Restricted** (allowed in narrowed form) | Boss & Combat: *Blanket high-end PvM boss policy*; Data & Privacy: *No crowdsourcing data about other players*; Language: *No reflection* | Base `RaidsPlugin` (`Varbits.IN_RAID`, `RAID_STATE`, `RAID_PARTY_SIZE`, `TOTAL_POINTS`, `PERSONAL_POINTS`); Base `PartyPlugin`; Hub **Dink** | **Narrowed form:** Report only the floor/boss **currently reached** (`RAID_STATE` + active `HpbarHud` boss name + `"level complete!"` chat). Do **not** pre-scout unentered rooms, do **not** reflect into `RaidsPlugin`, and count non-GIM party members as `+N others` (never send stranger RSNs). |
| 5 | **Theatre of Blood (Entry / Normal / Hard Mode)** — current boss room, mode, party size (`1–5`), alive/dead orb count, deaths with room, completion KC/time | Teammates see which of the 6 ToB boss rooms (or Loot Vault) a groupmate is in, mode, party size, deaths by room, and completion. | **Restricted** (allowed in narrowed form) | Boss & Combat: *Blanket high-end PvM boss policy*; Data & Privacy: *No crowdsourcing data about other players* | Base `DiscordGameEventType` (ToB regions `12611..13379`); Base `Varbits.THEATRE_OF_BLOOD` & orb varbits; Hub **Dink** | **Narrowed form:** Room identity from unwrapped instance region ID (`WorldPoint.fromLocalInstance`) + orb varbits `6442–6446` for party size and wipe/death state. Zero Sotetseg maze, Nylo wave, or Verzik tick helpers. Non-GIM teammates counted as `+N others`. |
| 6 | **Tombs of Amascut (Entry / Normal / Expert)** — raid level (invocation total), current Nexus / puzzle / boss room, party size (`1–8`), deaths with room, completion KC/time | Teammates see a groupmate's ToA raid level (`e.g. 350 Expert`), current path/boss room (`Het`, `Akkha`, `Crondis`, `Zebak`, `Scabaras`, `Kephri`, `Apmeken`, `Ba-Ba`, `Wardens`), party size, deaths, and completion. | **Restricted** (allowed in narrowed form) | Boss & Combat: *Blanket high-end PvM boss policy*; Data & Privacy: *No crowdsourcing data about other players* | Base `DiscordGameEventType` (ToA regions `14160..15700`); Hub **Tombs of Amascut** (`LlemonDuck/tombs-of-amascut` party sync); Hub **Dink** | **Narrowed form:** Read `VarbitID` raid level (`14380`), orb varbits (`14346–14353`), and unwrapped region ID. Zero puzzle solvers, Akkha memory helpers, or Warden skull/prayer indicators. Non-GIM teammates counted as `+N others`. |
| 7 | **Wintertodt** — solo vs small group vs mass, Wintertodt energy %, player warmth %, points, between-round countdown, subdue KC/points, death | Teammates see whether a groupmate is soloing Wintertodt or on a mass world, the boss's remaining energy %, their points/warmth, and round completions. | **Restricted** (allowed in narrowed form) | Data & Privacy: *No exposing player information over HTTP / no crowdsourcing data about other players* | Base `WintertodtPlugin` (`Varbits.WINTERTODT_TIMER` `7980`, chat lines) & `DiscordGameEventType` (`region 6462`); Hub **Wintertodt Helper** | **Narrowed form:** Solo vs group vs mass is computed from `players().size()` in region `6462` (`1` = `solo`, `2–4` = `small group`, `5+` = `mass, N players`). Only GIM roster mates are named; non-roster players are strictly an integer count. |
| 8 | **Tempoross** — solo vs small group vs mass, energy %, essence %, storm intensity %, reward permits, phase, subdue KC/time | Teammates see whether a groupmate is soloing Tempoross or in a mass boat, current boss energy/essence/storm %, permits earned, and subdue completion. | **Restricted** (allowed in narrowed form) | Data & Privacy: *No crowdsourcing data about other players* | Base `DiscordGameEventType` (`region 12076`); Hub **Tempoross** plugin (HUD widget `437` state & timers); Hub **Dink** | **Narrowed form:** Read HUD widget values + chat lines; derive `solo` / `duo` / `mass (N)` from player count in instanced region `12076`, naming only GIM roster mates. Zero wave/fire auto-pathing. |
| 9 | **Barbarian Assault** — wave `1–10` (Queen on 10), player's role (`Attacker`, `Defender`, `Collector`, `Healer`), GIM teammates on team, Queen completion | Teammates see which BA wave (`1–10`) and role a groupmate is playing and when Queen is cleared. | **Allowed, with precedent** | Data & Privacy: *No crowdsourcing data about other players* | Base `BarbarianAssaultPlugin` (`Varbits.IN_GAME_BA` `3923`, role widgets `Ba*`); Base `DiscordGameEventType` (`regions 7508, 7509`) | Share wave number + own role name + GIM roster mates present (`+N others` for randoms). Do not share horn call timers or defender bait overlays. |
| 10 | **Pest Control** — lander tier (`Novice` / `Intermediate` / `Veteran`), portals remaining (`0–4`), Void Knight HP, activity bar status, time left, win/loss | Teammates see which lander boat tier a groupmate is running, portals left, Knight health, and round completion. | **Allowed, with precedent** | Data & Privacy: *No crowdsourcing data about other players* | Base `PestControlPlugin` (`InterfaceID.PestStatus.*` widget `408`); Base `DiscordGameEventType` (`region 10536`) | Read HUD widget `408` values + completion chat; count non-GIM boat players without naming them. |
| 11 | **The Gauntlet & Corrupted Gauntlet** — mode (`Crystalline` vs `Corrupted`), phase (`Prep` + time left vs `Hunllef`), completion KC/time, death with phase | Teammates see whether a groupmate is in normal or Corrupted Gauntlet, still prepping (with prep time remaining) or fighting Hunllef, and whether they cleared or died. | **Allowed, with precedent** | Boss & Combat: *No attack counters / prayer switching indicators* (Hunllef tornado/stomp/attack counters are explicitly banned) | Base `DiscordGameEventType` (`regions 7512, 7768`); Base `Varbits.GAUNTLET_ENTERED` (`9178`) & `GAUNTLET_FINAL_ROOM_ENTERED` (`9177`); Hub **The Gauntlet** (resource/prep tracker); Hub **Dink** | Report **only** `Prep (mm:ss left)` vs `Hunllef` phase, mode (`CG` vs `Gauntlet`), elapsed time, and completion/death. Zero Hunllef attack/prayer/tornado tracking. |
| 12 | **Guardians of the Rift (comparable)** — solo vs small group vs mass, Great Guardian power %, elemental/catalytic energy, portal timer, Rift closed KC | Teammates see whether a groupmate is soloing GotR or in a mass, Guardian power %, points, and rifts closed. | **Restricted** (allowed in narrowed form) | Data & Privacy: *No crowdsourcing data about other players* | Base `DiscordGameEventType` (`region 14484`); Hub **Guardians of the Rift Helper** (HUD widget `746`) | Derive `solo` / `small group` / `mass (N)` from player count in `14484`; never transmit non-roster names. |
| 13 | **Zalcano (comparable)** — solo vs small team vs mass, boss HP / shield %, KC & MVP completion, death | Teammates see team size (`solo`, `4-man`, `mass`), current Zalcano kill progress, and KC/MVP completions. | **Restricted** (allowed in narrowed form) | Data & Privacy: *No crowdsourcing data about other players* | Base `DiscordGameEventType` (`region 12126`); Base `OpponentInfo` / `HpbarHud`; Hub **Dink** | Count players inside Zalcano's arena (`12126`), naming only GIM mates. Zero falling-rock/symbol combat indicators. |
| 14 | **Moons of Peril (comparable)** — moons slain (`0/3–3/3`: Blood, Blue, Eclipse), current chamber, Lunar Chest completion | Teammates see which Moon boss chamber a groupmate is fighting and how many of the 3 Moons they have slain this run before looting the Lunar Chest. | **Allowed, with precedent** | Boss & Combat: *No mechanic indicators* | Hub **Dink** (Lunar Chest & Moons tracking); OSRS Wiki Neypotzli regions & moon varbits | Shared non-instanced dungeon: report own `X/3 moons` state and current boss room; do not label shared cave as solo/mass and do not name passersby. |
| 15 | **Barrows live crypts (comparable)** — brothers slain (`0/6–6/6`), reward potential %, chest loot (existing `BARROWS` event) | Teammates see live `4/6 brothers, 86% potential` while a groupmate is running Barrows, complementing the existing `BARROWS` chest-loot event. | **Allowed, with precedent** | None (non-high-end PvM, Base client ships full Barrows plugin) | Base `BarrowsPlugin` (`Varbits.BARROWS_KILLED_*` `457–462`, `BARROWS_NPCS_SLAIN` `464`); existing `BarrowsProgress.java` | Read varbits `457–464` and regions `14131`/`14231`; do not solve Barrows door puzzles. |
| 16 | **Hallowed Sepulchre & TzHaar-Ket-Rak's Challenges (comparable)** — Sepulchre floor `1–5` + Grand Coffin; Ket-Rak `1–6` Jad challenge tier + completion | Teammates see which Sepulchre floor (`1–5`) or Ket-Rak challenge (`1–6 Jads`) a groupmate is attempting and completing. | **Allowed, with precedent** | Boss & Combat: *No hazard/projectile indicators* (both Sepulchre statues/arrows and multi-Jad prayers are restricted if assisted) | Base `DiscordGameEventType` (Sepulchre regions `8797..10077`); Hub **Dink** | Floor/challenge name + timer + completion/death only. Zero statue/arrow/prayer helpers. |
| 17 | **Party sync (`"k":"activity"`) & Discord webhooks (`Progress` / `Deaths` / `StatusBoard`)** — sending live activity state to GIM party and milestones to player-configured Discord webhooks | Opted-in GIM party members receive live activity status via RuneLite `PartyService`; the player's own client posts their milestones to their configured Discord webhook. | **Allowed, with precedent** | Data & Privacy: *No exposing player information over HTTP* & `AGENTS.md` *3rd-party server opt-in warning* | Base `PartyPlugin` (`PartyService`); Hub **Dink**; existing `GroupOutbox.java`, `DiscordSender.java`, `StatusBoard.java` | Party sync uses RuneLite's built-in `PartyService` gated by `groupShareEvents` (`false` by default, `SharingPrompt`). Discord webhooks are user-supplied URLs with existing 3rd-party IP warning (`GroupIronmanConfig.java:328`), posting **own** events only (`EventRouter.java:58`) and honoring `MemberPrefs` opt-outs. |

### 1.3 Deliberately not built (compliance cuts)

Recorded here per `runelite-compliance` step 5 so these ideas are never re-proposed in future slices:

| Cut feature | Exact rule violated (`AGENTS.md` / Hub wiki) | Why it is cut |
| --- | --- | --- |
| **PvP minigame live round/team tracking** (Last Man Standing survivors/kills, Soul Wars avatar/zeal/barricades, Castle Wars flag/score, TzHaar Fight Pit remaining foes, Emir's Arena, Clan Wars) | **PvP Restrictions:** *No PvP target scouting information*, *No player group summaries (attackable counts, prayer usage, etc.)*, *No opponent freeze/status indicators* | Even "innocent" round summaries in LMS or Soul Wars (e.g. players remaining near you, team counts, opponent status) cross directly into PvP scouting and player group summaries. Cut completely; presence in those regions stays a plain region name in `Group` tab if `Share stats` is on. |
| **In-encounter boss mechanic, attack, prayer, or hazard tracking** (Jad/Zuk/Triple-Jad attack styles, Inferno set/shield timers, Colosseum reinforcement countdowns or solar-flare/totem helpers, Hunllef attack/prayer/tornado counters, Olm phase/burn timers, Verzik/Sotetseg/Nylocas counters, Wardens skull/prayer helpers, Sepulchre arrow/statue timers) | **Boss & Combat Restrictions:** *No next-attack prediction*, *No projectile target/landing indicators*, *No prayer switching indicators*, *No attack counters*, *No additional visual or audio indicators of a boss mechanic*, *New high-end PvM boss plugins are not accepted* | Any mechanic-level tracking turns status telemetry into a banned PvM combat helper. Only coarse stage/wave/room, elapsed time, and player-selected modifiers are read. |
| **Pre-scouting unentered Chambers of Xeric rooms or reading `RaidsPlugin` / Hub plugin internals via reflection** | **Forbidden Language Features:** *No use of reflection*; **Boss & Combat Restrictions:** *Blanket PvM policy* | Reading another plugin's private state via `Class.forName` / `getDeclaredField` is an automatic Hub rejection (`runelite-compliance` trap #4). Unentered room layout scouting is unnecessary for group progress; GIM Utilities reports only the floor/boss the player has actually reached. |
| **Transmitting names of non-group players in raids, BA, Pest Control, Wintertodt, Tempoross, GotR, or Zalcano** | **Data & Privacy Restrictions:** *No exposing player information over HTTP*, *No crowdsourcing data about other players (locations, gear, names, etc.)* | Other players in an instance or mass world never consented to have their RSN sent over Party or posted to a group's Discord. Non-roster players are strictly counted (`solo`, `with Sam +2 others`, `mass (48 players)`), reusing the pattern in `EventWatchers.java:193-219`. |
| **Chatbox commands that send public/clan chat messages (e.g. `!wave` or auto-typing completions in `/g`)** | **Input Restrictions:** *No autotyping — plugins must not programmatically insert text into the chatbox input*, *No modifying outgoing chat messages* | All sharing happens over `PartyService`, local Swing/Canvas UI, and optional Discord webhooks; nothing ever writes to game chat input. |

---

## 2. Per-Activity Data Sources & Solo vs Group Detection

### 2.1 Instance region rule (applies to almost every activity below)
Inside instanced areas (Fight Caves, Inferno, Colosseum, CoX, ToB, ToA, Tempoross boat/cove, Gauntlet, BA waves, Pest Control island, Hallowed Sepulchre), `player.getWorldLocation().getRegionID()` returns a dynamic instance chunk ID (`>= 16000`), **not** the template region ID in [`regions.txt`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt). Every detector **must** unwrap the local player's template coordinate via:

```java
WorldPoint template = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
int regionId = template == null ? -1 : template.getRegionID();
```

This is already how [`GroupEventEngine.whereIs`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L719-L723) resolves regions on death.

### 2.2 Shared Solo vs Group / Mass classifier (`TeamSummary`)
Extract and generalize [`EventWatchers.raidDetail`](../../src/main/java/com/vinnyvangogh/groupironman/EventWatchers.java#L193-L219) into a single pure helper `TeamSummary` (avoiding any duplicated loop for `code_rot`):

| Activity family | How solo vs group / mass is detected | Output label examples |
| --- | --- | --- |
| **Strict solo instances** (Fight Caves, Inferno, Ket-Rak, Fortis Colosseum, Gauntlet, Corrupted Gauntlet, Hallowed Sepulchre) | Hardcoded `SOLO` by game design (instance admits 1 player). | `solo` (omitted on tight surfaces where solo is implied) |
| **Raids** (CoX, ToB, ToA) | Party size from raid varbits (`RAID_PARTY_SIZE` `5424` in CoX; count of non-zero party HP orb varbits `6442–6446` in ToB and `14346–14353` in ToA), cross-checked with `client.getTopLevelWorldView().players()` against `ClanID.GROUP_IRONMAN` (`GroupEventEngine.java:670-695`). | `solo` (`size == 1`); `duo with Sam` (`size == 2`, Sam in GIM); `3-man (with Sam +1 other)` |
| **Skilling bosses** (Wintertodt `6462`, Tempoross `12076`, GotR `14484`, Zalcano `12126`) | Count players in `client.getTopLevelWorldView().players()` whose unwrapped template `regionId` matches the arena (and for Zalcano, within the boss pit y/x bounds rather than the outer hallway):<br>• `count == 1` → **`solo`** (debounced over 3 ticks after round start so late renders on region load don't flash `solo` for 1 tick on a mass world)<br>• `2 <= count <= 5` → **`duo`** (`2`) or **`N-man`** (`3–5`), naming GIM roster members + `+K others`<br>• `count >= 6` → **`mass (N players)`** (plus `with Sam` if a GIM mate is in the same mass) | `solo`; `duo with Sam`; `4-man (with Sam +2 others)`; `mass (54 players, with Sam)` |
| **Fixed-team minigames** (Barbarian Assault `7508/7509`, Pest Control `10536`) | BA is always 5-player (`team`, naming GIM mates); Pest Control is boat team (`5–25 players`, naming GIM mates). | `with Sam, Pat +2 others`; `Veteran boat (with Sam +21 others)` |
| **Shared open dungeons** (Barrows `14131/14231`, Moons of Peril `5525..6039`) | Individual progression inside a public cave; other players do not share your chest/boss state. Marked `individual` (never `solo` vs `mass`). | *(no solo/mass tag; shows brother/moon count)* |

### 2.3 Verification key
Per `BASE_INSTRUCTIONS.md` (*Research Phase*) and [`EventChat.java:10-14`](../../src/main/java/com/vinnyvangogh/groupironman/EventChat.java#L10-L14):
- **Verified (repo / RuneLite / Wiki):** Confirmed directly against this repository's [`regions.txt`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt) (sourced from RuneLite's `DiscordGameEventType`), existing plugin classes, `runelite/runelite` source, or OSRS Wiki pages.
- **Unverified (`confirm in Slice X`):** Exact `net.runelite.api.gameval.VarbitID` / `InterfaceID` constant spelling in `runelite-api:1.13.5` or exact in-game chat punctuation where the Gradle cache was outside the read-only research sandbox. Each build slice **must** verify `gameval` constant names via `javap` / compiler against `1.13.5` (`AGENTS.md`: never hardcode magic numbers when `gameval` constants exist) and confirm chat regexes in game before marking the slice done.

---

### 2.4 Activity-by-activity data tables

#### A1. TzHaar Fight Cave (63 waves) & The Inferno (69 waves)
- **Wiki references:** <https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave>, <https://oldschool.runescape.wiki/w/Inferno>
- **RuneLite references:** [`regions.txt:308,337`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt#L308) (`DiscordGameEventType.java`); `runelite-client/.../plugins/fightcave/` & `ChatCommandsPlugin` / `ScreenshotPlugin` KC patterns; [`EventChat.java:21-22,42-43`](../../src/main/java/com/vinnyvangogh/groupironman/EventChat.java#L21-L43).

| Data point | Source kind & identifier | Details & edge cases | Status |
| --- | --- | --- | --- |
| **In Fight Caves** | Template Region ID `9551` | `regions.txt:337` (`The Fight Caves=9551`), unwrapped via `WorldPoint.fromLocalInstance`. Solo only. | **Verified** |
| **In The Inferno** | Template Region ID `9043` | `regions.txt:308` (`The Inferno=9043`), unwrapped via `WorldPoint.fromLocalInstance`. Distinguish from Ket-Rak's Challenges (see A6) via wave chat `"Wave: N"` vs Ket-Rak challenge start. Solo only. | **Verified** |
| **Current wave (`1–63` / `1–69`)** | `GAMEMESSAGE` regex `^Wave: (\d+)$` (tags stripped via `EventText.plain`) | Printed at the start of every wave in both Fight Caves (`max=63`, wave `63` = `TzTok-Jad`) and Inferno (`max=69`, wave `67` = `Single Jad`, `68` = `Triple Jads`, `69` = `TzKal-Zuk`). | **Verified** |
| **Mid-run login / world hop resume** | `GAMEMESSAGE` `^Wave: (\d+)$` + local RSProfile state `activity.tzhaarWave` | Logging out pauses Fight Caves/Inferno between waves; on relog inside `9551`/`9043`, the next wave prints `Wave: N` immediately, and storing `{region, wave, elapsedBeforeLogoutMs}` in `RSProfileConfiguration` preserves elapsed time across pauses! | **Verified** (design) |
| **Time in** | Local monotonic session timer (`elapsedMs` accumulated while logged in inside `9551`/`9043`) + official game timer on completion chat `Duration: ([0-9:.]+)` | Live card/overlay ticks from `startedAt` (or accumulated active ms); completion milestone uses exact game duration string when present. | **Verified** |
| **Completion** | `GAMEMESSAGE`: `Your TzTok-Jad kill count is: (\d+)` / `Your TzKal-Zuk kill count is: (\d+)` (`EventText.killCount`, `GroupEventEngine.java:602`) + duration chat line | Emits `ACTIVITY_COMPLETE` (`ac`) with KC, official duration, PB flag (`EventChat.personalBest`), and clears live activity state on exit to MorUlRek (`9808` / `10064`). | **Verified** |
| **Death / defeat with wave** | `onActorDeath` (`GroupEventEngine.java:726`) **OR** `GAMEMESSAGE` `^You have been defeated!$` while in/leaving `9551` or `9043` | Fight Caves & Inferno are safe deaths for regular/GIM (unsafe for HCGIM, which already checks `VarbitID.IRONMAN == 5` at `GroupEventEngine.java:755`). Watching **both** `ActorDeath` and `"You have been defeated!"` guarantees wave `N` is captured even if instance teardown happens on the same tick. Voluntary exit via cave entrance without defeat chat clears live state silently (no false death). | **Verified** (confirm exact defeat punctuation in Slice A) |

#### A2. Fortis Colosseum (Waves 1–12 & Sol Heredit)
- **Wiki reference:** <https://oldschool.runescape.wiki/w/Fortis_Colosseum>
- **RuneLite / Hub references:** [`regions.txt:305-306`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt#L305-L306) (`Fortis Colosseum=7216`, `Fortis Colosseum Lobby=7316`); `runelite-api` `VarPlayerID` / `VarbitID` Colosseum constants; Hub `LlemonDuck/fortis-colosseum` (`ColosseumPlugin.java`, `Handicap.java`); Hub `pajlads/DinkPlugin` issue #472 (Doom death edge case).

| Data point | Source kind & identifier | Details & edge cases | Status |
| --- | --- | --- | --- |
| **In Colosseum Arena / Lobby** | Template Region ID `7216` (Arena), `7316` (Lobby) | `regions.txt:305-306`. Solo only. | **Verified** |
| **Current wave (`1–12`)** | `GAMEMESSAGE` regex `^Wave: (\d+)$` (wave `12` = `Sol Heredit`) + wave completion line `Wave (\d+) completed!` | Wave `1–11` normal waves, wave `12` boss (`Sol Heredit`). | **Verified** (confirm exact wave-complete wording in Slice A) |
| **Active modifiers (handicaps) & ranks (`I–III`)** | `net.runelite.api.gameval.VarbitID` Colosseum modifier varbits (14 handicaps, values `0–3`, e.g. *Mantimayhem*, *Doom*, *Blasphemy*, *Myopia*, *Relentless*, *Solarflare*, *Quartet*, *Totemic*, *Dynamic Duo*, *Bees!*, *Volatility*, *Frailty*, *Red Flag*, *Reentry*) + fallback chat `Minimus: .*` / modifier selection widget | `LlemonDuck/fortis-colosseum` maps each of the 14 Colosseum handicaps to its dedicated varbit (`0` = not active, `1..3` = tier I..III). Slice A must resolve the exact `VarbitID.COLOSSEUM_*` constant names from `runelite-api:1.13.5` via `javap` rather than raw ints. | **Unverified constant names** (confirm `VarbitID` names in `1.13.5` in Slice A) |
| **Current Glory** | `VarPlayerID.COLOSSEUM_GLORY` (or Colosseum HUD varp/widget) | Shown in granular `Activities` tab card (`Wave 9/12 · 14,200 glory · Mantimayhem II, Myopia I`). | **Unverified constant name** (confirm in `VarPlayerID` in Slice A) |
| **Claim loot vs Sol Heredit completion** | Completion KC chat `Your Sol Heredit kill count is: (\d+)` (full wave 12 clear) vs leaving `7216 → 7316` after claiming chest without defeat | Full wave 12 clear emits `ACTIVITY_COMPLETE` (`Sol Heredit, KC N, time, modifiers`); early claim at wave `N < 12` clears live state and logs `Claimed loot after wave N` in `Recent runs` without firing a fake Sol Heredit KC post. | **Verified** (design) |
| **Death with wave & modifiers (Doom trap!)** | `onActorDeath` **OR** `GAMEMESSAGE` `^You have been defeated!$` / transition `7216 → 7316` with 0 HP or `COLOSSEUM_DOOM` stack | **Critical edge case (Dink #472):** Dying to the *Doom* handicap stack instant-kills the player and can skip standard `0 HP` / `ActorDeath` ordering. Watching `"You have been defeated!"` in addition to `ActorDeath` catches Doom deaths reliably with wave + active modifiers attached. | **Verified** |

#### A3. Raids: Chambers of Xeric (CoX), Theatre of Blood (ToB), Tombs of Amascut (ToA)
- **Wiki references:** <https://oldschool.runescape.wiki/w/Chambers_of_Xeric>, <https://oldschool.runescape.wiki/w/Theatre_of_Blood>, <https://oldschool.runescape.wiki/w/Tombs_of_Amascut>
- **RuneLite / Hub references:** [`regions.txt:300,340,342`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt#L300); [`GroupEventEngine.java:592-662`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L592-L662); [`EventWatchers.java:171-226`](../../src/main/java/com/vinnyvangogh/groupironman/EventWatchers.java#L171-L226); `runelite-client/.../plugins/raids/RaidsPlugin.java`; Hub `LlemonDuck/tombs-of-amascut`; Hub `pajlads/DinkPlugin`.

| Raid & data point | Source kind & identifier | Details & edge cases | Status |
| --- | --- | --- | --- |
| **CoX — in raid & floor state** | Regions `12889, 13136..13141, 13145, 13393..13397, 13401` (`regions.txt:300`) + `VarbitID.RAIDS_CLIENT_INDUNGEON` (`Varbits.IN_RAID = 5432`) + `RAID_STATE` (`5425`) | `IN_RAID == 1` when inside dungeon; `RAID_STATE` transitions from `0` (lobby) → started/floors → Olm. Floor milestones also announced in `FRIENDSCHATNOTIFICATION` / `GAMEMESSAGE`: `"The raid has begun!"`, `"Upper level complete!"`, `"Middle level complete!"`, `"Lower level complete!"`. | **Verified** (confirm exact `VarbitID` names in Slice C) |
| **CoX — Challenge Mode (CM)** | CM varbit (`VarbitID` CM flag in `runelite-api`, e.g. `6385`) + completion KC name `"Chambers of Xeric Challenge Mode"` (`GroupEventEngine.java:661`) | Distinguishes `CoX` vs `CoX (CM)` on panel, card, overlay, and Discord. | **Unverified varbit name** (confirm in `VarbitID` in Slice C) |
| **CoX — current room / boss (no pre-scouting)** | Olm region `12889` (`Great Olm`) + active boss HP bar widget (`InterfaceID.HpbarHud.*` NPC name, e.g. *Tekton*, *Vasa Nistirio*, *Vespula*, *Mutadile*, *Vanguards*, *Ice demon*, *Abyssal portal*) when visible; fallback to current floor (`Floor 1`, `Floor 2`, `Floor 3`) | Gives live `"CoX (CM) · Vasa · 42.1k pts"` while fighting a boss and `"CoX · Floor 2 · 18.4k pts"` between bosses, with **zero** unentered-room layout scouting and **zero** reflection. | **Verified** (confirm `InterfaceID.HpbarHud` child constant in Slice C) |
| **CoX — party size, solo vs team, points** | `RAID_PARTY_SIZE` (`5424`), `TOTAL_POINTS` (`5431`), `PERSONAL_POINTS` (`VarPlayerID` `5422`), `TeamSummary` from `playersHere()` + `groupMembers()` | `RAID_PARTY_SIZE == 1` → `solo`; `> 1` → `N-man` + GIM mates named. Points formatted with `Amounts.GAME` (`28.4k pts`). | **Verified** |
| **CoX — deaths & completion** | `onActorDeath` while `IN_RAID == 1` (safe death except HCGIM no—CoX is safe even for HCGIM! Check `VarbitID.IRONMAN` only when game actually revokes life) + `"Congratulations - your raid is complete!"` + KC line | **Note:** CoX is a **safe** death for HCGIM (unlike ToB and ToA with Hardcore invocations). `ACTIVITY_DEATH` (`ad`) records `"Died in CoX (Great Olm, 18.2k pts)"` without claiming an HCGIM life was lost. | **Verified** |
| **ToB — state, party size (`1–5`), deaths** | `Varbits.THEATRE_OF_BLOOD` (`6440`: `0`=outside, `1`=in party, `2`=inside raid, `3`=dead/spectating) + HUD orb varbits `6442–6446` (`0`=empty slot, `1–27`=HP ratio, `30`=dead) | Count of non-zero orb varbits (`6442..6446`) = exact raid party size (`1` = `solo`, `2–5` = team). Transition of local player's state `2 → 3` or `ActorDeath` records a room death; all orbs `== 30` = full party wipe! | **Verified** (confirm `VarbitID` names in Slice C) |
| **ToB — boss room mapping** | Unwrapped template Region ID (`regions.txt:340`): `12613` (*Maiden*), `13125` (*Pestilent Bloat*), `13122` (*Nylocas*), `13123` / `13379` (*Sotetseg* room / maze), `12612` (*Xarpus*), `12611` (*Verzik Vitur*), `12867` (*Monumental Chest / Vault*), `14642` (*Ver Sinhaza lobby*) | Every ToB boss has its own distinct template region ID — zero NPC scanning needed to know exact boss progress (`Room 1/6: Maiden` .. `Room 6/6: Verzik`). | **Verified** (double-check `12613` Maiden vs `12612` Xarpus ordering in Slice C) |
| **ToB — mode (Entry / Normal / Hard Mode)** | Active boss NPC name / HP HUD (`"... (Entry Mode)"`, `"... (Hard Mode)"`) + completion KC chat (`"Theatre of Blood: Entry Mode"`, `"Theatre of Blood"`, `"Theatre of Blood: Hard Mode"`) + mode varbit if present in `VarbitID` | Labels `ToB (Entry)`, `ToB`, or `ToB (HM)`. | **Unverified varbit** (NPC/KC text verified; check `VarbitID` in Slice C) |
| **ToA — raid level, mode, party size (`1–8`)** | `VarbitID.TOA_RAID_LEVEL` (`14380`: `0–600`, `< 150` = *Entry*, `150–299` = *Normal*, `>= 300` = *Expert*) + party orb varbits `14346–14353` (`TOA_MEMBER_0_HEALTH`..`7_HEALTH`: `0`=empty, `1–27`=HP, `30`=dead) | Count of non-zero `14346–14353` varbits = exact raid party size (`1` = `solo`, `2–8` = team). Raid level gives granular difficulty (`ToA 400 (Expert)`). | **Verified** |
| **ToA — path / puzzle / boss room mapping** | Unwrapped template Region ID (`regions.txt:342`):<br>• `13454` Lobby (*Jaltevas*)<br>• `14160` *Nexus* (choosing path)<br>• `14162` *Path of Scabaras* → `14164` *Kephri*<br>• `14674` *Path of Het* → `14676` *Akkha*<br>• `15186` *Path of Apmeken* → `15188` *Ba-Ba*<br>• `15698` *Path of Crondis* → `15700` *Zebak*<br>• `15184` *Wardens (Obelisk / P1–P2)* → `15696` *Wardens (P3)*<br>• `14672` *Burial Chamber (Loot)* | Gives exact path vs boss granularity (`ToA 350 · Path of Het` → `ToA 350 · Akkha` → `ToA 350 · Wardens P3`) purely from template region ID! Also track completed paths (`0/4..4/4`) as boss rooms transition back to `14160` Nexus. | **Verified** |

#### B1. Skilling Bosses: Wintertodt, Tempoross, Guardians of the Rift, Zalcano
- **Wiki references:** <https://oldschool.runescape.wiki/w/Wintertodt>, <https://oldschool.runescape.wiki/w/Tempoross>, <https://oldschool.runescape.wiki/w/Guardians_of_the_Rift>, <https://oldschool.runescape.wiki/w/Zalcano>
- **RuneLite / Hub references:** [`regions.txt:349,336,307,350`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt#L307); `runelite-client/.../plugins/wintertodt/WintertodtPlugin.java`; Hub `Tempoross` & `Guardians of the Rift Helper`.

| Activity & data point | Source kind & identifier | Details & edge cases | Status |
| --- | --- | --- | --- |
| **Wintertodt — arena & round timer** | Region `6462` (`regions.txt:349`) + `Varbits.WINTERTODT_TIMER` (`7980` / `VarbitID.WINT_TRANSMIT_RESPAWNDELAY`) | Non-instanced arena (`regionId == 6462`). When timer varbit `> 0`, Wintertodt is returning (`"Next round in Ns"`); when `0` and energy `> 0`, round is active. | **Verified** |
| **Wintertodt — energy %, warmth %, points** | Wintertodt HUD widget group `396` (`InterfaceID.Wint.*` child text widgets: `"Wintertodt's Energy: (\d+)%"`, `"Your warmth: (\d+)%"`, `"Points\n(\d+)"`) | Note OSRS's 2024 Wintertodt rework replaced direct HP cold chip with the **Warmth** meter; parse digits tolerantly so either label works cleanly. | **Unverified child names** (confirm `InterfaceID.Wint.*` in `1.13.5` in Slice B) |
| **Wintertodt — solo vs group vs mass** | `TeamSummary` over `client.getTopLevelWorldView().players()` in region `6462` | `1` player → `solo` (classic long 13,500-pt solo run shows `"Wintertodt (solo) · 18% energy · 9,450 pts"`); `2–5` → `duo` / `N-man`; `6+` (e.g. W307/W309) → `mass (N players)`. | **Verified** (design) |
| **Wintertodt — subdue completion & death** | `GAMEMESSAGE`: `"Your subdued Wintertodt count is: (\d+)"` (`EventText.killCount` / custom subdued pattern — verify `EventText.killCount` handles `"subdued Wintertodt count"`!) + `onActorDeath` (unsafe death!) | **Important check for Slice B:** Verify whether `EventText.killCount` already matches `"Your subdued Wintertodt count is: X."` or only `"kill count"` / `"completion count"`, and add subdue/rift patterns to `EventChat` if needed. | **Verified** |
| **Tempoross — arena & solo vs mass** | Instanced Arena Region `12076` (`regions.txt:336`, unwrapped via `WorldPoint.fromLocalInstance`); Lobby `12588` (*Ruins of Unkah*) | Boat departure moves players into an instance of template `12076`. Counting `players()` in `12076` after 3 ticks cleanly distinguishes solo boat (`1` = `solo`), GIM duo/small team (`2–5`), and W422 mass boat (`6–40`). | **Verified** |
| **Tempoross — energy %, essence %, storm %, permits** | Tempoross HUD widget group `437` (`InterfaceID.TemporossHud.*` or equivalent `gameval` children: `"Energy: (\d+)%"`, `"Essence: (\d+)%"`, `"Storm Intensity: (\d+)%"`, `"Reward permits: (\d+)"`) | Granular status: `"Tempoross (solo) · 64% energy · 82% storm · 6 permits"`. If Storm Intensity reaches `100%` (wave wipe), `"Tempoross escaped"` ends the round cleanly without a player death! | **Unverified child names** (confirm `InterfaceID` group `437` constants in Slice B) |
| **Tempoross — completion** | `GAMEMESSAGE`: `"Your Tempoross kill count is: (\d+)"` + `"Subdued in ([0-9:.]+)"` (`EventChat.NEW_BEST` already references `"subdued in"` at `EventChat.java:22`!) | Emits `ACTIVITY_COMPLETE` (`ac`) with KC, subdue time, solo/mass tag, and permits earned. | **Verified** |
| **Guardians of the Rift — arena, energy, solo vs mass** | Region `14484` (`regions.txt:307`) + HUD widget group `746` (`InterfaceID` GotR children: Great Guardian power `0–100%`, Rift timer, Elemental & Catalytic energy) + `TeamSummary` player count in `14484` | Distinguishes `GotR (solo)` vs `GotR (mass)` + `"78% power · 4/3 energy"`. Completion on `"Amount of rift closed: (\d+)"` chat; defeat when Guardian defeated chat fires. | **Unverified child names** (confirm `InterfaceID` group `746` constants in Slice B) |
| **Zalcano — arena, HP/shield, solo vs team vs mass** | Region `12126` (`regions.txt:350`) + `InterfaceID.HpbarHud` (Zalcano HP / tephra shield) + player count inside boss pit bounds | Distinguishes `Zalcano (4-man, with Sam +2 others)` vs `Zalcano (mass, 14 players)`. Completion on `"Your Zalcano kill count is: (\d+)"` (unsafe death via `ActorDeath`). | **Verified** (confirm pit coordinate bounds in Slice B) |

#### D1. Wave & Round Minigames: Barbarian Assault, Pest Control, Gauntlet / CG, Moons, Barrows, Sepulchre, Ket-Rak
- **Wiki references:** <https://oldschool.runescape.wiki/w/Barbarian_Assault>, <https://oldschool.runescape.wiki/w/Pest_Control>, <https://oldschool.runescape.wiki/w/The_Gauntlet>, <https://oldschool.runescape.wiki/w/Moons_of_Peril>, <https://oldschool.runescape.wiki/w/Barrows>, <https://oldschool.runescape.wiki/w/Hallowed_Sepulchre>, <https://oldschool.runescape.wiki/w/TzHaar-Ket-Rak%27s_Challenges>
- **RuneLite references:** [`regions.txt:297,298,302,309,330,338`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt#L297); `BarbarianAssaultPlugin.java`; `PestControlPlugin.java`; `BarrowsPlugin.java`.

| Activity & data point | Source kind & identifier | Details & edge cases | Status |
| --- | --- | --- | --- |
| **Barbarian Assault — active game, wave (`1–10`), role** | Arena regions `7508, 7509` (lobby `10322`, `regions.txt:297`) + `Varbits.IN_GAME_BA` (`3923` == `1`) + active role HUD widget (`InterfaceID.BaAttacker` `485`, `BaDefender` `486`, `BaCollector` `487`, `BaHealer` `488`) whose wave text child reads `"Wave (\d+)"` (`10` = *Penance Queen*) | Whichever of the 4 role widget groups is loaded immediately tells us the player's **exact role** (`Attacker`, `Defender`, `Collector`, `Healer`) and current **wave `1–10`**! Queen kill / wave 10 reward screen (`InterfaceID.BaReward` `497`) emits `ACTIVITY_COMPLETE`. Safe deaths inside BA emit `ACTIVITY_DEATH` (`Wiped on BA wave 8 (Defender)`). | **Verified** (confirm `InterfaceID.Ba*` wave child constants in Slice D) |
| **Pest Control — lander tier, portals, Knight HP, time** | Island region `10536` (`regions.txt:330`, Outpost `10537`) + HUD widget group `408` (`InterfaceID.PestStatus.*`: Void Knight HP, Activity bar, Time remaining, 4 portal shield/HP children) + lander boat widget `407` (`Novice` / `Intermediate` / `Veteran`) | Shows `"Pest Control (Veteran) · 2/4 portals left · Knight 142 HP"`. Completion detected on Void Knight victory dialog / commendation points chat; loss if Knight falls (`Knight 0 HP` / defeat dialog). | **Verified** (confirm `InterfaceID.PestStatus.*` child names in Slice D) |
| **The Gauntlet & Corrupted Gauntlet — mode & phase** | Lobby `12127` (`regions.txt:338`), **Crystalline Gauntlet** template region `7512` (`regions.txt:302`), **Corrupted Gauntlet** template region `7768` (`regions.txt:338`) + `Varbits.GAUNTLET_ENTERED` (`9178` == `1`) + `Varbits.GAUNTLET_FINAL_ROOM_ENTERED` (`9177` == `1`) + timer widget `637` | `region == 7768` → `Corrupted Gauntlet`; `region == 7512` → `The Gauntlet`. `9178 == 1 && 9177 == 0` → **`Prep`** (with time remaining from widget `637`); `9177 == 1` → **`Hunllef`** boss fight! Unsafe death for HCGIM (`onActorDeath` captures `Died in Corrupted Gauntlet (Hunllef)`). Completion via `"Your Corrupted Gauntlet completion count is: (\d+)"`. | **Verified** (confirm `VarbitID` names for `9177`/`9178` & widget `637` in Slice D) |
| **Barrows (live crypts)** | Regions `14131` (mounds), `14231` (crypts) (`regions.txt:298`) + `Varbits.BARROWS_KILLED_AHRIM..VERAC` (`457–462`, sum `0..6`) + `Varbits.BARROWS_NPCS_SLAIN` (`464` reward potential) | Live status `"Barrows · 5/6 brothers · 86%"`. Chest open (`InterfaceID.BARROWS_REWARD`, already wired at `GroupEventEngine.java:812`) resets brother varbits to `0`; existing `GroupEvent.Type.BARROWS` continues to handle the chest loot post! | **Verified** |
| **Moons of Peril** | Neypotzli regions (`5525..6039`) + Moon boss slain varbits (Blood, Blue, Eclipse `0/1` each → `0/3..3/3`) + Lunar Chest completion chat | Live status `"Moons of Peril · 2/3 moons (Blood Moon)"`. | **Unverified varbits/regions** (confirm exact `VarbitID` & Neypotzli region IDs in Slice D) |
| **Hallowed Sepulchre** | Floor regions `8797, 9051..10077` (`regions.txt:309`) mapped per floor `1–5` + Grand Hallowed Coffin completion chat | Live status `"Hallowed Sepulchre · Floor 5"`. | **Verified regions** (confirm per-floor region split in Slice D) |
| **TzHaar-Ket-Rak's Challenges** | Challenge arena (`region 9043` without `"Wave: N"` chat, entered via Ket-Rak challenge selection `1–6 Jads`) + completion chat `"Your TzHaar-Ket-Rak's Challenge ... completion count is: ..."` | Live status `"Ket-Rak's Challenge · 4 Jads"`. | **Unverified chat wording** (confirm in Slice A alongside TzHaar) |

---

## 3. Code Map (`main` @ `a545f38`, accounting for PRs #48 & #49)

Every file the build slices will read or touch, with exact line references:

| File | Current role (`file:line`) | Planned change across Slices A–D |
| --- | --- | --- |
| [`GroupIronmanPlugin.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupIronmanPlugin.java#L59-L120) | Plugin entrypoint, wires `GroupService`, `GroupEventEngine`, `GroupIronmanPanel` (plus `SidebarButton` in PR #49). | Register new `ActivityEngine` (or route client callbacks from `GroupEventEngine`) and wire live activity updates into `GroupIronmanPanel` and `GroupBoxOverlay`. |
| [`GroupCodec.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupCodec.java#L29-L45) & [`:330-485`](../../src/main/java/com/vinnyvangogh/groupironman/GroupCodec.java#L330-L485) | `Kind` enum (`BANK_PART`, `STORAGE`, `STATS`, `CARRIED`, `EXTRAS`, `EVENTS`), `decode`, `decodeEvents`. | Slice A: add `Kind.ACTIVITY("activity")` (`encodeActivity` / `decodeActivity`) and ensure `GroupEvent.Type` codes `as`, `aw`, `ac`, `ad` serialize via existing `encodeEvents` / `decodeEvents`. |
| [`GroupOutbox.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupOutbox.java#L19-L143) | Single choke-point for every outgoing `PartyService.send(...)` call. | Slice A: add `sendActivity(ActivityState, boolean shareEvents)` — returns `0` immediately unless `sync && shareEvents`. Keeps privacy audit in `GroupOutboxTest` airtight. |
| [`GroupService.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupService.java#L990-L1035) & [`:1330-1410`](../../src/main/java/com/vinnyvangogh/groupironman/GroupService.java#L1330-L1410) | `shareGroupEvent`, `resendAll` on party join, `notData` (`gset`, `roles`, `member`, `wh` from PR #48). | Slice A: accept incoming `Kind.ACTIVITY` only when `config.groupShareEvents()` is true and sender is not a stranger; store in `GroupRoster`; include current own `ActivityState` in `resendAll` when a member joins mid-run; clear member's `ActivityState` when they go offline. |
| [`GroupRoster.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupRoster.java#L39-L125) | Thread-safe `ConcurrentHashMap` store read by Swing & Overlay threads (`stats`, `heard`, `seen`, `feed`). | Slice A: add `Map<String, ActivityState> activities = new ConcurrentHashMap<>()`, `putActivity(member, state)` (removes entry when `!state.active`), `activities()` copy getter, and stale-expiry check (`online` / `MAX_ACTIVITY_AGE_MS`). |
| [`GroupEvent.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEvent.java#L28-L105) | `Category` & `Type` enums (`2`-letter wire codes). | Slice A: add `ACTIVITY_START("as", Category.ACTIVITIES, Need.TEXT, false)`, `ACTIVITY_WAVE("aw", Category.ACTIVITIES, Need.TEXT, false)`, `ACTIVITY_COMPLETE("ac", Category.ACTIVITIES, Need.TEXT, false)`, `ACTIVITY_DEATH("ad", Category.DEATHS, Need.TEXT, false)`. |
| [`Webhook.java`](../../src/main/java/com/vinnyvangogh/groupironman/Webhook.java#L17-L68) | Maps `Category.ACTIVITIES → Webhook.PROGRESS` and `Category.DEATHS → Webhook.DEATHS` (`ACTIVITIES` URL stays retired/hidden). | Zero new webhook slots needed! `as`/`aw`/`ac` automatically route to `Webhook.PROGRESS` (*Skillcape Seller* → fallback `MAIN`) and `ad` routes to `Webhook.DEATHS` (*Death's Office* → fallback `MAIN`), working seamlessly with `GroupWebhooks` (PR #48). |
| [`GroupIronmanConfig.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupIronmanConfig.java#L673-L679), [`:1159-1165`](../../src/main/java/com/vinnyvangogh/groupironman/GroupIronmanConfig.java#L1159-L1165), [`:1252-1259`](../../src/main/java/com/vinnyvangogh/groupironman/GroupIronmanConfig.java#L1252-L1259) | 8 fixed `@ConfigSection`s enforced by `ConfigLayoutTest` and pixel-width checked by `ConfigFitTest`. | Slice A: add `discordActivityStart` (`pos 34`), `discordActivityWave` (`35`), `discordActivityComplete` (`36`), `discordActivityDeath` (`37`) in `discordEventsSection`; `overlayActivityStart` (`pos 42`), `overlayActivityWave` (`43`), `overlayActivityComplete` (`44`), `overlayActivityDeath` (`45`) in `inGameSection`; `eventWaveEvery` (`pos 10`, `"Post every N waves"`) in `thresholdsSection`. |
| [`GroupSettingsSchema.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupSettingsSchema.java#L40-L185) & [`EventSettings.java`](../../src/main/java/com/vinnyvangogh/groupironman/EventSettings.java#L1-L160) | Group-synced leader/editor settings (`discord.<code >`, `overlay.<code >`, thresholds). | Slice A: add `eventWaveEvery` (`0..100`, default `10`) to `GroupSettingsSchema`; `discord.as/.aw/.ac/.ad` and `overlay.as/.aw/.ac/.ad` auto-register if iterated over `GroupEvent.Type` (verify and cover in `GroupSettingsSchemaTest`). |
| [`MemberPrefs.java`](../../src/main/java/com/vinnyvangogh/groupironman/MemberPrefs.java#L15-L75) & [`EventRouter.java`](../../src/main/java/com/vinnyvangogh/groupironman/EventRouter.java#L58-L95) | Per-member opt-out (`eventOptOutAll`, `noPost` comma list, `mute` banner list). | Already iterates `GroupEvent.Type` codes! Adding `as`, `aw`, `ac`, `ad` automatically exposes per-kind opt-out checkboxes in the `Settings` tab (`GroupSettingsTab.java`) and enforces them in `EventRouter`. |
| [`GroupIronmanPanel.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupIronmanPanel.java#L305-L345) & [`:535-580`](../../src/main/java/com/vinnyvangogh/groupironman/GroupIronmanPanel.java#L535-L580) | 3-column `tabBar`, `MemberCard` on `Group` tab, `PanelModel`. | Slice A: add `Activities` tab (`ActivitiesTab.java` pure view + `PanelModel` rows so `GroupIronmanPanel` methods stay `< 10` cyclomatic complexity) and 1-line live activity badge on `MemberCard`. |
| [`GroupBoxOverlay.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupBoxOverlay.java#L35-L61) | In-game `OverlayPanel` listing online members + recent events. | Slice A: append compact activity line (`LineComponent`) beneath/beside each member currently in an activity. |
| [`StatusBoard.java`](../../src/main/java/com/vinnyvangogh/groupironman/StatusBoard.java#L295-L345) & [`GroupEventEngine.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L339-L371) | Builds and `PATCH`es the single live Discord status message. | Slice A: pass coarse `Map<String, String> boardActivities` into `StatusBoard.lines(...)` so online members show `— in Fight Caves, wave 42` without triggering high-frequency Discord edits on minor `%` ticks. |
| [`EventWatchers.java`](../../src/main/java/com/vinnyvangogh/groupironman/EventWatchers.java#L193-L219) & [`EventText.java`](../../src/main/java/com/vinnyvangogh/groupironman/EventText.java#L1-L260) | `raidDetail` team formatter; plain-text/embed event copy. | Slice A/B: extract `TeamSummary` from `EventWatchers.raidDetail` (single source of truth, zero copy-paste) and add `EventText` formatting for `as`, `aw`, `ac`, `ad`. |

---

## 4. UI & Output Design (ASCII Mockups)

### 4.1 Sidebar Panel — `Activities` Tab Proposal

`GroupIronmanPanel.tabBar` (`GroupIronmanPanel.java:308`) uses `new GridLayout(0, 3, 2, 2)`. Adding **one `Activities` tab** (placed in row 2 beside `Find` and `Log`, with `Settings` below, or at position 4) keeps a compact header while giving one unified place to watch the group:

- **Filter pills at top:** `[All (3)]  [Bossing (2)]  [Minigames (1)]` — instant client-side filter without switching tabs.
- **Section 1 — `LIVE NOW`:** One rich dark card per active groupmate (including yourself even when `Share group events` is off, matching `ownStorage` in PR #47, with a subtle `(local only)` hint if sharing is off). Elapsed timer ticks locally on a 1s Swing timer (`startedAt` → `mm:ss` / `Xh Ym`) when the panel is visible, with zero extra Party messages.
- **Section 2 — `RECENT RUNS`:** Last 25 `ac` (completions) and `ad` (deaths/wipes) events from `GroupRoster.feed()`, with icon/badge, result, wave/time/KC, and relative timestamp (`14m ago`).

```text
+------------------------------------------+
|  GIM Utilities                  [Sync ●] |
+-------------+-------------+--------------+
|   Group     |    Banks    |   Storage    |
+-------------+-------------+--------------+
| [Activities]|    Find     |     Log      |
+-------------+-------------+--------------+
|  Settings   |             |              |
+-------------+-------------+--------------+
|  [ All (3) ]  [ Bossing ]  [ Minigames ] |
+------------------------------------------+
|  LIVE NOW (3)                            |
|  +------------------------------------+  |
|  | ● Alex                      W302   |  |
|  | Fortis Colosseum · Wave 9/12       |  |
|  | 14,200 glory · 19m 42s             |  |
|  | Mods: Mantimayhem II, Myopia I,    |  |
|  |       Relentless I                 |  |
|  +------------------------------------+  |
|  | ● Sam                       W422   |  |
|  | Tempoross · Solo                   |  |
|  | Energy 38% · Storm 74% · 6 permits |  |
|  | Vulnerable · 04m 15s               |  |
|  +------------------------------------+  |
|  | ● Pat                       W329   |  |
|  | Tombs of Amascut · 350 (Expert)    |  |
|  | Duo with Jordan · Akkha (2/4)      |  |
|  | 18m 05s · 0 deaths                 |  |
|  +------------------------------------+  |
|                                          |
|  RECENT RUNS                             |
|  +------------------------------------+  |
|  | ✓ Sam — Tempoross (solo)     9m ago|  |
|  |   Subdued in 5:48 · 8 permits · #42|  |
|  +------------------------------------+  |
|  | ✗ Alex — The Inferno        41m ago|  |
|  |   Died on wave 63/69 · 1h 44m      |  |
|  +------------------------------------+  |
|  | ✓ Pat — Corrupted Gauntlet   2h ago|  |
|  |   Completed in 9:12 · KC 87        |  |
|  +------------------------------------+  |
+------------------------------------------+
```

**Empty / unshared states:**
- When `groupShareEvents` is **off**: shows the existing one-click *"Turn on sharing"* prompt card (`SharingPrompt.java`, PR #45) at the top of `Activities` while still showing **your own** live activity locally below it labeled `You (only on your screen)`.
- When sharing is **on** and nobody is in an activity: `LIVE NOW` shows one muted line: *"No group members in a boss or minigame right now."*

---

### 4.2 `Group` Tab — Member Card Status Line

In [`GroupIronmanPanel.java:536-575`](../../src/main/java/com/vinnyvangogh/groupironman/GroupIronmanPanel.java#L536-L575), each `MemberCard` shows the member's name, online dot + world/region subtitle, and stat bars. When the member has an active `ActivityState`, one highlighted accent line (`ColorScheme.PROGRESS_COMPLETE_COLOR` / soft gold-amber) renders directly beneath the world/location subtitle:

```text
+------------------------------------------+
| ● Alex                        World 302  |
|   The Fight Caves                        |
|   ▸ Fight Caves — wave 42/63, 38m        |
|   [HP 84/99] [Pray 52/77]  Total 1,842   |
+------------------------------------------+
| ● Sam                         World 422  |
|   Tempoross Cove                         |
|   ▸ Tempoross (solo) — 38% energy, 6 perm|
|   [HP 99/99] [Pray 77/77]  Total 2,015   |
+------------------------------------------+
```

---

### 4.3 In-Game `GroupBoxOverlay`

In [`GroupBoxOverlay.java:43-59`](../../src/main/java/com/vinnyvangogh/groupironman/GroupBoxOverlay.java#L43-L59), `render` iterates `model.members` and `model.events` (computing nothing per frame, per `AGENTS.md` *Performance*: all strings are pre-formatted on state change in `GroupService` / `ActivityEngine`, never on the Swing/Canvas render path!). Members in an active activity get one indented subtle sub-line right below their online line:

```text
+-------------------------------------+
| GIM Utilities                       |
| ● Alex                World 302     |
|   Fight Caves · W42/63 (38m)        |
| ● Sam                 World 422     |
|   Tempoross solo · 38% · 6p         |
| ● Pat                 World 329     |
|   ToA 350 (duo) · Akkha             |
| ○ Jordan              2h ago        |
| ----------------------------------- |
| Sam: Tempoross 5:48 (solo, KC 42)   |
| Alex: Colosseum wave 10             |
+-------------------------------------+
```

---

### 4.4 Discord Milestones (`Progress` & `Deaths` Webhooks)

Four new `GroupEvent.Type` values give each milestone kind **its own player toggle** (`Discord: what to post` + `In-game` banners), **its own leader group setting** (`GroupSettingsSchema`: `discord.as`, `discord.aw`, `discord.ac`, `discord.ad` + `overlay.*`), and **its own per-member opt-out** (`MemberPrefs` in `Settings` tab):

| Event type | Code | Config key (`Discord` / `In-game`) | Config label (fits `ConfigFitTest`) | Webhook target | Trigger rule |
| --- | --- | --- | --- | --- | --- |
| `ACTIVITY_START` | `as` | `discordActivityStart` / `overlayActivityStart` | `Activity started` / `Banner: Activity started` | `Webhook.PROGRESS` (`0x9B59B6`) | Entering wave 1 / raid start (`RAID_STATE > 0`, ToB room 1, ToA first path/boss, Gauntlet prep start). Skips high-churn 3-minute skilling rounds (Wintertodt/Tempoross/GotR/PC/Barrows) unless explicitly raid/wave ladder, **or** debounced to first round of a session (see §5.4 anti-spam rule!). |
| `ACTIVITY_WAVE` | `aw` | `discordActivityWave` / `overlayActivityWave` | `Activity wave milestone` / `Banner: Activity wave` | `Webhook.PROGRESS` (`0x9B59B6`) | Wave `N` where `eventWaveEvery > 0 && N % eventWaveEvery == 0` (e.g. 10, 20, 30, 40, 50, 60) **plus** marquee final boss entry waves (`Jad` 63, `Triple Jads` 68, `Zuk` 69, `Sol Heredit` 12, `Penance Queen` 10, `Great Olm`, `Verzik`, `Wardens`). |
| `ACTIVITY_COMPLETE` | `ac` | `discordActivityComplete` / `overlayActivityComplete` | `Activity completed` / `Banner: Activity completed` | `Webhook.PROGRESS` (`0x9B59B6`) | Boss/raid/minigame clear: cape/completion KC, duration, solo/team summary, modifiers/raid level/permits. |
| `ACTIVITY_DEATH` | `ad` | `discordActivityDeath` / `overlayActivityDeath` | `Activity death` / `Banner: Activity death` | `Webhook.DEATHS` (`0xC0392B`) | Death or wipe inside a tracked activity, carrying wave/room, active modifiers/raid level, elapsed time, and killer if available. |

**Discord embed mockups (`DiscordStyle.EMBED`):**

```text
[Skillcape Seller — Progress channel]  ┃ 0x9B59B6 (purple)
┌──────────────────────────────────────────────────────────┐
│ Alex reached wave 60/69 in The Inferno                   │
│ 1h 32m in · solo                                         │
└──────────────────────────────────────────────────────────┘

[Skillcape Seller — Progress channel]  ┃ 0x9B59B6 (purple)
┌──────────────────────────────────────────────────────────┐
│ Alex completed Fortis Colosseum!                         │
│ Sol Heredit KC 1 · 29:14 (new personal best)             │
│ 24,800 glory · Mantimayhem II, Myopia III, Relentless II │
└──────────────────────────────────────────────────────────┘

[Death's Office — Deaths channel]      ┃ 0xC0392B (red)
┌──────────────────────────────────────────────────────────┐
│ Alex died in Fortis Colosseum (wave 11/12)               │
│ 24m 18s in · Doom II, Mantimayhem II, Blasphemy I        │
│ World 302                                                │
└──────────────────────────────────────────────────────────┘
```

*(Plain-text mode `DiscordStyle.PLAIN` renders single-line equivalents, e.g. `**Alex** died in Fortis Colosseum on wave 11/12 (24m 18s in; Doom II, Mantimayhem II, Blasphemy I).`)*

---

### 4.5 Discord `StatusBoard` (Live Board)

In [`StatusBoard.java:295-330`](../../src/main/java/com/vinnyvangogh/groupironman/StatusBoard.java#L295-L330), each online plugin member's line appends a coarse activity clause when an active `ActivityState` is present:

```text
**Group status**
🟢 **Alex** online (world 302) — in Fight Caves, wave 42
🟢 **Sam** online (world 422) — in Tempoross (solo)
🟢 **Pat** online (world 329) — in Tombs of Amascut (350, duo), Akkha
⚫ **Jordan** offline · last seen 2h ago
```

**Discord rate-limit rule for `StatusBoard`:**
`StatusBoard` edits one message via `PATCH` (`StatusBoard.java:90-140`). Live energy/warmth percentages change every few ticks in Wintertodt/Tempoross; putting `"64% energy"` into `StatusBoard.lines(...)` would trigger needless Discord `PATCH` churn every cooldown window. Therefore **`StatusBoard` uses `ActivityState.boardSummary()`** — which includes **activity name, mode (`solo`/`duo`/`mass`/`350`), and coarse stage/wave (`wave 42`, `Akkha`, `Hunllef`)**, omitting fast-changing `%` / point counters (those live in the panel, card, and overlay!). Furthermore, wave numbers on the board only trigger a `StatusBoard` dirty-edit on start, every `5` waves (or boss wave), room change, and exit.

---

## 5. Sync Wire Shape, `gim-test-2` Compatibility & Privacy Gates

### 5.1 Live state message (`"k":"activity"`)

Sent inside [`VvgGroupIronmanMessage`](../../src/main/java/com/vinnyvangogh/groupironman/VvgGroupIronmanMessage.java) (`PartyService`), small (`~160–280` bytes, hard cap `<= 512` chars):

```json
{
  "v": 1,
  "k": "activity",
  "at": 1763100000000,
  "id": "colosseum",
  "cat": "boss",
  "name": "Fortis Colosseum",
  "mode": "solo",
  "phase": "Wave 9/12",
  "board": "in Fortis Colosseum, wave 9",
  "wave": 9,
  "max": 12,
  "startedAt": 1763098818000,
  "elapsedMs": 1182000,
  "stats": "14,200 glory",
  "mods": "Mantimayhem II, Myopia I, Relentless I",
  "team": ""
}
```

When a player leaves an activity, logs out, or turns off `Share group events`, their client sends one clear frame (if `Share group events` was on):

```json
{
  "v": 1,
  "k": "activity",
  "at": 1763100600000,
  "id": ""
}
```

**Field bounds & validation (`GroupCodec.decodeActivity`):**
- `id`: ASCII slug `<= 24` chars (`""` = clear; e.g. `fight_caves`, `inferno`, `colosseum`, `cox`, `tob`, `toa`, `wintertodt`, `tempoross`, `gotr`, `zalcano`, `ba`, `pest_control`, `gauntlet`, `corrupted_gauntlet`, `barrows`, `moons`, `sepulchre`, `ket_rak`).
- `cat`: `"boss"` or `"minigame"` (drives the `All | Bossing | Minigames` filter chip in `Activities` tab without receivers needing to know future activity slugs — forward compatibility across Slices A→D!).
- `name` (`<= 40` chars), `mode` (`<= 24` chars), `phase` (`<= 40` chars), `board` (`<= 64` chars), `stats` (`<= 64` chars), `mods` (`<= 120` chars), `team` (`<= 64` chars): control characters stripped, truncated safely on decode.
- `wave`, `max`: clamped `0..1000`; `elapsedMs`: clamped `0..86_400_000L`.
- **Forward compatibility between Slices A, B, C, and D:** Because the wire payload carries display-ready `name`, `cat`, `mode`, `phase`, `board`, `stats`, and `mods`, a Slice A client **already renders** Slice B, C, and D activities from newer teammates with zero codec changes in Slices B–D!

### 5.2 Milestone event message (`"k":"events"`)

Uses the **existing** `Kind.EVENTS` (`"k":"events"`) envelope (`GroupCodec.java:290-380`) with four new 2-letter `GroupEvent.Type` codes (`as`, `aw`, `ac`, `ad`):
- `tx` (`text`): activity display name (`"The Inferno"`, `"Fortis Colosseum"`, `"Tempoross (solo)"`).
- `n` (`number`): wave number on `aw`/`ad`, or completion KC on `ac` (`0` when N/A).
- `dt` (`detail`): compact context string (`"wave 63/69 · 1h 44m"`, `"29:14 · 24,800 glory · Mantimayhem II, Myopia III"`, `"5:48 · 8 permits"`).
- `loc` (`location`) & `w` (`world`): populated on `ad` matching `DEATH` (`GroupEventEngine.java:756-761`).

### 5.3 Proof of backward compatibility with `gim-test-2` (`f3a142a` / `859cc70`)

Every wire path has been verified line-by-line against `gim-test-2`:

1. **Live `"k":"activity"` packets received by a `gim-test-2` client:**
   - `GroupService` passes incoming `VvgGroupIronmanMessage.getData()` to `GroupCodec.decode(json)` (`GroupCodec.java:415-443`), which switches on `Kind.byWire(k)` and returns **`null`** for `"activity"`.
   - `GroupService` then passes the raw JSON to `notData(sender, json)` (`GroupService.java:1354-1364`), which checks `GovernanceCodec.kind(json)` (`gset`, `roles`, `member`), finds none, logs a single `log.debug(...)` line, and drops the packet cleanly with **zero exceptions, zero state corruption, and zero UI noise**.
2. **New `as` / `aw` / `ac` / `ad` events inside `"k":"events"` received by a `gim-test-2` client:**
   - `GroupCodec.eventFrom` (`GroupCodec.java:335`) looks up `GroupEvent.Type.byCode(t)` and returns **`null`** for unknown 2-letter codes (`GroupEvent.java:48-51` explicitly designed 2-letter codes for this!).
   - `GroupCodec.decodeEvents` (`GroupCodec.java:476-477`) skips `null` entries individually while keeping every known event (`d`, `c`, `pt`, `dt`, `lv`, etc.) in the same batch intact.
3. **Updated `GroupSettings` (`"k":"gset"`) signed by a newer leader and received by a `gim-test-2` client:**
   - `GovernanceCodec.settings` (`GovernanceCodec.java:195`) allows up to `MAX_VALUES = 400` keys (currently ~86; adding 9 keys `eventWaveEvery` + 8 `discord/overlay` flags brings it to ~95) and preserves the raw `Map<String, String> values` untouched, so **ECDSA P-256 signature verification (`GroupSettings.verify`) still succeeds** on `gim-test-2` clients while `GroupSettingsSchema.Resolved` on `gim-test-2` simply reads the keys it knows.
4. **Updated `MemberPrefs` (`"k":"member"`) opt-outs (`"np":"as,aw"`) received by a `gim-test-2` client:**
   - `MemberPrefs.parse` (`MemberPrefs.java:48-64`) skips unknown comma-separated event codes without error.

### 5.4 Respecting `Share group events`, `GroupSettings`, and `MemberPrefs` Opt-Outs

| Gate / Setting | Live Party `"k":"activity"` | Party Milestone `"k":"events"` (`as`,`aw`,`ac`,`ad`) | Own Discord Webhook Posts (`Progress` / `Deaths`) | Own `StatusBoard` Activity Clause | Own Local `Activities` Tab |
| --- | --- | --- | --- | --- | --- |
| **`groupSync = false`** | Blocked in `GroupOutbox` (`0` sent) & `GroupService` (incoming ignored) | Blocked (`0` sent / incoming ignored) | Allowed if `discordEnabled = true` (player's own webhook) | Own self-activity shown if `LIVE_BOARD` on | Shows own activity locally |
| **`groupShareEvents = false`** *(default `false`, in `SharingPrompt`)* | **Blocked:** `GroupOutbox.sendActivity` sends nothing; incoming `"k":"activity"` from others ignored (`GroupService`) | **Blocked:** `GroupEventEngine.own` (`:508`) sends nothing; incoming `"k":"events"` ignored | Allowed if `discordEnabled = true` & event toggle on (posts own events to own webhook) | Omit activity clause for others; own activity shown only if `groupShareEvents = true` so opting out of sharing events keeps activity off shared boards too | Shows own activity locally (`"only on your screen"`) + `SharingPrompt` banner |
| **Leader `GroupSettings` (`discord.as/aw/ac/ad = false`)** | Still shared live to group panels/overlays | Still shared to group Log/banners | **Blocked** in `EventRouter` (`settings.discord` omits the type) | Unaffected (live board is presence) | Shown |
| **Member `MemberPrefs.eventOptOutAll = true`** (*"Don't post my events"*) | Still visible in-game to party if `groupShareEvents = true` (or player can untick `groupShareEvents` to hide live activity everywhere) | Marked opted-out | **Blocked:** `EventSettings` / `MemberPrefs` overrides leader's `GroupSettings` so this member's `as`/`aw`/`ac`/`ad` **never** post to Discord (`EventRouterTest`) | **Omitted:** if a member set `eventOptOutAll = true`, `StatusBoard` also omits their `— in <activity>` suffix so their bossing isn't broadcast to Discord by another member's `StatusBoard`! | Shown in-game only |
| **Member `MemberPrefs.noPost` contains `aw` (or `as`/`ac`/`ad`)** | Shared in-game | Shared in-game | **Blocked** for that specific kind (`aw` suppressed even if leader enabled `discord.aw = true`) | Shown (unless `as` & `aw` opted out; simplest rule: `eventOptOutAll` or `noPost.contains("as")` hides Discord board suffix) | Shown |
| **Viewer `MemberPrefs.mute` (`overlayMuteTypes`)** | Group box still shows status line | Logged in `Log` tab | Unaffected | Unaffected | Toast banner suppressed locally |

**Session anti-spam rule for `ACTIVITY_START` (`as`):**
Skilling bosses and quick minigames (Wintertodt ~5m, Tempoross ~5m, Pest Control ~3m, Barrows ~4m, GotR ~9m) repeat back-to-back. Firing `ACTIVITY_START` every 4 minutes would spam Discord and banners even for groups that want *"Alex started The Inferno"* or *"Pat started Tombs of Amascut"*. Therefore:
- **Wave ladders & raids** (Fight Caves, Inferno, Ket-Rak, Colosseum, CoX, ToB, ToA, Gauntlet/CG, BA, Sepulchre) emit `ACTIVITY_START` at run start.
- **Repeatable round bosses/minigames** (Wintertodt, Tempoross, GotR, Zalcano, Pest Control, Barrows, Moons) update **live state** (`"k":"activity"`) every round, emit `ACTIVITY_COMPLETE` (`ac`) on round completion (or every `N` completions if desired), and emit `ACTIVITY_START` **at most once per continuous session** in that arena (resetting after leaving the arena/lobby for `> 10` minutes).

---

## 6. Edge Cases, Threat Model & Party Relay Budget

### 6.1 Party relay rate & size budget
- **Throttle:** `ActivityEngine` sends `"k":"activity"` immediately on **enter**, **wave/room/phase transition**, and **exit**, and throttles continuous meter updates (Wintertodt/Tempoross/GotR energy & points) to **at most once every 25 game ticks (`15.0s`)** and only when the formatted display string actually changed.
- **Worst-case group traffic:** 5 members all inside Tempoross simultaneously = `5 × (220 bytes / 15s) ≈ 73 bytes/sec` total across the party — negligible on RuneLite's `WSClient` Party relay.
- **Stale live state on crash / Alt-F4:** If a member's client crashes mid-Inferno without sending `"id":""`, receivers clear or dim any `ActivityState` when `Presence.online(lastHeard, now)` goes false or when `now - state.at > 5 * 60_000L` (5 minutes without a heartbeat; active clients send a keepalive `"k":"activity"` every `60s` while inside an activity).

### 6.2 Threat model & failure modes

| Vector / Edge case | Impact if unhandled | Mitigation required in Slices A–D |
| --- | --- | --- |
| **Malformed or oversized `"k":"activity"` over Party** | UI freeze, Swing HTML injection (`<html>` in Swing `JLabel`!), or OOM | Never pass peer strings into `<html>...</html>` Swing labels; strip control chars, clamp every string field (`<= 120` chars) and integer (`0..1000`) in `GroupCodec.decodeActivity`; ignore payloads `> 2,048` chars; reject senders in `GroupRoster.strangers`. |
| **Instance coordinate bug** (`getWorldLocation().getRegionID()`) | Fight Caves/Inferno/ToB/ToA/Tempoross/Gauntlet never trigger because dynamic instance region ID is `16xxx+` | Always resolve template region via `WorldPoint.fromLocalInstance(client, me.getLocalLocation())` (`GroupEventEngine.java:721`). |
| **Colosseum *Doom* instant-kill or safe minigame wipe** | Player dies on wave 11 to Doom stacks without `ActorDeath` firing, losing the death milestone | Detect Colosseum/Fight Caves/Inferno defeat from **either** `onActorDeath` **or** `"You have been defeated!"` chat (deduplicated within 5 ticks by run ID so one death never fires twice). |
| **Double death post (`DEATH` + `ACTIVITY_DEATH`)** | One raid/Colosseum death sends two red embeds to `Death's Office` | In `GroupEventEngine.onActorDeath`, if `activityEngine.onPlayerDeath(...)` emits a wanted `ACTIVITY_DEATH` (`ad`), suppress the generic `DEATH` (`dt`) for that tick; still run `watchers.died(...)` (`DEATH_VALUE`, `GRAVESTONE_EXPIRING`) and `HC_LIFE_LOST` unchanged! |
| **CoX safe death on HCGIM** | Plugin falsely marks HCGIM life lost inside CoX | Existing `HC_LIFE_LOST` logic stays authoritative; `ACTIVITY_DEATH` never sets `LIFE_LOST` on its own. |
| **1-tick `solo` flicker on region load in Wintertodt/Tempoross** | On tick 1 after teleporting/boarding, other players haven't rendered yet, briefly mislabeling a mass world as `solo` | Wait 3 game ticks after arena entry before publishing the `solo` / `mass` mode tag (or upgrade immediately if `players > 1` on tick 1). |
| **Non-GIM player privacy leak** | Stranger RSNs sent over Party or posted to Discord | `TeamSummary` checks `ClanID.GROUP_IRONMAN` roster (`GroupMembers.key`); anyone not in the GIM clan is counted (`+N others`) and never serialized into any field. |

---

## 7. Adversarial Test Plan (Tests Written Before Code in Every Slice)

Before writing implementation code in each slice, add JUnit 4 tests matching the repo's pure-model test suite (and keep every method at **cyclomatic complexity `<= 10`** for `code_rot`):

1. **`ActivityCodecCompatibilityTest` (Slice A):**
   - Round-trips valid `"k":"activity"` and clear (`"id":""`) frames; strips control characters, truncates oversized strings, clamps negative/huge wave & elapsed numbers, returns `null` on missing/unknown fields or malformed JSON.
   - Verifies a simulated `gim-test-2` decoder ignores `"k":"activity"`, skips `as`/`aw`/`ac`/`ad` inside mixed `"k":"events"` batches without losing adjacent `d`/`lv` events, and verifies ECDSA signature on a `GroupSettings` payload containing `eventWaveEvery` and `discord.as..ad`.
2. **`ActivityPrivacyAndOptOutTest` (Slice A):**
   - `GroupOutboxTest`: asserts `0` packets leave `PartyService` when `groupSync = false` **or** `groupShareEvents = false`, even during wave transitions.
   - `EventRouterTest` & `MemberPrefsTest`: asserts leader enabling `discord.aw = true` is **overridden** when a member ticks `eventOptOutAll` or unticks `Activity wave milestone` in their own `Settings` tab, and asserts `StatusBoard.lines` omits `— in <activity>` for any member whose `MemberPrefs` opts out of Discord activity posts.
   - `TeamSummaryTest`: feeds mixed lists of local player, GIM clanmates (with space vs NBSP ` ` RSN normalization via `GroupMembers.key`), and 40 random players; asserts **zero** non-roster RSNs appear in any output string across `solo`, `duo`, `4-man`, and `mass` modes.
3. **`TzHaarAndColosseumTrackerTest` (Slice A):**
   - Fight Caves 1→63 clear, Inferno 1→69 clear, pause/logout mid-wave-42 and resume on wave 43 preserving elapsed time, voluntary cave exit (clears live state, **zero** `ad` death event), `ActorDeath` on wave 58 (emits `ad`, suppresses duplicate `dt`, runs once even when `"You have been defeated!"` follows 1 tick later), Colosseum *Doom* defeat via `"You have been defeated!"` **without** `ActorDeath` (still emits `ad` with wave + modifiers!), early Colosseum loot claim on wave 7 (clears live state, **zero** fake Sol Heredit KC), and `eventWaveEvery = 10` firing on waves `10, 20, 30, 40, 50, 60, 63` (and `0` firing none).
4. **`ConfigFitTest` & `ConfigLayoutTest` & `DigestRoutingTest` (Slice A):**
   - All existing invariants pass unchanged: 8 sections in exact order, every new `@ConfigItem` label fits RuneLite's font width budget, `as`/`aw`/`ac` route to `Webhook.PROGRESS` & `Category.ACTIVITIES` digest, and `ad` routes to `Webhook.DEATHS` & `Category.DEATHS` digest.
5. **Slice-specific state-machine unit tests (Slices B, C, D):**
   - **Slice B (`SkillingBossTrackerTest`):** 3-tick `solo` vs `mass` debounce on arena entry; `15s` meter throttle + immediate transition flush; once-per-session `ACTIVITY_START` anti-spam across back-to-back Wintertodt/Tempoross rounds; Tempoross 100% storm wipe (clears round without fake player death).
   - **Slice C (`RaidActivityTrackerTest`):** CoX `IN_RAID` + `RAID_STATE` floor progression + Olm + CM tag + safe HCGIM death; ToB room mapping across all 7 regions + orb varbits `6442–6446` party size & `30`-dead wipe detection; ToA raid level tiering (`50` Entry, `200` Normal, `400` Expert) + path→boss→Nexus room transitions + orb varbits `14346–14353`.
   - **Slice D (`MinigameActivityTrackerTest`):** BA role & wave `1–10` + Queen completion; Pest Control lander tier + `4 → 0` portals + Knight HP; Gauntlet vs Corrupted Gauntlet (`7512` vs `7768`) + Prep (`9178=1, 9177=0`) → Hunllef (`9177=1`) transition + death phase tagging; Barrows `0/6..6/6` crypt live state clearing cleanly on `BARROWS_REWARD` chest without double-posting `BARROWS`.

---

## 8. Sliced Build Plan (Ready-to-Sign Tasks)

Each slice below is an independent, shippable StayPoint task (`Push-Policy: pr` to `main`, never merge without Board approval). Slice A builds the shared engine, wire sync, settings, and all 5 display surfaces end-to-end with TzHaar & Fortis Colosseum; Slices B, C, and D plug pure activity detectors into Slice A's `ActivityTracker` interface with zero wire or UI rework.

```
Slice A (Engine + Wire + 5 UI/Discord surfaces + Fight Caves, Inferno, Ket-Rak, Colosseum)
   ├──▶ Slice B (Skilling bosses: Wintertodt, Tempoross, GotR, Zalcano + TeamSummary)
   ├──▶ Slice C (Raids: CoX + CM, ToB Entry/Normal/HM, ToA raid level + paths/rooms)
   └──▶ Slice D (Minigames: BA wave+role, Pest Control, Gauntlet/CG, Barrows, Moons, Sepulchre)
```

*(Note: Once Slice A merges, Slices B, C, and D touch disjoint detector classes and only register entries in `ActivityRegistry`, so they can run sequentially or in parallel.)*

---

### Slice A — Title & Brief
**Title:** `GIM Utilities: live activities Slice A — engine, wire sync, 5 surfaces, TzHaar & Fortis Colosseum`

```markdown
Goal: Ship the end-to-end live activity & milestone system from `docs/plans/activities.md` and wire up TzHaar Fight Caves (63 waves), The Inferno (69 waves), TzHaar-Ket-Rak's Challenges, and Fortis Colosseum (waves 1–12, glory, active modifiers, Doom-safe death detection).

Read `docs/plans/activities.md` and `AGENTS.md` in full before writing code.

Scope:
1. Wire & privacy (`GroupCodec`, `GroupOutbox`, `GroupService`, `GroupRoster`, `GroupEvent`):
   - Add `GroupCodec.Kind.ACTIVITY` (`"k":"activity"`, clear frame `"id":""`, field clamping, `<= 512` chars) and `GroupEvent.Type` codes `ACTIVITY_START("as")`, `ACTIVITY_WAVE("aw")`, `ACTIVITY_COMPLETE("ac")`, `ACTIVITY_DEATH("ad")`.
   - Gate all outgoing/incoming Party `"k":"activity"` and `"k":"events"` traffic on `config.groupShareEvents()`; resend own live activity on party join; expire stale peer activities on logout or after 5m silence (60s keepalive while active).
2. Config, GroupSettings & Opt-outs (`GroupIronmanConfig`, `GroupSettingsSchema`, `EventSettings`, `MemberPrefs`, `EventRouter`, `EventText`):
   - Add `discordActivityStart/Wave/Complete/Death` (`pos 34..37` in `discordEventsSection`), `overlayActivityStart/Wave/Complete/Death` (`pos 42..45` in `inGameSection`), and `eventWaveEvery` (`pos 10`, name `"Post every N waves"`, default `10` in `thresholdsSection`).
   - Route `as`/`aw`/`ac` (`Category.ACTIVITIES`) to `Webhook.PROGRESS` and `ad` (`Category.DEATHS`) to `Webhook.DEATHS`. Suppress duplicate plain `DEATH` (`dt`) on the same tick when `ad` fires for an `ActorDeath`.
   - Sync `eventWaveEvery` + `discord/overlay` flags in `GroupSettingsSchema`, honor `MemberPrefs` (`eventOptOutAll`, `noPost`, `mute`), and omit a member's live board activity suffix when they opted out of Discord activity posts.
3. All 5 UI & Discord surfaces:
   - New `Activities` tab (`ActivitiesTab.java` + `PanelModel`) in `GroupIronmanPanel` with `All | Bossing | Minigames` filter pills, `LIVE NOW` cards (1s local elapsed timer, local-only own preview + `SharingPrompt` when sharing is off), and `RECENT RUNS` list.
   - `Group` tab `MemberCard` 1-line live status (`Fight Caves — wave 42/63, 38m`).
   - `GroupBoxOverlay` pre-formatted sub-line per active member (zero per-frame string allocation).
   - `StatusBoard` coarse suffix (`— in Fight Caves, wave 42`), rate-limited to start/exit/every-5-waves so Discord `PATCH` is never spammed.
4. Detectors (`ActivityTracker` SPI + `TzHaarTracker` + `ColosseumTracker`):
   - Unwrap template region IDs via `WorldPoint.fromLocalInstance` (`9551` Fight Caves, `9043` Inferno / Ket-Rak, `7216` Colosseum Arena, `7316` Lobby).
   - Verify exact `net.runelite.api.gameval.VarbitID` / `VarPlayerID` names in `1.13.5` for Colosseum modifiers & glory (no magic ints when `gameval` constants exist; zero reflection; zero in-arena combat helpers).
   - Handle pause/logout wave resume via `RSProfileConfiguration`, voluntary cave exit vs defeat (`ActorDeath` + `"You have been defeated!"` deduplicated for Colosseum Doom), and early Colosseum loot claim vs Wave 12 Sol Heredit clear.
5. Write adversarial unit tests first (`ActivityCodecCompatibilityTest`, `ActivityPrivacyAndOptOutTest`, `TzHaarAndColosseumTrackerTest`, plus `ConfigFitTest`, `ConfigLayoutTest`, `DigestRoutingTest`, `./gradlew test`), and keep all methods at cyclomatic complexity `<= 10`.

## Spec interview
- Q: Which activities? A (Board 2026-11-13): Fight Caves + Inferno, Fortis Colosseum, raids progress, other wave/round minigames; 'very granular, e.g. whether they're soloing or not in Wintertodt/Tempoross'.
- Q: Where should it show? A (Board 2026-11-13): Group tab card, in-game group box overlay, Discord milestones, live board; and 'maybe a new bossing and minigames tab, one for each, or something like that'.
```

---

### Slice B — Title & Brief
**Title:** `GIM Utilities: live activities Slice B — granular skilling bosses (Wintertodt, Tempoross, GotR, Zalcano)`

```markdown
Goal: Add granular live progress, solo-vs-group-vs-mass detection, and round milestones for Wintertodt, Tempoross, Guardians of the Rift, and Zalcano on top of Slice A's `ActivityTracker` engine (`docs/plans/activities.md` §2.2 & §2.4-B1).

Depends on: Slice A merged to `main`. Read `docs/plans/activities.md` and `AGENTS.md` in full before writing code.

Scope:
1. Extract shared `TeamSummary` from `EventWatchers.raidDetail` (`EventWatchers.java:193-219`) — do not copy-paste it (`code_rot` duplication check). Classify arena player counts into `solo` (`1`), `duo` / `N-man` (`2–5`), and `mass (N players)` (`6+`), naming ONLY `ClanID.GROUP_IRONMAN` members and counting everyone else (`+K others`). Debounce `solo` for 3 ticks after arena entry so region-load pop-in never flickers `solo` on a mass world.
2. Detectors (using `net.runelite.api.gameval.VarbitID` and `InterfaceID` constants verified against `1.13.5`, zero magic numbers when `gameval` exists):
   - **Wintertodt** (`region 6462`): `WINTERTODT_TIMER` (`7980`) between-round countdown vs active round, HUD widget `396` energy %, warmth %, points, subdue completion chat, unsafe death.
   - **Tempoross** (instanced `region 12076` via `WorldPoint.fromLocalInstance`): HUD widget `437` energy %, essence %, storm intensity %, reward permits, phase, subdue time + KC completion, 100% storm escape reset (not a player death).
   - **Guardians of the Rift** (`region 14484`): HUD widget `746` Great Guardian power %, portal/rift state, elemental & catalytic energy, rift closed completion, Guardian defeat reset.
   - **Zalcano** (`region 12126`, pit bounds): boss HP/shield HUD, team count inside barrier, KC/MVP completion, unsafe death.
3. Anti-spam & throttle: throttle continuous `%`/point Party updates to at most once per 25 ticks (`15s`), omit volatile `%` from `StatusBoard` text, and limit `ACTIVITY_START` (`as`) on repeatable skilling bosses to once per continuous arena session (`10m` cooldown after leaving).
4. Write `TeamSummaryTest` and `SkillingBossTrackerTest` first, run `./gradlew test`, and keep cyclomatic complexity `<= 10`.

## Spec interview
- Q: Which activities? A (Board 2026-11-13): Fight Caves + Inferno, Fortis Colosseum, raids progress, other wave/round minigames; 'very granular, e.g. whether they're soloing or not in Wintertodt/Tempoross'.
- Q: Where should it show? A (Board 2026-11-13): Group tab card, in-game group box overlay, Discord milestones, live board; and 'maybe a new bossing and minigames tab, one for each, or something like that'.
```

---

### Slice C — Title & Brief
**Title:** `GIM Utilities: live activities Slice C — raids progress (CoX, ToB, ToA rooms, party size & deaths)`

```markdown
Goal: Add live raid progress, mode/invocation level, party size (solo vs team), room/floor transitions, raid deaths, and completions for Chambers of Xeric (Normal & CM), Theatre of Blood (Entry, Normal, Hard Mode), and Tombs of Amascut (`docs/plans/activities.md` §1.1, §1.2 & §2.4-A3).

Depends on: Slice A merged to `main` (and reuse `TeamSummary` if Slice B merged first, otherwise extract from `EventWatchers.raidDetail`). Read `docs/plans/activities.md` and `AGENTS.md` in full before writing code.

Scope:
1. Strict compliance guardrails:
   - Zero combat/mechanic overlays or timers; zero reflection (`Class.forName`) into `RaidsPlugin` or Hub plugins; zero unentered-room layout pre-scouting in CoX; non-GIM party members counted only (`+N others`), never named.
2. Detectors (verified `VarbitID`, `VarPlayerID`, `InterfaceID` constants in `1.13.5`, template regions via `WorldPoint.fromLocalInstance`):
   - **Chambers of Xeric** (`regions 12889, 13136..13401`): `IN_RAID` (`5432`), `RAID_STATE` (`5425`), `RAID_PARTY_SIZE` (`5424`), CM indicator, `TOTAL_POINTS` (`5431`) & `PERSONAL_POINTS` (`5422`), current floor (`Upper`/`Middle`/`Lower`/`Great Olm`) + currently engaged boss name from `HpbarHud`, safe raid deaths (never trigger fake HCGIM life lost), completion KC + time.
   - **Theatre of Blood** (`Varbits.THEATRE_OF_BLOOD` `6440`, orb varbits `6442–6446`): party size (`1–5`), Entry/Normal/Hard Mode, exact room from unwrapped template region (`12613` Maiden, `13125` Bloat, `13122` Nylocas, `13123`/`13379` Sotetseg, `12612` Xarpus, `12611` Verzik, `12867` Vault), room deaths + `30`-all-dead party wipe, completion KC + time.
   - **Tombs of Amascut** (`TOA_RAID_LEVEL` `14380` → Entry `<150` / Normal `150–299` / Expert `300+`, orb varbits `14346–14353` party size `1–8`): exact puzzle/boss room from unwrapped template region (`14160` Nexus, `14162/14164` Scabaras/Kephri, `14674/14676` Het/Akkha, `15186/15188` Apmeken/Ba-Ba, `15698/15700` Crondis/Zebak, `15184/15696` Wardens, `14672` Burial Chamber), completed path counter (`0/4..4/4`), room deaths, completion KC + time.
3. Write `RaidActivityTrackerTest` first, run `./gradlew test`, and keep cyclomatic complexity `<= 10`.

## Spec interview
- Q: Which activities? A (Board 2026-11-13): Fight Caves + Inferno, Fortis Colosseum, raids progress, other wave/round minigames; 'very granular, e.g. whether they're soloing or not in Wintertodt/Tempoross'.
- Q: Where should it show? A (Board 2026-11-13): Group tab card, in-game group box overlay, Discord milestones, live board; and 'maybe a new bossing and minigames tab, one for each, or something like that'.
```

---

### Slice D — Title & Brief
**Title:** `GIM Utilities: live activities Slice D — wave & round minigames (BA, Pest Control, Gauntlet/CG, Barrows, Moons, Sepulchre)`

```markdown
Goal: Complete the live activity roster with Barbarian Assault (wave + role), Pest Control (lander tier + portals/Knight HP), The Gauntlet & Corrupted Gauntlet (prep vs Hunllef), plus Barrows live crypts, Moons of Peril, and Hallowed Sepulchre (`docs/plans/activities.md` §1.2, §1.3 & §2.4-D1).

Depends on: Slice A merged to `main`. Read `docs/plans/activities.md` and `AGENTS.md` in full before writing code.

Scope:
1. Strict compliance guardrails:
   - Do NOT build any PvP minigame trackers (LMS, Soul Wars, Castle Wars, Fight Pit — cut in `docs/plans/activities.md` §1.3).
   - Zero Hunllef attack/prayer/tornado counters, zero BA horn/defender helpers, zero Barrows puzzle solvers, zero Sepulchre hazard timers.
2. Detectors (verified `VarbitID` & `InterfaceID` constants in `1.13.5`, template regions via `WorldPoint.fromLocalInstance`):
   - **Barbarian Assault** (`regions 7508, 7509`, `IN_GAME_BA` `3923`): detect active role (`Attacker`, `Defender`, `Collector`, `Healer`) and wave `1–10` (`Penance Queen` on 10) from loaded `InterfaceID.Ba*` widget group, team summary (`TeamSummary`), Queen completion & wave wipe.
   - **Pest Control** (`region 10536`, widget `408` `InterfaceID.PestStatus.*` + lander `407`): boat tier (`Novice`/`Intermediate`/`Veteran`), portals remaining (`0–4`), Void Knight HP, time left, win/loss.
   - **The Gauntlet & Corrupted Gauntlet** (`7512` Crystalline vs `7768` Corrupted, `GAUNTLET_ENTERED` `9178`, `GAUNTLET_FINAL_ROOM_ENTERED` `9177`, timer widget `637`): live `Prep (mm:ss left)` vs `Hunllef` boss phase, completion KC + duration, death tagged with `Prep` vs `Hunllef`.
   - **Barrows live crypts** (`14131, 14231`, `BARROWS_KILLED_*` `457–462`, `BARROWS_NPCS_SLAIN` `464`): live `X/6 brothers · Y%` status that clears on `BARROWS_REWARD` without duplicating the existing `GroupEvent.Type.BARROWS` chest post.
   - **Moons of Peril** (Neypotzli regions + 3 Moon varbits → `X/3 moons` + Lunar Chest) & **Hallowed Sepulchre** (`regions 8797..10077` → `Floor 1–5` + Grand Hallowed Coffin).
3. Write `MinigameActivityTrackerTest` first, run `./gradlew test`, and keep cyclomatic complexity `<= 10`.

## Spec interview
- Q: Which activities? A (Board 2026-11-13): Fight Caves + Inferno, Fortis Colosseum, raids progress, other wave/round minigames; 'very granular, e.g. whether they're soloing or not in Wintertodt/Tempoross'.
- Q: Where should it show? A (Board 2026-11-13): Group tab card, in-game group box overlay, Discord milestones, live board; and 'maybe a new bossing and minigames tab, one for each, or something like that'.
```
