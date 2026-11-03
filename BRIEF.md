# Build Guard — research & build brief

The authoritative record of what was decided and why. Findings go **in this
file**, under the phase that produced them, so nothing has to be re-derived
later. If a claim here is a measurement, say what was measured and how.

## What this plugin is

A small plugin that warns before a quest, a lamp or an activity gives
experience that breaks an account's build — pures, zerkers, skillers, anything
deliberately capped. It shows the XP headroom left under each of the account's
own rules, gives a verdict per quest with the reason behind it, warns before a
build-blocked quest is started, and marks up the experience lamp's skill-choice
interface so the squares that would end the build are obvious.

**Build restrictions are about XP, not levels.** A 1-Defence account is blocked
by any quest that awards Defence XP, whatever its level requirements, and the
check runs the whole prerequisite chain. It is carved out of questpath-plugin,
which already has the engine; `docs/SOURCE-MAP.md` names every file.

## What this plugin is not

Not a router, not a shop run, not a diary tracker, not a companion to a web
app. It makes no network calls at all. See "Out of scope" below for what was
checked against the rules and cut.

---

## Phase 0 — compliance

Read, in order:

1. `AGENTS.md` in this repo (the forbidden-features list at the bottom).
2. <https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features>
3. <https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1>

Then, for each feature in scope, write down: which rule it lands near, and the
precedent in the base client or an accepted hub plugin that shows it is
allowed. A feature with no precedent and no clean rule is not a feature yet.

### Phase 0 findings (completed 2026-09-17)

Precedent was read from the base client at **runelite-client 1.12.39**, paths
relative to `runelite-client/src/main/java/net/runelite/client/plugins/`, and
from the local checkout of Quest Helper 4.15.1.2 (BSD-2, Zoinkwiz), an
accepted Hub plugin.

**The one precedent that carries most of this plugin: `attackstyles`.** The
base client ships a plugin whose entire purpose is *"warn me when I am about
to gain experience in a skill I have decided not to train"*. It takes per-skill
configuration — `warnForDefence`, `warnForAttack`, `warnForStrength`,
`warnForRanged`, `warnForMagic` (`AttackStylesConfig.java:52`) — warns when a
warned skill's style is selected, and with `removeWarnedStyles`
(`AttackStylesConfig.java:113-118`) goes further and **hides the offending
widgets outright** (`AttackStylesPlugin.java:195`, `:449-453`,
`widget.setHidden(hidden)`). Build Guard is that idea applied to quests and
lamps instead of attack styles.

**1. Build rules UI, per skill, with XP headroom — allowed, with precedent.**

What the player now knows: their own declared caps, and how much of their own
experience is left under each. Nothing about anyone else.

- *Nearest rule.* Nothing in the forbidden list touches a player's own skill
  data. The closest is Data & Privacy, "no exposing player information over
  HTTP" — this plugin makes no requests at all.
- *Precedent.* `attackstyles/AttackStylesConfig.java:52` — per-skill
  "I do not want XP here" configuration, shipped by RuneLite. `xptracker` and
  `skillcalculator` both read and project the player's own experience.

**2. A verdict per quest, with its reason — allowed, with precedent.**

What the player now knows: whether their own account can do a quest, from
their own levels and quest state plus a bundled table of wiki-derived quest
requirements and rewards.

- *Nearest rule.* Content Restrictions, "no plugins that use player-provided
  IDs for their entire functionality" — the quest graph is bundled at build
  time; the player provides nothing.
- *Precedent.* **Quest Helper**, an accepted Hub plugin, computes exactly this:
  `src/main/java/com/questhelper/requirements/player/SkillRequirement.java`
  checks a requirement against the account, and
  `questinfo/QuestHelperQuest.java:839-874` resolves per-quest state. In the
  base client, `achievementdiary/QuestRequirement.java` reads `QuestState` to
  decide whether a requirement is met.

**3. A warning when starting a build-blocked or unreachable quest — allowed,
with precedent.**

