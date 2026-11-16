# Activity, Wave, Rotation & Split Detection Research (`RUNELITE-207` / `RUNELITE-208`)

> **Scope:** Research deliverable for Tuesday's GIM Utilities tasks [`RUNELITE-207`](http://staypoint.localhost:41421/RUNELITE-207/gim-utilities-notify-when-a) (*notify when a group member enters the Fight Caves, Inferno or Colosseum, plus milestones, deaths and completions*) and [`RUNELITE-208`](http://staypoint.localhost:41421/RUNELITE-208/gim-utilities-activities-rotation-and) (*rotation and per-wave split times for the Fight Caves, Inferno, Colosseum and the rest*). Docs only — no code changes.

---

## 1. Executive Summary & Existing Baseline in `group-ironman-plugin`

PR #74 (`67498ec`, Slice A of [`docs/plans/activities.md`](../plans/activities.md)) already shipped the core live-activity wire protocol (`"k":"activity"` in [`ActivityCodec.java`](../../src/main/java/com/vinnyvangogh/groupironman/ActivityCodec.java#L16-L128)), display model ([`ActivityState.java`](../../src/main/java/com/vinnyvangogh/groupironman/ActivityState.java#L13-L229), [`ActivityRows.java`](../../src/main/java/com/vinnyvangogh/groupironman/ActivityRows.java#L15-L128)), privacy-safe team classifier ([`TeamSummary.java`](../../src/main/java/com/vinnyvangogh/groupironman/TeamSummary.java#L15-L96)), Colosseum modifier reader ([`ColosseumModifiers.java`](../../src/main/java/com/vinnyvangogh/groupironman/ColosseumModifiers.java#L21-L63)), and wave state machine ([`WaveTracker.java`](../../src/main/java/com/vinnyvangogh/groupironman/WaveTracker.java#L25-L449), wired in [`GroupEventEngine.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L205-L207) and [`:1384-1409`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L1384-L1409)).

### What PR #74 already detects vs what `RUNELITE-207` and `RUNELITE-208` add

| Capability | Current state on `main` (`67498ec`) | Needed for `RUNELITE-207` (Notifications) & `RUNELITE-208` (Rotations & Splits) |
| --- | --- | --- |
| **Fight Caves (`9551`), Inferno (`9043`), Colosseum (`7216`) wave & run timer** | `WaveTracker` reads `^Wave: (\d+)$`, `^Wave (\d+) completed!`, `"Sol Heredit jumps down from his seat"`, `^You have been defeated!?$`, `EventText.killCount`, and `(?i)duration: ?([0-9:.]+)`. Tracks wall-clock `elapsedMs` across logouts (`WAVE_RUN_KEY = "activityRun"`). | Add **per-wave tick split timing** (`int[] waveTicks`), **Fight Caves spawn rotation (`1–15`)**, and desktop/sound notifications via RuneLite `Notifier` when a GIM teammate enters, reaches a boss wave, dies, or completes a run. |
| **Colosseum modifiers & Glory** | `ColosseumModifiers` reads the 10 tiered `VarbitID.COLOSSEUM_MODIFIER_*_STACKS_CLIENT` varbits (`I–III`) and `VarPlayerID.COLOSSEUM_GLORY` (`4130`). | Add detection for the **4 untiered modifiers** (*Dynamic Duo*, *Quartet*, *Red Flag*, *Totemic*) via widget `865` / NPC spawn fallback, and parse exact per-wave durations from `Wave N completed! Wave duration: M:SS.ss (Total: M:SS.ss)`. |
| **Other wave / staged activities** | Specified in `docs/plans/activities.md` Slices B–D (`Barbarian Assault`, `The Gauntlet / Corrupted Gauntlet`, `Chambers of Xeric`, `Theatre of Blood`, `Tombs of Amascut`, `Hallowed Sepulchre`, `TzHaar-Ket-Rak's Challenges`, `Pest Control`, `Wintertodt`, `Tempoross`, `Guardians of the Rift`), not yet wired into `WaveTracker`. | Wire detectors using the exact varbits, varps, template region IDs, widgets, and chat patterns documented in §3–§4 below; report `"no rotation"` for activities without predetermined rotations rather than inventing one. |

---

## 2. Plugin Hub Compliance & Guardrails (`runelite-compliance` & `docs/runelite/hub-rules.md`)

Checked against [`AGENTS.md`](../../AGENTS.md), `docs/runelite/hub-rules.md` in `runelite-plugin-dev` (`RUNELITE-217`), [RuneLite's Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features), and [Jagex's Third-Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1).

### 2.1 Feature-level compliance rulings

