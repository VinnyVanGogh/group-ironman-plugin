# Source map — what to port from questpath-plugin

Read-only survey, taken 2026-09-17 against
`/Users/vincevasile/Documents/dev/questpath-plugin` at runelite-client 1.12.39.
**Nothing has been ported.** Every path below is a real file that was opened;
none of this is recalled.

All Java paths are relative to
`src/main/java/com/vinnyvangogh/questpath/` unless written out in full.

A note that governs the whole map: questpath-plugin is a **companion to a web
app**. Build rules arrive over a push, the quest graph can be refetched over
HTTP, and account state is sent out. Build Guard makes **zero network calls**,
so every port below is also a severing. The "drags in" column is where that
severing costs something.

---

## 1. The build rules model

| File | Lines | What it is |
| --- | --- | --- |
| `BuildRule.java` | 188 | The model: skill, type, value, note, blocking. |
| `BuildRuleCodec.java` | 197 | JSON in, JSON out. Pure `String` → `String`, one rule per skill. |
| `LocalBuildRules.java` | 118 | Per-account store over `ConfigManager`. |
| `BuildRuleSections.java` | 42 | Config enum: blocking / informational / both. |
| `BuildRulesEditor.java` | 201 | The Swing form that writes a rule. |
| `BuildRulesOverlay.java` | 265 | Headroom on screen, escalating as a cap nears. |

**Port all six.** This is the closest thing to a clean carve-out in the
repo — `LocalBuildRules` already exists precisely because rules had to work
with no web app running, and its javadoc says so.

**Two things the port must not lose:**

1. `BuildRule.blocking()` ORs the wire field with a type check
   (`NO_XP` or `HARD_CAP`). Its javadoc records why: the generated vectors
   carry no `blocking` flag, and trusting the field alone made every rule
   non-blocking — **94 of 218 verdicts silently came back ELIGIBLE for quests
   that end a pure.**
2. Four types are two different sentences. NO_XP and HARD_CAP end a build;
   SOFT_CAP and GOAL only inform. Drawing them alike makes the real warning
   stop meaning anything.

**Drags in / out of scope:**

- `BuildRule`'s class javadoc says "questpath owns these — the plugin displays
  them and never writes them back". **That is the pushed-rule origin and it
  goes.** In Build Guard `LocalBuildRules` is the only source, and the
  `@SerializedName("blocking")` wire field becomes vestigial — keep the field
  or drop it, but the type-derived check stays either way.
- `LocalBuildRules` references `QuestpathConfig.GROUP` and its own frozen key
  `localBuildRules`. Both get renamed to Build Guard's group, and **the new
  names freeze on first publish.**
- It stores per **RSProfile** (`getRSProfileConfiguration`), not per RuneLite
  profile, so two accounts under one profile do not overwrite each other. Keep
  that; the javadoc explains the failure it prevents.
- `BuildRulesOverlay extends QuestpathOverlay` — see §8.

---

## 2. The verdict engine and prerequisite closure

| File | Lines | What it is |
| --- | --- | --- |
| `QuestClassifier.java` | 296 | **The engine.** Seven statuses from graph + account. |
| `QuestStatus.java` | 49 | The verdict enum, with `permanent()` and `doable()`. |
| `PlayerState.java` | 175 | Levels, XP, completed/in-progress quests, rules. |
| `QuestVerdicts.java` | 153 | Grouping and labels for a list view. |
| `Boosts.java` | 187 | Whether a shortfall is small enough to drink away. |
| `QuestBlockers.java` | 210 | Not yet read in detail; sits beside the classifier. |

**Port `QuestClassifier`, `QuestStatus`, `PlayerState` and `Boosts` as one
unit.** `QuestClassifier` is a port of questpath's Go
`internal/planner/classify.go` and `guard.go`, and it holds the rule this
plugin exists for:

- **Both sides are checked.** A reward that pushes a capped skill past its cap
  **and** a requirement that demands a level above one. The javadoc's example:
  Land of the Goblins is not blocked by anything it grants — a prerequisite
  requires Attack 15 and Prayer 25 while both are capped at 1.
- **NO_XP means any XP at all**, including XP that raises no level.
- **A permanently blocked prerequisite poisons everything downstream**
  (`UNREACHABLE`), with memoisation and cycle detection.
