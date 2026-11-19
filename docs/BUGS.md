# Bug Log

Merged bug fixes in reverse chronological order (newest first).

## 2026-11-18 — Banks and Storage tabs flickered and dropped game clicks during member syncs (#81, #90)

- **What broke:** Every `GroupService.onChange` callback queued a full `tab.removeAll()` and rebuilt the visible tab on the Swing Event Dispatch Thread (EDT), sorting and valuing every member bank and allocating up to 200 `JLabel`s per bank on the EDT. The blank interval between `removeAll()` and repaint caused visible grid flicker, and blocking the AWT event queue dropped clicks on the game canvas. Out-of-order or stale-timestamp bank updates could also overwrite newer snapshots.
- **How it was found:** Observed by the Board during live gameplay when leaving the sidebar open on the `Banks` and `Storage` tabs while teammates were active, and during unit testing of bank timestamp ordering in PR #90.
- **Fix:** Built `Banks` and `Storage` models off the EDT on the injected `ScheduledExecutorService`, coalesced redraws to at most once per second via `RedrawCoalescer`, skipped redraws when the model was unchanged, updated existing item cells and cards in place (`ItemGridView`, `BanksTabView`, `StorageTabView`), and ignored bank updates older than the stored snapshot (#81, merged via #90).

## 2026-11-18 — Captured boss personal bests could carry across account switches on the same client (#89)

- **What broke:** `PbCapture` accumulated boss personal bests read from in-game chat without clearing its local state when the player logged out, which could mix personal bests across different accounts used in the same client session.
- **How it was found:** Reviewing multi-account lifecycle handling while building personal-best capture and `MemberProgressBook` synchronization.
- **Fix:** Reset `PbCapture` state on logout so each account starts with a clean personal-best capture state (#89).

## 2026-11-18 — Duplicate alerts across sources and dropped same-millisecond multi-drop banners (#87)

- **What broke:** An event reported simultaneously through party sync and the in-game Group channel broadcast could fire duplicate in-game banners, while `AlertDedup` initially keyed deduplication in a way that collapsed two distinct drops occurring on the same millisecond tick.
- **How it was found:** Writing adversarial unit tests (`AlertsTest`) for announcement and alert deduplication.
- **Fix:** Added cross-source event deduplication in `AlertDedup` that includes the event details so distinct items dropped on the same millisecond tick both display (#87).

## 2026-11-18 — Path-to stop card overlay shared a `@ConfigItem` position and defaulted on (#86)

- **What broke:** The new `PathStopOverlay` configuration toggle initially collided with another `@ConfigItem` position in `inGameSection` and was not disabled by default, failing configuration layout and all-off default invariants.
- **How it was found:** Checked against `ConfigLayoutTest` and `PluginIdentityTest` during PR #86 review.
- **Fix:** Assigned the path-stop card setting a unique position in `inGameSection`, defaulted it to `false`, and used `net.runelite.api.Point` for overlay text rendering (#86).

## 2026-11-18 — Noted and placeholder items showed raw item IDs in shared data and container tooltips lacked game formatting (#85)

- **What broke:** Uncanonicalized noted or placeholder item IDs could render as generic `"item <id>"` strings when resolved for display, and held-item quantities inside container tooltips were formatted without `Amounts` / comma separators.
- **How it was found:** Testing carried-container tooltips and noted item resolution in `TooltipAndChargesTest`.
- **Fix:** Canonicalized noted and placeholder item IDs via `ItemManager#canonicalize` when resolving item names and formatted container tooltip quantities consistently (#85).

## 2026-11-15 — Plugin source exceeded the Plugin Hub's 200,000-token auto-review limit (#79)

- **What broke:** The Plugin Hub auto-review bot reported the plugin's `src/main/java` tree at ~204,000 tokens against its 200,000-token limit. During the token-reduction fold in `DiscordWebhook`, a helper call to `allowMentions` was referenced before the helper method was added.
- **How it was found:** Flagged by the Plugin Hub auto-review bot on the submission PR and caught while verifying the token-reduction refactor.
- **Fix:** Extracted shared Swing builders into `Ui.java`, moved Easter egg strings and item aliases into `easter-eggs.txt` and `item-aliases.txt`, shortened `@ConfigItem` tooltip descriptions without changing keys or defaults, folded repeated cache/codec patterns, added the missing `allowMentions` helper, and added `tools/tokens.py` to track the token estimate (#79).

## 2026-11-15 — Group storage Discord posts summed deposits and withdrawals instead of showing net value (#78)

- **What broke:** When a group storage visit (or grouped storage message) included both deposits and withdrawals, the Discord post's `Value` field summed the GP value of all items moved in both directions (e.g. taking 180 gp of trout and depositing 9,300 gp of yew logs reported `9,480 gp` instead of `9,120 gp`).
- **How it was found:** Found by the Board during a live Discord test of grouped storage posts.
- **Fix:** Updated `DiscordMerge.visit` and `EventPosts.net` to calculate net value (`deposited - taken`) and display both sides when items move in both directions (`9,120 gp (deposited 9,300, took 180)`) (#78).

## 2026-11-15 — Switching accounts or logging into a non-group account posted stale achievements to Discord (#75)

- **What broke:** `GroupEventEngine` only reset `ProgressTracker` and `EventWatchers` at the end of the `LOGIN_SCREEN` branch of `onGameStateChanged`, without tracking account hashes or waiting for post-login varbits and skills to settle. Switching to a second account (or logging into an account not in the configured Group Ironman group) compared the second account's existing stats and diaries against the previous baseline and posted long-completed diaries and levels to the group's Discord and Log.
- **How it was found:** Observed by the Board when switching accounts on the same client posted the second account's completed achievement diaries and total level to the group's Discord channel.
- **Fix:** Added `LoginGate` to re-baseline immediately on any out-of-game state, account-hash change, or `RuneScapeProfileChanged` event and suppress progress detection for a 9-second settle window after login; added `LoginBurst` to hold and drop login achievement backlogs (> 3 in the first minute); and bound the client to its group via `GroupMembers` so non-member accounts never post, log, or sync (#75).

## 2026-11-15 — Long Shopping tab descriptions were clipped at the right edge of the sidebar (#73)

- **What broke:** Swing's HTML renderer treats CSS `px` as roughly `1.3` screen pixels, causing `width:175px` HTML blocks in the Shopping tab to render ~227 px wide inside a ~197 px card and clip text at the right edge.
- **How it was found:** Found by the Board during live testing of 1 Defence pure preset lists in the Shopping tab and reproduced in `ShoppingTabUiTest`.
- **Fix:** Added `ShopCards.Wrap` to measure HTML preferred heights at the exact pixel width of the container and switched shopping lists to folded cards with compact item rows (#73).

## 2026-11-15 — Noted inventory items appeared unnoted after a client restart and empty worn slots had low contrast (#71)

- **What broke:** `GroupCache` persisted carried items using the legacy counts-only encoder (`encodeCarried(gson, inventory, worn)`), discarding the 28-slot `is` array. On startup, cached member cards fell back to canonical item counts and drew noted stacks as unnoted items until the member sent a live update. Empty worn-equipment silhouettes also blended into dark cell backgrounds, and a null sprite return would throw a `NullPointerException` when cached in `ConcurrentHashMap`.
- **How it was found:** Reported by the Board after logging in and seeing a teammate's cached noted stack drawn unnoted and empty worn slots barely visible.
- **Fix:** Persisted the full `Carried` payload (including `is` slots and `cc` container contents) in `group-cache.json`, lightened empty worn-slot silhouettes and cell backgrounds (`WornSlotIcons.lighten`, `SmallItem.CELL`), and skipped caching null sprites (#71).

## 2026-11-15 — Level-up Discord streaks started a new post after a client restart (#67)

- **What broke:** `LevelThreads` kept active level-up streak message IDs in memory only, so restarting the client inside the *Combine level-ups* window caused the next level-up in the same skill to post a new Discord message instead of editing the existing one.
- **How it was found:** Observed by the Board when training Strength across a client restart produced two separate Discord posts 16 minutes apart despite a 30-minute combine window.
- **Fix:** Persisted active level-up streaks keyed by webhook ID (`w<digits>`) to `plugin-data/gim-utilities/level-streaks.txt` on every change and restored unexpired streaks on startup (#67).

## 2026-11-15 — Live Discord status board posted duplicate messages across rapid client restarts (#63)

- **What broke:** `StatusBoard` stored its Discord message ID through `ConfigManager.setConfiguration`, which RuneLite flushes to disk only every ~5 minutes or on a clean `ClientShutdown`. Stopping or restarting a developer client before the periodic config flush lost the message ID, causing the next run to create a duplicate `"Group status"` board and leave the old one behind. Webhook URL variations (trailing slash, `thread_id` query, domain aliases) and pre-settle startup fallbacks also hashed to different board tags.
- **How it was found:** Observed by the Board when repeated `./gradlew run` restarts during testing left three separate `"Group status"` boards in Discord within 25 minutes.
- **Fix:** Keyed status boards by numeric webhook ID (`w<webhookId>`), persisted board state immediately to `plugin-data/gim-utilities/status-board.txt` via atomic temp-file replacement (`PluginFiles`), waited 20 seconds after login for group roles and shared webhooks to settle before sending, and queued known predecessor boards for deletion whenever a replacement board is posted (#63).

## 2026-11-14 — Closing group storage without saving logged and posted discarded changes (#60)

- **What broke:** `StorageSession.close()` diffed and committed the temporary group storage container (`INV_GROUP_TEMP`) whenever the storage interface closed, even if the player walked away without clicking Save and the game discarded the changes.
- **How it was found:** Found by the Board during live testing when depositing 1,000 logs and walking away without pressing Save logged and posted the discarded deposit.
- **Fix:** Changed `StorageSession` to commit only on an explicit Save button click or when the game's loading modal (`InterfaceID.LOADING_ICON_MODAL`) displays `"Saving..."`, and added a truth-check reconciliation on the next storage open that silently corrects the local snapshot if a previous save failed (#60).

## 2026-11-14 — Unknown map regions rendered raw `"region 13470"` numbers on member cards (#57)

- **What broke:** `RegionNames` fell back to `"region " + id` whenever a map region ID was absent from the bundled `regions.txt` table (such as `13470` in the Wilderness Slayer Cave).
- **How it was found:** Observed by the Board on a member card showing `Offline - last seen W315, region 13470`.
- **Fix:** Added the Wilderness Slayer Cave regions (`13470`, `13726`) to `regions.txt`, added a generated supplemental table (`regions-generated.txt` via `tools/regions/gen_regions.py`) from RuneLite world-map and clue data, and replaced the numeric fallback with human-readable descriptions (*Wilderness lvl A-B*, *near X*, *under X*, *Underground*, *Instance*, *Unmapped area*) (#57).

## 2026-11-14 — Sidebar panel button disappeared when all sharing switches were off (#49)

- **What broke:** `GroupIronmanPlugin.updateNavButton()` only added the sidebar toolbar button while at least one sharing switch was enabled (`group.anyOn()`), so a fresh install showed no panel button and unticking the last switch made the panel vanish.
- **How it was found:** Reported during a two-player test with the Board when a tester with default all-off settings saw no GIM Utilities panel icon and thought the plugin had failed to load.
- **Fix:** Added `SidebarButton` on `startUp` and removed it only on `shutDown`, displaying an *"Everything is off. Turn on what you want to share."* notice with an **Open GIM Utilities settings** button when all switches are disabled (#49).

## 2026-11-14 — Local group storage visits vanished when *Share group storage changes* was off (#47)

- **What broke:** `GroupService.commitStorage` returned early before recording the player's own visit in `GroupRoster` whenever *Share group storage changes* was unchecked, hiding the player's own storage history from the `Storage` tab and leaving `Log` entries unexplained.
- **How it was found:** Observed during a two-player test when a tester withdrew items from group storage before enabling *Share group storage changes* and the visit did not appear in their `Storage` tab.
- **Fix:** Recorded the player's own storage visits locally in `GroupOutbox.ownStorage` even when sharing is off, marking them `*(not shared: Share group storage changes is off)*` in the `Storage` and `Log` tabs while sending nothing over the party (#47).

## 2026-11-14 — Leader's *Save to group* lost unsaved edits when another member had the Settings tab open (#45)

- **What broke:** `GroupSettingsTab.draw` reset its editable draft whenever `View.signature()` changed—which included member presence, `heard` timestamps, and member preference announcements—and **Save to group** (`setFocusable(false)`) did not commit in-progress `JSpinner` text edits before saving. When another member toggled a preference or rejoined, the leader's draft silently reverted to the old values before Save ran.
- **How it was found:** Found by the Board during a two-account test of group settings when clicking **Save to group** while the second account had the `Settings` tab open failed to persist the leader's changes.
- **Fix:** Extracted `SettingsDraft` so the draft resets only on role changes or when unedited, committed typed `JSpinner` text before every save and rebuild, surfaced version-conflict notices when newer group settings arrive under an active draft, and reported explicit `SaveResult` status notes (#45).

## 2026-11-12 — Fresh installs enabled before login did not display the in-game welcome message (#40)

- **What broke:** `UpdateNotes.toShow` initially suppressed the welcome message unless the plugin was turned on while already logged in (`startedLoggedIn`), so enabling the plugin at the login screen before logging in recorded the version without showing the welcome lines.
- **How it was found:** Found by the Board while reviewing the first-install welcome behavior in PR #39.
- **Fix:** Removed the `startedLoggedIn` gate in `UpdateNotes.toShow` so any profile with an empty `lastSeenVersion` sees the welcome message on its first login (#40).

## 2026-11-12 — Long setting labels and 7-digit GP values were truncated in RuneLite's config panel (#36, #38)

- **What broke:** RuneLite's `ConfigPanel` gives fixed right-hand space to 6-column number spinners and wide dropdowns, truncating centre `JLabel` setting names with `"..."` (e.g. `Poster name` squeezed to `"P..."` beside `"Use the webhook's own name"`, and `Offline grace (minutes)` cut off) and clipping comma-formatted `1,000,000` GP defaults to `1,000,00`.
- **How it was found:** Spotted by the Board when capturing screenshots of the 8-section settings panel for the README.
- **Fix:** Shortened setting and dropdown display labels without changing any `keyName`, formatted GP spinners with plain digits (`@Units(GP_PLAIN)`) so 7-digit values fit in 6 columns, added `@Units(MINUTES)` to `Offline grace`, collapsed non-*Group* sections by default, and added font-metrics width assertions in `ConfigFitTest` (#36, #38).

## 2026-11-12 — Level-up Discord screenshots were captured before the chatbox confirmation line rendered (#31)

- **What broke:** `GroupEventEngine` captured the level-up screenshot on the tick `StatChanged` fired, before the game's `"Congratulations, you've just advanced your ... level. You are now level N."` message arrived in the chatbox, causing the screenshot's chatbox to show the previous level.
- **How it was found:** Found by the Board during an in-game Smithing 1 → 4 test where the Discord post said level 4 but the screenshot showed the level 3 chat message.
- **Fix:** Added `LevelShot` to hold level-up screenshot capture until the matching level-up chat message arrives (or up to 2 seconds, capped at 3 seconds across rapid multi-level gains) before taking the frame (#31).

## 2026-11-12 — Members without the plugin appeared as `unknown` or offline despite being visible in the in-game Group channel (#30)

- **What broke:** Online status on the live board, sidebar cards, and Group box overlay relied solely on GIM Utilities party messages, showing members who did not run the plugin as `unknown` and displaying *"Nobody else online"* in the Group box even while the in-game Ironman Group channel showed them online.
- **How it was found:** Observed by the Board during live testing when a group member online on World 615 without the plugin appeared as `unknown` on the live board and absent from the Group overlay.
- **Fix:** Added `GamePresence` and `ChannelPosts` to read member presence and world numbers directly from `client.getClanChannel(ClanID.GROUP_IRONMAN)` (with `FriendContainer` as fallback) across the live board, Group overlay, sidebar cards, and deduplicated login/logout posts (#30).

## 2026-11-11 — Slayer gem checks false-fired `"task done"` and dialogue task assignments did not post (#27)

- **What broke:** Checking an enchanted gem triggered a false `"Slayer task done"` event, whereas receiving a new Slayer task through a Slayer master's dialogue (or a Slayer partner) posted nothing. In addition, a custom poster name rejected by Discord fell back to `"GIM Utilities"` instead of the webhook's own name (#18, #27), and multi-level single-action jumps (such as burying bones at low Prayer) reported `number - 1` as the starting level instead of the true previous level (#27).
- **How it was found:** Found during review and testing of Slayer chat patterns, webhook poster names, and level-up streaks in the PR #18 / #21 / #22 stack.
- **Fix:** Matched Slayer completions strictly on the game's return-to-master completion line and parsed master dialogue assignments in `SlayerMessage`, fell back to the webhook's own name on rejected custom names (later updated to the webhook's default persona name in #41), and recorded the pre-action skill level on `GroupEvent.previous` in `ProgressTracker` (#27).

## 2026-11-11 — Direct `.runelite` `File` I/O violated the Plugin Hub's Filepath API requirement (#26)

- **What broke:** `GroupCache` read and wrote `.runelite/gim-utilities/group-cache.json` directly via `RuneLite.RUNELITE_DIR`, `java.io.File`, and `java.nio.file.Files`, which Plugin Hub reviewers reject for new plugins in favor of RuneLite's `Filepath` API.
- **How it was found:** Flagged by a Plugin Hub reviewer on submission PR `runelite/plugin-hub#20149`.
- **Fix:** Migrated group cache persistence to `getPluginDirectory()` and `net.runelite.client.util.Filepath`, adding `internalName = "gim-utilities"` and `legacyDataDirectory = "gim-utilities"` on `@PluginDescriptor` for automatic directory migration (#26).

## 2026-11-11 — Discord webhook `?wait=true` target construction dropped existing query parameters and Wilderness regions lacked names (#24)

- **What broke:** `DiscordSender.target()` appended `"?wait=true"` directly to the webhook URL string, which would produce a malformed query string if a webhook URL already carried query parameters (such as `thread_id`), and 2xx replies missing a message `id` failed silently without diagnostic logging. Several Wilderness locations also fell back to raw region numbers.
- **How it was found:** Investigated after the Board saw consecutive Prayer level-ups post separately and noticed missing Wilderness place names during live testing.
- **Fix:** Preserved existing query strings when inserting `/messages/<id>` and `wait=true` in `DiscordSender.target()`, logged 2xx replies missing a message ID at `debug`, added `FakeDiscordTest` over real `OkHttpClient` requests, and added Wilderness region definitions and fallback handling to `regions.txt` (#24).

## 2026-11-11 — `"Gravestone expiring"` warning attached a screenshot of the player's current location and fired even when no grave existed (#20)

- **What broke:** The 5-minute `"Gravestone expiring"` Discord warning captured a screenshot at the time of the warning (wherever the player was standing) rather than where they died, and the warning timer fired even after safe deaths, 0-item deaths, or instanced deaths where items went to a reclaim NPC instead of spawning a gravestone.
- **How it was found:** Found by the Board during live death and gravestone testing.
- **Fix:** Added `GraveShot` and the *Grave picture* setting (*Where I died*, *Current view*, *None*) to reuse the death-moment frame and list the top grave items, cancelled the timer when nothing left inventory/equipment, and suppressed the warning unless `GRAVESTONE_VISIBLE` was observed (#20).

## 2026-11-11 — Freeloader storage Easter egg did not trigger at the exact configured GP threshold (#17)

- **What broke:** `EasterEggs.freeloader()` checked `took > freeloaderAt` while `legend()` checked `>=`, so withdrawing exactly 10,000 gp with *Freeloader at (gp)* set to `10000` produced no Easter egg line while depositing 10,000 gp triggered *Legend*.
- **How it was found:** Found by the Board during live testing with both *Legend at (gp)* and *Freeloader at (gp)* set to `10000`.
- **Fix:** Changed `EasterEggs.freeloader()` to use `took >= freeloaderAt` and added four deterministic rotating phrasings each for Legend and Freeloader (#17).

## 2026-11-11 — Ticking a daily digest category suppressed instant Discord posts and 0 gp deaths posted `"lost about 0 gp"` (#16)

- **What broke:** Enabling a category under *Discord: daily digest* held and suppressed that category's instant Discord posts instead of sending both, multiple events from the same game moment (such as a group storage visit or a drop that also unlocked a collection log slot) posted separate Discord messages, and a 0 gp death posted `"lost about 0 gp"`.
- **How it was found:** Observed by the Board during live Discord webhook testing across PRs #12, #13, and #14 (merged via carrier PR #16).
- **Fix:** Made daily digests additive alongside instant posts and added game-chat validation warnings for invalid webhook URLs (#13, #16); merged same-moment events into a single consolidated Discord post with one screenshot in `DiscordMerge` (#14, #16); and omitted 0 gp death loss lines unless an Easter egg applies (#12, #16).

## 2026-11-10 — Quantities printed as raw unformatted integers and stacked coins rendered a single-coin icon (#15)

- **What broke:** Item quantities and GP values in the `Find` tab, `Storage` log, overlays, and Discord posts printed as raw integers (`Coins - 450001 in group storage`, `took 40000 x Coins`), and `Find` rows and Discord thumbnails always requested the single-item sprite (`COINS 995`) regardless of stack size.
- **How it was found:** Found by the Board while searching for coins in the `Find` tab and reviewing Discord storage posts.
- **Fix:** Wrapped `QuantityFormatter.quantityToStackSize` in `Amounts` (`GAME`, `EXACT`, `BOTH`) across all panel, tooltip, chat, overlay, and Discord formatters, passed stack quantities to `ItemManager.getImage` in `Find` rows, and mapped coin stack thresholds to `ItemID.COINS_2..COINS_10000` in `StackIcons` (#15).

## 2026-11-10 — Offline Discord posts were titled `"You: Online"` and fired on every world hop or brief disconnect (#11)

- **What broke:** Offline Discord posts were built on the background executor after logout when `group.localPlayer()` fell back to `"You"` and `EventText.title` mapped `GroupEvent.Type.ONLINE` to `"Online"` for both logins and logouts, producing `"You: Online"` with body `"is offline"`. World hops and brief disconnects also triggered immediate offline/online post pairs.
- **How it was found:** Found by the Board during live login/logout and world-hop testing with Discord webhooks enabled.
- **Fix:** Captured the player's login name before logout, titled logout events `"Offline"` with session duration and logout time, ignored `GameState.HOPPING`, and added `PresenceDebounce` with a configurable *Offline grace* window and 10-minute pair throttle (#11).

## 2026-11-10 — Windows launcher failed when invoked from PowerShell 7 due to inherited `PSModulePath` (#10)

- **What broke:** When `tools/windows/Run-GimUtilities.cmd` was started from a PowerShell 7 (`pwsh`) process, the child Windows PowerShell 5.1 (`powershell.exe`) process inherited PowerShell 7's `PSModulePath` and failed in step `[1/3]` with `The term 'Get-FileHash' is not recognized`.
- **How it was found:** Discovered by the `.github/workflows/windows-launcher.yml` end-to-end CI run on `windows-latest`.
- **Fix:** Cleared `PSModulePath` (`set "PSModulePath="`) in `Run-GimUtilities.cmd` before invoking `powershell.exe` (#10).

## 2026-11-10 — Held daily digest lines were lost when no webhook was set and dry-streak alerts depended on chat message arrival order (#7)

- **What broke:** `GroupEventEngine` dropped queued daily digest lines and advanced `digestLastAt` even when no Discord webhook was configured or all digest switches were off, and dry-streak call-outs could misfire or miss a drop depending on whether the boss kill-count chat message arrived before or after `NpcLootReceived`.
- **How it was found:** Found during code review and unit testing (`DryStreakTest`) of medium group events in PR #7.
- **Fix:** Preserved held digest lines until a valid webhook is configured and at least one digest category is enabled, and held dry-streak call-outs for 10 seconds so a qualifying unique drop arriving in either order cancels the dry-streak alert (#7).

## 2026-11-05 — Group tab rendered blank when reading item stats off the client thread and collapse arrows lacked glyphs (#3)

- **What broke:** In the initial tabbed sidebar panel, reading item equipment stats off the client thread caused the `Group` tab to render blank, the RuneScape font lacked Unicode `▾`/`▸` collapse arrow glyphs on member cards, panel text overflowed the right edge, the `Leave` button label was truncated, and `::find` printed `"equipment"` instead of `"equipped"`.
- **How it was found:** Found by the Board during the 2026-11-04 in-game test of the round-2 panel (`0f88ddf`..`cc357de`).
- **Fix:** Resolved item equipment slots on the client thread, custom-painted the member-card collapse arrow, aligned panel columns and shortened the `Leave` button so nothing clipped, added a fallback error notice if a tab fails to draw, and updated `::find` wording to `"equipped"` (#3).

## 2026-11-05 — Windows launcher source download returned a bare HTTP 404 when the repository was private or tag was missing (#2)

- **What broke:** When `tools/windows/Run-GimUtilities.cmd` ran standalone outside a checkout before the repository was public or the target tag was pushed, step `[2/3]` failed with an unexplained HTTP 404 while downloading `archive/refs/tags/gim-test-1.zip`.
- **How it was found:** Porting and testing the Windows launcher from Build Guard in PR #2.
- **Fix:** Added a diagnostic error message in `Run-GimUtilities.cmd` and `SETUP.md` explaining that a 404 at step `[2/3]` means the repository is not public yet or the release tag has not been pushed (#2).

## 2026-11-04 — Developer client (`./gradlew run`) exited with code 255 on RuneLite 1.12.39 (#1)

- **What broke:** Inheriting `runeLiteVersion = '1.12.39'` from Build Guard caused `./gradlew run` to exit with code 255 roughly 12 seconds after startup against the live game.
- **How it was found:** Found by the Board when launching `./gradlew run` on the initial split branch.
- **Fix:** Bumped `runeLiteVersion` in `build.gradle` from `1.12.39` to `1.13.5` (#1).
