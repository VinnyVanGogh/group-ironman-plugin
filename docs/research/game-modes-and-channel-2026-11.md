# Research: OSRS Group Game Modes, Bingo/Challenge Plugins, Plugin-Dev YouTube Channel & Monetization

**Author:** Antigravity / Gemini CLI  
**Date:** November 2026  
**Repository:** `group-ironman-plugin` (GIM Utilities)  
**Deliverable:** Research Report & Strategic Plan (replaces GENERAL-125)  
**Status:** In Review  

---

## Executive Summary

This report delivers a four-part research study and roadmap for Old School RuneScape (OSRS) group game modes, clan event/bingo infrastructure, RuneLite plugin development content on YouTube, and legal monetization boundaries.

### Key Findings
1. **The Clan Event Gap:** Clans and Group Ironman (GIM) teams currently run competitions by stitching together Wise Old Man (WOM), Google Sheets, and Discord webhooks (Dink). Existing RuneLite bingo plugins (Anvil, BingoScape, RS-Bingo, AIO Bingo) universally depend on fragile third-party web backends, external database registrations, or manual screenshot verification channels. **There is currently zero serverless, peer-to-peer bingo or challenge plugin on the Plugin Hub that syncs directly between clients via RuneLite's native `PartyService`.**
2. **The GIM Utilities Opportunity:** GIM Utilities already possesses the exact peer-to-peer architecture required: deterministic room derivation (`GroupKey`), client-to-client WebSocket message exchange over RuneLite's party relay (`VvgGroupIronmanMessage`), native event detection (`EventDetect`), and rich Discord webhook broadcasting (`DiscordSender`). Adding a built-in Challenge & Bounty engine inside GIM Utilities represents the lowest-effort, highest-impact feature to re-energize stagnant mid/late-game GIM teams.
3. **Mootrius's Goldman Mode:** A hybrid between Bronzeman and Region-Locked Ironman, dividing Gielinor into 300+ sub-regions unlocked via progression currency ("Goldman GP"). The mode is strictly an **honor-system overlay** (Region Locker + custom spreadsheet) because RuneLite and Jagex rules forbid hard client-side game engine locks. Audience demand is high, but player friction is extreme due to the lack of an integrated companion plugin.
4. **YouTube Market Demand:** OSRS plugin development content sits in an underserved sweet spot between pure gameplay entertainment (Settled, Framed, Gudi) and comedy devlogs (Idyl, 94K subs / 10.7M views). Pairing real plugin engineering with an innovative agentic workflow (**StayPoint**) creates a differentiated, high-credibility channel format.
5. **Monetization Reality:** Paywalled in-game features and closed-source plugins are **strictly banned** by both Jagex's Third-Party Client Guidelines and RuneLite's Plugin Hub policy. Direct voluntary donations (Patreon, Ko-fi, GitHub Sponsors) for open-source plugins yield minimal revenue even for legendary creators (117HD earns ~$37/mo; Zoinkwiz / Quest Helper earns ~$550/mo; RuneLite itself earns ~$1,960/mo). The viable path to monetization is **content creation** (YouTube AdSense and brand sponsorships) and enterprise clan SaaS infrastructure, not in-game plugin paywalls.

---

## Part 1: Game Modes & Clan Bingo

### 1.1 How Clans Run In-Game Events & Bingo Today

Today, running an OSRS clan bingo or community event requires organizers to stitch together three disconnected layers: a hiscores scraper, a spreadsheet dashboard, and a Discord screenshot dumping ground.