- **`startLevelFor`**: a NO_XP skill still sits at its start level, so
  Hitpoints is 10 and everything else 1. Treating the floor as 1 would block
  quests asking for nothing the account has not already got.
- **Boostable is not consulted on the requirement side** — a boost is relative
  to base level, so a capped skill cannot boost past its cap.

**Drags in / out of scope:**

- `QuestStatus.REFUSED` **cannot be learned locally.** `QuestVerdicts` says it
  plainly: "the plugin has no way to learn it — that flag lives in questpath".
  Build Guard either drops the state or gives the player a way to set it. It
  is in `docs/SPEC.md` under open decisions.
- `PlayerState.hasRules()` exists because rules used to arrive over a sync.
  With `LocalBuildRules` as the only source the flag still matters — no rules
  still means no BUILD_BLOCKED is possible — but the sentence the UI shows
  changes from "not synced" to "you have not written any rules".
- Quest completion is matched **by name, not by id**, and the javadoc is
  emphatic: RuneLite's `Quest` ids and questpath's SQLite rowids are unrelated
  id spaces, and comparing them produced a completed set that was pure
  coincidence. Keep name matching.
- `QuestVerdicts` is list-view presentation. Port it only when scope item 2
  needs a list; the verdict itself does not depend on it.
- `QuestBlockers.java` was not read line by line in this survey. Read it
  before porting — do not assume it is redundant.

---

## 3. The bundled quest graph, and how it is built

| File | Size | What it is |
| --- | --- | --- |
| `src/main/resources/com/vinnyvangogh/questpath/quests.json` | 1.38 MB | The bundle. |
| `QuestGraph.java` | 1171 | Loader and model. |
| `QuestGraphFetcher.java` | 190 | **Network.** Refetch from a self-hosted questpath. |

**How it is built:** generated by questpath's Go command
**`-export-quest-graph`**, and bundled as a jar resource. `QuestGraph.load`
reads it with `getResourceAsStream("quests.json")`. Nobody transcribes 218
quests by hand.

**Port `QuestGraph`'s loader and the parts of the model the classifier
touches:** `Quest`, `Requirement`, `Reward`, `prerequisites()`,
`prerequisitesStartedOnly()`, `Boost`.

**Drags in / out of scope:**

- **`QuestGraphFetcher` does not come.** It is an HTTP client for a
  self-hosted instance — exactly what Build Guard's "zero network calls" rule
  forbids. The bundle is the only source.
- **The bundle carries far more than Build Guard needs.** Graph version 12
  includes `shops()` (166 shops with walkable tiles), `diary_tasks`, a
  1,301-row item catalogue with `sources()`, `itemId()`, `unresolved()` and
  `shopNames()`, `DropLocation`, `Reachability`, `Alternative`, and
  `Quest.startX()/startY()`. Every one of those serves a feature that is
  **out of scope** (shop run, diaries, routing). A slimmed export would cut
  the 1.38 MB substantially.
- **But the generator lives in the Go app.** Slimming the bundle means a new
  export flag in questpath, not a change here. Until then, ship the fat bundle
  or write a build-time filter — that is an open decision in `docs/SPEC.md`.
- **The version gate is load-bearing.** `QuestGraph` refuses a version it does
  not know rather than parsing it, because a graph missing a field parses into
  nulls and then answers "eligible" for a quest whose requirements were never
  read. Keep that behaviour and keep the version number honest.
- **The bundle goes stale by design** — it updates when the plugin is
  released, not when the wiki changes.

---

## 4. The 218 verdict vectors

| File | What it is |
| --- | --- |
| `src/test/resources/com/vinnyvangogh/questpath/planner-vectors.json` (26 KB) | One account (skills, 38 completed quests, 24 build rules) and **218 expected verdicts** — `quest_id`, `slug`, `status`. |
| `src/test/java/com/vinnyvangogh/questpath/PlannerVectorsTest.java` | Runs the classifier against them. |
| `src/test/java/com/vinnyvangogh/questpath/QuestVerdictsTest.java` | Grouping-level tests. |
| `src/test/resources/com/vinnyvangogh/questpath/lamp-vectors.json` (48 KB) | **9 lamp cases**, shared with the Go side. |
| `src/test/java/com/vinnyvangogh/questpath/LampVectorsTest.java` | Runs the advisor against them. |

