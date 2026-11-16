# Competitor Comparison: GIM Utilities vs. RuneLite Plugin Hub Group Ironman Ecosystem

**Date:** 2026-11-16  
**Target Release:** Tuesday Reset (2026-11-17)  
**Repository:** [`VinnyVanGogh/group-ironman-plugin`](https://github.com/VinnyVanGogh/group-ironman-plugin) (`GIM Utilities`, Hub PR `runelite/plugin-hub#20149`)  
**Scope:** Feature comparison of GIM Utilities (`main` @ `ca055e2`, tag `gim-test-7`) against **The Group Ironman Homebase** (`bonscot1/Homebase`, Hub PRs `#20070`, `#20391`, `#20570`), **Group Ironmen Tracker** (`christoabrown/group-ironmen-tracker` / `groupiron.men` + `KingKj52` In-Game Companion), and every other GIM, shared-bank, or group-storage plugin in the RuneLite Plugin Hub `plugins/` catalog.

---

## 1. Executive Summary

Across the RuneLite Plugin Hub (`runelite/plugin-hub/tree/master/plugins` and the Hub stats index at `api.runelite.net`), **20 plugins** target Group Ironman (GIM) teams, group storage, or shared party inventories. They divide into four architectural categories:

1. **Serverless RuneLite Party Suites (2 plugins):** **GIM Utilities** (`VinnyVanGogh/group-ironman-plugin`) and **The Group Ironman Homebase** (`bonscot1/Homebase`). Both sync directly between clients over RuneLite's native `PartyService` (`WSClient`) without requiring a third-party backend server.
2. **External Web-Backend Dashboards (9 plugins):** Led by **Group Ironmen Tracker** (`christoabrown/group-ironmen-tracker`, **23,828 active installs**, paired with `groupiron.men` and `KingKj52`'s **In-Game Companion** at **60 installs**) and **GIMP** (`davidvorona/gim-plugin`, **4,585 installs**), plus smaller web trackers (**GIMP Tracker** at **974**, **GIM Hub** at **366**, **Group Ironman Tracker** by Danj at **294**, **GIM Visualiser** at **38**, **Vibe Steps Progress Tracker** at **46**, **GIM Shed** at **19**, and **GroupScape** at **1**). All require a third-party server warning or self-hosted infrastructure.
3. **Discord Group Storage Webhooks (3 plugins):** **Discord Group Bank Notifications** (`barthogenes`, **2,522 installs**), **GIM Bank Discord** (`Boredska`, **1,766 installs**), and **GIM Discord Tracker** (`Raccoonz`, **20 installs**) — plus general webhook plugin **Dink** (`pajlads/DinkPlugin`, **200,000+ installs**).
4. **Local Group Storage / Hiscore / Utility Addons (6 plugins):** **Group Iron Man** (`Spedwards`, **~15,544 installs**), **Group Iron Panel** (`toasty-toast`, **8,144 installs**), **Group Storage Reminder** (`RedSparr0w`, **1,261 installs**), **GIM Bank Extended** (`Quyaz`, **318 installs**), **Group Item Tracker** (`CodyLyttle`, **118 installs**), **GIM Tasks** (`PlumpPanda-123`, **62 installs**), **Group Storage Tracker** (`MerryGusmas`, **53 installs**), and **GIM Notifications** (`DaRoms`, **19 installs**).

### Bottom-Line Verdict
* **Where GIM Utilities leads every plugin on the Hub:**
  1. **Discord Integration & Anti-Spam Engineering:** 7 category webhooks with RuneScape-flavored personas, 30-minute per-member in-place message editing (`DiscordMerge.java`, `LevelThreads.java`), a persistent 1-message Live Status Board with daily midnight repost (`StatusBoard.java`), non-plugin member broadcast relay (`GroupBroadcasts.java`), net-value storage visit merging, gravestone/level screenshots, daily digests (`Digest.java`), weekly recaps, and meme Easter eggs (`EasterEggs.java`). **Homebase and Group Ironmen Tracker have zero native Discord webhook integration.**
  2. **Cryptographic Group Governance:** ECDSA P-256 signed leader & editor roles (`GroupSigner.java`, `GroupGovernance.java`), leader-shared webhooks (`GroupWebhooks.java`), and signed group-wide settings (`GroupSettingsSchema.java`). No other GIM plugin has role-based settings sync.
  3. **Quest-Aware Group Shopping Lists & Shortest Path Routing:** Multi-member shopping lists with 1-Defence pure milestone presets, live subtraction of group holdings (`ShopAllocation.java`), shop & monster drop sources (`ShopSources.java`, `ItemDrops.java`), proximity shop alerts (`ShopVisits.java`), and one-click `PluginMessage` routing via Shortest Path (`ShortestPathBridge.java`, `ItemPaths.java`).
  4. **Granular Live Wave & Activity Tracking:** Live Fight Caves (`1–63`), Inferno (`1–69`), and Fortis Colosseum (`1–12` + Glory + handicap modifiers + tiers) wave tracking across the Group card, `Activities` tab, in-game `GroupBoxOverlay`, and Discord (`WaveTracker.java`, `ColosseumModifiers.java`).
  5. **Carried Skilling Container Inspection:** Real-time item counts inside carried log baskets, fish barrels, and coal bags drawn on inventory slots and indexed in `::find` (`CarriedContainers.java`, `SkillingContainer.java`), plus live Rune Pouch and Dizana's Quiver varbit inspection (`MemberExtras.java`).
  6. **Strict Hub Compliance:** Homebase currently includes two features that violate or risk rejection under RuneLite Plugin Hub rules: **auto-joining `PartyService` on launch/login** (explicitly listed on RuneLite's *Rejected or Rolled-Back Features* wiki and rejected in `plugin-hub#19833`) and **hiding `Withdraw` / `Withdraw-X` menu entries in group storage** for soft-locked bounty items (violates menu-modification rules). GIM Utilities stays 100% compliant on `main` (`BRIEF.md` Phase 0) while planning an opt-in Cloudflare Workers + Durable Objects sync backend (`RUNELITE-206`) to eliminate the manual Party join click legally.

* **What GIM Utilities lacks (Ranked Gaps):**
  1. **Zero-click auto-sync on login + server-side Discord deduplication** -> Covered by **`RUNELITE-206`** (`task-235f05b5`).
  2. **Shared full Quest states, Quest Points, Achievement Diaries & Combat Achievements** -> Covered by **`RUNELITE-218`** (`task-c3ec10ad`) & **`RUNELITE-190`** (`task-4c61d485`).
  3. **Shared Boss Kill Counts (KC), Boss Personal Bests (PBs) & Collection Log boss-page PB overlay** -> Covered by **`RUNELITE-218`** (`task-c3ec10ad`).
  4. **Player-vs-Player & Group Hiscores Compare tab** -> Covered by **`RUNELITE-218`** (`task-c3ec10ad`) & **`RUNELITE-88`** (`task-2776b5ff`).
  5. **On-canvas Group Vitals HUD beside the local player** -> Covered by **`RUNELITE-215`** (`task-23dbffa6`).
  6. **Looting Bag contents & Wilderness skulled/unskulled risk value** -> Covered by **`RUNELITE-193`** (`task-ebaa9acd`).
  7. **Borrowed-item ("Items Owed Back") tracking & return reminders** -> **`new`** (or extension of `RUNELITE-102` / `RequestBoard`).
  8. **Full Collection Log & POH Costume Room / furniture sync** -> **`new`**.
  9. **Shared Group Goals / Milestones & Group Bounties** -> Partially covered by **`RUNELITE-86`** (`task-4c7f42be`, watchlists); non-item XP/KC goals and bounties are **`new`**.
  10. **In-game Skill Tab hover tooltips showing teammates' levels/XP** -> **`new`**.
  11. **Shared World Map / Ground Pings, Group Tile Markers & Group NPC Highlights** -> **`new`** (low priority; overlaps base `party` and `Hub Party Panel`).

---

## 2. Complete Catalog of Group Ironman & Shared-Bank Plugins on the Hub

Install counts are sourced from the RuneLite Plugin Hub index (`https://api.runelite.net/runelite-<version>/pluginhub` as indexed by `runelite.phyce.dev` in November 2026) and cross-checked against `runelite/plugin-hub` manifests.

| # | Plugin Name | Hub Slug (`plugins/<slug>`) | Author / Repo | Active Installs | Peak Installs | Architecture | Primary Scope |
| :--- | :--- | :--- | :--- | ---: | ---: | :--- | :--- |
| 1 | **GIM Utilities** *(ours)* | `gim-utilities` *(PR #20149)* | `VinnyVanGogh/group-ironman-plugin` | *In Review* | *In Review* | Serverless (`PartyService` + local cache + 7 Discord webhooks; opt-in CF Worker planned) | Full GIM suite: banks, storage diff/log, live stats/vitals, 28-slot inv/gear, pouches/baskets, `::find`, 39-type event log & Discord engine, quest shopping lists + Shortest Path, requests, live wave activities, ECDSA group settings |
| 2 | **The Group Ironman Homebase** | `group-ironman-homebase` *(PR #20070 merged Nov 3; #20391 / #20570 open)* | `bonscot1/Homebase` | *New (Nov 2026)* | *New (Nov 2026)* | Serverless (`PartyService` + gossip summary relay + `HiscoreManager`) | Full GIM sidebar suite: member cards (skills, quests, diaries, CAs, clog, boss KC/PBs, inv, gear, bank, seed vault, looting bag), storage bounties & items owed, requests, goals, compare tab, world map/tile pings, group HUD |
| 3 | **Group Ironmen Tracker** | `group-ironmen-tracker` | `christoabrown/group-ironmen-tracker` + `christoabrown/group-ironmen` | **23,828** | 32,000+ | External SaaS (`groupiron.men` or self-hosted) | Passive client uploader syncing banks, storage, inv, gear, pouch, vault, skills, quests, diaries, clog, and map location to `groupiron.men` web dashboard |
| 4 | **Group Ironmen Tracker In-Game Companion** | `group-ironmen-tracker-ingame-companion` | `KingKj52` / `Varzeki/group-ironmen-tracker-ingame-companion` | **60** | 60 | External SaaS reader (`groupiron.men` API) | Pulls `groupiron.men` data into RuneLite: sidebar panel, skill-tab hover tooltips, `Ctrl+G` custom group bank interface, cross-world ghost players |
| 5 | **Group Iron Man** | `group-ironman` | `Spedwards/runelite-plugins` (`group-ironman` branch, PR #293/#300) | **~15,544** | ~18,000 | Local client + Hiscores | Pre-official GIM / custom group plugin (`@ConfigGroup("groupironman")`): chat badge icon and basic roster lookup |
| 6 | **Group Iron Panel** | `group-iron-panel` | `toasty-toast/runelite-group-iron-panel` | **8,144** | ~9,500 | Hiscores lookup (`HiscoreClient`) | Sidebar panel displaying manual list of GIM members' skill levels and hiscores side by side |
| 7 | **GIMP** | `gim-plugin` | `Vijuhas` (`davidvorona/gim-plugin`) | **4,585** | ~5,200 | External self-hosted Node server | Sidebar status, skills, boss kills, live world map tracking, and shared ground tile pings |
| 8 | **Discord Group Bank Notifications** | `discord-group-bank-notifications` | `barthogenes` | **2,522** | ~2,700 | Outbound Discord Webhook | Posts Group Storage deposits and withdrawals to a Discord webhook |
| 9 | **GIM Bank Discord** | `gim-bank-discord` | `Boredska/gim-bank-discord` | **1,766** | 1,806 | Outbound Discord Webhook | Sends a Discord webhook notification when Group Storage changes are saved |
| 10 | **Group Storage Reminder** | `group-storage-reminder` | `RedSparr0w` | **1,261** | ~1,400 | Local client reminder | Reminds GIM players before logout or on bank close to return borrowed items to Group Storage |
| 11 | **GIMP Tracker** | `gimp-tracker` | `Rachnus/GIMP-Tracker` (`jimppan/GIMP-Tracker`) | **974** | ~1,100 | External 3-part web stack | Streams location, inventory, equipment, skills, and XP to a self-hosted web frontend |
| 12 | **GIM Hub** | `gim-hub` | `wouterrutgers/gim-hub-plugin` (`gim-hub.com`) | **366** | ~400 | External SaaS (`gim-hub.com` or self-hosted) | Streams live locations, inventories, banks, storage, and skills to `gim-hub.com` dashboard |
| 13 | **GIM Bank Extended** | `gim-bank-extended` | `Quyaz` | **318** | ~350 | Local client logger | In-game Group Ironman shared bank transaction logger and history viewer |
| 14 | **Group Ironman Tracker** | `group-ironman-tracker` | `Danj` | **294** | ~310 | External server / tracker | Tracks group drops, skill levels, quests, diaries, collection logs, boss sessions, and CAs |
| 15 | **Group Item Tracker** | `group-item-tracker` | `C.L` (`CodyLyttle/group-item-tracker`) | **118** | ~130 | Local client bank tagger | Right-click items to mark as belonging to Group Storage; outlines them and filters via `/g` in bank search |
| 16 | **GIM Tasks** | `gim-tasks` | `PlumpPanda-123` | **62** | ~75 | Local / shared task list | Manage and track shared tasks and goals for a Group Ironman team |
| 17 | **Group Storage Tracker** | `group-storage-tracker` | `MerryGusmas/groupstoragetracker` | **53** | 53 | Local client + Bank Tags sync | Learns items from Group Storage above a GP threshold, watches bank/inv/equipped, and syncs a Bank Tag tab of unreturned items |
| 18 | **Vibe Steps Progress Tracker** | `vibe-steps-progress-tracker` | `Vibesteps` | **46** | 46 | External web tracker | Group Ironman progression tracker |
| 19 | **GIM Visualiser** | `gim-visualiser` | `GIM Team` | **38** | 38 | External web dashboard | Syncs and visualizes GIM player data on an external web dashboard |
| 20 | **GIM Discord Tracker** | `gim-discord-tracker` | `Raccoonz` | **20** | 20 | Outbound Discord Webhook | Tracks GIM bank and milestone updates to Discord |
| 21 | **GIM Notifications** | `gim-notifications` | `DaRoms` | **19** | 19 | Outbound Webhook / Alert | Notifications for GIM milestones |
| 22 | **GIM Shed** | `gim-shed` | `Conduitflow` | **19** | 19 | External / local storage view | Tracks and manages shared group storage and inventory data |
| 23 | **GroupScape** | `groupscape` | `MayzrDev/groupscape-plugin` | **1** | 3 | External SaaS (`groupscape.com`) | Streams stats, locations, and slayer tasks to an OSRS-themed web dashboard |

*(Adjacent general plugins frequently used by GIM teams: **Hub Party Panel** by `TheStonedTurtle` at **259,943 installs**, **Dink** by `pajlads` at **200,000+ installs**, **My BiS Finder** by `ZeroRuin` at **593 installs** with optional GIM storage support, **Banked BiS** by `asg86260` at **828 installs** with GIM storage support, and **Wealth Tracker** by `WyattLB` with optional GIM storage net worth.)*

---

## 3. Deep Dive: Competitor Architectures & Feature Sets

### 3.1 The Group Ironman Homebase (`bonscot1/Homebase`)
* **Citations:** Repo [`bonscot1/Homebase`](https://github.com/bonscot1/Homebase) (PRs `#1` foundation, `#2` requests & group name, `#3` auto-join & updates summary), Hub submission [`runelite/plugin-hub#20070`](https://github.com/runelite/plugin-hub/pull/20070) (merged Nov 3, 2026 at `0d9b517`), Hub update [`runelite/plugin-hub#20391`](https://github.com/runelite/plugin-hub/pull/20391) (`f8a3a7d..6e56f6c`), and Hub update `#20570` (`63771f4`).
* **Sync Architecture:** Serverless over RuneLite's `PartyService`. Each client caches the entire group's data on disk per member and per section with capture timestamps. When any member joins the party, clients swap section timestamp summaries and relay missing/newer sections on behalf of offline members (*gossip relay*).
* **Party Join Mechanism (Compliance Risk):** For Group Ironman accounts, Homebase reads the group name from `ClanSettings` (`ClanID.GROUP_IRONMAN`) and **automatically joins a party derived from the group name on login/launch** (with an optional secret word setting).
  * *Why this matters:* RuneLite's *Rejected or Rolled-Back Features* wiki explicitly bans *"Auto-rejoin for parties: Would put undue strain on the party servers"*, and maintainers closed `plugin-hub#19833` (*Party Smarty*) on 2026-10-31 for calling `partyService.changeParty` automatically. GIM Utilities (`BRIEF.md:142-148`) deliberately requires one player click (`Join group sync`) on `main` to guarantee Hub acceptance.
* **Sidebar Layout (2 rows of tabs):**
  1. **Members:** Header shows the in-game GIM group name (e.g. `HungryHorse`). One card per member with online status / world / area name / `"Updated X ago"`, live HP / Prayer / Run energy from party data, total level, Quest Points, and collapsible sections for **Skills**, **Quests**, **Achievement Diaries**, **Combat Achievements**, **Collection Log** (captured via script `4100` when clicking Search in the Collection Log, the same way WikiSync/ProgressScape work, plus new-slot chat lines), **Boss Kill Counts** (via `HiscoreManager`), **Boss Personal Bests (PBs)**, **POH Contents** (built rooms/furniture and Costume Room storage), **Inventory**, **Equipment**, **Bank**, **Seed Vault**, and **Looting Bag**.
  2. **Storage:** Last seen Group Storage snapshot (as item pictures), **Storage History** (with item pictures), **Bank Bounties** (soft-locking an item in group storage for specific members), and **Items Owed Back** (tracking items withdrawn from group storage that need to be returned).
     * *Soft-lock mechanism (Compliance Risk):* When `Hold items in group storage` is on, Homebase hides `Withdraw` menu options in Group Storage that would take more than the unreserved amount, and hides `Withdraw-X` whenever any amount is held (`plugin-hub#20070`). Under `AGENTS.md` (*Menu Restrictions: No conditional menu entry removal*), hiding bank/storage withdraw actions based on plugin state is a high-risk compliance area.
  3. **Items (Search):** Searches across members' banks, inventories, equipment, seed vaults, looting bags, and group storage.
  4. **Requests:** Ask the group for an item + quantity + optional note. Teammates can click `I'll get it`, `Give it back`, or `Done`; requester can `Cancel` or `Clear`. Announces recent changes in game chat with `[Homebase]` prefix.
  5. **Goals:** Shared group goals / milestones where items or tasks tick off as members complete or build them.
  6. **Updates:** Chronological feed of what each member did since their last update (level-ups, quests, boss KCs, new Boss PBs, clog slots, drops), with skill/boss/item icons and a toggle to include your own updates.
  7. **Compare:**
     * **Player Compare:** Puts any two group members side by side, skill by skill and boss by boss, highlighting who is ahead.
     * **Group Hiscores Compare (PR `#20570` / `63771f4`):** Looks up where the group sits on the official Group Ironman (or Hardcore GIM) hiscores and compares the group skill-by-skill against the group ranked one place above, plus a text box to compare against any named group.
* **In-Game Overlays & Map Collaboration:**
  * **Group HUD:** Compact on-canvas HUD showing teammates' live vitals (configurable fields, compact mode, size).
  * **Collection Log Boss Page PB Overlay:** Displays groupmates' personal bests next to your own PB inside the in-game Collection Log boss pages.
  * **World Map & Ground Pings / Group Tile Markers / Group NPC Highlights:** Mark a tile, ping the map, or highlight an NPC and the whole group sees it in each member's configured colour.

### 3.2 Group Ironmen Tracker (`christoabrown/group-ironmen-tracker` / `groupiron.men`) & In-Game Companion (`KingKj52`)
* **Citations:** Repo [`christoabrown/group-ironmen-tracker`](https://github.com/christoabrown/group-ironmen-tracker), web repo [`christoabrown/group-ironmen`](https://github.com/christoabrown/group-ironmen), companion repo [`Varzeki/group-ironmen-tracker-ingame-companion`](https://github.com/Varzeki/group-ironmen-tracker-ingame-companion) (Hub PRs `#16524`, `#20189`), `BRIEF.md:57-60, 279-285, 520-526`.
* **Architecture:** Passive client observer that batches and POSTs account state to `https://groupiron.men` (or a self-hosted server) using a group name + secret token. Requires the Hub's third-party server IP/data warning.
* **Strengths:**
  * **Zero-click always-on sync:** Because it posts over HTTP to a central database, members never have to click `Join Party` or be online simultaneously for their first sync.
  * **Correct Group Storage save detection:** Only commits group storage changes when `InterfaceID.LOADING_ICON_MODAL` (`293:1`) displays `"Saving..."` (which GIM Utilities also adopted in PR `#68` / `ce6996b5`).
  * **Full Web Dashboard (`groupiron.men`):** Interactive leaflet world map, full skill/XP charts, quest/diary tables, collection log viewer, and global item search accessible from a browser or phone.
  * **In-Game Companion (`KingKj52`, 60 installs):** Adds an in-client sidebar reading the `groupiron.men` API, **hover tooltips on the vanilla Skills tab** showing every teammate's level/XP/remaining XP, a **`Ctrl+G` custom in-game bank interface** for browsing group banks, and **"Ghosts"** (drawing teammates on other worlds as translucent ghosts when standing in the same area, added in `plugin-hub#20189`).
* **Weaknesses:**
  * Base plugin (**23,828 installs**) has **no in-game UI at all** — players must alt-tab to `groupiron.men` unless they install the separate 60-install companion plugin.
  * **No Discord webhook integration** (no drops, deaths, levels, storage logs, or live status board in Discord).
  * **No shopping lists, no item requests, no Shortest Path routing, no live wave/activity tracking, and no skilling container (log basket/fish barrel/coal bag) tracking.**

### 3.3 Other Specialized GIM & Shared-Bank Hub Plugins
* **Discord Group Bank Notifications (`barthogenes`, 2,522 installs) & GIM Bank Discord (`Boredska`, 1,766 installs):** Single-purpose webhook senders that diff Group Storage on close/save and post raw deposits/withdrawals to Discord. Neither has 30-minute net-value visit merging, "took the last one" alerts, low-stock minimum thresholds, or multi-channel routing.
* **Group Iron Panel (`toasty-toast`, 8,144 installs):** Purely a `HiscoreClient` lookup panel where you type up to 5 RSNs and view their hiscore skills. GIM Utilities (`HiscoreBook.java`) already does this automatically from the in-game `ClanID.GROUP_IRONMAN` roster for members without the plugin, while merging live boosted stats for plugin users.
* **GIMP (`Vijuhas` / `davidvorona/gim-plugin`, 4,585 installs):** Early (Nov 2021) GIM plugin with sidebar stats, boss KCs, map locations, and tile pings, but requires self-hosting a custom backend server.
* **Group Storage Reminder (`RedSparr0w`, 1,261 installs), Group Item Tracker (`CodyLyttle`, 118 installs), & Group Storage Tracker (`MerryGusmas`, 53 installs):** Solve the **"borrowed group item" problem** — reminding a player before logging out that they still have the group's Dragon Warhammer, Imbued Heart, or BGS in their personal inventory/equipment/bank, outlining tracked group items, or syncing a `Group Storage Tracker` tab in RuneLite's Bank Tags plugin.

---

## 4. Master Feature Matrix

Columns compare **GIM Utilities** (`main` @ `ca055e2` + planned branches), **Group Ironman Homebase** (`bonscot1/Homebase` @ `#20070` + `#20391` + `#20570`), **Group Ironmen Tracker + Companion** (`groupiron.men` + `KingKj52`), **GIMP** (`Vijuhas`), **Group Iron Panel** (`toasty-toast`), **Discord Group Bank / GIM Bank Discord** (`barthogenes` / `Boredska`), and **Storage Reminder / Item Tracker** (`RedSparr0w` / `MerryGusmas` / `CodyLyttle`).

Legend: **Yes** = Shipped on `main` | **Partial** = Subset shipped | **Planned** = In StayPoint backlog | **No** = Not supported.

| Feature Category & Capability | GIM Utilities (Ours) | GIM Homebase (`bonscot1`) | GIM Tracker + Companion (`groupiron.men`) | GIMP (`Vijuhas`) | Group Iron Panel (`toasty-toast`) | Discord Bank Plugins (`barthogenes` / `Boredska`) | Storage Trackers (`RedSparr0w` / `MerryGusmas`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Install Count (Nov 2026)** | *Hub PR #20149* | *New (PR #20070 / #20570)* | **23,828** (+ **60** companion) | **4,585** | **8,144** | **2,522** / **1,766** | **1,261** / **118** / **53** |
| **1. Architecture & Sync** | | | | | | | |
| Serverless P2P sync (`PartyService`) | **Yes** (`GroupSync.java`) | **Yes** | No (Requires web server) | No (Self-hosted server) | No (Hiscores only) | No | No |
| Works without Party plugin enabled | **Yes** (`PartyDataService` sub) | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** |
| Auto-sync on login without clicking Join | **Planned** (`RUNELITE-206` CF Worker) | **Yes** *(⚠️ Party auto-join ban risk)* | **Yes** (HTTP POST to server) | **Yes** (Custom server) | **Yes** (Hiscores) | **Yes** | **Yes** |
| Multi-hop gossip relay for offline members | Partial (Events/lists/roles sync; banks/stats direct) | **Yes** (Summaries swapped on join) | **Yes** (Central DB) | **Yes** (Central DB) | No | No | No |
| Roster verification (`ClanID.GROUP_IRONMAN`) | **Yes** (`GroupMembers.java`, `GroupBinding.java`) | **Yes** (+ optional secret word) | No (Token-based) | No | Manual RSN entry | No | No |
| Alt / non-group account isolation | **Yes** (`GroupBinding.java`, `LoginGate.java`) | **Yes** (PR #3 group name check) | **Yes** (Per-character check) | Partial | No | No | No |
| **2. Member Status, Vitals & Presence** | | | | | | | |
| Online status & world for non-plugin members | **Yes** (`GamePresence.java` from GIM clan channel) | Partial (Party + last seen) | No (Plugin users only) | No | No | No | No |
| Live HP, Prayer, Run Energy & Special Attack | **Yes** (Minimap-style orbs + Spec) | Partial (HP, Prayer, Run; no Spec) | **Yes** (HP, Prayer, Run) | **Yes** | No | No | No |
| Hiscore skill fallback for non-plugin members | **Yes** (`HiscoreBook.java`, 6h cache + assumed orbs) | No (Only boss KC via Hiscores) | No | No | **Yes** (Hiscores only) | No | No |
| Named locations (never raw region IDs) | **Yes** (970+ regions + nearest-town/wildy fallback) | **Yes** (Area names) | **Yes** (Web map coordinates) | **Yes** (Map coordinates) | No | No | No |
| On-canvas Group Vitals HUD next to player | **Planned** (`RUNELITE-215`) | **Yes** (Configurable Group HUD) | No | No | No | No | No |
| Cross-world translucent "Ghost" teammates | No | No | **Yes** (Companion v1.1.0 `#20189`) | No | No | No | No |
| **3. Inventories, Equipment, Banks & Containers** | | | | | | | |
| Personal bank snapshot + total GP value | **Yes** (`BankSnapshot.java`, collapsible cards) | **Yes** (Collapsible on member card) | **Yes** (Web + `Ctrl+G` companion) | No | No | No | Local only |
| Exact 28-slot inventory layout + noted drawing | **Yes** (PR #70/#76 `GroupIronmanPanel.java`) | Partial (Item grid) | **Yes** (Web + companion) | No | No | No | No |
| Worn equipment grid with empty-slot icons | **Yes** (`WornSlotIcons.java`) | **Yes** | **Yes** | No | No | No | No |
| Rune Pouch & Dizana's Quiver (live varbits) | **Yes** (`MemberExtras.java`) | No | **Yes** (Rune pouch + quiver) | No | No | No | No |
| Seed Vault snapshot | **Yes** (`MemberExtras.java`) | **Yes** | **Yes** | No | No | No | No |
| Log basket, Fish barrel & Coal bag counts | **Yes** (`CarriedContainers.java`, `SkillingContainer.java`) | No | No | No | No | No | No |
| Looting Bag contents & Wilderness risk value | **Planned** (`RUNELITE-193`) | Partial (Looting bag items; no risk GP) | No | No | No | No | No |
| Player-Owned House (POH) rooms & Costume Room | No (`new`) | **Yes** (Built rooms + Costume Room) | No | No | No | No | No |
| **4. Group Storage & Borrowed Item Tracking** | | | | | | | |
| Group Storage snapshot + GP total | **Yes** (`StorageSession.java`) | **Yes** | **Yes** | No | No | No | Local only |
| Verified save detection (`"Saving..."` modal 293) | **Yes** (`StorageSession.java`, PR #68) | Partial (On close/update) | **Yes** (`GROUP_STORAGE_LOADER = 293`) | No | No | Partial | No |
| Group Storage deposit/withdrawal history log | **Yes** (`StorageLog.java`, last 200 changes) | **Yes** (With item pictures) | **Yes** (Web activity log) | No | No | Discord only | No |
| Storage low-stock minimums + desktop alert | **Yes** (`LowStock.java`) | No | No | No | No | No | No |
| Best-guess offline attribution + "That was me" | **Planned** (`RUNELITE-102`) | No | No | No | No | No | No |
| Borrowed item tracking ("Items owed back") | No (`new`) | **Yes** (Items owed back in Storage tab) | No | No | No | No | **Yes** (`Group Storage Tracker` / `Reminder`) |
| Bank Bounties (soft-lock storage withdrawals) | No *(Deliberately avoided: menu rule)* | **Yes** *(⚠️ Hides `Withdraw` menu options)* | No | No | No | No | No |
| **5. Item Search (`::find` & Panel)** | | | | | | | |
| Panel cross-group item search | **Yes** (Banks, storage, inv, worn, pouches, baskets) | **Yes** (`Items` tab) | **Yes** (Web + companion) | No | No | No | Bank `/g` filter |
| Chatbox `::find <item>` command + shorthand aliases | **Yes** (`FindCommand.java`, `item-aliases.txt`) | No | No | No | No | No | No |
| **6. Progression, Hiscores & Comparison** | | | | | | | |
| Skills with live potion/prayer boosts (`90 (+8)`) | **Yes** (`BoostText.java`, `MemberStats.java`) | **Yes** | **Yes** | **Yes** | Hiscores only | No | No |
| Skill Tab hover tooltips showing group XP/levels | No (`new`) | No | **Yes** (Companion plugin) | No | No | No | No |
| Shared Quest states & Quest Points on cards | **Planned** (`RUNELITE-218`; bundled quests synced today for Shopping) | **Yes** (Quests + QP on cards) | **Yes** (Web + companion) | No | No | No | No |
| Shared Achievement Diaries & Combat Achievements | **Planned** (`RUNELITE-218`; milestones logged today) | **Yes** (Full state on member cards) | **Yes** (Diaries on web) | No | No | No | No |
| Full Collection Log browsing per member | No (`new`; new clog slots logged in feed/Discord) | **Yes** (Script `4100` full sync) | **Yes** (Web dashboard) | No | No | No | No |
| Shared Boss Kill Counts (KC) on member cards | **Planned** (`RUNELITE-218`; KC milestones in feed) | **Yes** (`HiscoreManager` on login) | **Yes** | **Yes** | **Yes** | No | No |
| Shared Boss Personal Bests (PBs) + Clog overlay | **Planned** (`RUNELITE-218`; PB beats in feed/Discord) | **Yes** (Cards + Collection Log boss page) | No | No | No | No | No |
| Player-vs-Player Compare tab (skills + bosses) | **Planned** (`RUNELITE-218`) | **Yes** (`Compare` tab) | Web comparison | No | Side-by-side table | No | No |
| Group Hiscores rank & Compare vs group above | **Planned** (`RUNELITE-218` & `RUNELITE-88`) | **Yes** (PR `#20570` / `63771f4`) | No | No | No | No | No |
| **7. Group Coordination: Shopping, Requests & Goals** | | | | | | | |
| Shared Item Requests (`I'll get it`, claims, progress) | **Yes** (`RequestBoard.java`, `RequestCards.java`) | **Yes** (`Requests` tab + `[Homebase]` chat) | No | No | No | No | No |
| Quest-aware Group Shopping Lists + Pure Presets | **Yes** (`ShoppingTab.java`, `ShopPresets.java`) | No | No | No | No | No | No |
| Shop price & Monster drop sources per needed item | **Yes** (`ShopSources.java`, `ItemDrops.java`) | No | No | No | No | No | No |
| Proximity shop alerts when walking past a shop | **Yes** (`ShopVisits.java`; group sync in `RUNELITE-192`) | No | No | No | No | No | No |
| One-click `"Path to"` via Shortest Path plugin | **Yes** (`ShortestPathBridge.java`, `ItemPaths.java`) | No | No | No | No | No | No |
| Clipboard list import (text, CSV, JSON) | **Planned** (`RUNELITE-194`) | No | No | No | No | No | No |
| Personal & Group Watchlists with suggestions | **Planned** (`RUNELITE-86`, branch built) | **Yes** (`Goals` tab + `Bounties`) | No | No | No | No | No |
| **8. Live Bossing, Wave Activities & Map** | | | | | | | |
| Live Fight Caves, Inferno & Colosseum wave tracker | **Yes** (`WaveTracker.java`, `ColosseumModifiers.java`) | Partial (Coarse area/activity label) | No | No | No | No | No |
| Wave rotations & per-wave split timers | **Planned** (`RUNELITE-208`) | No | No | No | No | No | No |
| Entry/milestone desktop notifications for waves | **Planned** (`RUNELITE-207`) | No | No | No | No | No | No |
| Raids (CoX/ToB/ToA) & Skilling Boss live state | **Planned** (`docs/plans/activities.md` Slices B–D) | No | No | No | No | No | No |
| Teammates on World Map | **Yes** (`MemberMapMarkers.java`) | **Yes** | **Yes** (Web + companion) | **Yes** | No | No | No |
| Shared Map/Ground Pings, Tile Markers & NPC Tags | No (`new`) | **Yes** (Pings, group tiles, group NPCs) | No | **Yes** (Tile pings) | No | No | No |
| **9. Event Feed, Discord Webhooks & Governance** | | | | | | | |
| Searchable 2,000-event / 30-day sidebar Log | **Yes** (`GroupIronmanPanel.drawLog`, 39 event types) | Partial (`Updates` tab with icons) | Partial (Web activity feed) | No | No | No | Storage only |
| In-game toast banners & live Group Box overlay | **Yes** (`EventToastOverlay.java`, `GroupBoxOverlay.java`) | Partial (`[Homebase]` chat lines) | No | No | No | No | No |
| Native Discord Webhooks (7 channels + personas) | **Yes** (`DiscordWebhook.java`, `Webhooks.java`) | **No** | **No** | **No** | **No** | Storage only (1 URL) | **No** |
| 30-min per-member in-place Discord post grouping | **Yes** (`DiscordMerge.java`, `LevelThreads.java`) | **No** | **No** | **No** | **No** | **No** | **No** |
| Persistent Discord Live Status Board + daily repost | **Yes** (`StatusBoard.java`) | **No** | **No** | **No** | **No** | **No** | **No** |
| Broadcast relay for members without the plugin | **Yes** (`GroupBroadcasts.java`, single-poster dedup) | **No** | **No** | **No** | **No** | **No** | **No** |
| Daily Digest & Weekly Recap Discord summaries | **Yes** (`Digest.java`, `GroupEventEngine.java`) | **No** | **No** | **No** | **No** | **No** | **No** |
| Event screenshots (level-ups, drops, death/grave) | **Yes** (`LevelShot.java`, `GraveShot.java`) | **No** | **No** | **No** | **No** | **No** | **No** |
| Curated OSRS meme Easter eggs (73, 69, spade, etc.) | **Yes** (`EasterEggs.java`, `EasterEggLines.java`) | **No** | **No** | **No** | **No** | **No** | **No** |
| Cryptographic Leader/Editor roles & Group Settings | **Yes** (`GroupGovernance.java`, `GroupSigner.java`) | **No** | **No** | **No** | **No** | **No** | **No** |
| Leader-shared Discord webhooks | **Yes** (`GroupWebhooks.java`) | **No** | **No** | **No** | **No** | **No** | **No** |

---

## 5. Features GIM Utilities Lacks — Ranked by Value to a Group

Each gap below is ranked from highest to lowest day-to-day value for an active 2–5 player Group Ironman team, paired with its matching StayPoint backlog task (or marked **`new`** if no task exists yet) and its Plugin Hub compliance status.

| Rank | Feature We Lack | Competitor(s) With It | Value to a GIM Group | Matching Backlog Task | Compliance & Implementation Notes |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **1** | **Zero-Click Auto-Sync on Login + Offline Relay & Server-Side Discord Deduplication** | **Group Ironmen Tracker** (`groupiron.men`, HTTP backend), **Homebase** (`PartyService` auto-join) | **Critical (High).** Today, players must click `Join group sync` every session (`PartyService` rule), and two members who are never online at the same time cannot exchange bank/stat snapshots directly. A lightweight backend eliminates the manual click, stores snapshots for offline teammates, keeps one authoritative Discord Live Board, and deduplicates Discord posts across clients. | **`RUNELITE-206`** (`task-235f05b5`, *own sync server on Cloudflare Workers + SQLite Durable Objects*, building `docs/plans/gim-server.md`) | **Allowed, with precedent** (`Group Ironmen Tracker`). Must be opt-in (`serverSync = false` default) with the standard 3rd-party server IP warning. Note: Homebase's approach (auto-joining `PartyService` on login) is a **rejected feature** on the Hub (`plugin-hub#19833`), so `RUNELITE-206` is the only compliant way to achieve zero-click login sync. |
| **2** | **Shared Full Quest States, Quest Points, Achievement Diaries & Combat Achievements on Member Cards** | **Homebase** (`Members` card), **Group Ironmen Tracker** + **Companion**, **Danj Tracker** | **High.** Today GIM Utilities reads quest states only for the ~55 quests in `quest-items.json` (`QuestStates.java`) to power Shopping presets, and logs quest/diary/CA completions as one-time events (`ProgressTracker.java`, `EventWatchers.java`). Teammates cannot expand a member's card to see all 170+ quests, exact Quest Points, per-region Diary tiers (Easy–Elite), or Combat Achievement tiers completed. | **`RUNELITE-218`** (`task-c3ec10ad`, item #3) & feeds **`RUNELITE-190`** (`task-4c61d485`, *Group quest-item needs across Quest Route*) | **Allowed, with precedent** (`Homebase`, `Group Ironmen Tracker`). Read all `Quest.values()` states on the client thread, `VarPlayer.QUEST_POINTS`, diary completion varbits (`EventWatchers.java`), and CA tier varbits; compress into a bitset on `VvgGroupIronmanMessage`. |
| **3** | **Shared Boss Kill Counts (KC) & Boss Personal Bests (PBs) on Cards + Collection Log PB Overlay** | **Homebase** (PR `#20391` / `#20570`), **GIMP**, **Group Iron Panel**, **Danj Tracker** | **High.** GIM Utilities detects live `(new personal best)` and KC chat lines as events (`EventChat.java`), but does not pull baseline boss KCs from `HiscoreClient` onto member cards, does not store a per-member PB table, and does not show teammates' PBs next to your own on Collection Log boss pages. | **`RUNELITE-218`** (`task-c3ec10ad`, item #1) | **Allowed, with precedent** (`HiscorePlugin` in base client; `Homebase` PR `#20391`). Reuse `HiscoreBook.java` (`HiscoreSkill` includes all bosses) for baseline KCs; capture PBs from chat and Collection Log boss widgets (`InterfaceID.Collection`). |
| **4** | **Compare Tab: Player-vs-Player (Skills + Bosses) & Group Hiscores Rank / Neighbour Comparison** | **Homebase** (`Compare` tab in `#20391`, Group Hiscores compare in `#20570` / `63771f4`), **Group Iron Panel** | **High.** Friendly competition drives GIM engagement: putting any two teammates side by side (who leads in each skill, total XP, and boss KC/PB), plus seeing the group's official Group Ironman Hiscores rank and comparing skill totals against the group ranked 1 spot ahead (or any typed group name). | **`RUNELITE-218`** (`task-c3ec10ad`, item #2) & **`RUNELITE-88`** (`task-2776b5ff`, item #38 *group hiscore rank change*) | **Allowed, with precedent** (`Homebase` `#20570`, base `HiscorePlugin`). Note: RuneLite's `HiscoreEndpoint` enum lacks a GIM team endpoint (`BRIEF.md:346`), so Group Hiscores HTML/CSV lookup must use injected `OkHttpClient` against `secure.runescape.com/m=hiscore_oldschool_ironman/group-ironman/` (official Jagex domain, rate-limited). |
| **5** | **Group Vitals HUD Overlay Beside Your Character (HP, Prayer, Spec, Run, World, Activity)** | **Homebase** (`Group HUD`), base `PartyPlugin` HUD | **High.** During group bossing, raids, or Wilderness trips, players stare at the game canvas rather than the sidebar panel. Showing compact bars beside/near the player (without needing the base Party plugin enabled) makes live vitals and wave/Wilderness status immediately actionable. | **`RUNELITE-215`** (`task-23dbffa6`, *group vitals HUD beside your character*) | **Allowed, with precedent** (base `PartyPlugin` status overlay, `Homebase`). Strictly group roster members only (no non-group PvP scouting). |
| **6** | **Members' Looting Bag Contents & Wilderness Risk Value (Skulled vs. Unskulled)** | **Homebase** (shares Looting Bag items; lacks risk value), **Wealth Tracker** (local looting bag) | **Medium-High.** Essential for Wilderness GIM content (Revenants, Wildy bosses, Chaos Altar, clues). Knowing what is inside a teammate's looting bag (`InventoryID.LOOTING_BAG = 516`) and their exact GP at risk (unskulled keep-3 vs. skulled keep-0, `+1` Protect Item, plus always-lost looting bag) prevents costly group wipe mistakes. | **`RUNELITE-193`** (`task-ebaa9acd`, *members' looting bag contents and risk value*) | **Allowed, with precedent** (`Homebase` looting bag sync, base `Items Kept on Death` interface). Own group members only. Also pairs with **`RUNELITE-180`** (`task-d59d6dad`, *"bring this" gear checks for bosses/revs/raids*). |
| **7** | **Live Activity Expansion: Wave Rotations/Splits + Raids (CoX/ToB/ToA) & Skilling Bosses** | **Dink** (raid/wave completions), **ToA** plugin (party sync) | **Medium-High.** GIM Utilities already leads with Slice A (`WaveTracker.java` for Fight Caves, Inferno, Colosseum). Adding Fight Caves spawn rotations, per-wave split times (`RUNELITE-208`), entry notifications (`RUNELITE-207`), and Slices B–D from `docs/plans/activities.md` (CoX, ToB, ToA, Gauntlet, Wintertodt, Tempoross, BA, Barrows live crypts, Moons) completes the live PvM picture. | **`RUNELITE-207`** (`task-56974316`), **`RUNELITE-208`** (`task-5a024341`), and **`RUNELITE-134` Slices B–D** (`docs/plans/activities.md`, research in `task-a608d276`) | **Allowed in narrowed form** (see `docs/plans/activities.md` §1.2: coarse room/wave/points/time only, zero boss mechanic helpers, non-GIM players counted as `+N others`). |
| **8** | **Borrowed-Item ("Items Owed Back") Tracking & Return-to-Storage Reminders** | **Homebase** (`Items owed back` in Storage tab), **Group Storage Tracker** (`MerryGusmas`), **Group Storage Reminder** (`RedSparr0w`), **Group Item Tracker** (`CodyLyttle`) | **Medium.** The #1 friction point in GIM groups is a member withdrawing a shared unique (DWH, BGS, Zenyte, Rune Pouch/imbued items) for a task and logging out with it in their personal bank or worn equipment. Tracking "who currently holds an item withdrawn from Group Storage" and reminding them before logout or on bank close solves this cleanly without menu manipulation. | **`new`** *(partially adjacent to `RUNELITE-102` `task-d35e2d07` offline storage attribution)* | **Allowed, with precedent** (`Group Storage Reminder`, `Group Storage Tracker`, `Homebase`). Derive "borrowed" status purely from `StorageLog` withdrawals + current `BankSnapshot`/carried holdings, with an optional logout/bank-close chat/notifier reminder. **Do not** copy Homebase's `Hold items in group storage` menu-entry hiding (`MenuEntry` removal is restricted). |
| **9** | **Full Collection Log & POH (Rooms + Costume Room) Sync** | **Homebase** (Script `4100` clog sync + POH rooms/Costume Room), **Group Ironmen Tracker** (clog) | **Medium.** GIM Utilities currently tracks new Collection Log slots via chat messages (`ClogMessage.java`) and Barrows chest pieces (`BarrowsProgress.java`), and indexes Seed Vault/Rune Pouch/Quiver (`MemberExtras.java`). It does not sync a member's existing historical Collection Log when they open/search it, nor items stored in their POH Costume Room (treasure trail rewards, skilling outfits) or built POH utility rooms (ornate pool, occult altar, fairy ring). | **`new`** | **Allowed, with precedent** (`Homebase`, `ProgressScape`, `WikiSync` watching `ScriptID 4100` on Collection Log search; POH costume room containers / game objects on POH load). Improves `::find` so Clue items stored in a member's Costume Room are found. |
| **10** | **Shared Group Goals, Watchlists & Compliant Bounties** | **Homebase** (`Goals` tab + `Bounties`), **GIM Tasks** (`PlumpPanda-123`) | **Medium.** Beyond item Shopping Lists and member Requests (which GIM Utilities already has), groups enjoy setting non-item goals (e.g. *"Everyone 70 Herblore for SotE"*, *"Group 100 Cox KC"*) and item watchlists/bounties. | **Partial:** **`RUNELITE-86`** (`task-4c7f42be`, *personal & group watchlists with suggestions*, branch `7240213` built) + **`RUNELITE-194`** (`task-87890032`, *clipboard list import*) + **`RUNELITE-192`** (`task-5b99e469`, *shared need/shop alerts*). Non-item skill/KC goals & bounties are **`new`**. | **Allowed, with precedent** (`Homebase`, `GIM Tasks`). Keep bounties purely informational (progress bar + claim/reward note + Discord announcement); never hide Group Storage `Withdraw` menu options. |
| **11** | **Skill Tab Hover Tooltips (Group Levels & XP on Vanilla Skills Tab)** | **Group Ironmen Tracker In-Game Companion** (`KingKj52`) | **Low-Medium.** Hovering over a skill widget in the vanilla OSRS Skills tab appends teammates' levels and XP to the tooltip, saving a trip to the sidebar panel. | **`new`** | **Allowed, with precedent** (`Group Ironmen Tracker In-Game Companion`). Uses RuneLite's `TooltipManager` on `InterfaceID.Stats` hover when an opt-in setting is enabled. |
| **12** | **Shared World Map / Ground Pings, Group Tile Markers & Group NPC Highlights** | **Homebase** (Pings, group tile markers, group NPC highlights), **GIMP** (tile pings), base `PartyPlugin` | **Low.** Useful when scouting or teaching a boss together, but base RuneLite `PartyPlugin` already provides tile pings (`Shift`+ping), and `Ground Markers` allows clipboard import/export. Furthermore, group NPC highlighting in boss rooms skirts close to PvM combat restrictions if not carefully scoped. | **`new`** *(not recommended for near-term build)* | **Restricted / Low ROI.** Base `PartyPlugin` already handles pings; adding custom NPC highlights risks Plugin Hub reviewer pushback under *Boss & Combat Restrictions* and increases token count against the 200k-token Hub limit (`RUNELITE-211` / `task-1338d75c`). |
| **13** | **Cross-World Translucent "Ghost" Players in the Game Scene** | **Group Ironmen Tracker In-Game Companion** (`plugin-hub#20189`) | **Low.** Draws teammates who are in the same map region on a different world as translucent ghost models in your 3D scene. Neat visual novelty, zero functional coordination value. | **`new`** *(not recommended)* | **Allowed, with precedent** (`plugin-hub#20189`), but high code/token complexity for a cosmetic gimmick. |

---

## 6. One-Line Verdicts on Every Homebase Feature (`RUNELITE-218` Item #4)

Per `RUNELITE-218` (`task-c3ec10ad` item #4: *"Look through their README and update notes for any other feature we lack, and list them with a one-line verdict each"*):

1. **Boss Personal Bests (PBs) shared on cards, Collection Log boss pages & Updates feed (`#20391`):** **Build in `RUNELITE-218`** — high-value competitive feature with clean compliance precedent.
2. **Player-vs-Player Compare tab & Group Hiscores Compare (`#20391`, `#20570`):** **Build in `RUNELITE-218`** — top-tier engagement feature for GIM teams; use `HiscoreClient` for players and rate-limited Jagex GIM hiscore page reads for groups.
3. **Full Quests, Quest Points & Achievement Diaries shared (`#20070`, `#20391`):** **Build in `RUNELITE-218`** — essential for member cards and directly powers `RUNELITE-190` (Quest Route group item needs).
4. **Combat Achievements & Boss Kill Counts on member cards (`#20070`):** **Fold into `RUNELITE-218`** — natural companions to diaries and boss PBs (`HiscoreBook` already fetches `HiscoreResult` which includes boss KCs).
5. **Group Vitals HUD on canvas (`#20070`):** **Build in `RUNELITE-215`** — high utility during group PvM/Wilderness; keep strictly scoped to group roster members.
6. **Looting Bag contents synced on member cards (`#1`, `#20070`):** **Build in `RUNELITE-193`** — pair with skulled/unskulled Wilderness risk value so it is far more useful than a plain item list.
7. **Items Owed Back (borrowed storage item tracking in Storage tab, `#20391`):** **Worth adding (`new` backlog candidate)** — solves borrowed-gear friction cleanly by diffing `StorageLog` withdrawals against current holdings.
8. **Bank Bounties that hide `Withdraw` / `Withdraw-X` menu options in Group Storage (`#20070`):** **Do NOT build the menu lock** — removing or hiding game menu entries conditionally in storage risks Plugin Hub rejection under *Menu Restrictions* and frustrates players on emergencies; keep requests/bounties informational.
9. **Auto-joining `PartyService` on launch/login from group name (`#1`, `#3`):** **Do NOT build on `PartyService`** — explicitly banned by RuneLite's *Rejected or Rolled-Back Features* wiki ("Auto-rejoin for parties") and rejected in `plugin-hub#19833`; solve via our own Cloudflare sync server in `RUNELITE-206` instead.
10. **Multi-hop gossip relay of offline members' cached sections on party join (`#1`):** **Superseded by `RUNELITE-206`** — our Cloudflare Durable Object sync server solves offline state persistence permanently without bloating `PartyService` join bursts.
11. **Full Collection Log capture via `ScriptID 4100` & POH Costume Room sync (`#20070`):** **Defer (`new` backlog)** — nice-to-have for `::find` completeness (finding clue items in Costume Rooms), but watch the 200k-token Hub budget (`task-1338d75c`).
12. **Shared Group Goals (`#20070`):** **Partially covered by `RUNELITE-86` (Watchlists) & Shopping Lists** — our quest-aware Shopping Lists with Shortest Path routing are already much deeper for item goals; non-item XP/KC goals can wait.
13. **Group Pings, Group Tile Markers & Group NPC Highlights (`#20070`):** **Skip** — duplicates base `PartyPlugin` pings, carries PvM compliance risk on NPC highlights, and isn't worth the token budget.
14. **Group Ironman group name shown in panel header (`#2`):** **Trivial polish (`new`)** — reading `client.getClanSettings(ClanID.GROUP_IRONMAN).getName()` to display the group's in-game name in `SyncHeader.java` is a 5-line UX win.

---

## 7. Where GIM Utilities Is Ahead of Every Competitor

1. **Complete Discord Ecosystem (`DiscordSender.java`, `DiscordMerge.java`, `LevelThreads.java`, `StatusBoard.java`, `GroupBroadcasts.java`):**
   * Neither **Homebase** nor **Group Ironmen Tracker** sends Discord webhooks. GIM Teams using those plugins must install **Dink** + **Discord Group Bank Notifications** separately and configure webhooks 5 times across every teammate's PC.
   * GIM Utilities provides **7 category webhooks** with distinct OSRS personas (`Town Crier`, `World Hopper`, `Party Pete`, `Skillcape Seller`, `Death's Office`, `Gielinor Banker`, `Varrock Herald`), **leader-shared webhooks** (`GroupWebhooks.java`) so teammates just click one toggle, **30-minute per-member in-place message editing** (preventing level-up, diary, and storage spam), **net-value storage visit merging** (`efbf3bad`), a **persistent 1-message Live Status Board** with midnight repost (`05c4b9af`), **broadcast relay for teammates without the plugin** (`870b71d6`), **Daily Digests**, **Weekly Recaps**, **event screenshots**, and **OSRS meme Easter eggs**.
2. **Cryptographic Group Governance (`GroupGovernance.java`, `GroupSigner.java`, `GroupSettingsTab.java`):**
   * Leader and editor roles signed with ECDSA P-256 (`java.security`), allowing the group leader to configure thresholds, event toggles, quiet hours, and webhooks once for the entire group (`RUNELITE-210` expands this to nearly all settings), while preserving per-member privacy opt-outs (`MemberPrefs.java`).
3. **Quest-Aware Group Shopping Lists + Shortest Path Integration (`ShoppingTab.java`, `ShopPresets.java`, `ShortestPathBridge.java`, `ItemPaths.java`):**
   * Homebase and GIM Tasks have basic manual item requests/goals. GIM Utilities has **build presets** (1-Defence pure milestones: Mithril gloves, Ava's, Desert Treasure, MM1, etc.) that automatically inspect each member's completed/in-progress quests (`QuestStates.java`), subtract items already held across the group's banks/storage/inventories/pouches (`ShopAllocation.java`), show **reachable shops with prices** and **monster drop sources** (`ShopSources.java`, `ItemDrops.java`), fire **in-game chat alerts when walking near a needed shop** (`ShopVisits.java`), and draw **one-click in-game routes via Shortest Path** (`Path to` / `Path to next`).
4. **Live Wave & Boss Activity Telemetry (`WaveTracker.java`, `ColosseumModifiers.java`, `ActivityState.java`):**
   * Real-time wave tracking for **Fight Caves** (`1–63`), **The Inferno** (`1–69`), and **Fortis Colosseum** (`1–12`, including Glory and selected modifiers + tiers), surfaced on member cards, the dedicated `Activities` tab, the in-game `GroupBoxOverlay`, and the Discord Live Status Board.
5. **Fast In-Game Lookup (`::find` + Carried Skilling Containers):**
   * Instant chatbox `::find <item>` with shorthand aliases (`dwh`, `ppot`, `brew`), colour-coded by location (`FindCommand.java`, `FindStyle.java`), and deep container coverage including **live Rune Pouch**, **Dizana's Quiver**, **Seed Vault**, **Log Basket**, **Fish Barrel**, and **Coal Bag** (`CarriedContainers.java`).
6. **Zero-Install Friend Onboarding (`tools/windows/Run-GimUtilities.cmd`):**
   * SHA-256-pinned portable Windows launcher tested in CI (`windows-launcher` workflow) that downloads Temurin Java 11 and runs the latest test tag with a double-click.

---

## 8. Code Map & Backlog Cross-Reference

### 8.1 GIM Utilities Code Map (Files Touched by Existing Features & Upcoming Gaps)
* **Plugin Entry & Panel:** `src/main/java/com/vinnyvangogh/groupironman/GroupIronmanPlugin.java:65-578`, `GroupIronmanPanel.java:298-327` (8 tabs: `Group`, `Banks`, `Storage`, `Find`, `Log`, `Activities`, `Shopping`, `Settings`), `GroupIronmanConfig.java:1-1300`.
* **Party Sync & Roster:** `GroupService.java:1-1900`, `GroupSync.java`, `GroupCodec.java`, `GroupMembers.java`, `GroupBinding.java`, `GamePresence.java`.
* **Hiscores & Progress:** `HiscoreBook.java:1-195` (extend in `RUNELITE-218` for Boss KCs and Compare), `ProgressTracker.java:18-156`, `QuestStates.java:21-125`, `QuestProgress.java:17-82`.
* **Containers & Storage:** `BankAssembler.java`, `BankSnapshot.java`, `MemberExtras.java:16-59`, `CarriedContainers.java:37-289`, `SkillingContainer.java:21-88`, `StorageSession.java`, `StorageLog.java`, `LowStock.java`.
* **Shopping, Requests & Routing:** `ShoppingTab.java`, `ShoppingBoard.java`, `ShopPresets.java`, `ShopAllocation.java`, `ShopSources.java`, `ItemDrops.java`, `RequestList.java:23-131`, `RequestBoard.java`, `RequestCards.java:20-224`, `ShortestPathBridge.java`, `ItemPaths.java`.
* **Live Activities:** `WaveTracker.java`, `ColosseumModifiers.java`, `ActivityState.java`, `ActivityLog.java`, `ActivityRows.java`.
* **Events, Overlays & Discord:** `GroupEvent.java:25-367`, `GroupEventEngine.java`, `EventWatchers.java`, `EventChat.java`, `EventToastOverlay.java:18-61`, `GroupBoxOverlay.java:19-77`, `DiscordWebhook.java`, `DiscordSender.java`, `DiscordMerge.java`, `LevelThreads.java`, `StatusBoard.java`, `GroupBroadcasts.java`, `Digest.java`, `EasterEggs.java`.
* **Governance:** `GroupGovernance.java`, `GroupRoles.java`, `GroupSigner.java`, `GroupSettingsSchema.java`, `GroupSettingsTab.java`, `GroupWebhooks.java`.

### 8.2 Summary Table of Backlog Tasks Mapped to Gaps
| StayPoint Task ID | Identifier | Stage | Gap / Feature Covered |
| :--- | :--- | :--- | :--- |
| `task-235f05b5` | **`RUNELITE-206`** | `backlog` | Own sync server (Cloudflare Workers + SQLite Durable Objects) for zero-click login sync, server-held group settings, and server-side Discord relay/deduplication (`docs/plans/gim-server.md`) |
| `task-c3ec10ad` | **`RUNELITE-218`** | `backlog` | Shared Boss PBs (cards + Collection Log boss page + feed), Compare tab (Player-vs-Player + Group Hiscores rank & group above), and shared full Quests / Quest Points / Achievement Diaries (+ Boss KCs & CAs) |
| `task-23dbffa6` | **`RUNELITE-215`** | `backlog` | Group vitals HUD beside local player character (HP, Prayer, Spec, Run, world, Wilderness/activity marker) |
| `task-ebaa9acd` | **`RUNELITE-193`** | `backlog` | Members' Looting Bag contents (`InventoryID 516`) and Wilderness risk value (skulled vs. unskulled) |
| `task-56974316` | **`RUNELITE-207`** | `backlog` | Desktop/in-game notifications when a group member enters Fight Caves, Inferno, or Colosseum (and milestones/deaths/completions) |
| `task-5a024341` | **`RUNELITE-208`** | `backlog` | Fight Caves / Inferno / Colosseum wave rotations and per-wave split timers (`task-a608d276` research) |
| `task-5760e864` | **`RUNELITE-210`** | `backlog` | Expand leader-controlled group settings over Party sync to cover nearly all Discord and group settings + new-install defaults |
| `task-18e49022` | **`RUNELITE-209`** | `backlog` | Suppress separate online/offline posts when the group has an active Live Status Board; fold combat-level increases into level-up posts |
| `task-406fffdc` | **`RUNELITE-214`** | `backlog` | Shopping `"Path to"` polish: clear Shortest Path requirement notice, arrival hand-off, and route withdrawals to nearest usable bank |
| `task-4c61d485` | **`RUNELITE-190`** | `backlog` | Whole-group quest-item needs across Quest Route (`Minimal quests` / `Full quests`), crossed off from synced quest states |
| `task-87890032` | **`RUNELITE-194`** | `backlog` | Import new shopping/request lists from clipboard (plain text, CSV, JSON) |
| `task-5b99e469` | **`RUNELITE-192`** | `backlog` | Share need and shop proximity alerts with the whole group/party |
| `task-d59d6dad` | **`RUNELITE-180`** | `backlog` | Location-aware `"Bring this"` gear checks for bosses, Revenants, and raids, showing which group member holds missing gear |
| `task-d35e2d07` | **`RUNELITE-102`** | `backlog` | Best-guess attribution for offline/mobile Group Storage changes + `"That was me"` claim + catch-up summary |
| `task-4c7f42be` | **`RUNELITE-86`** | `in_review` | Personal and group item Watchlists with `Suggested` box (`Accept` / `Dismiss` / `Accept all`) |
| `task-2776b5ff` | **`RUNELITE-88`** | `backlog` | Hard events (Part D): PK events, next-quest item needed, group hiscore rank change, trades between group members |
| *None (`new`)* | **`new`** | — | Borrowed-item (`Items owed back`) tracking in Storage tab + optional logout/bank-close return reminder |
| *None (`new`)* | **`new`** | — | Full Collection Log (`ScriptID 4100`) and POH Costume Room / built furniture sync |
| *None (`new`)* | **`new`** | — | Skill Tab hover tooltips showing teammates' levels and XP on the vanilla Skills tab |
| *None (`new`)* | **`new`** | — | Display in-game Group Ironman team name (`ClanSettings.getName()`) in `SyncHeader` |