```
+-----------------------------------------------------------------------------------+
|                            Current Clan Bingo Workflow                            |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [ In-Game Player ]                                                               |
|        |                                                                          |
|        +---> [ Wise Old Man (WOM) ] -------> Scrapes OSRS Hiscores                |
|        |     (XP, Boss KC, Clues)            (Delayed, No RNG Item Drops)         |
|        |                                                   |                      |
|        +---> [ RuneLite / Dink ] -----------> Discord Webhook                     |
|        |     (Loot & Clog Drops)             (Unverified Screenshots)             |
|        |                                                   |                      |
|        +---> [ Manual Screenshot ] --------> Discord #submissions                |
|              (Chatbox + Timestamp)                         |                      |
|                                                            v                      |
|                                            [ Clan Moderators / Admins ]           |
|                                            (Manual Verification Hell)             |
|                                                            |                      |
|                                                            v                      |
|                                            [ Google Sheets / Web Board ]          |
|                                            (Formulas, Scores, Standings)          |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

#### Layer 1: Wise Old Man (WOM)
* **Website:** [wiseoldman.net](https://wiseoldman.net)
* **Role:** The backend metrics engine for XP gains, Boss Kill Counts (KC), and Clue Scroll milestones.
* **Mechanism:** Organizers create a Clan Group and configure a timed Competition. WOM scrapes the official OSRS Hiscores API at regular intervals to calculate member deltas.
* **Strengths:** Fully automated, cheat-proof for hiscore-indexed metrics, open REST API (`wiseoldman.net/api`).
* **Critical Limitations:** 
  1. **Zero RNG Drop Visibility:** WOM cannot track specific item drops (e.g. Bandos Tassets, Enhanced Weapon Seed, Pets).
  2. **Hiscore Latency:** Updates only occur when a player logs out or is manually tracked, creating latency during live events.
  3. **No In-Game UI:** Players must alt-tab out of the client to inspect the web leaderboard.

#### Layer 2: Google Sheets
* **Role:** The event coordinator's master control panel and scoring sheet.
* **Mechanism:** Organizers build custom 5x5 or 7x7 grid matrices. Advanced sheets use Google Apps Script or `IMPORTDATA`/`IMPORTJSON` formulas to poll the Wise Old Man API for automated KC/XP tiles. Non-hiscore tiles (item drops, quest points, minigame completions) are manually checked off by moderators.
* **Pain Points & Vulnerabilities:**
  * **Concurrence & Formula Fragility:** High edit volume during 50-player events leads to cell corruption, rate-limiting, and broken scripts.
  * **Administrative Burnout:** Moderators spend 15–20 hours per week cross-referencing drop screenshots against timestamps and sheet rows.

#### Layer 3: Discord Bots & Webhook Plugins
* **Tools:** [Dink for Discord](https://runelite.phyce.dev/dink) (pajlads), [Discord Loot Logger](https://runelite.phyce.dev/discord-loot-logger), Scavenger Bot, custom slash-command bots.
* **Mechanism:** Players configure a RuneLite webhook plugin with a Discord webhook URL provided by the clan. When a drop or collection log notification fires, the plugin sends an embedded message with an attached screenshot to a submission channel.
* **Pain Points:** Flooded channels (hundreds of unsorted images), duplicate submissions, and frequent moderator disputes over whether a drop was received within the official event timeframe.

---

### 1.2 Plugin Hub Ecosystem: Clan Event, Bingo & Mode Plugins

A comprehensive audit of active Plugin Hub plugins related to clan competitions, bingo, and game modes reveals the current market landscape:

| Plugin Name | Author | Active Installs | Peak Installs | Primary Function | Architecture | Major Gaps & User Complaints |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Anvil Companion** | Anvil Team | ~562 | 980 | Full clan event & bingo companion platform | External SaaS (`anvilosrs.com`) + Client overlay | Requires third-party web account; server outages break client overlay; steep setup for casual clans. |
| **BingoScape Companion** | kaffeed | ~419 | 650 | In-game side panel bingo board & submitter | External SaaS (`bingoscape.org`) + Client overlay | Dependent on `bingoscape.org` uptime; rigid board layouts; lacks P2P team sync. |
| **AIO Bingo** | BajanSDK | ~34 | 85 | Event tracker sending drops/XP to external API | External SaaS (`aiobingo.com`) via team tokens | Obscure setup requiring secret team tokens; lack of community maintenance. |
| **RS-Bingo** | RS-Bingo Team | ~110 | 220 | Side panel board viewer & drop auto-submitter | External SaaS (`rs-bingo.com`) | External dependency; UI scaling issues; low adoption. |
| **Bingo Team Indicators** | Ew Its Mike | 1,346 | 1,890 | Colored hull & minimap indicators for teammates | Local client overlay | Only handles visual player identification; zero bingo board or tracking logic. |
| **Dink for Discord** | pajlads | 200,000+ | 250,000+ | Webhook notifications for loot, clog, death, quests | Outbound HTTP Webhooks (Discord API) | Broadcast tool only; has no internal bingo state, board tracking, or team coordination. |
| **Another Bronzeman Mode** | CodePanter | 3,726 | 7,026 | Unlocks GE purchasing after obtaining items self-found | Local `.runelite` profile JSON cache | Unlock history corruption; desyncs on multi-device play; lacks real-time group sync. |
| **Bronzeman Unleashed** | elertan | 1,378 | 1,950 | Modernized Bronzeman with group support | Local cache + External server sync | Server connectivity bugs; complex group setup; occasional unlock delay. |
| **Tileman Mode** | ConorLeckey, sram1337 | 5,466 | 8,200 | Movement restriction based on earned XP tiles | Local client tile marking engine | Solo-focused; manual export strings required to share progress. |
| **Group Tileman Addon** | Community | 1,859 | 2,750 | Shared tile grid for multiplayer Tileman | External relay / string export | Severe desyncs; ghost tiles; high latency between clients. |
| **Hub Party Panel** | TheStonedTurtle | 259,943 | 310,000+ | Displays party member inventory, gear, and stats | RuneLite `PartyService` (Official WS) | General inspection only; no challenge, task, or competition features. |
| **Group Ironmen Tracker** | Christopher Brown | 23,135 | 32,000+ | Tracks GIM group inventories and shared bank | External SaaS (`groupironmen.business`) | Third-party web backend dependency; data privacy concerns. |

---

### 1.3 The Missing Architectural Piece: Serverless Peer-to-Peer Bingo

**Question:** *Is there a plugin that runs a whole bingo board for a team the way GIM Utilities syncs a group?*

**Answer:** **No.** 

Every existing bingo plugin on the RuneLite Plugin Hub relies on a centralized third-party web application (`anvilosrs.com`, `bingoscape.org`, `rs-bingo.com`, `aiobingo.com`). When those servers go down, change monetization models, or suffer database corruption, the plugin ceases to function.

In contrast, **GIM Utilities** (`com.vinnyvangogh.groupironman`) operates on a fundamentally superior architecture:
1. **Zero External Backend:** It uses RuneLite's core `PartyService` (`net.runelite.client.party.PartyService`).
2. **Deterministic Cryptographic Rooms:** `GroupKey.passphrase(groupName, key)` generates a SHA-256 derived party passphrase. Any team member entering the same group name and key automatically converges on the same encrypted WebSocket relay room hosted directly on RuneLite's official servers (`api.runelite.net`).
3. **Decentralized Wire Protocol:** Clients exchange typed `VvgGroupIronmanMessage` payloads containing local state (bank snapshots, carried inventories, live stats, drop events).
4. **Discord Integration as Output Only:** Completed milestones and drops are posted directly to the clan or group Discord via client-originated HTTP requests (`DiscordSender`), eliminating the need for an intermediate proxy server.

**The Strategic Opportunity:** Extending this architecture to power an in-game, decentralized Bingo & Challenge engine represents an uncontested niche on the Plugin Hub.

---

### 1.4 Deep Dive: Mootrius's Goldman Mode Case Study

* **Video Subject:** *"I created a NEW game mode for OSRS || Goldman Mode [#1]"* by Mootrius
* **Direct URL:** [https://youtu.be/e1IoSzGeRGU](https://youtu.be/e1IoSzGeRGU)
* **Creator Profile:** Mootrius (OSRS YouTuber, author of the widely used *Mootrius Ironman Guide* on the official OSRS Wiki).

```
+-----------------------------------------------------------------------------------+
|                           Mootrius's Goldman Mode Loop                            |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [ Start: Lumbridge Castle (Restricted Area) ]                                    |
|         |                                                                         |
|         v                                                                         |
|  [ Play Within Boundaries ] ----> Train Skills, Kill NPCs, Complete Quests        |
|         |                                                                         |
|         v                                                                         |
|  [ Earn "Goldman GP" ] ---------> Formula: XP thresholds + KC + Clog slots        |
|         |                                                                         |
|         v                                                                         |
|  [ Spend Goldman GP ] ----------> Unlock 1 of 300+ adjacent Gielinor areas        |
|         |                                                                         |
|         v                                                                         |
|  [ Unlock Gear & Trading ] -----> Self-obtained items can now be bought on GE     |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

