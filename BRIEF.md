# Group Ironman — research & build brief

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
and not a server: it makes no network calls of its own. Everything goes
through RuneLite's `PartyService`.

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
| Config group | `vvg-group-ironman` | `groupironman` is already the `@ConfigGroup` of the Hub plugin **Group Iron Man** (Spedwards, slug `group-ironman`, `GroupIronManConfig.java:9` at `8700097`). Sharing it would read and write that plugin's settings. |
| Party message type | `VvgGroupIronmanMessage` | The relay routes by simple class name, so the name is shared by every plugin on the client. Build Guard uses `BuildGuardGroupMessage`; a generic `GroupIronmanMessage` could meet another group plugin's. A GitHub-wide code search to prove the generic name free was held at the gate, so the author prefix makes it moot. |
| Saved data | `.runelite/group-ironman/` | Build Guard's is `.runelite/build-guard/`. |
| Display name | `Group Ironman` (working name) | **Clashes on the Hub**: slug `group-ironman` exists, display name "Group Iron Man". Must change before submission; see "The one thing to raise". |

### Out of scope — do not implement, do not propose workarounds

- **Reflection**, and therefore any integration that reads another plugin's
  state. If integration is ever wanted, the channel is
  `net.runelite.client.events.PluginMessage`.
- **Every network call** of our own, and any server. Group Ironmen Tracker
  needs a website; this plugin's point is that it does not.
- **Automating anything**: moving items, withdrawing, walking. Input
  Restrictions.

### The one thing to raise before submitting

**The display name.** "Group Ironman" clashes with the existing Hub plugin
"Group Iron Man" (slug `group-ironman`). Candidates checked free on
2026-11-04 (no slug in `plugin-hub/plugins/` matches): **GIM Party Share**
(`gim-party-share`) and **Group Iron Party** (`group-iron-party`). Changing
the display name later costs nothing: the config group and message type do
not depend on it.

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