- *Nearest rules.* Menu Restrictions — "no conditional menu entry removal", "no
  adding new menu entries that cause actions to be sent to the server". Input
  Restrictions — "no injecting input events".
- *Why it falls on the allowed side.* It warns. It removes no menu entry,
  adds none, injects nothing, and does not block the click. The player starts
  the quest if they want to.
- *Precedent.* `attackstyles` warns on selection of a warned style, and
  RuneLite's own version of "stop me doing this" goes further than a warning
  by hiding the widget. A warning is strictly narrower than what the base
  client ships.

**4. Lamp advisor: grey blocked skills, box suggested ones — allowed, with
precedent.**

- *Nearest rules.* Interface Restrictions — "no unhiding hidden interface
  components", "no moving or resizing click zones for combat options,
  inventory, equipment, or spellbook".
- *Why it falls on the allowed side.* Purely additive drawing over components
  already on screen, on `OverlayLayer.ABOVE_WIDGETS`. It unhides nothing,
  moves nothing, resizes no click zone, removes no menu entry; every square
  stays exactly as clickable as the game made it.
- *Precedent.* `attackstyles` with `removeWarnedStyles` **hides** the widget
  (`AttackStylesPlugin.java:449-453`). Drawing over a square is strictly
  narrower than hiding it, and the hiding is what RuneLite itself ships.
- This verdict is not new: questpath-plugin's `LampOverlay.java:28-45` carries
  the same argument in its own javadoc, against the same precedent.

### Backlog — verdicts before building, not after

The backlog in `docs/SPEC.md` was checked too, because the cheapest time to
find out is now.

| Backlog item | Verdict |
| --- | --- |
| Attack-style warnings derived from build rules | **Allowed, with precedent** — this *is* `attackstyles`, per-skill config included. Deriving the warned set from a build rule instead of five checkboxes changes nothing about what reaches the player. |
| Tears of Guthix warning and marker | **Allowed, with precedent.** The base client ships `tearsofguthix` (plugin, config and overlay). Telling the player that the activity trains their lowest skill is a game mechanic plus their own stats. |
| Quest journal warnings | **Allowed, with precedent.** The base client's `questlist` plugin already modifies the quest journal interface. Read it before writing any journal code. |
| XP alarm when a no-XP skill gains any XP | **Allowed, with precedent** — reading one's own XP drops is what `xptracker` does, and `attackstyles` already warns on the same event class. |
| **Lamp locking — "consume clicks on blocked skills"** | **Restricted. Do not build it as written.** See below. |

