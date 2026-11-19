# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- First "what's new" notes, a `::whatsnew` chat command to replay them, and dev builds that show them again whenever the build commit changes (`generateBuildInfo` in `build.gradle`; stored in `lastSeenVersion` as `version+commit`).
- Short Group box event lines with a skill or item icon and a short time, plus *Group box size*, *Group box icons* and *Group box times* settings (#98).
- Shopping *Path to* / *Path to next* names the stop's town, lists what the next stop supplies, and re-picks the nearest stop as the player moves (#97).
- *Above player* banner position for event toasts (#94).
- Hiscores-style `Leaderboard` skill grid with per-member colours and Skilling / Bossing / Activities / Minigames categories, a two-column `Compare` table, and a *Personal bests* section above *Recent runs* on the `Activities` tab (#95).
- Category filter pills and a top-20 value-ranked preview (with *Show all*) on the `Banks` and `Storage` tabs, quick-find category/combat-style/holder filter pills and one-click search presets on the `Find` tab, and High Alchemy values in item grid tooltips (#90).
- `Compare` tab (`GroupCompare`, `BenefitFinder`, `ProgressView`) for side-by-side member comparisons, sortable per-stat leaderboards, public group hiscore comparisons, and *who benefits most* item upgrade rankings, plus shared boss personal bests (`PbCapture`, `PersonalBests`), quest progress, quest points, and achievement diary tiers (`AchievementState`, `MemberProgressBook`) under *Share group events* (#89).
- Group announcements (`::announce <text>`), everyone-online transition alerts (`EveryoneOnline`), per-event banner switches, quiet hours, and banner dismissal via hotkey or click (#87).
- Shared overlay visual style (`OverlayStyle`) for the group box and event toast banners, plus an optional in-game Shopping path-stop card overlay (`PathStopOverlay`, `PathStopPlan`) showing destination, needed items, recipients, pricing, and next stop (#86).
- Per-member jewellery charge tracking and display (`jc` on member cards) and rich tooltips showing open log basket contents and looting bag contents and value from container ID 516 (#85).
- RuneLite `in_review` sweep disposition report in `docs/research/in-review-sweep-2026-11-18.md` (#91).
- Research documentation on celebratory Discord webhook posts for pets and major milestones and a three-layer notable collection-log filter in `docs/research/discord-celebration-posts.md` (#84).
- Research documentation on wave, rotation, and game-tick split detection for Fight Caves, Inferno, Fortis Colosseum, and staged activities in `docs/research/activity-detection.md` (#83).
- Feature matrix and gap comparison of GIM Utilities against 22 RuneLite Plugin Hub Group Ironman, shared-bank, and storage plugins in `docs/research/gim-competitor-comparison.md` (#82).
- Per-member Discord message grouping across all event families (*Group posts (minutes)*, `discordGroupPosts`, default 30 minutes; `EventThreads`, `EditedThreads`, persisted in `grouped-posts.txt`), stacking up to 10 event cards newest-first and netting group storage movements per item (#76).
- Live wave tracking (`WaveTracker`, `TeamSummary`, new `Activities` tab) for the TzHaar Fight Caves, the Inferno, and the Fortis Colosseum (wave, elapsed time, solo/team context, and Colosseum glory and modifier tiers) across member cards, the `Activities` tab, the in-game group box, and the Discord live board, plus opt-in start, wave-milestone (*Post every N waves*), completion, and death notifications (#74).
- Bundled OSRS Wiki monster drop rates (`item-drops.txt`) for common non-quest items in the `Shopping` tab (#73).
- **Path to** and **Path to next** buttons on shopping-list lines and open item requests that route to the nearest reachable shop or group-storage bank via the Shortest Path plugin (`ShortestPathBridge`, `ItemPaths`, `PathSession`, `banks.tsv`) (#72).
- Member item request lists (*Request: ask the group*, synced via `req` and `rqc`) in the `Shopping` tab with **I'll get it** member claims, automatic completion when group holdings meet the target, owner-only controls, and optional *Request* Discord and banner notifications (#69).
- Multi-skill per-member Discord level-up grouping (one message per member with an embed card per skill, including broadcast level-ups and combat levels for members without the plugin, defaulting *Combine level-ups* to 60 minutes on new installs) (#68).
- Single-poster Discord relaying for members without the plugin by parsing in-game Ironman Group channel broadcasts (`ChatMessageType.CLAN_GIM_MESSAGE` in `GroupBroadcasts`) with 24-hour per-profile deduplication, party `posted` claims, and the *Post events for members without the plugin* group setting (#66).
- `Shopping` tab with group-synchronized shopping lists (`shop` and `qstate`), nine 1 Defence pure quest-milestone presets generated from Quest Path data (`scripts/gen-quest-items.py`, `quest-items.json`), custom lists, live group-holding subtraction, reachable shop and drop sources, and an opt-in proximity *Shopping: shop alert* (#65).
- Verified item counts on carried log basket, fish barrel, and coal bag inventory slots (`ContainerContents`, synced via `cc` and searchable in `Find` and `::find` when all items inside are known), empty worn-equipment slot silhouettes (`SpriteID.EQUIPMENT_SLOT_*`), and local member-card rendering before joining group sync (#64).
- Opt-in level 67 (*"six seven"*) and level 69 (*"nice"*) Easter egg lines and emoji-free in-game banner and group-box tags for skill and combat level-ups (#61).
- Daily live-board reposting (*Repost board daily*, `discordRepostBoard`, on by default) that posts a fresh status board at the bottom of the Discord channel after midnight and deletes the previous day's message once the new message ID is saved (#59).
- Headless Swing UI test driver and RuneLite `ConfigPanel` test harness (`SwingDriver`, `ConfigPanelHarness`, `GimPanelUiTest`, `GimConfigPanelUiTest`, `docs/testing-ui.md`, `scripts/add-ui-driver.sh`) plus `./gradlew screenshots` (`ReadmeShots`) for deterministic README screenshot rendering (#55).
- Safe computer-use test rig boundary and five-slice build plan in `docs/plans/computer-use-rig.md` (#54).
- JSON-driven fake game-event scenario testing harness (`src/test/java/com/vinnyvangogh/scenarios`, `GimScenarioRig`, `GimScenariosTest`, developer-only `ScenariosPlugin` sidebar, `TESTING.md`, and `scripts/add-test-harness.sh`) (#53).
- Compliance review, wire/UI design, and four-slice implementation plan for live bossing and minigame activity tracking in `docs/plans/activities.md` (#52).
- Real 28-slot inventory layout (`is` field in `carried` messages, preserving empty slots, stack positions, and noted item appearances while keeping canonical counts for search and totals) and online-first member card ordering in the `Group` tab (#51).
- Leader-signed Discord webhook sharing (`GroupWebhooks`, party kind `gweb`) allowing the group leader to share all seven webhooks and poster names with members without exposing URLs in member configs or UI (#48).
- One-click *"Share with your group?"* onboarding card (`SharingPrompt`) after saving a group key (#47).
- Collapsible member bank cards (`BankFolds`) with **Collapse all** / **Expand all** controls and automatic filter expansion in the `Banks` tab (#46).
- Windows 11 Smart App Control PowerShell download workaround in `SETUP.md` (#44).
- `Settings` panel tab with ECDSA-signed group leadership and editor roles (`GroupGovernance`, `GovernanceSync`), group-synchronized thresholds and event switches with version history, per-member posting and banner opt-outs with a read-only *"Who posts what"* grid, and *Post every N levels* (`eventLevelEvery`) (#42).
- Seven independently configurable Discord webhooks (*Main*, *Online status*, *Drops & loot*, *Progress*, *Deaths & danger*, *Group storage*, *Daily digest*), each with its own poster-name mode, RuneScape-themed default persona, and isolated rate-limited send queue (`Webhooks`) (#41).
- One-time first-install welcome lines and per-version update notes in the game chat (`UpdateNotes`), controlled by *Show update notes* (`showUpdateNotes` in *Advanced*) (#39).
- Retaken README screenshots for *Discord: connection* and *Thresholds and Advanced* settings (`docs/images/settings-discord-connection.png`, `docs/images/settings-thresholds-advanced.png`) (#37).
- Source-verified RuneScape meme Easter egg research documentation in `docs/research/runescape-easter-eggs.md` (#35).
- Cached, rate-limited OSRS hiscore skill level lookups (`HiscoreBook`, via RuneLite's `HiscoreClient.lookupAsync`, max once per 6 hours with exponential retry backoff) and dimmed assumed Run/Spec orbs for group members without the plugin (#34).
- Fifteen curated RuneScape community meme Easter egg triggers (level 73, level 92, skill 99, max total, combat 126, 2000 total, 200M XP, Cook's Assistant, Dragon Slayer I, Recipe for Disaster, TzTok-Jad death, dry streak, gold ring deposit, pet, and 0 gp death) in `EasterEggs` (#33).
- Durable last-seen timestamps and worlds (`LastSeen`, persisted under `seen` in `group-cache.json`) for offline members on the Discord live board, plus a staleness footer when no plugin user is online (#32).
- Online status and world detection for group members without the plugin from the in-game Ironman Group channel (`ClanID.GROUP_IRONMAN`) with Friends list fallback (`GamePresence`), displayed across the live board, Group overlay, sidebar cards, and single-poster login/logout posts (`ChannelPosts`) (#30).
- Design and phased build plan for an optional GIM Utilities sync server in `docs/plans/gim-server.md` (#28).
- Per-webhook poster name choices (*Webhook's own*, *Default name*, *Custom*), editable Discord *Live board* online-status mode (`StatusBoard`), and an expanded sidebar `Log` tab recording all group events across restarts (`feed` in `group-cache.json`, up to 2,000 events / 30 days) with search and category/member/time filters (#27).
- Research documentation on OSRS group game modes, bingo plugins, and content formats in `docs/research/game-modes-and-channel-2026-11.md` (#25).
- In-place Discord message editing (`LevelThreads`, `LevelPosts`, `?wait=true` + `PATCH /messages/{id}`) to combine consecutive level-ups in the same skill within a configurable window (*Combine level-ups*, `discordCombineLevels`) into a single message (#23).
- *Grave picture* setting (*Where I died*, *Current view*, *None*) and `GraveShot` frame holder so *"Gravestone expiring"* Discord warnings reuse the death-moment screenshot and list the top items left in the grave (#20).
- Additive daily Discord digests alongside instant posts, game-chat warnings for invalid webhook URLs, single-post merging (`DiscordMerge`) for same-moment storage visits, drops, level-ups, and deaths, and optional Discord Easter egg lines (`EasterEggs`, `EasterEggLines`) with configurable Legend and Freeloader GP thresholds (#16).
- Game-style quantity and GP formatting (`Amounts` wrapping `QuantityFormatter.quantityToStackSize`, with exact numbers in panel tooltips and Discord posts) and stack-size coin pile icons (`StackIcons`) in `Find` rows and Discord thumbnails (#15).
- Configurable *Offline grace* (`discordOfflineGrace`, 0–10 minutes, default 2), world-hop suppression, 10-minute online/offline pair throttling, and pending-logout restart recovery (`PresenceDebounce`) (#11).
- Opt-in `GU_BUILD_ONLY=1` build-only mode in `tools/windows/Run-GimUtilities.cmd` and the `.github/workflows/windows-launcher.yml` end-to-end CI workflow on `windows-latest` (#10).
- StayPoint test suite definition (`.staypoint/test.toml`) requiring the Gradle `test` task (#8).
- Sixteen opt-in medium group event types (raid uniques, clue rewards, dry streaks, death value lost, gravestone expiring warnings, Hardcore group life lost, Wilderness/skulled alerts, diary tiers, combat tasks, kill count milestones, personal bests, weekly recaps, daily category digests, Slayer tasks, farming runs, and bird house / Hespori timers) (#7).
- Nine privacy-redacted feature screenshots (`docs/images/*.png`) in `README.md` (#6).
- Group events engine part A (`GroupEvent`, `EventRouter`, `DiscordWebhook`, `DiscordQueue`, `DiscordSender`, `EventToastOverlay`, `GroupBoxOverlay`) covering 20 opt-in event types routed to rate-limited Discord webhooks and in-game banners / group box overlay (#4).
- Standalone RuneLite Party group sync via a 128-bit random group key (`PartyService` / `PartyDataService`) and in-game `ClanID.GROUP_IRONMAN` roster allow-list, last-seen group storage snapshot and save-diff log, unified `Find` tab and `::find` item search with shorthands and custom colours, and a five-tab sidebar panel (`Group`, `Banks`, `Storage`, `Find`, `Log`) with offline member persistence (#3).
- Windows double-click test launcher (`tools/windows/Run-GimUtilities.cmd`) with SHA-256-pinned Eclipse Temurin JDK 11 bootstrap and PowerShell checksum tests (`tools/windows/tests/Checksum.Tests.ps1`) (#2).
- Split GIM Utilities out of Build Guard as a standalone plugin (`com.vinnyvangogh.groupironman`, `@ConfigGroup("gimutilities")`, party message `VvgGroupIronmanMessage`, cache directory `gim-utilities`) with all sharing features and `::find` disabled by default (#1).

### Changed

- Updated the Windows launcher, `SETUP.md`, and CI validation workflow across test release tags `gim-test-2` (#43), `gim-test-3` (#50), `gim-test-4` (#62), `gim-test-5` (#70), `gim-test-6` (#77), and `gim-test-7` (#80).
- Reduced plugin source token count below the Plugin Hub's 200,000-token auto-review limit (~204k to ~198k) by extracting shared UI builders (`Ui.java`), moving Easter egg lines and item aliases to resource files (`easter-eggs.txt`, `item-aliases.txt`), shortening config tooltips, folding repeated cache/codec helpers, and adding `tools/tokens.py` (#79).
- Folded shopping lists and requests by default with progress bars and item previews (`ShopFolds`), and compacted item rows with expandable ownership, source, quest, and tradeability details (#73).
- Configured development builds in `build.gradle` to track `latest.release` from `repo.runelite.net` with zero dynamic-version cache TTL and print the resolved RuneLite client version (`printRuneLiteVersion`), while still supporting `-PruneLiteVersion=...` pinning (#58).
- Bumped the baseline RuneLite build target from `1.13.5` to `1.13.6` so the developer client loads current Plugin Hub plugins (#56).
- Set all configuration sections except *Group* to start collapsed by default (`closedByDefault = true`) (#38).
- Regrouped the RuneLite configuration panel from 12 sections into 8 ordered sections (*Group*, *Discord: connection*, *Discord: what to post*, *Discord: how to post*, *Discord: daily digest*, *In-game*, *Thresholds*, *Advanced*) without changing any published `keyName` (`ConfigLayoutTest`) (#29).
- Merged the historical living-docs branch into `main` to align the documentation baseline (#93).

### Fixed

- Prevented `Banks` and `Storage` tab flicker and dropped game clicks during member syncs by building tab models off the Swing EDT on `ScheduledExecutorService`, coalescing redraws to at most once per second (`RedrawCoalescer`), skipping unchanged models, updating item cells and cards in place (`ItemGridView`, `BanksTabView`, `StorageTabView`), and ignoring stale-timestamp bank snapshots (#81, #90).
- Reset captured boss personal bests (`PbCapture`) on logout so personal bests from different accounts on the same client are never mixed (#89).
- Prevented duplicate banners when the same event arrives from multiple sources while keeping distinct drops that land on the same millisecond tick (`AlertDedup`) (#87).
- Assigned the Shopping path-stop card overlay setting a unique `@ConfigItem` position in `inGameSection`, defaulted it to off, and used `net.runelite.api.Point` for overlay text rendering (#86).
- Canonicalized noted and placeholder item IDs via `ItemManager#canonicalize` when resolving display names and formatted held-item quantities with `Amounts` in container tooltips (#85).
- Calculated Discord group storage post `Value` fields (`DiscordMerge.visit`, `EventPosts.net`) as net value (`deposited minus taken`, showing both sides when items move both ways) instead of summing deposits and withdrawals together (#78).
- Prevented account switches, world hops, reconnects, and non-group logins from posting old achievements as new (`LoginGate` re-baselining on out-of-game states and account-hash/profile changes with a 9-second settle window, `LoginBurst` dropping first-minute achievement backlogs > 3, and `GroupMembers` suppressing all posts, logs, and sync for accounts outside the bound group) (#75).
- Measured Shopping tab HTML label heights at exact container pixel widths (`ShopCards.Wrap`) so text no longer clips at the panel's right edge (#73).
- Persisted full carried-item slot data (`is` and `cc`) in `group-cache.json` so noted stacks in member inventories keep their noted icons after a client restart, lightened empty worn-slot silhouettes and cell backgrounds for contrast, and skipped caching null sprites (#71).
- Persisted active level-up streaks keyed by webhook ID to `plugin-data/gim-utilities/level-streaks.txt` so subsequent level-ups within the combine window continue editing the same Discord message across client restarts (#67).
- Prevented duplicate `"Group status"` live boards across rapid client restarts by persisting webhook-ID-keyed board state immediately to `plugin-data/gim-utilities/status-board.txt` (`PluginFiles`), waiting 20 seconds after login for group roles and shared webhooks to settle, and deleting replaced board messages (#63).
- Committed and posted group storage changes in `StorageSession` only when the game actually saves (via the Save button or the `"Saving..."` loading modal, with a truth-check reconciliation on the next open) rather than when closing the window with unsaved changes (#60).
- Added the Wilderness Slayer Cave (`13470`, `13726`) and a RuneLite-derived supplemental region table (`regions-generated.txt` via `tools/regions/gen_regions.py`), and replaced raw `"region N"` fallbacks in `RegionNames.describe` with human-readable descriptions (*Wilderness lvl A-B*, *near X*, *under X*, *Underground*, *Instance*, *Unmapped area*) (#57).
- Kept the GIM Utilities sidebar panel button visible whenever the plugin is enabled (`SidebarButton`), showing an *"Everything is off. Turn on what you want to share."* notice and settings shortcut when all switches are disabled (#49).
- Kept local group storage visits visible in the `Storage` and `Log` tabs with a `*(not shared: Share group storage changes is off)*` marker when storage sharing is disabled (#47).
- Preserved unsaved leader/editor group-settings drafts (`SettingsDraft`) and uncommitted `JSpinner` text across redraws triggered by other members' presence or preference updates, with version-conflict notices and explicit `SaveResult` feedback (#45).
- Showed the first-install welcome message in `UpdateNotes` on any fresh profile's first login, even when the plugin was enabled at the login screen before logging in (#40).
- Shortened truncated setting display names and dropdown choices to fit RuneLite's config panel width, formatted GP threshold spinners as plain digits (`1000000`) so 7-digit values fit in 6 columns, and added font-metrics layout assertions (`ConfigFitTest`) (#36, #38).
- Delayed level-up Discord screenshots (`LevelShot`) up to 2 seconds (3-second cap across rapid levels) until the in-game level-up chat confirmation line renders (#31).
- Fixed Slayer task completion and dialogue assignment detection (`SlayerMessage`), pre-action level tracking (`GroupEvent.previous`) for multi-level single-action jumps, 3-edit failure caps, and queue-drop recovery (#27).
- Migrated on-disk group cache persistence to RuneLite's Filepath API (`getPluginDirectory()` / `net.runelite.client.util.Filepath`) with `internalName = "gim-utilities"` and `legacyDataDirectory = "gim-utilities"` on `@PluginDescriptor` (#26).
- Preserved existing webhook query parameters (`thread_id`) when building Discord `?wait=true` and `/messages/<id>` URLs, logged 2xx replies missing a message ID, added `FakeDiscordTest` over real `OkHttpClient` calls, and added Wilderness region names to `regions.txt` (#24).
- Suppressed *"Gravestone expiring"* Discord and overlay warnings when nothing left inventory/equipment or no gravestone was spawned (`GRAVESTONE_VISIBLE`) (#20).
- Triggered the Freeloader group-storage Easter egg at the exact configured GP threshold (`took >= freeloaderAt`, matching Legend) and added four deterministic rotating phrasings each for Legend and Freeloader (#17).
- Suppressed `"lost about 0 gp"` death loss lines unless an Easter egg applies (#16).
- Fixed offline Discord embeds showing `"You: Online"` instead of the player's login name and `"Offline"` (#11).
- Cleared `PSModulePath` before starting Windows PowerShell 5.1 in `tools/windows/Run-GimUtilities.cmd` so invoking the launcher from PowerShell 7 does not break `Get-FileHash` (#10).
- Preserved held daily digest lines when no webhook is set and held dry-streak alerts for 10 seconds so unique drops arriving in either message order cancel the call-out (#7).
- Resolved item equipment slots on the client thread so the `Group` tab never renders blank, custom-painted the member-card collapse arrow, prevented panel text and `Leave` button clipping, and updated `::find` wording to `"equipped"` (#3).
- Bumped `runeLiteVersion` from `1.12.39` to `1.13.5` to fix `./gradlew run` exiting with code 255 after startup (#1).

### Removed

- Build Guard build rules, attack-style XP warnings, party build-rule/at-risk/gear-name sharing, and the quest/lamp classifier engine (`QuestGraph`, `QuestClassifier`, `Lamp*`, `XpTable`) when splitting GIM Utilities into its own repository (#1).
- Retired the standalone *Activities* Discord webhook (`discordActivitiesUrl` and `discordNameActivities` kept hidden for config compatibility), routing Barrows chests to *Drops & loot* and clues, kill counts, personal bests, Slayer, farming, and birdhouses to *Main* (#41).
