# Player Compare group contract, v1

GIM Utilities no longer draws its own Compare views. The Leaderboard, Compare (two players side by side), Group
(group vs group), Item ("who benefits most") and Banked XP views move into the separate **Player Compare** plugin
(repo `VinnyVanGogh/player-compare-plugin`) as its **Group** source. GIM keeps the Compare tab so it still feels
native: the tab hands Player Compare the group as GIM knows it and asks it to show its group view. GIM decides who
is in the group and what each member has shared (group logic); Player Compare does the maths, the hiscore lookups,
the item data and the drawing.

The two plugins talk only through RuneLite's own `net.runelite.client.events.PluginMessage` on the injected
`EventBus`. No reflection, no shared classes, no dependency. The contract is the namespace, the message names and
the JSON-shaped maps below.

This document is shared by both repos. A change to any message is a change to both plugins: bump `v`, update the
fixtures in `src/test/resources/contracts/player-compare-group-v1/` (copied byte for byte into Player Compare), and
keep both contract tests green.

- GIM side: `src/main/java/com/vinnyvangogh/groupironman/PlayerCompare.java` (builders, receive),
  `GroupService` (`compareSnapshot`, `openCompare`, `onPluginMessage`), `GroupIronmanPanel.drawCompare` (the tab),
  test `PlayerCompareContractTest`.
- Player Compare side: ports the code listed under "Port list", adds the Group source, and runs the same fixtures.

## Transport

| | |
|---|---|
| Event | `net.runelite.client.events.PluginMessage(namespace, name, data)` posted on the injected `EventBus` |
| Namespace | `player-compare` (both directions) |
| Data | `Map<String, Object>` holding only `String`, `Boolean`, numbers (a receiver must accept any `Number`), `List`, `Map` and `null` |
| Envelope | every message carries `v` (contract version, `1`) and `source` (`gim-utilities` from GIM, `player-compare` from Player Compare) |
| Direction | a receiver ignores messages whose `source` is its own, and messages whose `v` it does not know |
| Thread | `EventBus.post` runs subscribers on the caller's thread. GIM sends from its start-up/shut-down, from the Swing thread (the tab's button) and from whatever thread delivered Player Compare's `ready`/`request` (it answers on that same thread). Player Compare must copy what it needs and hand drawing to Swing (`SwingUtilities.invokeLater`) and game reads to `clientThread.invoke`. Every map GIM sends is a fresh copy the receiver may keep |

## Handshake

1. GIM starts (after `GroupService` is registered on the bus): sends `hello`.
2. Player Compare answers any `hello` with `ready`, and also sends `ready` at its own start-up.
3. GIM, on `ready`: marks Player Compare installed, sends a `snapshot` straight away and redraws the tab.
4. While Player Compare's group view is showing, it sends `request` whenever it wants fresh figures (suggested:
   on open and then at most every 5 seconds). GIM answers each `request` with a `snapshot`. A `request` also
   counts as proof Player Compare is there, in case its `ready` was missed.
5. Either side stopping sends `bye`. GIM on Player Compare's `bye`: back to the install card. Player Compare on
   GIM's `bye`: drop the Group source's data (show "GIM Utilities is off").

Until a `ready` (or `request`) arrives, GIM's Compare tab shows one card: **"Install Player Compare to compare
your group (from the Plugin Hub)."** (`PlayerCompare.INSTALL_HINT`). Once installed, the tab shows one line and an
**Open group compare** button, which sends a fresh `snapshot` and then `open`.

## Messages, GIM to Player Compare

### `hello`

`{v, source, version}`: `version` is GIM's plugin version (`UpdateNotes.VERSION`). Fixture `hello.json`.

### `snapshot`

Everything the old Compare tab read. Fixture `snapshot.json`.