#### What the Mode Is
Goldman Mode is an area-restriction and progression ruleset blending elements of Bronzeman Mode, Region-Locked Ironman (Chunk accounts), and Grand Exchange economics:
1. **Micro-Area Locking:** Gielinor is divided into over 300 micro-regions (individual towns, castle floors, dungeon wings, or wilderness sectors), rather than standard 64x64 chunk grids. The player begins confined to Lumbridge Castle.
2. **Goldman GP Currency:** Players accumulate virtual "Goldman GP" by hitting in-game milestones: gaining combat/skilling XP, defeating monsters, completing quests, and checking off Collection Log slots.
3. **Purchasing Map Expansion:** Accumulated Goldman GP is spent to purchase borders and open adjacent micro-areas, gradually unlocking access to the rest of the world.
4. **Economic Hybrid Rules:** Items must first be self-obtained (or purchased from a newly unlocked NPC shop) before they are unlocked. Once an item is unlocked, the player is permitted to trade or buy duplicates freely from the Grand Exchange, removing the suffocating secondary resource grinds (e.g. collecting thousands of secondary herbs) that plague standard Ironmen.

#### How It Is Enforced (Honor System vs. Plugin)
* **Enforcement Reality:** It is strictly an **honor-system playstyle supported by visual overlays**.
* **Tools Used:** Mootrius uses RuneLite's *Region Locker* plugin (or custom chunk/tile borders) to draw visual boundary lines on screen, combined with a **master Google Spreadsheet** that tracks his earned Goldman GP, unlock costs, and area dependencies.
* **Why No Hard Client Lock?** RuneLite's API and Jagex's Third-Party Client Guidelines strictly prohibit plugins from blocking user pathfinding, disabling client click packets, or forcibly locking player movement. Any enforcement must remain advisory (visual highlights, warning toasts, or outbox error banners).

#### Audience Reaction & Market Demand
* **High Viewer Engagement:** The video garnered over 50,000 views in its opening run, with viewers widely praising the concept as a breath of fresh air compared to traditional "chunk-locked" series. Audiences appreciated that progression felt active and strategic rather than bogged down by 500-hour grinds on low-level monsters.
* **The Critical Friction Point:** Commenters consistently noted that *playing* the mode themselves was prohibitively tedious. Manually managing a spreadsheet while editing Region Locker chunk lists in RuneLite proved too cumbersome for casual players. Viewers explicitly requested a dedicated, automated companion plugin to track Goldman GP and highlight unlockable borders automatically.

---

### 1.5 Brainstorming 14 Plugin & Game-Mode Ideas

The following 14 concepts are ranked by verified community demand evidence and estimated development effort:

| Rank | Concept Name | Target Audience | Core Gameplay Mechanism | Demand Evidence | Build Effort | Fit for GIM Utilities |
| :---: | :--- | :--- | :--- | :--- | :---: | :---: |
| **1** | **Group Challenge & Bounty Board** | GIM Teams & Small Clans | In-group custom bounty boards (kill boss X, get drop Y, craft Z items). Auto-tracks via game events, syncs over PartyService, posts celebration embeds to Discord. | High: Top requested GIM social feature on r/ironscape. | Medium | **Native (Top Priority)** |
| **2** | **Squad Bingo (Serverless P2P Bingo)** | Mid-sized Clans & PVM Teams | Decentralized 5x5 / 7x7 interactive bingo board in RuneLite side panel. Auto-detects drops, KC, and XP; syncs live between clients using PartyService; zero external web server. | Very High: Weekly threads on r/2007scape complaining about sheet verification. | High | High (Standalone or Addon) |
| **3** | **Tamper-Evident Drop Verifier** | Clan Event Organizers | Generates cryptographic watermark (SHA-256 of timestamp + world + session UUID + drop text) embedded onto screenshots and Discord payloads to eliminate fake drop disputes. | High: Clan Discord admins constantly battle Photoshop/fake submissions. | Low | High (Sub-module) |
| **4** | **Group Bronzeman Co-Op** | Casual Friends & Duos | Shared Bronzeman unlocks. When Player A receives a drop, it automatically unlocks on the Grand Exchange for all connected party members via PartyService. | High: Group Bronzeman Mode has 732 installs despite being buggy and abandoned. | Low-Medium | Medium |
| **5** | **Supply Burn Rate & Bank Forecast** | Hardcore & Casual GIMs | Analyzes shared group storage withdrawals over 7/14 days; forecasts depletion dates for potions, food, and runes; posts restock alerts to Discord webhook. | High: GIM supply burnout is a primary cause of team collapse. | Low-Medium | **Native (Sub-module)** |
| **6** | **LiveSpeedrun Splitter (Ghost Pace Car)** | Speedrunners & Quest Enthusiasts | LiveSplit-style overlay for quest speedrunning, Hallowed Sepulchre, and boss encounters. Compares live split times against personal bests or party members. | Medium-High: Quest speedrunning community relies on cumbersome external timers. | Medium | Low |
| **7** | **Goldman Mode Official Companion** | Restricted Account Creators | Automates Mootrius's Goldman Mode ruleset: tracks Goldman GP calculations from XP/drops and renders clickable purchase borders on the world map. | Medium-High: Established YouTube audience wanting to play along with Mootrius. | High | Low |
| **8** | **PvE Bounty Hunter / Slayer Roulette** | End-Game PVMers | Daily/weekly randomized combat contracts with handicaps (e.g. "Kill 20 Vorkath using only crossbows", "Barrows run with 0 prayer"). Party-synced progress. | Medium: High appetite for structured endgame variety. | Medium | Medium |
| **9** | **Co-Op Tileman 2.0** | Snowflake Creators & Duos | Real-time peer-to-peer tile grid sharing over PartyService, eliminating the corrupted string imports and desyncs of the existing addon. | Medium: Group tileman addon has 1,859 active installs despite severe desyncs. | Medium | Low |
| **10** | **Collection Log Fantasy Draft** | Clan Events & Streamers | Teams draft specific Collection Log categories (e.g. Team A drafts Slayer, Team B drafts Raids). The plugin restricts gear usage to drafted items and tallies points. | Medium: Popularized by streamer tournaments (Gielinor Games style). | High | Low |
| **11** | **Equipment Slot Locked Mode** | Challenge Streamers | Starts player with 1 equipment slot unlocked. Levels and quest points roll random slot unlocks, visually masking locked inventory slots. | Medium: Proven YouTube series format (Slot Locked accounts). | Low | Low |
| **12** | **Hardcore Economy (Taxman Mode)** | Content Creators | Automatically calculates a progressive gold tax on all XP gained or deaths; tracks required payments into Death's Coffer or Well of Goodwill. | Low-Medium: Niche challenge format for creator series. | Medium | Low |
| **13** | **Clan Snakes & Ladders (Board Game)** | Community Clans | Maps a virtual board game onto Gielinor. Completing boss/skilling tasks rolls dice to advance across regions with traps and shortcuts. | Medium: Rapidly growing clan event format in 2026. | High | Low |
| **14** | **Ironman Nuzlocke (Gear Permadeath)** | Hardcore Challenge Runners | Upon death, any equipped items or carried inventory are permanently marked "dead" in local cache and forbidden from future re-equipping. | Medium: High streaming entertainment value; simple ruleset. | Low | Low |

---

## Part 2: Compliance & Anti-Cheating Analysis

Using the `runelite-compliance` evaluation framework, every candidate feature must be checked against Jagex's Third-Party Client Guidelines and RuneLite's Rejected Features list.

### 2.1 The Two Fundamental Compliance Axioms
1. **Information Axiom:** Rules are enforced based on *what information reaches the player*, not how it is drawn or stored. Providing unearned tactical advantage (e.g. boss projectile timing, hidden opponent inventory in PvP) is banned regardless of local caching.
2. **Input & Automation Axiom:** Plugins must never generate simulated mouse/keyboard events, automate gameplay loops, or modify outgoing game packets.

---

### 2.2 Compliance Matrix for Candidate Concepts