**Port the JSON unchanged and the tests with it.** The account in the vectors
is a real capped build: Attack, Defence and Prayer at NO_XP, Hitpoints hard
capped at 28, Ranged and Magic at 56, twenty goals besides.

**Two corrections to the brief, both minor and both worth knowing:**

- The file is **`planner-vectors.json`**, not a verdict-vectors file, and the
  test is `PlannerVectorsTest`. "218" is the length of its `expected` array,
  which is exactly 218 quest verdicts — the number in the brief is right.
- The vectors are regenerated by questpath's **`-export-planner-vectors`**;
  the test's own failure message says so. Build Guard cannot regenerate them
  without the Go app. **That is fine and is the point**: a failing vector is a
  porting bug, not a vector to edit.

**Drags in:** `PlannerVectorsTest` needs `QuestGraph.load` and therefore the
bundled `quests.json`, plus Gson (injected, never constructed).

---

## 5. Combat level and the free Ranged / Magic ceiling

| File | Lines | What it is |
| --- | --- | --- |
| `CombatMath.java` | 181 | The formula and the two questions a pure asks of it. |
| `CombatStats.java` | 144 | The seven combat levels, with `with()` for what-ifs. |
| `CombatMathTest.java` | — | Carries the Go vectors. |

**Port whole.** A direct port of `internal/osrs/combat.go` with the same
vectors. Pure arithmetic: no client, no graph, no network — the javadoc calls
it "the first thing this plugin does" for that reason.

What it gives Build Guard:

- `combatLevelExact` / `combatLevel` / `combatProgress`.
- **`freeRangedCeiling(attack, strength)`** — the highest Ranged level whose
  term does not exceed the melee term, so every level below it is free. The
  ceiling drifts upward with Strength; hardcoding it leaves levels on the
  table.
- `nextCeilingStep` — the next Strength level that moves the ceiling, and by
  how much.
- `levelsToNextCombat` / `nextCombatLevelAt`.

**Drags in:** nothing out of scope. All integer division is deliberate and
matches the game (`Prayer / 2` and `Ranged / 2` floor); do not "clean it up".

---

## 6. The XP table

| File | Lines | What it is |
| --- | --- | --- |
| `XpTable.java` | 117 | The experience curve, levels 1–126. |
| `SkillXp.java` | 114 | Real XP per skill, keeping "not loaded yet" distinct from a value. |
| `XpTableTest.java` | — | The anchors that catch the wrong reading. |

**Port both.** `XpTable` is a port of `internal/osrs/xp.go`. The classifier
already depends on it (`XpTable.levelFor(after)`), and headroom under a cap is
the same table read the other way.

The trap its javadoc records: `xp(L) = floor( ( sum … ) / 4 )` divides the
**completed sum**, not each term. Both readings agree for the first few levels
and diverge higher up. `XpTableTest`'s anchors are what catch it — port them.

**Drags in:** nothing out of scope.

---

## 7. The lamp advisor — it exists, in full

| File | Lines | What it is |
| --- | --- | --- |
| `LampAdvisor.java` | 416 | **The advisor.** Port of `internal/planner/lampchoice.go`. |
| `LampVerdict.java` | 225 | Four states: BLOCKED, RISKY, UNKNOWN, and fine. |
| `Lamp.java` | 193 | One lamp: item, amount, restrictions, level floor. |
| `LampCatalogue.java` | 177 | Every lamp questpath can price, keyed by item id. |
| `src/main/resources/com/vinnyvangogh/questpath/lamps.json` | 4.5 KB | The catalogue data, bundled. |
| `LampWatcher.java` | 126 | Which lamp is being used. Pure: ticks in, item id out. |
| `LampSkillWidgets.java` | 92 | Skill → component id, all 24 skills. |
| `LampOverlay.java` | 232 | Greys blocked squares, boxes suggested ones. |
| `LampSuggestions.java` | 46 | How many targets to box. |

**Scope item 4 is a port, not a stub.** The brief allowed for either; the
whole stack is there, with nine shared golden vectors.

Things the port must keep:

- **Classification order is load-bearing**: NO_XP first (before the lamp's own
  requirements and before the amount is consulted), then the lamp's
  restrictions, then UNKNOWN, then caps, then risks.