**Lamp locking, in detail.** Consuming a click on an interface component is
not the same feature as drawing over it, and it is the one backlog item that
lands inside a rule rather than beside one. The nearest rules are Input
Restrictions ("no injecting input events" — consuming is not injecting, but it
is the same family) and Menu Restrictions ("no conditional menu entry
removal"). There is no base-client precedent for intercepting and swallowing a
widget click.

*The narrower form that does have precedent:* **hide the square**, exactly as
`attackstyles`'s `removeWarnedStyles` hides a warned attack style
(`AttackStylesPlugin.java:195`, `:449-453`). A hidden component cannot be
clicked, the outcome the player wanted is achieved, and RuneLite ships the
technique itself.

Decide between those two before writing any of it, and write the verdict here.
Do not reach the same outcome by a different route: the rules are about what
happens to the player's click, not about which API does it.

### Phase 0 addendum — group features over RuneLite Party (2026-11-03)

Asked for by the Board for a Group Ironman team with pures. The source research
note named in the task (`00-hub/docs/research/group-ironman-plugins.md`,
commit 5e8e856) was **not reachable from this run**: `00-hub/docs/research/`
holds only `donation-links.md`, and no repo visible here has that commit. The
verdicts below come from the rules and precedent directly.

**5. Each member shares their build rules and the levels of those skills —
allowed, with precedent.**

- *Nearest rules.* Data & Privacy: "no crowdsourcing data about other
  players", "no exposing player information over HTTP".
- *Why it falls on the allowed side.* Nothing is collected about anyone. Each
  member's own client sends that member's own data, only after they turn it
  on, only to the party they joined, through RuneLite's own `PartyService`
  relay. No server or database of ours exists, and no HTTP client either.
- *Precedent.* The base client's `party` plugin (`StatusUpdate`) shares HP,
  prayer, run energy and special attack the same way. The Hub plugin **Party
  Panel** (TheStonedTurtle, `plugin-hub/plugins/party-panel`) shares
  "Inventory, worn Equipment, Active Prayers, and skill levels" between party
  members (its README, read 2026-11-03).

**6. Attack-style warning, locally and as an optional group line — allowed,
with precedent.** It is `attackstyles` (warn when a style trains a warned
skill), with the warned set taken from the build rules instead of five
checkboxes. The group line only repeats to the party what the member's own
client already knows about the member, opt-in, like #5. It is not about
combat against anyone and not a PvP group summary: the party is your own team.

**7. "Who has what" gear list — allowed, with precedent.** Party Panel shares
worn equipment and inventory over the same channel. Build Guard sends item
names only, worn and carried, never the bank, and only with its own switch on.

All three are **off by default**, and with build sharing off the client sends
no message at all (`PartyOutboxTest.optedOutMemberSendsNothing`).

### Out of scope — do not implement, do not propose workarounds

- **Reflection, and therefore any integration that reads another plugin's
  state.** questpath-plugin's `QuestHelperBridge.java` is the worked example:
  a grep for `Class.forName`, `getMethod`, `getDeclaredMethod`, `getField`,
  `getDeclaredField` and `invoke(` returns **17 matches in that one file**.
  All correct, all fatal — reflection is an automatic Plugin Hub rejection.
  Do not port it and do not rebuild it. If integration is ever wanted, the
  channel that needs no reflection is
  `net.runelite.client.events.PluginMessage`.
- **Every network call.** questpath-plugin fetches its quest graph and pushes
  account state; `docs/SOURCE-MAP.md` §9 lists every file that assumes a
  server. None of them come. Data & Privacy also forbids exposing player
  information over HTTP, and the simplest way to comply is to have no client.
- **Automating any of it** — walking to a quest start, clicking a lamp square,
  choosing a skill. Input Restrictions. The plugin's job ends at telling the
  player.
- **Shop run, diaries, routing, Quest Helper and Shortest Path hooks, website
  sync, locked gear and max hits, themes.** Out by the brief, not by the rules.

### The one thing to raise before submitting

**Two plugins by the same author, on the same Hub, carrying the same ported
engine.** Build Guard and questpath would overlap substantially. That is a
review question rather than a rules question, and it is in `docs/SPEC.md`
under open decisions: **ask before submitting.**

---

## Phase 0.5 — measure before building

If the plugin makes a claim about a number the client does not hand you
directly, measure it before designing around it. An estimate written into a
README becomes a promise you cannot keep.

### Phase 0.5 findings

The numbers this plugin will state — XP headroom, the free Ranged ceiling, the
XP a quest awards — all come from ported code that carries the original's test
vectors. That is the standard for any new number too: a wiki URL with a revision
id, and worked vectors as tests.

**Measured 2026-09-17 — what the vectors are actually worth.** A green suite
proves nothing unless the vectors discriminate, so the documented failure mode
was reproduced deliberately: `BuildRule.blocking()`'s body was replaced with
`return blocking;` — trusting the wire field alone, which neither vector file
carries — and `PlannerVectorsTest` was run.

| Result | Figure |
| --- | --- |
| Verdicts disagreeing with the Go planner | **99 of 218** |
| …that should have been `BUILD_BLOCKED` | 58 |
| …that should have been `UNREACHABLE` | 41 |
| …that came back flatly `ELIGIBLE` | 13 |

The mutation was reverted and the suite is green again. Two things this settles:
the 218 comparisons genuinely run and genuinely discriminate, and **questpath's
own javadoc figure of 94 does not reproduce here** — it was measured against an
earlier graph version. The 99 is this repo's number, and the method above is how
to re-derive it. Build Guard's own javadoc now states 99 and says how it was
obtained, rather than inheriting a number nobody re-checked.

---

## Phase 1 — reference reading

Which base-client plugin or hub plugin already does the closest thing, and
what does its source do? Copying the shape of something already accepted is
the cheapest compliance argument there is.

### Phase 1 findings (2026-09-17)

| Source | What to take from it |
| --- | --- |
| `attackstyles/` (base client) | Per-skill warn configuration, warning on selection, and `hideWidget`/`setHidden` as the sanctioned way to stop a click. The precedent for most of this plugin. |
| `questlist/` (base client) | How the quest journal interface is modified. Read before the journal backlog item. |
| `tearsofguthix/` (base client) | Plugin + config + overlay for the activity itself. |
| `xptracker/`, `skillcalculator/` (base client) | Reading and projecting the player's own experience. |
| Quest Helper 4.15.1.2 (Hub, BSD-2) | `SkillRequirement`, `QuestHelperQuest.getState` — per-quest requirement resolution in an accepted plugin. **Check the licence before reusing code; it is BSD-2, so attribution is required.** |
| questpath-plugin | The engine itself. `docs/SOURCE-MAP.md` is the full map. |

---

## Phase 2 — scope

See `docs/SPEC.md`. Four items in order, a backlog of five, and a list of open
decisions that are not to be closed by guessing.

**Milestone 1 (2026-09-17) — the engine, no UI.** Ported from questpath-plugin
into a flat `com.vinnyvangogh.buildguard`, package-private as upstream:
`XpTable`, `SkillXp`, `CombatMath`, `CombatStats`, `BuildRule`,
`BuildRuleCodec`, `QuestStatus`, `PlayerState`, `Boosts`, `QuestGraph`,
`QuestClassifier`, `Lamp`, `LampCatalogue`, `LampVerdict`, `LampAdvisor`. Four
bundled files came across byte-identical — `quests.json` (1.38 MB, graph v12,
218 quests, 300 boosts), `lamps.json`, `planner-vectors.json` (218 verdicts) and
`lamp-vectors.json` (9 cases). **81 tests, 0 skipped, 0 failures, and no
expected value edited.** `.claude/PLANNING.md` records the three decisions
behind the shape.

What was severed rather than ported, all of it web-app or out of scope:
`QuestGraphFetcher`, `AccountStateSender`, `PushResponse` and the rest of the
sync stack; `BuildRuleCodec.merge(pushed, local)`, which reconciled two rule
origins where Build Guard has one; and roughly 700 lines of `QuestGraph`'s model
for shops, items, diary tasks and quest start coordinates.

**Still no UI, deliberately.** The four stubs — `rules.BuildRules`,
`verdict.QuestVerdictEngine`, `warning.QuestStartWarning`, `lamp.LampAdvice` —
still throw `UnsupportedOperationException`, and `SkeletonStubsTest` still holds
them to it. That matters more here than in most scaffolds: a wrong verdict is
acted on by spending experience, and experience does not come back.

---

## Phase 3 — quality bar

- Pure logic separated from client access, and unit tested. The ported engine
  is already built this way — `QuestClassifier`, `CombatMath`, `XpTable` and
  `LampAdvisor` all take plain values and return plain values.
- **The ported vectors pass unchanged.** 218 quest verdicts, 9 lamp cases. A
  failing vector is a porting bug, not a vector to edit — and they cannot be
  regenerated here in any case, since the generator is questpath's Go
  `-export-planner-vectors`.
- `startUp` / `shutDown` symmetric: everything registered is unregistered.
- `Quest#getState` reads varbits and **must run on the client thread**.
- No unused config keys, fields or imports.

---

## Phase 4 — getting it installed

See the `runelite-hub-submit` skill. Manifest, checklist, PR, stale bot.

Not yet. `icon.png` is still the 1×1 placeholder the scaffold wrote, and the
overlap question above is unanswered.