| Feature (`RUNELITE-207` / `RUNELITE-208`) | What the player / group learns | Verdict | Nearest rule (`AGENTS.md` / `hub-rules.md`) | Base-client & Plugin Hub precedent | Required guardrails |
| --- | --- | --- | --- | --- | --- |
| **Fight Caves rotation (`Rotation 1–15`) & wave number (`1–63`)** | Which of the 15 wiki spawn rotations a groupmate's Fight Caves run is on, and their current wave. | **Allowed, with precedent** | Boss & Combat: *No Jad prayer/attack indicators*; *No automatic indicators showing where to stand* | Base `TimersAndBuffsPlugin` (`Wave: N` timer) & `DiscordGameEventType` (`region 9551`); Hub **Fight Cave Waves** (`nightfirecat`, slug `fight-cave-waves`) & **Fight Caves Spawn Predictor** (`damencs/spawn-predictor`, PR #3987) | Identify and display the **rotation number (`Rotation 1`..`15`) and wave (`N/63`)** in the panel, member card, and Discord summary. **Never** draw in-arena safe-spot tiles, next-wave floor markers, kill-order tags, or Jad prayer cues. |
| **Inferno wave (`1–69`), phase labels (`Triple Jads`, `TzKal-Zuk`), & per-wave / checkpoint splits** | Which Inferno wave a groupmate is on and how long each wave or split section took. | **Allowed, with precedent** | Boss & Combat: *New high-end PvM boss plugins are not accepted*; *No boss mechanic indicators* | Base `TimersAndBuffsPlugin` (`INFERNO_REGION_ID = 9043`, `TZHAAR_WAVE_MESSAGE`); Hub **Inferno Stats** (`ollieatkinson/InfernoStats`, 35k installs), **Inferno Split Timer** (`usa-usa-usa-usa/inferno-wave-splits`, 28k installs), **Dink** (`pajlads/DinkPlugin`) | Show wave number, fixed boss wave name (`67` Single Jad, `68` Triple Jads, `69` TzKal-Zuk), and elapsed/completed wave split times. **Never** track Zuk shield movement, 3:30 set timers, nibbler pillar HP, or monster attack ticks. |
| **Fortis Colosseum wave (`1–12`), chosen modifiers (`I–III` + untiered), Glory, & wave splits** | Current Colosseum wave, handicaps already selected at Minimus's pillar, Glory, and completed wave times. | **Allowed, with precedent** | Boss & Combat: *Blanket high-end PvM boss policy*; *No advance warning of future hazards* | Base `DiscordGameEventType` (`7216`, `7316`); Hub **Fortis Colosseum** (`LlemonDuck/fortis-colosseum`, 137k installs, `ColosseumStateTracker`, `Modifier`, `SplitsTracker`); Hub **Dink** | Read only modifiers **already chosen**, current wave, Glory (`VarPlayerID.COLOSSEUM_GLORY`), and completed wave durations. **Never** show 40s reinforcement spawn countdowns, Manticore orb sequences, Solarflare tiles, or Sol Heredit attack cues. |
| **Barbarian Assault wave (`1–10`) & role; Gauntlet `Prep` vs `Hunllef` stage splits; Raids floor/room splits** | Coarse stage/wave/role and completed stage times for opted-in GIM teammates. | **Allowed, with precedent** (raids narrowed) | Boss & Combat: *No Hunllef tornado/attack counters*; Data & Privacy: *No crowdsourcing data about other players* | Base `BarbarianAssaultPlugin`, `RaidsPlugin`, `DiscordGameEventType`; Hub **The Gauntlet**, **BA Minigame**, **Tombs of Amascut**, **Dink** | Never pre-scout unentered CoX rooms; never reflect into other plugins; never count Hunllef attacks/prayers; count non-GIM teammates strictly as `+N others` via `TeamSummary`. |
| **Group member activity notifications (`RUNELITE-207`)** | Desktop/tray notification via RuneLite `Notifier` when a GIM teammate starts an activity, hits a boss wave, dies, or completes a run. | **Allowed, with precedent** | Input Restrictions: *No automation*; Data & Privacy: *Opt-in party data only* | Base `IdleNotifierPlugin` & `PartyPlugin`; existing `LowStock` alerts (`BRIEF.md` #12) | Fire **at most once per member per run** per milestone kind (never spam on every wave); **never** fire for non-GIM accounts (`GroupBinding`, PR #75); default toggles `false` except completion (`RUNELITE-207`). |

### 2.2 Technical & scanner rules (`docs/runelite/hub-rules.md`)

1. **No reflection (`Class.forName`, `java.lang.reflect.*`):** Bot-blocking automatic rejection (`reviewbot`). Do not read state from `TimersAndBuffsPlugin`, `RaidsPlugin`, `Fortis Colosseum`, or `Inferno Stats`; read directly from `Client` events, varbits, varps, widgets, and chat messages.
2. **Use `net.runelite.api.gameval.*` constants:** Always reference `VarbitID`, `VarPlayerID`, `InterfaceID`, `NpcID`, and `ObjectID` from `net.runelite.api.gameval` when a constant exists (`AGENTS.md` *API Usage*). Where a CS2 script or varbit has no `gameval` constant in `runelite-api:1.13.6`, document the omission or read the widget/chat representation instead.
3. **Filepath API only for local split/PB storage:** Store per-member personal-best wave splits through `getPluginDirectory()` (`net.runelite.client.util.Filepath` / `PluginFiles.java`), never `java.io.File` or `RuneLite.RUNELITE_DIR` (`plugin-hub#20149`).
4. **No PvP minigame tracking:** Never track wave/round/survivor state in Last Man Standing, Soul Wars, Castle Wars, TzHaar Fight Pit (`region 9552`), Emir's Arena, or Clan Wars (*PvP Restrictions: No PvP target scouting information / No player group summaries*).
5. **Token budget (`< 200,000` tokens):** Keep rotation tables and wave metadata compact (e.g., a 15-element `byte[]` spawn ring for Fight Caves rather than 63×15 hardcoded wave tables, or place static tables in `src/main/resources/` which the Hub token counter ignores per `LESSONS.md` row 3).

---

## 3. Primary Wave Activities: Detection & Rotation Reference Tables

> **Instance Region Unwrapping Rule (Mandatory for all instanced activities):**
> In instanced areas, `player.getWorldLocation().getRegionID()` returns a dynamic instance chunk ID (`>= 16000`). Every detector must unwrap the template coordinate via `WorldPoint.fromLocalInstance(client, me.getLocalLocation()).getRegionID()` (already implemented in [`GroupEventEngine.templateRegion`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L1405-L1409)). Because `fromLocalInstance` can return `null` on the single loading tick between waves or rooms, ignore `-1` (`regionId < 0`) so a loading frame never clears an active run ([`WaveTracker.java:132-135`](../../src/main/java/com/vinnyvangogh/groupironman/WaveTracker.java#L132-L135)).

---

### 3.1 TzHaar Fight Cave (63 Waves, 15 Spawn Rotations)

- **OSRS Wiki sources:** [TzHaar Fight Cave](https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave), [TzHaar Fight Cave/Rotations](https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave/Rotations), [TzHaar Fight Cave/Strategies](https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave/Strategies)
- **RuneLite & Hub sources:**
  - `runelite-client`: `plugins/timersandbuffs/TimersAndBuffsPlugin.java` (`FIGHT_CAVE_REGION_ID = 9551`, `TZHAAR_WAVE_MESSAGE`, `TZHAAR_PAUSED_MESSAGE`), `plugins/discord/DiscordGameEventType.java` (`MG_FIGHT_CAVES`, region `9551`), `plugins/screenshot/ScreenshotPlugin.java` (`BOSSKILL_MESSAGE_PATTERN`)
  - Plugin Hub: `damencs/spawn-predictor` (`SpawnPredictorPlugin.java`, `FightCavesNpc.java`, `FightCavesSpawnLocation.java`), `nightfirecat` (`fight-cave-waves`)
  - `group-ironman-plugin`: [`WaveTracker.java:29`](../../src/main/java/com/vinnyvangogh/groupironman/WaveTracker.java#L29)

#### A. Lifecycle & Signal Table — TzHaar Fight Cave

| Stage / Event | Exact Game Signal (Chat / Varbit / Varp / Region / NPC / Widget) | RuneLite / GIM Source Citation | Notes & Edge Cases |
| --- | --- | --- | --- |
| **Entering** | Template Region ID `9551` (`WorldPoint.fromLocalInstance`) + `ChatMessageType.GAMEMESSAGE`: `<col=ef1020>Wave: 1</col>` (plain: `^Wave: 1$`) | `DiscordGameEventType.java` (`9551`); `TimersAndBuffsPlugin.java` (`FIGHT_CAVE_REGION_ID = 9551`); `WaveTracker.java:29,83` | Outer city is region `9808` (*Mor Ul Rek*). Entering via entrance `ObjectID.CAVE_ENTRANCE_44` teleports player to template `(2413, 5117, 0)` in `9551` and prints `Wave: 1` immediately on tick 1. Strict solo instance. |
| **Current Wave (`1–63`)** | `ChatMessageType.GAMEMESSAGE` matching `^Wave: (\d+)$` after stripping `<col=ef1020>...</col>` tags (`EventText.plain`). Wave `63` = `TzTok-Jad`. | `TimersAndBuffsPlugin.java` (`TZHAAR_WAVE_MESSAGE = Pattern.compile("Wave: (\\d+)")`); `WaveTracker.java:83,169` | Wave composition follows a deterministic binary carry ladder (`22 → 45 → 90 → 180 → 360 → 702`): whenever a wave has two identical monsters, the next wave replaces them with one monster of the next tier; otherwise a `Tz-Kih` (`22`) is appended. |
| **Rotation (`1–15`)** | **Method 1 (In-cave NPC spawns — authoritative):** `NpcSpawned` on Waves `1–4` mapped to template coordinates `(x % 64, y % 64)` in region `9551` across the 5 spawn quadrants (`NW`, `C`, `SE`, `S`, `SW`).<br>**Method 2 (Entry UTC clock — pre-wave-2 fallback):** `(epochMinutes + offset) % 16` mapped to the 16-minute server rotation schedule (where `Rotation 4` spans 2 consecutive minutes). | `damencs/spawn-predictor` (`SpawnPredictorPlugin.java`); OSRS Wiki *TzHaar Fight Cave/Rotations* | **13 of the 15 rotations are uniquely identified by Wave 2.** Only **Rotation 7** and **Rotation 8** share Wave 1 (`S`) and Wave 2 (`NW, C`) and Wave 3 (`C`), resolving on **Wave 4** (`C, S` = Rotation 7 vs `C, SE` = Rotation 8). While on Wave 1 (or Wave 2–3 for `{7, 8}`), show candidate set (e.g. `Rot 7/8`) or UTC-inferred rotation until confirmed by spawn. |
| **Wave Completion** | Implicit: wave $w$ ($1 \le w \le 62$) completes 2 ticks (`1.2s`) before `Wave: (w+1)` prints (when the last monster, including both `Tz-Kek` level-22 splits, dies). Wave `63` completes when `TzTok-Jad` dies (`ActorDeath` / KC chat). | `WaveTracker.java:214-234` | Unlike Fortis Colosseum, the Fight Caves does **not** print a `"Wave N completed!"` message; wave splits are measured from `Wave: w` to `Wave: (w+1)` (see §5). |
| **Pause & Mid-Run Logout** | `ChatMessageType.GAMEMESSAGE`: `<col=ef1020>The Fight Cave has been paused. You may now log out.</col>` (`^The (?:Inferno\|Fight Cave) has been paused\. You may now log out\.$`), followed by `GameState.LOGIN_SCREEN`. | `TimersAndBuffsPlugin.java` (`TZHAAR_PAUSED_MESSAGE`); `WaveTracker.java:297-305,404-440` | Clicking logout mid-wave requests a pause at wave end; clicking logout between waves logs out immediately. On relog inside `9551`, the current wave reprints `Wave: N` (do not emit a duplicate milestone: `WaveTracker.java:227`). |
| **Death / Defeat vs Voluntary Exit** | **Defeat:** Local player `onActorDeath` inside `9551` **or** `GAMEMESSAGE` `^You have been defeated!?$`.<br>**Voluntary exit:** Template region changes `9551 → 9808` (via cave exit `ObjectID.CAVE_ENTRANCE_45`) with neither `ActorDeath`, `DEFEATED`, nor `pendingKc`. | `GroupEventEngine.java:1070-1105`; `WaveTracker.java:86,155-163,260-281` | Safe death for standard/GIM accounts (awards partial TokKul = $N(N+1)/2$), **unsafe** death for Hardcore Group Ironman (`VarbitID.IRONMAN == 5`, `BRIEF.md` #11). |
| **Activity Completion** | `ChatMessageType.GAMEMESSAGE`:<br>1. `Your TzTok-Jad kill count is: <col=ff0000>(\d+)</col>.`<br>2. `Duration: <col=ff0000>(\d+:\d+(?::\d+)?)</col>` (optional ` (new personal best)`) | `ScreenshotPlugin.java` (`BOSSKILL_MESSAGE_PATTERN`); `EventText.java:30-31`; `EventChat.java:21-22`; `WaveTracker.java:88,201-212,284-294` | Both lines arrive on the same game tick when Jad's death animation finishes, right before the player is teleported out to `9808` with Item `6570` (*Fire cape*) and `8,032` TokKul (`16,064` with Karamja Elite). |

#### B. Fight Caves NPC IDs (`net.runelite.api.gameval.NpcID` / `net.runelite.api.NpcID`)

| Monster | Combat Level | First Wave | Base / Normal NPC ID | Off-colour (Orange) Variant NPC ID | Notes for Spawn Rotation Detection |
| --- | --- | --- | --- | --- | --- |
| **Tz-Kih** (bat) | `22` | Wave 1 | `3116` (`TZHAAR_FIGHTCAVE_SWARM_1A`) | `3117` (`TZHAAR_FIGHTCAVE_SWARM_1B`) | Drains 1 + damage Prayer points even on a `0`. |
| **Tz-Kek** (big blob) | `45` | Wave 3 | `3118` (`TZHAAR_FIGHTCAVE_SWARM_2A`) | `3119` (`TZHAAR_FIGHTCAVE_SWARM_2B`) | Recoils 1 melee damage; splits into two level-22 `Tz-Kek` on death. |
| **Tz-Kek** (small split) | `22` | Wave 3 (on 45 death) | `3120` (`TZHAAR_FIGHTCAVE_SWARM_2C`) | — | **Ignore `3120` (and `Yt-HurKot`) when reading wave start spawns** — they spawn mid-wave at the parent's tile, not at a rotation spawn point! |
| **Tok-Xil** (ranger) | `90` | Wave 7 | `3121` (`TZHAAR_FIGHTCAVE_SWARM_3A`) | `3122` (`TZHAAR_FIGHTCAVE_SWARM_3B`) | Ranged + Melee. |
| **Yt-MejKot** (meleer) | `180` | Wave 15 | `3123` (`TZHAAR_FIGHTCAVE_SWARM_4A`) | `3124` (`TZHAAR_FIGHTCAVE_SWARM_4B`) | Heals self and nearby monsters when below 50% HP. |
| **Ket-Zek** (mager) | `360` | Wave 31 | `3125` (`TZHAAR_FIGHTCAVE_SWARM_5A`) | `3126` (`TZHAAR_FIGHTCAVE_SWARM_5B`) | Magic + Melee (max hit 54). |
| **TzTok-Jad** (boss) | `702` | Wave 63 | `3127` (`TZHAAR_FIGHTCAVE_SWARM_BOSS`) | — | Always spawns at the **exact same location as the Wave 3 `Tz-Kek` (level 45)** (`RING[(start + 2) % 15]`). |
| **Yt-HurKot** (Jad healer) | `108` | Wave 63 (at 50% Jad HP) | `3128` (`TZHAAR_FIGHTCAVE_SWARM_BOSSTINY`) | `7705` | 4 healers spawn when Jad drops below 50% HP (125 HP); ignore for rotation detection. |

#### C. Fight Caves 15-Element Spawn Ring, Quadrant Coordinates & Full 15-Rotation Table

Every rotation in the TzHaar Fight Cave is a cyclic shift of one 15-step spawn ring (verified from [OSRS Wiki: TzHaar Fight Cave](https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave) and `damencs/spawn-predictor`):

```text
RING[0..14] = [ SE, SW, C, NW, SW, SE, S, NW, C, SE, SW, S, NW, C, S ]
```

**How the game assigns spawn locations on Wave $w$ ($1 \le w \le 63$):**
- Each run has a starting index `start` $\in \{0 \dots 14\}$ in `RING`.
- On Wave $w$, monsters are sorted from **highest combat level to lowest** (or in the recursive wave-generation order of `SpawnPredictorPlugin.calculateSpawns`), and the $k$-th monster ($k = 0, 1, \dots$) spawns at:
  $$\text{location}(w, k) = \text{RING}[(\text{start} + w - 1 + k) \bmod 15]$$
- Because Wave 3 has a single monster (`Tz-Kek`, level 45, $w=3, k=0$) and Wave 63 has a single initial monster (`TzTok-Jad`, level 702, $w=63, k=0$), and $(63 - 1) \equiv (3 - 1) \pmod{15}$ (since $60 = 4 \times 15$), **TzTok-Jad on Wave 63 always spawns at the exact same quadrant as the Wave 3 monster** (`RING[(start + 2) % 15]`).

**The 5 Spawn Quadrants in Template Region `9551` (`baseX = 2368, baseY = 5056`):**

| Code | Name | Approx. Template World Tile `(X, Y)` | Region-Local Tile `(sceneX, sceneY)` | Bounding Classification Rule (`sceneX = templateX & 63`, `sceneY = templateY & 63`) |
| --- | --- | --- | --- | --- |
| **`NW`** | North-West | `(2379, 5106)` | `(11, 50)` | `sceneX < 22 && sceneY >= 40` |
| **`C`** | Centre | `(2401, 5088)` | `(33, 32)` | `22 <= sceneX <= 42 && 24 <= sceneY <= 40` |
| **`SE`** | South-East | `(2416, 5082)` | `(48, 26)` | `sceneX > 42 && sceneY >= 20` |
| **`S`** | South | `(2400, 5071)` | `(32, 15)` | `22 <= sceneX <= 42 && sceneY < 24` |
| **`SW`** | South-West | `(2381, 5071)` | `(13, 15)` | `sceneX < 22 && sceneY < 40` |

**Complete 15-Rotation Lookup Table:**

| Wiki Rotation | Ring Index `start` | Wave 1 (`22`) | Wave 2 (`22, 22`) | Wave 3 (`45`) & **Wave 63 (`TzTok-Jad`)** | Wave 4 (`45, 22`) | Wave Solved By |
| --- | --- | --- | --- | --- | --- | --- |
| **Rotation 1** | `2` | `C` | `NW, SW` | **`SW`** | `SW, SE` | Wave 2 |
| **Rotation 2** | `13` | `C` | `S, SE` | **`SE`** | `SE, SW` | Wave 2 |
| **Rotation 3** | `8` | `C` | `SE, SW` | **`SW`** | `SW, S` | Wave 2 |
| **Rotation 4** *(2 min in UTC cycle)* | `12` | `NW` | `C, S` | **`S`** | `S, SE` | Wave 2 |
| **Rotation 5** *(fastest speedrun rotation)* | `7` | `NW` | `C, SE` | **`SE`** | `SE, SW` | Wave 2 |
| **Rotation 6** | `3` | `NW` | `SW, SE` | **`SE`** | `SE, S` | Wave 2 |
| **Rotation 7** | `11` | `S` | `NW, C` | **`C`** | **`C, S`** *(split: `C, S, SE`)* | **Wave 4** *(tied with Rot 8 on Waves 1–3)* |
| **Rotation 8** | `6` | `S` | `NW, C` | **`C`** | **`C, SE`** *(split: `C, SE, SW`)* | **Wave 4** *(tied with Rot 7 on Waves 1–3)* |
| **Rotation 9** | `14` | `S` | `SE, SW` | **`SW`** | `SW, C` | Wave 2 |
| **Rotation 10** | `5` | `SE` | `S, NW` | **`NW`** | `NW, C` | Wave 2 |
| **Rotation 11** | `0` | `SE` | `SW, C` | **`C`** | `C, NW` | Wave 2 |
| **Rotation 12** | `9` | `SE` | `SW, S` | **`S`** | `S, NW` | Wave 2 |
| **Rotation 13** | `1` | `SW` | `C, NW` | **`NW`** | `NW, SW` | Wave 2 |
| **Rotation 14** | `10` | `SW` | `S, NW` | **`NW`** | `NW, C` | Wave 2 |
| **Rotation 15** | `4` | `SW` | `SE, S` | **`S`** | `S, NW` | Wave 2 |

---

### 3.2 The Inferno (69 Waves, Fixed Wave Ladder + Random 9-Spawn Markers)

- **OSRS Wiki sources:** [Inferno](https://oldschool.runescape.wiki/w/Inferno), [Inferno/Strategies](https://oldschool.runescape.wiki/w/Inferno/Strategies), [TzKal-Zuk](https://oldschool.runescape.wiki/w/TzKal-Zuk)
- **RuneLite & Hub sources:**
  - `runelite-client`: `plugins/timersandbuffs/TimersAndBuffsPlugin.java` (`INFERNO_REGION_ID = 9043`, `TZHAAR_WAVE_MESSAGE`, `TZHAAR_PAUSED_MESSAGE`), `plugins/discord/DiscordGameEventType.java` (`MG_INFERNO`, region `9043`)
  - Plugin Hub: `ollieatkinson/InfernoStats` (`InfernoStats`), `usa-usa-usa-usa/inferno-wave-splits` (`Inferno Split Timer`), `pajlads/DinkPlugin`
  - `group-ironman-plugin`: [`WaveTracker.java:30`](../../src/main/java/com/vinnyvangogh/groupironman/WaveTracker.java#L30)

#### A. Lifecycle & Signal Table — The Inferno

| Stage / Event | Exact Game Signal (Chat / Varbit / Varp / Region / NPC / Widget) | RuneLite / GIM Source Citation | Notes & Edge Cases |
| --- | --- | --- | --- |
| **Entering & 10-Tick Cutscene Offset** | Template Region ID `9043` (`WorldPoint.fromLocalInstance`) + `ChatMessageType.GAMEMESSAGE`: `<col=ef1020>Wave: 1</col>` | `TimersAndBuffsPlugin.java` (`INFERNO_REGION_ID = 9043`, comment: *"The first wave message of the inferno comes six seconds after the ingame timer starts counting"*); `DiscordGameEventType.java` (`9043`); `WaveTracker.java:30` | Jumping into the Inferno pit in inner Mor Ul Rek (`region 10064`) plays a 10-tick (`6.0s`) falling/standing animation before `Wave: 1` is printed. The official server timer starts when the player stands up (10 ticks before `Wave: 1`). Wait for `Wave: 1` before starting an Inferno run so `TzHaar-Ket-Rak's Challenges` (which share region `9043`) are not misclassified as the Inferno! |
| **Current Wave (`1–69`)** | `ChatMessageType.GAMEMESSAGE` matching `^Wave: (\d+)$` (`EventText.plain`).<br>• Waves `1–66`: pillar waves<br>• Wave `67`: Single `JalTok-Jad`<br>• Wave `68`: Triple `JalTok-Jad`s<br>• Wave `69`: `TzKal-Zuk` | `TimersAndBuffsPlugin.java`; `WaveTracker.java:30,83` | Waves `68` and `69` are always treated as marquee boss milestones (`bossFrom = 68` at `WaveTracker.java:30`). At the end of Wave `66`, all remaining pillars collapse. |
| **Rotation / Spawn Mechanics** | **No predetermined spawn rotation (`1–15`) exists in The Inferno.**<br>• **Wave monster composition (`1–69`):** 100% deterministic (binary carry ladder `85 → 165 → 240 → 370 → 490` plus `Jal-Nib`s on waves `1–66`).<br>• **Spawn positions:** On each wave `1–66`, non-nibbler monsters are assigned **randomly** by the server at wave start among the **9 fixed arena spawn markers** (without replacement), while `Jal-Nib`s spawn as a cluster targeting one of the 3 pillars (`W`, `N`, `S`). | OSRS Wiki *Inferno/Strategies*; Hub `ollieatkinson/InfernoStats` | Per `RUNELITE-208` (*"for activities without rotations, say so rather than invent one"*): The Inferno has **no run-wide rotation number**. Instead, `RUNELITE-208` should label rotation as `"random spawns"` (or omit rotation on the compact card and show the **wave composition / speedrun split section**, e.g. `Waves 50–66 (Mager + Ranger)` or `Wave 68 (Triple Jads)`). |
| **Standard Community Split Checkpoints** | Wave start messages `Wave: 9`, `18`, `25`, `35`, `42`, `50`, `57`, `60`, `63`, `66`, `67`, `68`, `69` | `runelite/runelite#12378`; Hub `usa-usa-usa-usa/inferno-wave-splits` | These 13 waves correspond to new monster introductions (`9` Meleer, `18` Blob+Meleer, `25` Meleer×2, `35` Ranger, `42` Ranger+Meleer, `50` Mager, `57` Mager+Meleer, `60` Mager+Ranger, `63` Mager+Ranger+Blob+Meleer, `66` Double Mager, `67` Jad, `68` Triples, `69` Zuk). |
| **Pause & Mid-Run Logout** | `ChatMessageType.GAMEMESSAGE`: `<col=ef1020>The Inferno has been paused. You may now log out.</col>` (`TZHAAR_PAUSED_MESSAGE`), followed by `GameState.LOGIN_SCREEN`. | `TimersAndBuffsPlugin.java`; `WaveTracker.java:297-305` | Identical pause/resume lifecycle to Fight Caves (`WAVE_RUN_KEY`). |
| **Death / Defeat vs Voluntary Exit** | `onActorDeath` inside `9043` **or** `GAMEMESSAGE` `^You have been defeated!?$`. | `WaveTracker.java:86,260-281`; `GroupEventEngine.java:1070-1105` | Safe death for standard/GIM accounts; **unsafe** death (loses a group life) for Hardcore Group Ironman (`VarbitID.IRONMAN == 5`). |
| **Activity Completion** | `ChatMessageType.GAMEMESSAGE`:<br>1. `Your TzKal-Zuk kill count is: <col=ff0000>(\d+)</col>.`<br>2. `Duration: <col=ff0000>(\d+:\d+(?::\d+)?)</col>` (optional ` (new personal best)`) | `EventText.java:30-31`; `WaveTracker.java:30,88,201-212` | Awards `Infernal cape` (Item `21295`) and `16,440` TokKul (`8,220` without Karamja diary). |

#### B. Inferno NPC IDs & 9 Arena Spawn Coordinates (Template Region `9043`)

| Monster | Combat Level | First Wave | NPC ID (`net.runelite.api.gameval.NpcID` / raw ID) | Role / Notes |
| --- | --- | --- | --- | --- |
| **Jal-Nib** (nibbler) | `32` | Wave 1 | `7691` (`INFERNO_NIBBLER`) | 3–6 spawn per wave (`1–66`), targeting a remaining pillar (`Rocky support`, NPC `7709` / `7710`). |
| **Jal-MejRah** (bat) | `85` | Wave 1 | `7692` (`INFERNO_BAT`) | Ranged, drains run energy and combat stats. |
| **Jal-Ak** (blob) | `165` | Wave 4 | `7693` (`INFERNO_BLOB`) | Alternates Magic/Ranged based on player prayer; splits into 3 `Jal-AkRek` on death. |
| **Jal-AkRek-Mej / Xil / Ket** (bloblets) | `70` | Wave 4 (on blob death) | `7694` (Magic), `7695` (Ranged), `7696` (Melee) | Spawn at the slain `Jal-Ak`'s tile; ignore for initial wave spawn tracking. |
| **Jal-ImKot** (meleer) | `240` | Wave 9 | `7697` (`INFERNO_MELEE`) | 4×4 melee monster; digs to player if out of LoS. |
| **Jal-Xil** (ranger) | `370` | Wave 18 | `7698` (`INFERNO_RANGER`) | 3×3 Ranged + Melee. |
| **Jal-Zek** (mager) | `490` | Wave 35 | `7699` (`INFERNO_MAGER`) | 4×4 Magic + Melee; can respawn slain wave monsters at 50% HP. |
| **JalTok-Jad** | `900` | Wave 67 (×1), Wave 68 (×3), Wave 69 (at 480 Zuk HP) | `7700` (`INFERNO_JAD`) | Summons 5 `Yt-HurKot` (`7701` / `7705`, level 141) on Wave 67, 3 each on Wave 68, and 3 on Wave 69. |
| **TzKal-Zuk** | `1400` | Wave 69 | `7706` (`INFERNO_ZUK`) | Shield is `Ancestral Glyph` (`7707`); Zuk healers below 240 HP are `Jal-MejJak` (`7708`, level 250). |

*(For reference only — not displayed as in-arena overlays per §2.1: the 9 fixed spawn markers in template region `9043` are `NW (2258, 5353)`, `N (2274, 5353)`, `NE (2280, 5352)`, `W (2257, 5343)`, `C (2272, 5341)`, `E (2282, 5341)`, `SW (2260, 5332)`, `S (2271, 5329)`, `SE (2279, 5331)`.)*

---

### 3.3 Fortis Colosseum (12 Waves, Sol Heredit & 14 Wave Modifiers)

- **OSRS Wiki sources:** [Fortis Colosseum](https://oldschool.runescape.wiki/w/Fortis_Colosseum), [Fortis Colosseum/Strategies](https://oldschool.runescape.wiki/w/Fortis_Colosseum/Strategies), [Sol Heredit](https://oldschool.runescape.wiki/w/Sol_Heredit)
- **RuneLite & Hub sources:**
  - `runelite-api`: `gameval/VarPlayerID.java` (`COLOSSEUM_GLORY = 4130`), `gameval/VarbitID.java` (`COLOSSEUM_MODIFIER_*_STACKS_CLIENT`)
  - `runelite-client`: `plugins/discord/DiscordGameEventType.java` (`MG_FORTIS_COLOSSEUM` `7216`, `MG_FORTIS_COLOSSEUM_LOBBY` `7316`)
  - Plugin Hub: `LlemonDuck/fortis-colosseum` (`ColosseumStateTracker.java`, `util/Modifier.java`, `splits/SplitsTracker.java`), `pajlads/DinkPlugin` (issue #472 Doom death)
  - `group-ironman-plugin`: [`WaveTracker.java:31`](../../src/main/java/com/vinnyvangogh/groupironman/WaveTracker.java#L31), [`ColosseumModifiers.java:21-63`](../../src/main/java/com/vinnyvangogh/groupironman/ColosseumModifiers.java#L21-L63)

#### A. Lifecycle & Signal Table — Fortis Colosseum

| Stage / Event | Exact Game Signal (Chat / Varbit / Varp / Region / NPC / Widget) | RuneLite / GIM Source Citation | Notes & Edge Cases |
| --- | --- | --- | --- |
| **Entering** | Template Region ID `7216` (Arena, unwrapped via `WorldPoint.fromLocalInstance`; Lobby is `7316`) + `ChatMessageType.GAMEMESSAGE`: `<col=ef1020>Wave: 1</col>` (plain: `^Wave: 1$`) after selecting the Wave 1 modifier at Minimus. | `DiscordGameEventType.java` (`7216`, `7316`); `WaveTracker.java:31,83` | Unlike Fight Caves/Inferno, the player selects 1 of 3 random modifiers **before** every wave (`1–12`). The Colosseum cannot be paused by logging out (logging out forfeits the run). |
| **Current Wave (`1–12`)** | • Waves `1–11`: `ChatMessageType.GAMEMESSAGE` `^Wave: (\d+)$`<br>• Wave `12` (`Sol Heredit`): `ChatMessageType.GAMEMESSAGE` containing `"Sol Heredit jumps down from his seat"` (or `^Wave: 12$`) | `LlemonDuck/fortis-colosseum` (`ColosseumStateTracker.java`); `WaveTracker.java:83,85,169-179` | Wave 12 prints `"Sol Heredit jumps down from his seat..."` when the boss fight begins. |
| **Rotation / Chosen Wave Modifiers (14 Handicaps)** | **10 Tiered Modifiers (`I–III`):** `VarbitID.COLOSSEUM_MODIFIER_*_STACKS_CLIENT` (values `0` = not chosen, `1..3` = Tier `I..III`; full table below).<br>**4 Untiered Modifiers (*Dynamic Duo*, *Quartet*, *Red Flag*, *Totemic*):** Single-tier (`I`); see Table C below for widget/NPC/script detection. | `runelite-api` `gameval/VarbitID.java`; `ColosseumModifiers.java:23-37`; `LlemonDuck/fortis-colosseum` (`util/Modifier.java`) | Colosseum spawn positions (outside the central 3–4 Fremennik warbanders) are random each wave, with reinforcements arriving from the North or South gate after `40s` (`67 ticks`). Thus the "rotation" of a Colosseum run is the **set of modifiers chosen before each wave**. |
| **Glory Counter** | `VarPlayerID.COLOSSEUM_GLORY` (`varp 4130`) | `runelite-api` `gameval/VarPlayerID.java`; `GroupEventEngine.java:1393` | Updated at the end of each wave; formatted as `"14,200 glory"`. |
| **Wave Completion & Exact Wave Split** | `ChatMessageType.GAMEMESSAGE`:<br>`Wave (\d+) completed!`<br>`Wave duration: <col=ef1020>([0-9:.]+)</col> (Total: <col=ef1020>([0-9:.]+)</col>)` (may arrive as one or two lines depending on client chat wrapping; regex: `Wave (?<wave>\d+) completed!(?: Wave duration: (?<duration>[0-9:.]+))?`) | `LlemonDuck/fortis-colosseum` (`SplitsTracker.java`); `WaveTracker.java:84,180-185` | **Crucial for `RUNELITE-208`:** Because players spend variable time between waves choosing modifiers and inspecting the loot chest, per-wave splits in the Colosseum must end on `Wave N completed!` (or parse the game's exact `Wave duration: M:SS.ss` string), **never** `Wave: N` → `Wave: N+1`! |
| **Early Loot Claim vs Walk-Out** | Template region transitions `7216 → 7316` while alive (`resumedAt > 0`, no `ActorDeath` / `DEFEATED`, `pendingKc == 0`) with `completed >= 1` (`Wave N completed!` was seen). | `WaveTracker.java:155-163`; `WaveTrackerTest.java:198-214` | Emits `ACTIVITY_COMPLETE` (`number = 0`, `detail = "claimed loot after wave " + completed`) so teammates see a successful cash-out rather than a fake Sol Heredit kill or a silent abort. |
| **Death / Defeat (*Doom* Edge Case)** | `onActorDeath` inside `7216` **or** `ChatMessageType.GAMEMESSAGE` `^You have been defeated!?$` (deduplicated within `SAME_DEATH_MS = 3_000` ms). | `WaveTracker.java:86,90,260-281`; `pajlads/DinkPlugin#472` | **Critical:** Dying to the *Doom* modifier (reaching max Doom stacks) kills the player instantly and can bypass standard `0 HP` / `ActorDeath` ordering. Watching `^You have been defeated!?$` alongside `onActorDeath` guarantees Doom deaths record the wave and active modifiers. **Unsafe** death for all accounts (including HCGIM). |
| **Full Completion (Wave 12 Clear)** | `ChatMessageType.GAMEMESSAGE`: `Your Sol Heredit kill count is: <col=ff0000>(\d+)</col>.` + `Duration: ([0-9:.]+)` (or total from Wave 12 completion line). | `EventText.java:30-31`; `WaveTracker.java:31,201-212,284-294` | Unlocks `Dizana's quiver (uncharged)` (Item `28947`) from the rewards chest. |

#### B. Fortis Colosseum Modifiers (All 14 Handicaps)

| Modifier Name | Max Tier | `net.runelite.api.gameval.VarbitID` Constant (`runelite-api:1.13.6`) | Raw Varbit ID | Untiered / Fallback Detection (when no varbit exists) |
| --- | --- | --- | --- | --- |
| **Bees!** | `III` | `VarbitID.COLOSSEUM_MODIFIER_TOXICITY_STACKS_CLIENT` | `9791` | *(Note: gameval names Bees! `TOXICITY`; also spawns `Bees` NPC `12823`.)* |
| **Blasphemy** | `III` | `VarbitID.COLOSSEUM_MODIFIER_BLASPHEMY_STACKS_CLIENT` | `9790` | — |
| **Doom** | `III` | `VarbitID.COLOSSEUM_MODIFIER_DOOM_STACKS_CLIENT` | `10681` | *(Do not confuse with `VarbitID.COLOSSEUM_DOOM_STACKS_CLIENT = 9801`, which is the live Doom debuff stack on the player during combat.)* |
| **Frailty** | `III` | `VarbitID.COLOSSEUM_MODIFIER_FRAILTY_STACKS_CLIENT` | `9796` | — |
| **Mantimayhem** | `III` | `VarbitID.COLOSSEUM_MODIFIER_MANTIMAYHEM_STACKS_CLIENT` | `4588` | — |
| **Myopia** | `III` | `VarbitID.COLOSSEUM_MODIFIER_MYOPIA_STACKS_CLIENT` | `9795` | — |
| **Reentry** | `III` | `VarbitID.COLOSSEUM_MODIFIER_REENTRY_STACKS_CLIENT` | `9792` | — |
| **Relentless** | `III` | `VarbitID.COLOSSEUM_MODIFIER_RELENTLESS_STACKS_CLIENT` | `9798` | — |
| **Solarflare** | `III` | `VarbitID.COLOSSEUM_MODIFIER_SOLARFLARE_STACKS_CLIENT` | `9797` | — |
| **Volatility** | `III` | `VarbitID.COLOSSEUM_MODIFIER_VOLATILITY_STACKS_CLIENT` | `9799` | — |
| **Dynamic Duo** | `I` (untiered) | *(None in `VarbitID`)* | — | Passed in CS2 script `4931` bitmask (modifier ID `8`) when modifier pillar widget (`InterfaceID.ColosseumModSelect` / group `865`) updates; in-wave observable when 2 Colossi/Manticores spawn on single-spawn waves. |
| **Quartet** | `I` (untiered) | *(None in `VarbitID`)* | — | Modifier ID `6` in widget `865`; in-wave observable on Wave 1+ when 4 Fremennik warbanders (`12814–12816`) spawn instead of 3 (or 1 spawns on Wave 12). |
| **Red Flag** | `I` (untiered) | *(None in `VarbitID`)* | — | Modifier ID `12` in widget `865`; in-wave observable when `Minotaur` spawns with NPC ID `12813` (Red Flag variant) instead of `12812`. |
| **Totemic** | `I` (untiered) | *(None in `VarbitID`)* | — | Modifier ID `7` in widget `865`; in-wave observable when `Healing totem` (`NpcID 12825`) spawns. |

#### C. Fortis Colosseum NPC IDs ([OSRS Wiki: Fortis Colosseum/Strategies](https://oldschool.runescape.wiki/w/Fortis_Colosseum/Strategies))

| NPC Name | NPC ID | First Wave | Role / Notes |
| --- | --- | --- | --- |
| **Jaguar warrior** | `12810` | Wave 1 (reinforcement) | 2×2 Melee reinforcement (`40s` into Waves 1–6). |
| **Serpent shaman** | `12811` | Wave 1 | 1×1 Magic attacker (`Waves 1–6`, plus reinforcement on `10–11`). |
| **Minotaur** (standard / Red Flag) | `12812` / `12813` | Wave 7 (reinforcement) | 3×3 Melee reinforcement (`40s` into Waves 7–11) that heals other monsters in LoS; `12813` is the un-safespottable *Red Flag* variant. |
| **Fremennik warband archer** | `12814` | Wave 1 | 1×1 Ranged warbander (`Waves 1–11`, plus `12` with *Quartet*). |
| **Fremennik warband seer** | `12815` | Wave 1 | 1×1 Magic warbander (`Waves 1–11`, plus `12` with *Quartet*). |
| **Fremennik warband berserker** | `12816` | Wave 1 | 1×1 Melee warbander (`Waves 1–11`, plus `12` with *Quartet*). |
| **Javelin Colossus** | `12817` | Wave 2 | 3×3 Ranged attacker. |
| **Manticore** | `12818` | Wave 4 | 3×3 tri-brid (`Ranged / Magic / Melee` 3-orb volley). |
| **Shockwave Colossus** | `12819` | Wave 7 | 3×3 Magic attacker. |
| **Sol Heredit** | `12821` | Wave 12 | 5×5 final boss (combat level `1,563`, `1,500 HP`). |
| **Bees** / **Healing totem** | `12823` / `12825` | Modifier-dependent | Spawned by *Bees!* (`12823`) and *Totemic* (`12825`). |

---

## 4. Other Wave-Based, Staged & Round Activities

### 4.1 Barbarian Assault (Waves 1–10, 4 Roles, 30s Horn Call Cycle)

- **OSRS Wiki sources:** [Barbarian Assault](https://oldschool.runescape.wiki/w/Barbarian_Assault), [Penance Queen](https://oldschool.runescape.wiki/w/Penance_Queen)
- **RuneLite sources:** `runelite-client/.../plugins/barbarianassault/BarbarianAssaultPlugin.java`, `Role.java`, `Round.java`; `runelite-api` `gameval/InterfaceID.java`, `gameval/VarbitID.java`, `widgets/WidgetID.java`

| Stage / Event | Exact Game Signal (Chat / Varbit / Varp / Region / NPC / Widget) | RuneLite Source Citation | Notes & Edge Cases |
| --- | --- | --- | --- |
| **Entering & Active Wave State** | Template Region IDs `7508, 7509` (Arena; Lobby/Outpost is `10322`, [`regions.txt:404`](../../src/main/resources/com/vinnyvangogh/groupironman/regions.txt#L404)) + `Varbits.IN_GAME_BA` (`varbit 3923 == 1`) + `WidgetLoaded` for one of the 4 role overlay groups. | `BarbarianAssaultPlugin.java` (`onWidgetLoaded`, `onVarbitChanged`); `DiscordGameEventType.java` (`7508, 7509`) | Always a 5-player team (`TeamSummary` names GIM roster mates and counts randoms as `+N others`). |
| **Current Wave (`1–10`) & Player Role** | Loaded role HUD widget group (`net.runelite.api.gameval.InterfaceID`):<br>• **Attacker:** `InterfaceID.BARBASSAULT_OVER_ATT` (`group 485`)<br>• **Defender:** `InterfaceID.BARBASSAULT_OVER_DEF` (`group 486`)<br>• **Collector:** `InterfaceID.BARBASSAULT_OVER_COL` (`group 487`)<br>• **Healer:** `InterfaceID.BARBASSAULT_OVER_HEAL` (`group 488`)<br>Wave number is read from child index `BA_WAVE_NUM_INDEX = 2` (`"Wave (\d+)"`, where Wave `10` = *Penance Queen*). | `BarbarianAssaultPlugin.java` (`BA_WAVE_NUM_INDEX = 2`, `START_WAVE = "1"`, `case InterfaceID.BARBASSAULT_OVER_ATT..COL`); `WidgetID.java` (`BA_ATTACKER_GROUP_ID`..`BA_HEALER_GROUP_ID`) | Whichever of `485..488` is loaded identifies both the player's **exact role** (`Attacker`, `Defender`, `Collector`, `Healer`) and the **current wave (`1–10`)** in one check. |
| **Rotation / Wave Mechanics** | **No spawn rotation:** Each wave `1–10` has a fixed Penance quota (Fighters, Rangers, Healers, Runners) spawning from fixed role caves on a 10-tick (`6.0s`) cycle, while role horn calls rotate randomly every `50 ticks` (`30.0s`). On **Wave 10**, `Penance Queen` (`NpcID 5775`) spawns `25 ticks` (`15.0s`) after all regular Penance are killed (checked on every 10-tick boundary from wave start) or at `4:00` (`400 ticks`), whichever comes first. | OSRS Wiki *Penance Queen*; `BarbarianAssaultPlugin.java` | For `RUNELITE-208`, display the player's **role** (`Attacker`, `Defender`, `Collector`, `Healer`) in the slot where Fight Caves shows rotation, plus per-wave splits (`Waves 1–10`). Do **not** transmit horn call timers. |
| **Wave Completion (`1–9`) & Queen Completion (`10`)** | • **Wave end (`1–10`):** `WidgetLoaded` with `groupId == InterfaceID.BARBASSAULT_WAVECOMPLETE` (`group 497`) or `VarbitID.BARBASSAULT_AREAEXIT_PENDING` transitioning to `0`.<br>• **Wave 10 Queen Victory:** `client.getWidget(InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_QUEENREWARDS)` is non-null and its text contains `ENDGAME_REWARD_NEEDLE_TEXT = "<br>5"` (the 5 base bonus points awarded to off-roles alongside 80 points in the active role). | `BarbarianAssaultPlugin.java` (`InterfaceID.BARBASSAULT_WAVECOMPLETE`, `BARBASSAULT_COMPL_QUEENREWARDS`, `ENDGAME_REWARD_NEEDLE_TEXT = "<br>5"`, `VarbitID.BARBASSAULT_AREAEXIT_PENDING`) | `BarbarianAssaultPlugin` uses this exact `BARBASSAULT_COMPL_QUEENREWARDS` check to print wave and full-round (`Waves 1–10`) durations. |
| **Wipe / Failure** | A player dying or a teammate leaving mid-wave returns the team to the wave lobby (`10322`) without `InterfaceID.BARBASSAULT_WAVECOMPLETE` (`497`) loading. | `BarbarianAssaultPlugin.java` | Safe death for all accounts (including HCGIM). |

---

### 4.2 The Gauntlet & Corrupted Gauntlet (2 Stages: Prep vs Hunllef)

- **OSRS Wiki sources:** [The Gauntlet](https://oldschool.runescape.wiki/w/The_Gauntlet), [Crystalline Hunllef](https://oldschool.runescape.wiki/w/Crystalline_Hunllef), [Corrupted Hunllef](https://oldschool.runescape.wiki/w/Corrupted_Hunllef)
- **RuneLite sources:** `runelite-api` `Varbits.java` / `gameval/VarbitID.java` (`9177`, `9178`), `gameval/InterfaceID.java` (`InterfaceID.GauntletOverlay.CONTENT` group `637`); `runelite-client` `DiscordGameEventType.java` (`7512`, `7768`), `ui/overlay/WidgetOverlay.java`

| Stage / Event | Exact Game Signal (Chat / Varbit / Varp / Region / NPC / Widget) | RuneLite Source Citation | Notes & Edge Cases |
| --- | --- | --- | --- |
| **Entering & Mode (`Normal` vs `Corrupted`)** | Unwrapped Template Region ID (`WorldPoint.fromLocalInstance`):<br>• **The Gauntlet (Crystalline):** `7512` (`regions.txt:410`)<br>• **Corrupted Gauntlet:** `7768` (`regions.txt:445`)<br>• **Lobby:** `12127` (`regions.txt:446`)<br>+ `Varbits.GAUNTLET_ENTERED` (`varbit 9178 == 1`) | `DiscordGameEventType.java` (`7512`, `7768`); `Varbits.java` (`GAUNTLET_ENTERED = 9178`) | Strict solo instance. `regionId == 7768` vs `7512` immediately distinguishes `Corrupted Gauntlet` from `The Gauntlet`. |
| **Stage 1 (`Prep`) vs Stage 2 (`Hunllef`)** | • **Stage 1 (`Prep`):** `varbit 9178 == 1 && varbit 9177 == 0`. Prep countdown (`10:00` = `1,000 ticks` in Normal; `7:30` = `750 ticks` in Corrupted) is shown on `InterfaceID.GauntletOverlay.CONTENT` (`widget group 637`).<br>• **Stage 2 (`Hunllef`):** `Varbits.GAUNTLET_FINAL_ROOM_ENTERED` (`varbit 9177 == 1`) when the player passes through the barrier into the Hunllef chamber (or is force-teleported in when the prep timer reaches `0:00`). | `Varbits.java` (`GAUNTLET_FINAL_ROOM_ENTERED = 9177`, `GAUNTLET_ENTERED = 9178`); `WidgetOverlay.java` (`InterfaceID.GauntletOverlay.CONTENT`, `"GAUNTLET_TIMER_CONTAINER"`) | **Split structure for `RUNELITE-208`:** Every Gauntlet run has two natural splits measured in ticks:<br>1. **Prep split:** `9178: 0 → 1` until `9177: 0 → 1`<br>2. **Hunllef split:** `9177: 0 → 1` until kill or death<br>3. **Total time:** Prep + Hunllef (matching the game's completion chat). |
| **Rotation / Map Layout** | **Procedural 7×7 room grid (no numbered rotation):** The starting room and Hunllef room are fixed near the centre; demibosses (*Bear*, *Dragon*, *Dark Beast*) spawn randomly only in the **12 outer middle-three border rooms** (3 per cardinal edge, never the 4 corners). Hunllef morphs between Ranged/Magic protection every 6 off-prayer player hits and switches its own attack style every 4 standard attacks (`Crystalline Hunllef` NPCs `9021–9024`, `Corrupted Hunllef` NPCs `9035–9038`). | OSRS Wiki *The Gauntlet*; `AGENTS.md` *Boss & Combat Restrictions* | **Compliance rule:** Never count Hunllef's 4-attack cycle, 6-hit prayer switch, stomp, or tornado ticks (`AGENTS.md`: *No attack counters / No prayer switching indicators*). Report only `Prep` vs `Hunllef` stage and their split times. |
| **Death / Leave** | • **Death:** `onActorDeath` while `varbit 9178 == 1` (tagged with `Prep` if `9177 == 0` or `Hunllef` if `9177 == 1`). **Unsafe death for HCGIM** (`VarbitID.IRONMAN == 5`)!<br>• **Early leave:** `varbit 9178` resets `1 → 0` (returning to lobby `12127`) without `ActorDeath` or completion KC chat. | `GroupEventEngine.java:1070-1117` | Distinguishing `Died in Corrupted Gauntlet (Hunllef, 3:42 prep + 2:18 boss)` vs leaving an unfavorable prep early (`9178 → 0` with `9177 == 0` and no `ActorDeath`) prevents false death posts when resetting prep! |
| **Activity Completion** | `ChatMessageType.GAMEMESSAGE`:<br>1. `Your (?:Gauntlet\|Corrupted Gauntlet) completion count is: <col=ff0000>(\d+)</col>.`<br>2. `Challenge duration: <col=ff0000>([0-9:.]+)</col>` / `Preparation time: ([0-9:.]+). Hunllef kill time: ([0-9:.]+).` | `EventText.java:30-31`; `EventChat.java:21-22`; OSRS Wiki *The Gauntlet* | When precise game timer settings are enabled in OSRS, the game prints both `Preparation time:` and `Hunllef kill time:` alongside total `Challenge duration:`. |

---

### 4.3 Other Wave, Room-Sequence & Round Activities (Raids, Ket-Rak, Sepulchre & Non-Rotation Contrasts)

The brief notes: *"for the Fight Caves, Inferno, Fortis Colosseum, and any other wave-based activity with rotations (e.g. Pest Control waves aren't, but the Barbarian Assault waves and the Gauntlet stages are worth listing)"*. Below is the complete detection table for the remaining staged/wave activities in `docs/plans/activities.md` and how each behaves with respect to rotations and splits:

| Activity | Template Region IDs (`regions.txt`) | Entering & Stage / Wave Detection (Varbits / Varps / Widgets / Chat) | Rotation Mechanics (Does it have rotations?) | Split Timing Granularity (`RUNELITE-208`) | Completion & Death Signals | RuneLite Source Citations |
| --- | --- | --- | --- | --- | --- | --- |
| **TzHaar-Ket-Rak's Challenges** *(1–6 Jads)* | `9043` *(same arena template as The Inferno; outer city `10064`)* | Entered via Ket-Rak challenge dialog (`Challenges 1–6`) in `9043` **without** `Wave: N` chat; count of spawned `JalTok-Jad` (`NpcID 7700`, `1..6`) on tick 1 identifies `Challenge 1 (1 Jad)` .. `Challenge 6 (6 Jads)`. | **FixedJad layouts per challenge (`1–6`), no random rotation.** | Single-stage timer (`Challenge start → last Jad killed`). | **Complete:** `Your TzHaar-Ket-Rak's [First–Sixth] Challenge completion count is: (\d+).` + `Duration: ([0-9:.]+)`<br>**Death:** `onActorDeath` in `9043` (safe for regular/GIM, **unsafe for HCGIM**). | `ChatCommandsPlugin.java` (`TzHaar-Ket-Rak's [First-Sixth] Challenge`); `DiscordGameEventType.java` (`9043`) |
| **Theatre of Blood (ToB)** *(Entry / Normal / Hard Mode)* | Lobby `14642`<br>1. **Maiden:** `12613`<br>2. **Bloat:** `13125`<br>3. **Nylocas:** `13122`<br>4. **Sotetseg:** `13123` *(maze `13379`)*<br>5. **Xarpus:** `12612`<br>6. **Verzik:** `12611`<br>Vault: `12867` | `Varbits.THEATRE_OF_BLOOD` (`varbit 6440`: `0`=outside, `1`=in party, `2`=in raid, `3`=dead/spectating) + party HP orb varbits `6442–6446` (`0`=empty, `1–27`=HP, `30`=dead) + `InterfaceID.TobHud.STATUS_CONTAINER` + unwrapped room template region. | **Linear 6-boss sequence.** Room 3 (*Nylocas Vasilias*, `13122`) has **31 predetermined Nylocas waves** across 3 lanes (`West`, `South`, `East`) before the Nylocas boss spawns (`Wave 31` + boss). Compliance: **never** highlight Nylocas pillars/timers; track only room-by-room splits! | **6 Room Splits** (`Maiden`, `Bloat`, `Nylocas`, `Sotetseg`, `Xarpus`, `Verzik`), matching OSRS's own `"Wave 'The Maiden of Sugadinti' (Normal Mode) complete! Duration: M:SS.ss Total: M:SS.ss"` chat lines! | **Room split chat:** `^Wave '(.+?)'(?: \(.+?\))? complete! Duration: ([0-9:.]+).*`<br>**Complete:** `Theatre of Blood.* completion count is: (\d+).`<br>**Death/Wipe:** Orb varbit `== 30` / all orbs `== 30` (**unsafe for HCGIM**). | `DiscordGameEventType.java` (`12611..13379`); `Varbits.java` (`THEATRE_OF_BLOOD = 6440`, `THEATRE_OF_BLOOD_ORB1..5 = 6442..6446`); `WidgetOverlay.java` |
| **Chambers of Xeric (CoX)** *(Normal & Challenge Mode)* | Olm: `12889`<br>Floors: `13136..13141, 13145, 13393..13397, 13401` (`regions.txt:408`) | `VarbitID.RAIDS_CLIENT_INDUNGEON` (`Varbits.IN_RAID = 5432 == 1`), `RAID_STATE` (`5425`), `RAID_PARTY_SIZE` (`5424`), `RAIDS_CLIENT_PARTYSCORE` (`5431`), `VarPlayerID.RAIDS_PLAYERSCORE` (`4609`), CM varbit (`6385`), active boss name on `InterfaceID.HpbarHud`. | **Normal Mode:** Combat rooms are generated from **2 fixed rotation rings** (`[Tekton, Crabs?, Vasa, Tightrope?, Vanguards, Ice Demon, Thieving?, Vespula]` and `[Muttadiles, Mystics, Shamans, Guardians]`) walked forward or backward per floor.<br>**Challenge Mode (CM):** Fixed 3-floor rotation containing every combat & puzzle room in the exact same order every run! | **Floor Splits (`Floor 1`, `Floor 2`, `Floor 3`, `Great Olm`)** from `FRIENDSCHATNOTIFICATION` / `GAMEMESSAGE`:<br>• `"The raid has begun!"`<br>• `"Upper level complete! Duration: ([0-9:.]+)"`<br>• `"Middle level complete! Duration: ([0-9:.]+)"`<br>• `"Lower level complete! Duration: ([0-9:.]+)"` | **Complete:** `"Congratulations - your raid is complete!"` + `"Your completed Chambers of Xeric(?: Challenge Mode)? count is: (\d+)."`<br>**Death:** `onActorDeath` while `IN_RAID == 1` (**safe death even for HCGIM**). Compliance: **never** pre-scout unentered rooms. | `RaidsPlugin.java`; `EventWatchers.java:171-226`; `GroupEventEngine.java:592-662` |
| **Tombs of Amascut (ToA)** *(Entry `<150` / Normal `150–299` / Expert `300–600`)* | Lobby `13454`, Nexus `14160`<br>• **Scabaras / Kephri:** `14162` / `14164`<br>• **Het / Akkha:** `14674` / `14676`<br>• **Apmeken / Ba-Ba:** `15186` / `15188`<br>• **Crondis / Zebak:** `15698` / `15700`<br>• **Wardens P1–P2 / P3:** `15184` / `15696`<br>• **Loot:** `14672` | `VarbitID.TOA_CLIENT_RAID_LEVEL` (`14380`), party orb varbits `14346–14353` (`0`=empty, `1–27`=HP, `30`=dead), `VarPlayerID.TOA_PERSONAL_CONTRIBUTION` (`3606` at raid end), unwrapped room template region. | **Player-chosen path order + Invocations:** Players complete the 4 paths (`Crondis`, `Scabaras`, `Het`, `Apmeken`) in any order from the Nexus (`14160`), followed by `Wardens`. Raid level (`0–600`) acts as the modifier summary (`"350 Expert"`). | **9 Stage Splits:** 4 puzzle rooms + 4 path bosses + `Wardens`, measured on template region transitions (`14160 → puzzle → boss → 14160`) or the game's challenge/path completion chat lines. | **Complete:** `Tombs of Amascut.* completion count is: (\d+).` + entering Burial Chamber (`14672`).<br>**Death:** Orb varbit `== 30` / `onActorDeath` (**unsafe for HCGIM**). | `DiscordGameEventType.java` (`14160..15700`); Hub `LlemonDuck/tombs-of-amascut` |
| **Hallowed Sepulchre** *(Floors 1–5)* | `8797, 9051..9054, 9309, 9563, 9565, 9821, 10074, 10075, 10077` (`regions.txt:417`) | Unwrapped template region per floor (`Floor 1`..`Floor 5`) + HUD widget `InterfaceID.HALLOWED_OVERLAY` (`WidgetID.HALLOWED_SEPULCHRE_TIMER_GROUP_ID`). | **Each floor (`1–5`) has 2–3 layout variants** (north/south/east/west starting stairs), plus fixed statue/flame obstacle cycles. Compliance: **never** track statue/arrow/lightning cycles. | **Per-Floor Splits (`Floor 1` .. `Floor 5`)**, printed by the game at each floor obelisk (`"Floor N time: M:SS.ss"`). | **Complete:** Looting `Grand Hallowed Coffin` (`Your Grand Hallowed Coffin count is: (\d+).`) or leaving via obelisk.<br>**Death:** Safe exit on time-out; unsafe if player dies to traps without ring of life. | `DiscordGameEventType.java`; `WidgetID.java` (`HALLOWED_SEPULCHRE_TIMER_GROUP_ID`) |
| **Pest Control** *(Non-wave portal round — contrast)* | Island `10536`, Outpost `10537` (`regions.txt:438`) | `InterfaceID.PestStatusOverlay.PEST_STATUS_PORT2` (`widget group 408` loaded = in game; lander boat is `InterfaceID.PestLanderOverlay.INFO` group `407`). Portal health (`PEST_STATUS_PORTTXT1..4`), Void Knight HP (`CONTENT_LEFT`), Activity bar (`CONTENT_RIGHT`). | **Not wave-based; has 6 portal shield drop rotations:** The 4 portals (*Purple/W*, *Blue/E*, *Yellow/SE*, *Red/SW*) drop their shields one by one at fixed tick marks: **25 ticks (`15s`), 75 ticks (`45s`), 125 ticks (`1m 15s`), and 175 ticks (`1m 45s`)** in 1 of 6 possible permutations (`W-E-SE-SW`, `W-SE-E-SW`, `E-SW-SE-W`, `E-W-SW-SE`, `SE-SW-W-E`, `SW-W-E-SE`). | **4 Portal-Fall Splits** (`Landing → 1st portal → 2nd → 3rd → 4th portal`), or single round time (`~1m 45s`). | **Complete:** Void Knight victory dialog / commendation points chat.<br>**Loss:** Void Knight reaches 0 HP or 20m timer expires. Safe death. | `PestControlPlugin.java`; `PestControlOverlay.java` (`InterfaceID.PestStatusOverlay.PEST_STATUS_PORT2..4`) |
| **Wintertodt, Tempoross, Guardians of the Rift** *(Single-round skilling bosses — contrast)* | • **Wintertodt:** `6462`<br>• **Tempoross:** `12076` *(lobby `12588`)*<br>• **GotR:** `14484` | • **Wintertodt:** `Varbits.WINTERTODT_TIMER` (`7980` / `VarbitID.WINT_TRANSMIT_RESPAWNDELAY`), widget `396` (`InterfaceID.Wint.*`)<br>• **Tempoross:** `InterfaceID.TemporossHud.STATUS` (`group 437`: Energy %, Essence %, Storm Intensity %, Permits)<br>• **GotR:** widget `746` (Guardian power %, Elemental/Catalytic energy) | **No wave rotations:** Single continuous round per kill/subdue. Granular status is **team mode** (`solo` / `duo` / `mass (N)` via `TeamSummary`, debounced 3 ticks on entry) + live boss energy/points. | **Single round duration** (`Subdued in M:SS` for Tempoross; round start → subdue/rift closed for Wintertodt/GotR). | • **Wintertodt:** `Your subdued Wintertodt count is: (\d+).` (unsafe death!)<br>• **Tempoross:** `Your Tempoross kill count is: (\d+).` + `Subdued in ([0-9:.]+)` (safe wipe at 100% storm)<br>• **GotR:** `Amount of rift closed: (\d+).` | `WintertodtPlugin.java`; `WidgetOverlay.java` (`InterfaceID.TemporossHud.STATUS`); `EventChat.java:21-22` |

---

## 5. Measuring Per-Wave Split Timing in Game Ticks (`RUNELITE-208`)

### 5.1 Why splits must be measured in game ticks (`0.6s / tick`) rather than wall-clock milliseconds

1. **OSRS engine quantization:** Every OSRS wave transition, monster spawn, death check, and official in-game `Duration:` message is quantized to server game ticks (`1 tick = 600 ms = 0.6 s`).
2. **Automatic pause & logout immunity:** In [`WaveTracker.java:101-104`](../../src/main/java/com/vinnyvangogh/groupironman/WaveTracker.java#L101-L104), wall-clock time (`System.currentTimeMillis()`) requires manual `activeMs` + `resumedAt` bookkeeping on logout/relog, and still suffers from client lag or OS sleep skew. Incrementing an `int waveTicks` counter once per `@Subscribe onGameTick(GameTick)` while the player is logged in inside the activity's template region automatically pauses during between-wave logouts (`GameState.LOGIN_SCREEN`), world hops (`GameState.HOPPING`), and Colosseum inter-wave modifier selection!
3. **Exact parity with OSRS official timers:** With tick-precise game timers enabled in OSRS settings, the game formats durations as `M:SS.cc` where `.cc` is one of `.00`, `.60`, `.20`, `.80`, `.40` (`ticks * 0.6s`). Measuring in ticks matches the game's own wave and completion timers with zero rounding drift.

### 5.2 Exact start-tick and end-tick boundaries per activity

| Activity | Wave / Stage $w$ **Start Tick (`t_start`)** | Wave / Stage $w$ **End Tick (`t_end`)** | Special Timing Rules & Offsets |
| --- | --- | --- | --- |
| **TzHaar Fight Cave (`9551`)** | Tick when `GAMEMESSAGE` `^Wave: (\d+)$` arrives for wave $w$ (`1..63`). | • For $w \in [1..62]$: tick when `^Wave: (w+1)$` arrives (or tick when `TZHAAR_PAUSED_MESSAGE` + logout occurs after wave $w$ ends).<br>• For $w = 63$ (`TzTok-Jad`): tick when `Your TzTok-Jad kill count is: (\d+)` arrives. | The 2-tick (`1.2s`) spawn delay between killing the last monster of wave $w$ and the `Wave: (w+1)` message is included in wave $w$'s split, so $\sum_{w=1}^{63} \text{waveTicks}[w]$ equals the total run duration printed on `Duration: M:SS`. |
| **The Inferno (`9043`)** | • **Wave 1:** Tick when `^Wave: 1$` arrives, **initialized at `waveTicks[1] = 10` (`6.0s`)**.<br>• **Waves `2–69`:** Tick when `^Wave: w$` arrives (`waveTicks[w] = 0`). | • For $w \in [1..68]$: tick when `^Wave: (w+1)$` arrives.<br>• For $w = 69$ (`TzKal-Zuk`): tick when `Your TzKal-Zuk kill count is: (\d+)` arrives. | **Why Wave 1 starts at `10 ticks` (`6.0s`):** As documented in RuneLite's `TimersAndBuffsPlugin.java` (*"The first wave message of the inferno comes six seconds after the ingame timer starts counting"*) and Mod Ash's confirmation (`runelite/runelite#12401`), the server starts the Inferno run clock when the player stands up from the entry jump animation, exactly **10 ticks (`6.0s`) before** `Wave: 1` prints in chat. Starting `runTicks` / `waveTicks[1]` at `10` makes cumulative splits match the official Zuk `Duration:` chat line to the tick. |
| **Fortis Colosseum (`7216`)** | • **Waves `1–11`:** Tick when `^Wave: w$` arrives.<br>• **Wave `12` (`Sol Heredit`):** Tick when `"Sol Heredit jumps down from his seat"` (or `^Wave: 12$`) arrives. | • **Waves `1–11`:** Tick when `^Wave (\d+) completed!` arrives.<br>• **Wave `12`:** Tick when `Your Sol Heredit kill count is: (\d+)` (or `Wave 12 completed!`) arrives. | **Inter-wave pause:** Between `Wave w completed!` and `Wave: (w+1)`, the player is choosing their next handicap at Minimus or inspecting the loot chest, and the game's run timer is **stopped**. Therefore, `inWave` must be set to `false` on `Wave w completed!` so inter-wave ticks are excluded. Furthermore, if the chat line includes `Wave duration: M:SS.ss`, parse it directly into ticks (`parseDurationToTicks`) so the recorded split is guaranteed identical to the game's chat line. |
| **The Gauntlet & Corrupted Gauntlet (`7512` / `7768`)** | • **Stage 1 (`Prep`):** Tick when `Varbits.GAUNTLET_ENTERED (9178)` transitions `0 → 1`.<br>• **Stage 2 (`Hunllef`):** Tick when `Varbits.GAUNTLET_FINAL_ROOM_ENTERED (9177)` transitions `0 → 1`. | • **Stage 1 (`Prep`):** Tick when `9177` transitions `0 → 1`.<br>• **Stage 2 (`Hunllef`):** Tick when completion KC chat or `onActorDeath` fires. | Gives clean `Prep` (`e.g. 5:42`) and `Hunllef` (`e.g. 3:18`) splits that sum to total `Challenge duration: 9:00`. |
| **Barbarian Assault (`7508, 7509`)** | Tick when `InterfaceID.BARBASSAULT_OVER_{ATT,DEF,COL,HEAL}` (`485..488`) loads with child `2` = `"Wave w"` (`1..10`). | Tick when `InterfaceID.BARBASSAULT_WAVECOMPLETE` (`497`) loads. | Pauses automatically in the lobby (`10322`) between waves. |
| **Theatre of Blood (`12611..13379`)** | Tick when player enters boss template region (`12613`, `13125`, `13122`, `13123`, `12612`, `12611`) and boss spawns / HP HUD opens. | Tick when `^Wave '(.+?)'(?: \(.+?\))? complete! Duration: ([0-9:.]+)` arrives. | Parse the game's exact `Duration: ([0-9:.]+)` from the room-complete chat line as the authoritative room split. |

### 5.3 Reference tick-conversion & split-state formulas (for Tuesday's `RUNELITE-208` implementation)

```text
1 game tick = 600 ms = 0.6 seconds
ticks -> milliseconds : ms = ticks * 600L
ticks -> M:SS (coarse): totalSec = (ticks * 3) / 5;  String.format("%d:%02d", totalSec / 60, totalSec % 60)
ticks -> M:SS.t (exact): tenths = (ticks * 6) % 10;  String.format("%d:%02d.%d", totalSec / 60, totalSec % 60, tenths)
"M:SS.ss" -> ticks    : Math.round(parsedSeconds / 0.6)
```

### 5.4 Edge cases for split & rotation tracking in `RUNELITE-208`

1. **Mid-run plugin enable / missed Wave 1 (`startedFromWave1 == false`):**
   - If the player enables the plugin or joins mid-run on Wave $w > 1$ with no saved profile state (`WAVE_RUN_KEY`), record per-wave splits for waves $w, w+1, \dots$, leave waves $1 \dots w-1$ as `-1` (`unknown`), and **never** overwrite cumulative run PBs or wave-1-based rotation inference with a partial run.
2. **Between-wave logout in Fight Caves / Inferno:**
   - When the player requests a pause (`TZHAAR_PAUSED_MESSAGE`), the game finishes the current wave $w$, pauses before spawning wave $w+1$, and lets the player log out. When `GameState.LOGIN_SCREEN` occurs (or `TZHAAR_PAUSED_MESSAGE` is followed by wave end), freeze `currentWaveTicks`. When the player logs back into `9551` or `9043`, the game immediately starts wave $w+1$ and prints `Wave: (w+1)` (or reprints `Wave: w` if they logged out before the wave ended).
3. **Wire budget on `"k":"activity"` (`ActivityCodec.MAX_CHARS = 2048`):**
   - A 69-wave Inferno run has at most 69 integer tick counts (`<= 4` digits each). Encoding completed wave splits as a compact comma-separated tick string (e.g., `"splits":"18,22,15,29,..."`, `~210 chars` at wave 69) plus `"rot":"Rotation 5 (Jad SE)"` (`<= 32 chars`) fits comfortably inside `ActivityCodec`'s existing payload budget while keeping older clients (`gim-test-2`..`gim-test-7`) 100% forward- and backward-compatible (unknown JSON keys in `"k":"activity"` are ignored by `ActivityCodec.decode`).