- **Four states, not two.** UNKNOWN exists because a lamp with no modelled
  amount against a capped skill is genuinely unanswerable, and saying so is
  the honest answer where a guess costs the most.
- **`LampWatcher` identifies the lamp by the item the player clicked**, not by
  the interface — every lamp opens the same `InterfaceID.XPREWARD`, and the
  amount differs by two orders of magnitude between them. It deliberately
  records *any* item click rather than matching a verb list.
- **`LampSkillWidgets` uses `net.runelite.api.gameval.InterfaceID` constants
  only**, read out of the shipped jar with `javap`, never recalled. Keep that
  and keep the command in the comment.

**Drags in / out of scope:**

- `LampOverlay extends Overlay` directly (not `QuestpathOverlay`) and draws on
  `OverlayLayer.ABOVE_WIDGETS`. It needs `TooltipManager`. Clean.
- `LampSuggestions` is a config enum → Build Guard's config group.
- `lamps.json` is bundled, not fetched — its javadoc says the advisor has to
  work with no questpath running. Comes across as-is.

---

## 8. The settings pattern and the overlay pattern

| File | Lines | Note |
| --- | --- | --- |
| `QuestpathConfig.java` | 1059 | **Do not port wholesale.** |
| `OverlaySettingsPanel.java` | 899 | Swing settings mirror of the config. |
| `QuestpathOverlay.java` | 380 | Shared chrome, layout and rules for every overlay. |
| `OverlayRow.java` | 753 | Row rendering. |
| `OverlayTheme.java` | 347 | Colours, with a contrast test. |
| `OverlayIcons.java` | 147 | Icon loading. |
| `CollapsibleGroup.java` | 229 | Sidebar group widget. |
| `QuestpathPanel.java` | 4616 | **Take the structure, never the content.** |

**The pattern to take** is `QuestpathOverlay`: a subclass says three things —
whether it is switched on, what it is called, and what rows it draws — and the
base class owns everything else. `BuildRulesOverlay` is 265 lines because of
that split.

**Drags in / out of scope:**

- `QuestpathConfig` is a 1059-line surface for a plugin with shop runs,
  diaries, routing and sync in it. Build Guard's config is a handful of keys
  and **every one of them freezes on first publish** — take the shape, write
  the keys fresh.
- `OverlaySettingsPanel` is held to the config by
  `OverlaySettingsPanelConfigParityTest`. If that pattern comes across, the
  parity test comes with it.
- `OverlayTheme` has a contrast test (`OverlayThemeContrastTest`). Worth
  keeping.
- `QuestpathPanel` at 4616 lines is the whole web-app-companion sidebar. Take
  `CollapsibleGroup` and the section structure; nothing else.

---

## 9. Everything that assumes a server — none of it comes

| File | Why it is excluded |
| --- | --- |
| `QuestGraphFetcher.java` | HTTP fetch of the quest graph. |
| `AccountStateSender.java` | Pushes account state out. |
| `AccountState.java` | Builds that payload — **but see below.** |
| `SyncSettings.java`, `SyncStatus.java`, `PushResponse.java` | Sync plumbing and the push wire format. |
| `GrandExchangeAccounts.java` and the rest of the GE/shop/diary/route stack | Out of scope by the brief. |
| `QuestHelperBridge.java`, `ShortestPathBridge.java` | Out of scope by the brief. **`QuestHelperBridge` is built on reflection** — a grep for `Class.forName`, `getMethod`, `getDeclaredMethod`, `getField`, `getDeclaredField` and `invoke(` returns 17 matches in that one file. Reflection is an automatic Hub rejection; see `BRIEF.md`. |

**`AccountState.java` is the exception worth reading before discarding.** It is
the push payload builder, so the payload half goes — but it also contains the
one piece Build Guard genuinely needs: reading the account from the client.

```java
for (Quest quest : Quest.values())       // net.runelite.api.Quest
{
    QuestState state = quest.getState(client);   // MUST run on the client thread
}
for (Skill skill : Skill.values())
{
    levels.put(skill.getName(), client.getRealSkillLevel(skill));
    experience.put(skill.getName(), client.getSkillExperience(skill));
}
```

Port that loop into something that feeds `PlayerState` directly and never
builds a payload. Its javadoc's warning stands: **`Quest#getState` reads
varbits and must run on the client thread.**