| Key | Type | Meaning |
|---|---|---|
| `self` | string | This player's display name ("" before login). Player Compare marks it as "you". |
| `members` | list | One map per member, in the group's order. **The order picks the member's colour** (index into the palette, see `MemberColors` in the port list), so Player Compare must keep it. This player is included, added last when the group list does not hold them. |
| `members[].name` | string | Display name as the group knows it. |
| `members[].levels` | map skill name → int | Real (unboosted) levels the member shares over the party. When they share none, GIM fills in their last hiscore levels; empty when neither is known. Skill names are RuneLite's `Skill.getName()` ("Attack", "Woodcutting"). |
| `members[].questPoints` | int | Shared quest points; `-1` when unknown. |
| `members[].bests` | map boss name → number | Shared personal-best times in seconds (`PersonalBests.all()`); empty when none. |
| `members[].scores` | map or null | Hiscore counts (boss kills, clues, minigames) by hiscore name, from GIM's own hiscore book; `null` when never read (unknown, never a loss). |
| `members[].carried` | boolean | The member shares what they carry. When false, `worn` and `inventory` are empty and mean "unknown", not "nothing". |
| `members[].worn` | map item id → qty | Worn items. Item ids are decimal strings so the map reads the same in JSON and in-process. |
| `members[].inventory` | map item id → qty | Inventory, noted counted as the item. |
| `members[].bank` | map or null | The last bank they closed (`BankSnapshot.items()`); `null` when never shared. |
| `storage` | map item id → qty | Group storage as last seen. Banked XP counts it once for the group, never per member. |

What Player Compare derives from it (all moved code, see the port list):

- **Leaderboard / skill grid / rank cards**: `SkillBoard` over `levels`, `questPoints`, `bests`, `scores`.
- **Compare (two players)**: `GroupCompare.rows` over the same figures, plus up to 5 other typed names
  (`MAX_OTHER_NAMES`) looked up with RuneLite's `HiscoreClient`.