```
+------------------------------------+--------------------------+-----------------------+------------------------------------------+
| Concept                            | Closest Forbidden Rule   | Precedent             | Compliance Verdict                       |
+------------------------------------+--------------------------+-----------------------+------------------------------------------+
| 1. Group Challenge & Bounty Board  | Menu / Action Injection  | Quest Helper / Clog   | ALLOWED (Clean Precedent)                |
| 2. Squad Bingo (P2P Board)         | Player Data Scraping     | Hub Party Panel       | ALLOWED (With Opt-In Party Consent)      |
| 3. Tamper-Evident Drop Verifier    | Unconsented Telemetry    | Dink / Screenshot     | ALLOWED (Standard Native Capture)        |
| 4. Group Bronzeman Co-Op           | Menu Modification (GE)   | Another Bronzeman     | RESTRICTED (Must not hide buy options)   |
| 5. Supply Burn Rate Forecast       | Automated Inventory Read | GIM Utilities Bank    | ALLOWED (Passive Container Inspect)      |
| 6. Goldman Mode Companion          | Map Movement Blockers    | Region Locker         | RESTRICTED (Advisory visual overlay only)|
| 7. LiveSpeedrun Splitter           | Advance Hazard Warnings  | Quest Speedrunning    | ALLOWED (Timer/Split overlay only)       |
+------------------------------------+--------------------------+-----------------------+------------------------------------------+
```

#### Detailed Rule Checks:

##### Concept 1 & 2: Group Challenges & Squad Bingo
* **Nearest Forbidden Rule:** *"No crowdsourcing data about other players (locations, gear, names, etc.)"* and *"No exposing player information over HTTP without consent."*
* **Compliance Precedent:** RuneLite's built-in `PartyPlugin` and TheStonedTurtle's `Hub Party Panel` (259,943 installs).
* **Why It Is Compliant:** Data exchange occurs exclusively between players who explicitly opt in by entering the same shared party passphrase. No global scrapers or public hiscore directories are queried without authorization.
* **Proof of Completion:** Relies strictly on native client events:
  * `LootReceived` (items dropped by monsters).
  * `ChatMessage` (boss kill count notifications, collection log popups, quest completion text).
  * `StatChanged` (XP milestones).
  * `ScriptPreFired` / `ScriptPostFired` (minigame rewards and raid completions).

##### Concept 3: Tamper-Evident Drop Verifier
* **Nearest Forbidden Rule:** *"No autotyping or modifying outgoing chat messages."*
* **Compliance Precedent:** RuneLite's core `ScreenshotPlugin` and `Dink for Discord`.
* **Why It Is Compliant:** It uses RuneLite's official `DrawManager.requestNextFrameListener(image -> ...)` to capture the rendered viewport and paint a subtle cryptographic verification hash in the canvas corner before saving or dispatching via webhook. It does not alter game memory or inject text into the chatbox.

##### Concept 4: Group Bronzeman Co-Op
* **Nearest Forbidden Rule:** *"No adding new menu entries that cause actions to be sent to the server"* and *"No removing or deprioritising Grand Exchange options."*
* **Compliance Precedent:** `Another Bronzeman Mode` (`CodePanter`) and `Bronzeman Unleashed` (`elertan`).
* **Compliance Restriction:** The plugin may visually highlight locked/unlocked items in the GE search interface and sound an alert if a player attempts to buy a locked item, but it **must not strip the Buy option from the native Grand Exchange interface**.

##### Concept 6: Goldman Mode Companion
* **Nearest Forbidden Rule:** *"No detached camera world interaction"* and *"No automated indicators showing where to stand."*
* **Compliance Precedent:** `Region Locker` and `Tileman Mode`.
* **Compliance Restriction:** The plugin can outline restricted chunk borders on the ground and minimap using standard `WorldPoint` polygon rendering. It **must not attempt to physically block player clicks or prevent character pathing across borders**, as client-side packet filtering is strictly prohibited.

---

## Part 3: YouTube Strategy & Creator Landscape

### 3.1 The OSRS Video Creator Landscape

| Creator Handle | Subscribers | Typical Video Views | Content Format | Publishing Cadence | Why Their Format Works |
| :--- | :---: | :---: | :--- | :--- | :--- |
| **Idyl** (`@Idyl`) | ~94,000 | 80,000 – 400,000 | Comedy plugin development, viewer challenge builds, AI experiments | Monthly / Bi-monthly | Treats coding as narrative comedy; turns bugs into running gags; high-energy editing. |
| **Gudi** (`@Gudi`, Mod Ronan) | ~165,000 | 120,000 – 600,000 | Custom game mode series (invented Bronzeman Mode), game design experiments | Sporadic (1-2 months) | Incredible game design instincts; builds custom rulesets that capture community imagination. |
| **Settled** (`@Settled`) | ~720,000 | 500,000 – 2,500,000 | High-stakes restriction series (Swampletics, Tileman, Nightmare) | Bi-weekly / Monthly | Master of emotional storytelling and dramatic progression pacing; popularized Tileman plugin. |
| **Stan** (`@operationstan`) | ~12,000 | 25,000 – 50,000 | Pure technical tutorials ("How to create a RuneLite plugin") | Low / Archive | Demonstrates strong organic search demand for clean, modern RuneLite developer instruction. |

---

### 3.2 Community Demand Signals

Direct evidence from OSRS community hubs demonstrates an appetite for automated event tooling and modern plugin engineering:

1. **r/2007scape (Clan Bingo Fatigue):**
   > *"Our clan just wrapped up a 2-week bingo and honestly the admin team is completely burnt out. We spent more time staring at Discord screenshots of low-tier clue drops and checking Google Sheet timestamps than actually playing the game. Why is there still no plugin that just ticks tiles automatically when someone in your team gets a drop?"*  
   > — *r/2007scape, 2026 discussion on clan event burnout.*

2. **r/ironscape (Group Motivation & Stagnation):**
   > *"Four out of five of our GIM members stopped logging in once we hit the red prison (Corrupted Gauntlet) grind. We need in-group minigames or challenge boards to give us short-term co-op goals instead of everyone slaving away solo in separate boss instances."*  
   > — *r/ironscape, recurring GIM player sentiment.*

3. **RuneLite Discord (#plugin-hub & #development):**
   > Frequent questions regarding outdated tutorials: *"Most of the YouTube guides for writing RuneLite plugins are from 2020 and use old Gradle setups or deprecated event patterns. We need a 2026-current guide on event listeners, PartyService sync, and panel architecture."*

---

### 3.3 The Channel Blueprint: "Engineering Gielinor"

#### Channel Positioning
* **Core Tagline:** *Software Engineering meets OSRS Game Design.*
* **Value Proposition:** Unlike standard Let's Play channels or dry coding tutorials, this channel blends high-level software engineering craft (showcased through **StayPoint**) with creative OSRS game design. Viewers watch a real senior engineer take wild community ideas, architect them cleanly, test them with AI rigor, and deploy them to the Plugin Hub.

```
+-----------------------------------------------------------------------------------+
|                        Video Production Workflow (StayPoint)                      |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [ Act 1: The Problem / The Idea ] (0:00 - 3:00)                                  |
|  - Real OSRS pain point (e.g. clan bingo admin hell, GIM stagnation).             |
|  - The audacious design goal.                                                     |
|                                                                                   |
|  [ Act 2: The Architecture & StayPoint Factory ] (3:00 - 8:00)                   |
|  - Breaking down the problem in StayPoint (doc/v1 plans, test-first specs).       |
|  - Fast-paced coding montage: pairing with AI, solving race conditions.           |
|  - Overcoming RuneLite API constraints & compliance checks.                       |
|                                                                                   |
|  [ Act 3: The Live Playtest & Chaos ] (8:00 - 13:00)                              |
|  - Deploying to live client; testing with real GIM teammates or clan.            |
|  - Bugs, funny edge cases, and unexpected emergent gameplay.                      |
|                                                                                   |
|  [ Act 4: The Release & Open Source ] (13:00 - 15:00)                             |
|  - Publishing to GitHub / Plugin Hub PR submission.                               |
|  - How viewers can install and play it today.                                     |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

### 3.4 First 5 Video Concepts

1. **Episode 1: "I Built an In-Game Bingo Board That Requires Zero Websites"**
   * *Hook:* Show a clan moderator surrounded by 40 open browser tabs of Google Sheets and Discord screenshots, then delete them all with a single RuneLite click.
   * *Content:* Architecting a 5x5 Bingo grid inside GIM Utilities; syncing tile completions over RuneLite's WebSocket `PartyService`; live drop demonstration triggering an instant party celebration.
   * *Outcome:* Plugin Hub PR opened; downloadable beta for viewers.

2. **Episode 2: "Why Every Clan Bingo Has Cheating Drama (And How Cryptography Fixes It)"**
   * *Hook:* Dissecting infamous OSRS clan bingo controversies where players faked drops using old screenshots or private server clips.
   * *Content:* Building a lightweight cryptographic drop verification engine into RuneLite. Watermarking screenshots with tamper-proof session hashes and verifying them via Discord webhook.
   * *Outcome:* Highly shareable tool adopted by clan leaders.

3. **Episode 3: "Coding a Custom Game Mode for My Burnt-Out GIM Team in 48 Hours"**
   * *Hook:* My 5-man GIM team has been inactive for 3 months. Can a custom "Bounty Roulette" plugin bring everyone back online this weekend?
   * *Content:* Designing procedural group bounties (e.g. "Kill 50 Dagannoth Kings wearing bronze helmets", "Race to obtain 5 unique barrows items"). Tracking the race live on stream with group audio reactions.
   * *Outcome:* High emotional payoff; showcases the human side of GIM co-op.

4. **Episode 4: "The Complete 2026 Guide to Building RuneLite Plugins"**
   * *Hook:* The definitive, zero-fluff developer tutorial the community has been begging for.
   * *Content:* From zero to a working plugin in IntelliJ. Understanding `clientThread`, handling event listeners, building clean Swing UI side panels, and avoiding the fatal compliance traps that get PRs rejected.
   * *Outcome:* Evergreen search traffic anchor for the channel.

5. **Episode 5: "Can an Autonomous AI Agent Build an OSRS Speedrun Plugin?"**
   * *Hook:* Pitting modern agentic coding workflows (StayPoint) against complex Java game client APIs.
   * *Content:* Setting up test-driven specifications; watching StayPoint scaffold the plugin, handle game ticks, and write regression tests; validating the resulting speedrun split timer live in the Hallowed Sepulchre.
   * *Outcome:* Appeals to both OSRS players and the exploding tech/AI audience.

---

### 3.5 Production Cadence & Solo Dev Sustainability
* **Release Frequency:** **1 polished video every 3 weeks** (or 1 per calendar month).
* **Rationale:** High-retention tech storytelling requires deliberate scripting, motion graphics, and live gameplay testing. Rushing out weekly low-effort videos leads to immediate solo creator burnout.
* **Asset Reusability:** Code written for videos feeds directly into the author's open-source Plugin Hub portfolio, creating compound value between software engineering and media.

---

### 3.6 Operational Security (OpSec) & Stream Safety

Running a public YouTube channel while employed as a professional software engineer requires strict isolation protocols:

```
+-----------------------------------------------------------------------------------+
|                           Stream & Workspace Isolation                            |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [ Work Machine / Employer Assets ]          [ Production Recording Profile ]     |
|  - Managed Solution repositories             - Dedicated macOS user: "DevStudio"  |
|  - Work email, Teams, Slack, Outlook         - Zero work repos cloned             |
|  - Corporate VPNs and certificates           - Clean browser with no work cookies |
|  - Client database connections               - Clean terminal (sanitized history) |
|                                                                                   |
|             \                                          /                          |
|              \                                        /                           |
|               +--------------------------------------+                            |
|               |         ABSOLUTE WALL: ZERO          |                            |
|               |         CROSS-CONTAMINATION          |                            |
|               +--------------------------------------+                            |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

1. **Absolute Workspace Isolation:** Recording must occur on a dedicated macOS user profile or isolated hardware profile. Never open work repositories, client codebases, or internal tickets during recording sessions. Ensure no work communication tools (Slack, Teams, Outlook) run in the background.
2. **Secret & Key Sanitization:** Zero hardcoded API keys, Discord webhook tokens, or credentials. Use mock webhook targets or local loopback servers during filming.
3. **In-Game Account Anonymity:** 
   * Always enable an in-game name obfuscation plugin or stream overlay covering display names.
   * Exposing real account names on YouTube invites targeted harassment, viewer spam, report bombing, and in-game scouting in PvP areas.

---

## Part 4: Monetization Reality & Legal Boundaries

### 4.1 Jagex Intellectual Property & Client Monetization Rules

Jagex enforces strict boundaries regarding commercial exploitation of Old School RuneScape and third-party software:

#### Jagex Third-Party Client Guidelines (Verbatim Policy Principle)
> *"You may not charge users a fee, subscription, or any monetary payment to download, access, or use any third-party client or plugin that interacts with Old School RuneScape. All features must be made freely available to the entire community."*  
> — *Sourced from Jagex Third-Party Client Guidelines & Terms of Service.*

#### Jagex Fan Content Policy
* **Permitted:** Platform-native monetization (YouTube AdSense, Twitch subscriptions/bits) and traditional brand sponsorships, provided the content does not promote real-world trading (RWT), botting, or gambling.
* **Prohibited:** Selling standalone commercial software that interfaces with the game, charging for in-game modifications, or creating digital paywalls around game features.

---

### 4.2 RuneLite Plugin Hub Policy on Monetization

The RuneLite Plugin Hub operates under uncompromising open-source standards:

1. **Mandatory Open Source:** Every plugin merged into the Plugin Hub must be open-source under a permissive license (BSD-2-Clause, MIT, or GPL compatible) hosted on a public GitHub repository.
2. **Strict Ban on Paywalls & Gated Code:** Plugins that require a paid license key, verify Patreon subscriptions over HTTP to unlock features, or disable functionality for non-donors are **summarily rejected and permanently banned**.
3. **Donation Links:** Plugin authors are permitted to place passive, voluntary donation links (Ko-fi, GitHub Sponsors, Patreon) in their GitHub repository `README.md` or external documentation. However, the client plugin itself cannot nag users for donations or restrict features based on donation status.

---

### 4.3 Real-World Creator & Author Earnings

To establish realistic expectations, public earnings across the RuneLite ecosystem were benchmarked:

```
+-----------------------------------------------------------------------------------+
|                        Monthly Plugin Donation Reality                            |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  RuneLite Core (Adam)        [=========================] ~$1,960 / mo (496 patrons)|
|  Quest Helper (Zoinkwiz)     [=======] ~$550 / mo (175 patrons)                   |
|  RS_117 (117HD)              [=] ~$37 / mo (15 patrons)                           |
|                                                                                   |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  YouTube Sponsorship Reality (1 Video with 150,000 Views)                         |
|  Single Brand Integration:   [==================================================] |
|                              $2,500 - $4,500 Flat Fee + $800 AdSense              |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

* **117HD (`RS_117`):** Created the most viral, visually revolutionary plugin in OSRS history (spurring an entire community revolt when Jagex initially blocked it). Today, his public Patreon sits at **15 paid members, generating ~$37 per month**.
* **Zoinkwiz (Quest Helper):** Author of the undisputed #1 most widely installed plugin on RuneLite (millions of users, essential for every player). His Patreon supports **~175 members, generating ~£450–£500 per month ($550–$650/mo)**.
* **RuneLite (Adam):** The foundational client powering 90%+ of the active OSRS desktop playerbase. RuneLite's official Patreon has **~496 paid members, generating ~$1,960 per month**.

#### The Financial Conclusion
* **Direct plugin donations are a tip jar, not a living.** Even with hundreds of thousands of users, conversion to voluntary Patreon/Ko-fi pledges hovers around 0.01%.
* **The real financial leverage is Content & Media:** An OSRS YouTube video averaging 100,000 to 200,000 views generates $600–$1,200 in AdSense and commands $2,000–$4,500 per sponsored mid-roll integration (e.g. NordVPN, Factor, Displate). Content pays 50x to 100x more than open-source donation buttons.

---

## Part 5: Strategic Recommendations & Roadmap

### 5.1 The First Plugin to Build: GIM Challenges (Native Module)

Rather than launching an unproven standalone plugin from scratch, the optimal technical and community strategy is:

**Phase 1: Build the Challenge & Bounty Engine directly inside `group-ironman-plugin` (GIM Utilities).**

#### Why GIM Utilities First?
1. **Existing Foundation:** GIM Utilities already has the exact infrastructure needed:
   * `GroupSync.java`: Instant, zero-config P2P party connections via `GroupKey`.
   * `VvgGroupIronmanMessage.java`: Wire protocol for broadcasting challenge progress.
   * `DiscordSender.java`: Rich embed formatting and rate-limited queueing to Discord.
   * `EventDetect.java`: Built-in listeners for loot drops, levels, and storage changes.
2. **Immediate Dogfooding:** The author and existing GIM teams can test and refine the feature in real gameplay immediately without waiting 4–8 weeks for Plugin Hub PR review.
3. **Phase 2 Spin-Off:** Once the engine is battle-tested in GIM Utilities, extract the core into **Squad Bingo**, a standalone Plugin Hub plugin for general clans.

---

### 5.2 The 3-Month and 6-Month Projection

```
+-----------------------------------------------------------------------------------+
|                                 6-Month Roadmap                                   |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [ Months 1 - 3: Foundation & Initial Launch ]                                    |
|  - Implement Challenge & Bounty engine inside GIM Utilities.                      |
|  - Launch YouTube Channel; publish Episodes 1 and 2.                             |
|  - Submit "Squad Bingo" / "Drop Verifier" to RuneLite Plugin Hub.                |
|  - Milestone: 2,500 YouTube subscribers, 50,000 cumulative video views.           |
|                                                                                   |
|  [ Months 4 - 6: Scaling & Community Adoption ]                                   |
|  - Publish Episodes 3, 4, and 5 (featuring StayPoint AI dev workflows).          |
|  - Onboard 25+ mid-sized OSRS clans to Squad Bingo / GIM Challenges.              |
|  - Secure first video sponsorship ($1,500 - $2,500 integration rate).             |
|  - Milestone: 10,000 YouTube subscribers, $1,500/mo blended creator revenue.      |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

#### Honest Odds & Risk Assessment
* **Odds of Building a Working, Popular Plugin:** **95%**. The technical architecture is fully validated by GIM Utilities, and the clan bingo problem space is desperate for a clean, serverless solution.
* **Odds of Meaningful Patreon Income (> $1,000/mo):** **< 5%**. Historical data from 117HD and Zoinkwiz proves players do not donate to open-source software in high volumes.
* **Odds of YouTube Success (> 10k subs in 6 months):** **45–60%**. Strong visual storytelling, high editing pacing (Idyl-style), and authentic engineering authority (StayPoint) provide a distinct competitive advantage over typical gameplay let's plays.

---

## Citations & Primary Sources

1. **Jagex Third-Party Client Guidelines:** [https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
2. **RuneLite Rejected & Rolled Back Features:** [https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
3. **RuneLite Plugin Hub Repository:** [https://github.com/runelite/plugin-hub](https://github.com/runelite/plugin-hub)
4. **RuneLite Example Plugin Template:** [https://github.com/runelite/example-plugin](https://github.com/runelite/example-plugin)
5. **RuneLite Plugin Statistics (Phyce.dev):** [https://runelite.phyce.dev](https://runelite.phyce.dev)
6. **Wise Old Man Open API & Documentation:** [https://wiseoldman.net](https://wiseoldman.net)
7. **Mootrius Goldman Mode [#1] Video:** [https://youtu.be/e1IoSzGeRGU](https://youtu.be/e1IoSzGeRGU)
8. **Mootrius OSRS Wiki Guide:** [https://oldschool.runescape.wiki/w/Guide:Mootrius_Ironman_Guide](https://oldschool.runescape.wiki/w/Guide:Mootrius_Ironman_Guide)
9. **RuneLite Patreon Public Financials:** [https://patreon.com/runelite](https://patreon.com/runelite)
10. **Zoinkwiz (Quest Helper) Patreon:** [https://patreon.com/zoinkwiz](https://patreon.com/zoinkwiz)
11. **117HD (RS_117) Patreon:** [https://patreon.com/117hd](https://patreon.com/117hd)
12. **Idyl YouTube Channel:** [https://youtube.com/@Idyl](https://youtube.com/@Idyl)
13. **Gudi YouTube Channel:** [https://youtube.com/@Gudi](https://youtube.com/@Gudi)