- **Group vs group**: `GroupCompare` + `CompareSupport.groupExperience` (summed hiscore XP per skill, looked up by
  Player Compare itself with RuneLite's `HiscoreClient`, a few names at a time, only when its button asks).
- **Item ("who benefits most")**: `BenefitFinder.runId` over each member's `levels`, `worn`, `inventory`, `carried`,
  item stats from Player Compare's own `ItemManager` on the client thread (`CompareSupport.source`/`statsOf`), and
  level requirements from `item-requirements.txt` (`ItemRequirements`).
- **Banked XP**: `BankedXp.bundled().xp(BankedXp.combine(bank, inventory))` per member, and
  `BankedXp.bundled().xp(storage)` once for the group, from `banked-xp.txt`.

### `open`

`{v, source, view: "group"}`: the player pressed **Open group compare** in GIM. Player Compare opens (selects)
its sidebar panel and shows the Group source. GIM always sends a `snapshot` just before. Fixture `open.json`.

### `bye`

`{v, source}`: GIM is stopping. Fixture `bye.json`.

## Messages, Player Compare to GIM

### `ready`

`{v, source: "player-compare", version}`: Player Compare is installed and listening. Fixture `ready.json`.

### `request`

`{v, source: "player-compare"}`: send a fresh `snapshot`. Fixture `request.json`.

### `bye`

`{v, source: "player-compare"}`: Player Compare is stopping. Fixture `bye-from-player-compare.json`.

## What stays in GIM

- Who is in the group (`GroupMembers`, the roster, the party sync) and everything members share over it: stats,
  carried items, banks, storage, bests, quests (`MemberStats`, `GroupCodec`, `GroupRoster`, `MemberProgressBook`,
  `ProgressView`, `PersonalBests`, `PbCapture`).
- GIM's own hiscore book (`HiscoreBook`, `StatCategory`) and its background lookups: member cards, the Group tab
  and the snapshot's `scores` and fallback `levels` still read it.
- `TeamSummary` (events and activities use it).

## Config migration

The Compare settings leave GIM's config interface. Their stored values stay in the profile under GIM's group;
Player Compare reads them **read-only** once on first run (`ConfigManager.getConfiguration("gimutilities", key)`)
and copies them into its own group when its own key is unset. GIM never deletes them.

| Group | keyName | Type | Default | Player Compare home |
|---|---|---|---|---|
| `gimutilities` | `compareStats` | String (comma separated: `melee_attack, melee_strength, ranged_attack, ranged_strength, magic_attack, magic_damage, defence, prayer`; blank = all) | `""` | Group source, Item view: "Stats that count" |
| `gimutilities` | `compareCategories` | String (comma separated: `head, cape, neck, ammo, weapon, body, shield, legs, hands, feet, ring, inventory`; blank = all) | `""` | Group source, Item view: "Item categories" |

## Parity checklist

| GIM Compare feature (origin/main) | New home |
|---|---|
| Compare tab in GIM's panel | Stays: install card, or one line + **Open group compare** (`open`) |
| Leaderboard: skill grid, total level, quest points, per-skill rank cards with leader colour | Player Compare Group source, from `snapshot.members[].levels/questPoints` (`SkillBoard`, `CompareTab.leaderboard/skillGrid/rankCard`) |
| Leaderboard categories (skills, bosses, clues, minigames) with counts and "not on hiscores" notes | Player Compare, from `members[].scores` (`StatCategory`, `CompareTab.categories/countList/countNote`) |
| One colour per member everywhere, by group order | Player Compare, `MemberColors` by `members` index |
| Skill icons | Player Compare's own `SkillIconManager` |
| Compare: two members (or a typed name) side by side, boss times, "bosses only" | Player Compare (`GroupCompare.rows`, `CompareTab.players/playerLines`), bests from `members[].bests` |
| Typed other names (up to 5), hiscore lookup | Player Compare's own `HiscoreClient` lookups |
| Group vs group: summed hiscore XP per skill, "ahead by" lines | Player Compare (`CompareTab.compareGroups/groupLines/parseNames`, `CompareSupport.groupExperience`) |
| Item: search dropdown, "who benefits most", per-member result lines | Player Compare (`BenefitFinder`, `ItemBenefit`, `CompareTab.item/rank/benefitLines`), its own `ItemManager.search` |
| Item settings: stats that count, item categories | Player Compare config, migrated from `compareStats` / `compareCategories` |
| Item level requirements table | Player Compare resource `item-requirements.txt` + `ItemRequirements` |
| Banked XP per member (bank + inventory) and group storage once | Player Compare (`BankedXp`, `banked-xp.txt`), from `members[].bank/inventory` and `storage` |
| "Unknown is never a loss" (unshared figures) | Player Compare: `scores: null`, `questPoints: -1`, `carried: false`, `bank: null` all mean unknown |

## Port list

Copy these from GIM's `origin/main` into Player Compare (package and visibility adjusted). In GIM they are emptied
on this branch and deleted in the same PR.

| GIM file (origin/main) | What it is |
|---|---|
| `src/main/java/com/vinnyvangogh/groupironman/CompareTab.java` | The views (Swing) |
| `src/main/java/com/vinnyvangogh/groupironman/CompareSupport.java` | Figures from the roster, banked XP, item source, group hiscore XP |
| `src/main/java/com/vinnyvangogh/groupironman/GroupCompare.java` | Two-player and group-vs-group maths |
| `src/main/java/com/vinnyvangogh/groupironman/SkillBoard.java` | Leaderboard maths |
| `src/main/java/com/vinnyvangogh/groupironman/BenefitFinder.java` | Item ranking driver |
| `src/main/java/com/vinnyvangogh/groupironman/ItemBenefit.java` | Item ranking maths and settings |
| `src/main/java/com/vinnyvangogh/groupironman/ItemRequirements.java` | Level requirements table reader |
| `src/main/java/com/vinnyvangogh/groupironman/BankedXp.java` | Banked XP table and maths |
| `src/main/java/com/vinnyvangogh/groupironman/MemberColors.java` | Member palette |
| `src/main/java/com/vinnyvangogh/groupironman/StatCategory.java` | Hiscore categories (GIM keeps its copy too) |
| `src/main/java/com/vinnyvangogh/groupironman/PersonalBests.java` | `format` for boss times (GIM keeps its copy too) |
| `src/main/resources/com/vinnyvangogh/groupironman/banked-xp.txt` | Banked XP data |
| `src/main/resources/com/vinnyvangogh/groupironman/item-requirements.txt` | Requirements data |
| Tests: `CompareTabTest`, `GroupCompareTest`, `SkillBoardTest`, `BenefitFinderTest`, `ItemBenefitTest`, `ItemRequirementsTest`, `BankedXpTest` | Move with the code |

`CompareSupport.figures/banked/members` read GIM's roster; in Player Compare they read the `snapshot` maps instead.

## Risks

- Until Player Compare ships the Group source, a GIM user sees only the install card: the Compare views are gone
  from GIM in this release. Ship Player Compare first, or together.
- A `snapshot` with full banks for 5 members is a few thousand map entries. It is in-process (no serialisation),
  and is sent only on `ready`, `request` and the button, never per redraw.
- Player Compare must keep the `members` order for colours, and treat item-id keys as strings (or parse any
  `Number`-like string).
