# Discord Relay contract, v1

GIM Utilities no longer posts to Discord. Every Discord post is made by the separate **Discord Relay** plugin
(repo `VinnyVanGogh/discord-relay-plugin`). GIM detects events, decides which client is responsible for each
one (group logic), and hands it over through RuneLite's own `net.runelite.client.events.PluginMessage` on the
injected `EventBus`. No reflection, no shared classes and no dependency between the two plugins: the contract is
the namespace, the message names and the JSON-shaped maps below.

This document is shared by both repos. A change to any message is a change to both plugins: bump `v`, update
the fixtures in `src/test/resources/contracts/discord-relay-v1/` (copied byte for byte into Discord Relay), and
keep both contract tests green.

- GIM side: `src/main/java/com/vinnyvangogh/groupironman/DiscordRelay.java` (builders, receive),
  `GroupEventEngine` (when each message is sent), test `DiscordRelayContractTest`.
- Relay side: ports the code listed under "Relay port list" and runs the same fixtures from its side.

## Transport

| | |
|---|---|
| Event | `net.runelite.client.events.PluginMessage(namespace, name, data)` posted on the injected `EventBus` |
| Namespace | `discord-relay` (both directions) |
| Data | `Map<String, Object>` holding only `String`, `Boolean`, numbers (`Integer`/`Long`; a receiver must accept any `Number`), `List`, `Map` and `null` |
| Envelope | every message carries `v` (contract version, `1`) and `source` (`gim-utilities` from GIM, `discord-relay` from Relay) |
| Direction | a receiver ignores messages whose `source` is its own, and messages whose `v` it does not know |
| Thread | `EventBus.post` runs subscribers on the caller's thread. GIM sends from the client thread and from its 1-second executor timer. Relay must not assume the client thread: anything that touches `Client` goes through `clientThread.invoke`, and the subscriber returns quickly (no network on the caller's thread) |

## Handshake

1. GIM starts (after it registers on the bus): sends `hello`.
2. Relay answers any `hello` with `ready`. Relay also sends `ready` at its own start-up and after any change to
   its webhook settings or its "Post to Discord" switch.
3. GIM, on `ready`: marks Relay installed, takes Relay's webhooks (for the leader's sharing and the live
   board rule), sends `legacy` once per GIM run, then sends `settings` and `board` again (it forgets what it
   sent before, since a fresh `ready` may be a restarted Relay).
4. Either side stopping sends `bye`. GIM on Relay's `bye`: Relay is gone, nothing is posted, the hint shows.
   Relay on GIM's `bye`: stop keeping the board for GIM and drop any held frame.

Until a `ready` arrives GIM posts nothing and shows one line in its Settings tab (above the shared-webhooks
block): **"Install Discord Relay to post to Discord"** (`DiscordRelay.INSTALL_HINT`).

## Messages, GIM to Relay

### `hello`

| Field | Type | |
|---|---|---|
| `version` | string | GIM's version (`UpdateNotes.VERSION`) |

### `event`

One event to post now, to note for the daily digest, or to attach to an open moment. Fixtures:
`event-drop.json`, `event-level-digest.json`, `event-storage-attach.json`, `event-gravestone.json`.

Generic envelope (what any plugin may send; enough for a plain post):

| Field | Type | |
|---|---|---|
| `channel` | string | webhook name: `MAIN`, `ONLINE`, `DROPS`, `PROGRESS`, `DEATHS`, `STORAGE`, `DIGEST` (GIM's `Webhook.of(type)`; never `DIGEST` for an instant post). An unset category webhook falls back to Main's URL and keeps its own poster name |
| `title` | string | heading (`EventText.title`) |
| `text` | string | one line, game amounts ("1.5M") (`EventText.describe`) |
| `fields` | list of `{name, value, inline}` | `Items` with the items and their total when the event has items |
| `iconItemId` | number or null | the most valuable item, for the embed thumbnail |
| `screenshot` | string | `none` (never a frame of this screen: a login, or another member's event), `frame` (the next frame, if Relay's screenshots are on), `grave` (the frame held by `capture` with key `grave`, per Relay's 'Grave picture') |
| `mergeKey` | string | the member: events of one member's moment may merge |
| `mention` | boolean | worth the configured mention (pets, 99s, max, Hardcore lives) |
| `post` | boolean | post it now |
| `digest` | boolean | a line for its category's daily digest |
| `attach` | boolean | not a post of its own: joins this member's open moment (a storage visit's summary listed under a big deposit or withdrawal, posted whether or not the summary kind is ticked) |

`post` and `digest` arrive as separate messages for one event (the router's two outputs). GIM sends `post` only
when the group's settings tick the kind for Discord and this member has not opted out, and only from the one
client responsible (see "Who posts"); it sends `digest` when the kind's category is in the group's digest.

Structured `event` object (GIM's `GroupEvent`, so Relay's GIM rendering matches GIM's posts exactly):

| Field | Type | |
|---|---|---|
| `type` | string | `GroupEvent.Type` name |
| `code` | string | the type's wire code (`d`, `lv`, ...) |
| `category` | string | `DROPS`, `DEATHS`, `STORAGE`, `PROGRESS`, `GROUP`, `ACTIVITIES` (embed colour, digest) |
| `at` | number | ms epoch, the sender's clock (also the Easter egg seed) |
| `member` | string | |
| `text`, `detail`, `location` | string | |
| `number`, `previous` | number | level, kill count, value...; `previous` a level-up's level before |
| `world` | number | 0 when unknown |
| `broadcast` | boolean | read from the game's Group channel for a member without the plugin: Relay adds the "group broadcast" footer, never merges it with this player's own moment, never screenshots |
| `items`, `taken` | list of `{id, quantity, name, price}` | `price` per item in gp (long); `taken` only for a storage summary |

### `board`

The live online board. Sent when any field changes. Fixture: `board.json`.

| Field | Type | |
|---|---|---|
| `channel` | string | `ONLINE` |
| `keep` | boolean | this client keeps the board: 'Online status' is Live board, Relay posts, the Online status webhook (or Main) is set, and on a webhook the leader shares only the leader's client keeps it (`BoardLines.keeps`) |
| `settled` | boolean | the session's first login was 20 s ago and the group cache is read; nothing is sent before (Board 2026-11-14). Relay adds its own "saved board ids are read" condition |
| `lines` | string | one line per member (`BoardLines.lines`), empty while not kept. Empty while kept means "nothing to show yet" |

Relay owns everything else of the board: post then edit, one edit per 10 s at most, quiet hours, daily repost
and delete ('Repost board daily'), the saved message ids per webhook, 404/429 handling, the "Updated" line,
embed or plain style and the poster name.

### `settings`

The group's settings that shape posts: they are the group's (signed by the leader or an editor, shared over the
party), so GIM resolves them and Relay only reads them. Sent when they change (checked every second, so the
leader's shared webhooks arriving over the party are passed on too). Fixture: `settings.json`.

| Field | Type | |
|---|---|---|
| `discordTypes` | list of string | kinds ticked for Discord (Relay's `DiscordMerge` reads these: a death's window, whether a storage summary shows) |
| `digestCategories` | list of string | |
| `digestHour` | number | 0-23, the daily digest's hour (and GIM's weekly recap) |
| `quietStart`, `quietEnd` | number | hours; equal means no quiet hours |
| `offlineGraceMinutes` | number | |
| `onlineStatus` | string | `POSTS`, `LIVE_BOARD` or `OFF`, as in force for this client |
| `dropMin`, `storageMin`, `clueMin` | number | thresholds (gp) |
| `easterEggs` | `{on, legendAt, freeloaderAt}` | |
| `shared` | object or null | the leader's shared webhooks, or null when the leader shares none: `{by, channels: {NAME: {url, poster, name}}}`. A member's own webhook (in Relay) wins per channel; an empty own URL takes the group's |

### `capture` and `release`

`capture {key}`: take the next frame now and hold it under `key`. GIM sends `capture {key: "grave"}` at this
player's own death when the gravestone warning posts to Discord. `release {key}`: the held frame is no longer
wanted (warned, looted, logged out). A later `event` with `screenshot: "grave"` uses the held frame. Fixtures:
`capture.json`, `release.json`.

### `legacy`

Once per GIM run, after the first `ready`: what GIM kept for its own posts before the split, so Relay keeps
editing the same Discord messages. Each value is the file's text as GIM wrote it, or null. Relay imports it only
when it holds nothing of its own yet. Fixture: `legacy.json`.

| Field | Source in GIM |
|---|---|
| `statusBoard` | plugin data file `status-board.txt`, else config key `gimutilities.statusBoardMessage` |
| `levelStreaks` | plugin data file `level-streaks.txt` |
| `groupedPosts` | plugin data file `grouped-posts.txt` |

GIM no longer writes any of them.

### `bye`

Envelope only. Fixture: `bye.json`.

## Messages, Relay to GIM

### `ready`

Fixture: `ready.json`.

| Field | Type | |
|---|---|---|
| `version` | string | Relay's version |
| `enabled` | boolean | Relay's own "Post to Discord" switch |
| `channels` | `{NAME: {url, poster, name}}` | Relay's own webhooks (`poster`: `WEBHOOK`, `GIM_UTILITIES`, `CUSTOM`). GIM reads them only to sign them into the leader's "Share webhooks with the group" document and to apply the board's keeper rule; GIM never posts with them |

### `bye`

Envelope with `source: "discord-relay"`.

## Who posts (stays in GIM)

| Rule | Where in GIM |
|---|---|
| Only this player's own events post (each member's client posts its own) | `EventRouter.route(own)` |
| A member without the plugin: the one plugin user who leads posts the game's Group broadcast / channel login | `ChannelPosts.leads`, `GroupBroadcasts.decide`, `GroupSettingsSchema.NO_PLUGIN_EVENTS` |
| A login post is skipped when another member's client keeps the live board | `GroupService.boardKeptByOthers` |
| Everyone online: the leader's client alone | `GroupEventEngine.everyoneTick` |
| "Don't post my events" and per-kind opt-outs; the login burst is held | `MemberPrefs`, `LoginBurst` |
| An account not in the group posts nothing | `group.member()` checks |
| Thresholds (drop, clue, level min, every N levels, storage min) | `EventRouter.threshold` |

## Config migration

GIM's config group is `gimutilities`. The keys below were removed from `GroupIronmanConfig` (their settings are
Relay's now). **GIM never unsets them**, so the stored values stay. On its first run Relay reads each with
`ConfigManager.getConfiguration("gimutilities", key)` (read-only), writes the value into its own group when its
own key is unset, then marks the migration done in its own group.

| Key | Type | GIM default | Notes |
|---|---|---|---|
| `discordEnabled` | boolean | false | "Post to Discord"; carried the third-party `warning` |
| `discordWebhookUrl` | String (secret) | "" | Main webhook |
| `discordPosterName` | `DiscordPosterName` | `WEBHOOK` | Main poster choice: `WEBHOOK` ("Webhook's own"), `GIM_UTILITIES` ("Default name"), `CUSTOM` |
| `discordNameMain` | String | "Town Crier" | |
| `discordGroupUrl` | String (secret) | "" | Online status webhook |
| `discordPosterOnline` | `DiscordPosterName` | `WEBHOOK` | |
| `discordNameGroup` | String | "World Hopper" | |
| `discordDropsUrl` | String (secret) | "" | |
| `discordPosterDrops` | `DiscordPosterName` | `WEBHOOK` | |
| `discordNameDrops` | String | "Party Pete" | |
| `discordProgressUrl` | String (secret) | "" | |
| `discordPosterProgress` | `DiscordPosterName` | `WEBHOOK` | |
| `discordNameProgress` | String | "Skillcape Seller" | |
| `discordDeathsUrl` | String (secret) | "" | |
| `discordPosterDeaths` | `DiscordPosterName` | `WEBHOOK` | |
| `discordNameDeaths` | String | "Death's Office" | |
| `discordStorageUrl` | String (secret) | "" | |
| `discordPosterStorage` | `DiscordPosterName` | `WEBHOOK` | |
| `discordNameStorage` | String | "Gielinor Banker" | |
| `discordDigestUrl` | String (secret) | "" | Daily digest webhook |
| `discordPosterDigest` | `DiscordPosterName` | `WEBHOOK` | |
| `discordNameDigest` | String | "Varrock Herald" | |
| `discordActivitiesUrl` | String (secret) | "" | retired, hidden, never read; do not migrate |
| `discordNameActivities` | String | "" | retired, hidden, never read; do not migrate |
| `discordRepostBoard` | boolean | true | 'Repost board daily' |
| `discordCombineLevels` | int (minutes, 0-240) | 60 | 'Combine level-ups'. Installs from before 30 kept 30: when `combineLevelsChosen` is unset, apply `LevelPosts.windowToKeep(upgrading = onlineStatusChosen is set, stored value)` once, as GIM did |
| `discordGroupPosts` | int (minutes, 0-240) | 30 | 'Group posts' |
| `discordStyle` | `DiscordStyle` | `EMBED` | `EMBED` ("Rich embed"), `PLAIN` ("Plain text") |
| `discordScreenshot` | boolean | false | 'Attach a screenshot' |
| `discordPickupShot` | boolean | false | 'Add picked-up screenshot' |
| `discordPickupMinutes` | int (1-30) | 5 | 'Pickup wait' |
| `discordGravestoneScreenshot` | `GraveScreenshot` | `DEATH` | `DEATH` ("Where I died"), `CURRENT` ("Current view"), `NONE` |
| `discordMention` | String | "" | 'Mention for big events' |
| `discordOnline` | boolean (hidden) | false | retired; GIM still reads its stored value once for `discordOnlineStatus`. Do not migrate |

Other stored state Relay reads once (read-only):

| Where | Key | |
|---|---|---|
| RS profile config, group `gimutilities` | `digestLines` | the day's held digest lines (`CATEGORY<tab>line` per line) |
| RS profile config, group `gimutilities` | `digestLastAt` | ms epoch of the last daily digest |
| config, group `gimutilities` | `combineLevelsChosen`, `onlineStatusChosen` | install markers (see `discordCombineLevels`) |
| `legacy` message | board ids, level streaks, grouped posts | see above |

Keys that stay in GIM because they are the group's shared settings (Relay receives them in `settings`): every
per-kind `discord*` switch ("Discord: what to post"), `digest*` and `discordDigestHour`, `discordQuietStart`,
`discordQuietEnd`, `discordOfflineGrace`, `discordOnlineStatus`, `discordEasterEggs`, `discordLegendAt`,
`discordFreeloaderAt`, `discordNoPluginEvents`, and `eventOptOutAll` ("Don't post my events").

## Parity checklist

Every Discord feature GIM had on `main` before the split, and where it lives now.

| Feature / setting | GIM before (on `origin/main`) | Now |
|---|---|---|
| Post to Discord switch | `discordEnabled` | Relay setting; `ready.enabled` |
| Seven webhooks, Main fallback | `Webhooks`, `Webhook`, 7 URL keys | Relay settings; `event.channel`; fallback rule in Relay (GIM keeps the rule for the board keeper) |
| Poster names (Webhook's own / Default / Custom, refused names) | `Webhooks.name`, `PosterNames` | Relay |
| Leader's "Share webhooks with the group" | `GroupWebhooks`, governance | stays GIM (signed, over the party); source of the leader's URLs is `ready.channels`; members get them in `settings.shared` |
| Webhook and poster-name warnings in chat | `WebhookWarnings`, `PosterNames.check` | Relay |
| Hub third-party warning text | `DiscordWebhook.WARNING` on `discordEnabled` | Relay's own `discordEnabled`-equivalent `@ConfigItem(warning = ...)` and Relay's `@PluginDescriptor` |
| Which kinds post (per-kind switches, group-shared) | `EventSettings.DISCORD`, schema `discord.*` | stays GIM; GIM sends only ticked kinds; `settings.discordTypes` |
| Thresholds, every N levels | `EventRouter.threshold` | stays GIM |
| Opt-outs, "Don't post my events" | `MemberPrefs` | stays GIM |
| Embeds / plain text, colours, icons, footer | `DiscordWebhook.payload`, `discordStyle` | Relay, from `event.event` |
| Merged moments (a death with its value, a storage visit) | `DiscordBatch`, `DiscordMerge` | Relay (`settings.discordTypes`, `settings.storageMin`, `attach`) |
| Grouped posts (same kind edits one message) | `EventPosts`, `EventThreads`, `EditedThreads`, `discordGroupPosts` | Relay; legacy `groupedPosts` |
| Level streaks (combine level-ups) | `LevelPosts`, `LevelThreads`, `discordCombineLevels` | Relay; legacy `levelStreaks` |
| Edited posts machinery | `EditedPosts`, `EditedThreads` | Relay |
| Screenshots on posts | `discordScreenshot`, `nextFrame` | Relay (`event.screenshot`) |
| Level-up picture waits for its chat line ("chat-box shot") | `LevelShot` | Relay, reading `ChatMessage` itself |
| Picked-up screenshot edit | `PickupWatch`, `discordPickupShot`, `discordPickupMinutes` | Relay, reading inventory and position itself, own drops only (`event.broadcast` false, type `DROP`) |
| Death posts; activity death replaces plain death | `postsPlainDeath` | stays GIM (decides which is sent) |
| Gravestone warning and its picture | `GraveShot`, `GraveScreenshot`, `discordGravestoneScreenshot` | GIM sends `capture`/`release` key `grave` and `screenshot: "grave"`; Relay holds the frame and applies 'Grave picture' |
| Mention for big events | `discordMention`, `Type.big` | Relay setting; `event.mention` |
| Quiet hours (posts wait) | `DiscordQueue.quiet`, schema | Relay; `settings.quietStart/End` (GIM keeps its banner quiet hours) |
| Rate limit, 429 / Retry-After, retries, queue | `DiscordQueue`, `DiscordSender` | Relay |
| Online status: posts, live board, off | `OnlineStatus`, schema | stays GIM (group setting); `settings.onlineStatus` |
| Live board: who keeps it, lines, stale line, activities | `StatusBoard.keeps/effective/lines/activitySuffixes` | stays GIM as `BoardLines`; `board` message |
| Live board: post/edit, repost daily, delete old, saved ids | `StatusBoard` sending, `discordRepostBoard`, `status-board.txt` | Relay; legacy `statusBoard` |
| Offline grace | schema `discordOfflineGrace`, `PresenceDebounce` | stays GIM (decides when the logout is an event) |
| Members without the plugin | `ChannelPosts`, `GroupBroadcasts`, `discordNoPluginEvents` | stays GIM; `event.broadcast` |
| Daily digest (lines, schedule, one post per category) | `Digest`, `flushDigest`, `DiscordWebhook.digest` | Relay (`event.digest`, `settings.digestCategories/digestHour`; reads `digestLines`/`digestLastAt` once) |
| Weekly recap | `Digest.Week`, `WEEKLY_RECAP` | stays GIM (an ordinary event) |
| Easter eggs on posts (Loot Goblin style lines, Legend, Freeloader...) | `EasterEggs`, `EasterEggLines`, `easter-eggs.txt` | Relay (`settings.easterEggs`, seed from `event.at/member/code`) |
| Easter egg 67/69 tag on banners and Group box | `EasterEggs.overlayTag` | stays GIM |
| "Post my events to Discord" button on the sharing card; Quick settings "Post to Discord" | `SharingPrompt`, `QuickSettings` | removed from GIM: it is Relay's own switch |
| Test post | (no test-post button existed in GIM on `main`) | Relay may add one; nothing to port |
| Upgrade: old "Online and offline" switch | `chooseOnlineStatus` | stays GIM (reads stored `discordOnline`, `discordEnabled`) |
| Upgrade: 'Combine level-ups' 30 to 60 | `keepCombineWindow` | Relay (see migration table) |

## Relay port list

Copy from GIM's `origin/main` at the commit before this split (the files are deleted or trimmed on the split
branch). Package `com.vinnyvangogh.groupironman` becomes Relay's own package.

Deleted from GIM, port whole:
`DiscordBatch`, `DiscordMerge`, `DiscordQueue`, `DiscordSender`, `DiscordStyle`, `DiscordWebhook`,
`EventPosts`, `EventThreads`, `EditedThreads`, `EditedPosts`, `LevelThreads`, `LevelPosts`, `LevelShot`,
`GraveShot`, `GraveScreenshot`, `StatusBoard`, `WebhookWarnings`, `PosterNames`, `EasterEggLines`,
`PickupWatch`.

Trimmed in GIM, port the `origin/main` version:
`EasterEggs` (GIM keeps only the 67/69 overlay tag), `Digest` (GIM keeps `weeklyDue` and `Week`), `Webhooks`
(GIM keeps the URL rules; port `name`, `urlFields`, `customNames`), and the Discord parts of
`GroupEventEngine`: `toDiscord`, `toDigest`, `flush`, `poster`, `post`, `pickupPost`, `pickedUp`,
`combineLevels`, `levelUp`, `broadcastToDiscord`, `picture`, `framesAtStart`, `nextFrame`, `png`,
`boardTick`/`sendBoard`/`loadBoards`/`saveBoard`, `loadLevels`/`saveLevels`, `loadGrouped`/`saveGrouped`,
`flushDigest`, `keepCombineWindow`, `warnBadWebhooks`, and the Discord `@ConfigItem`s in the migration table.

Copied, still used by GIM too (Relay keeps its own copy): `GroupEvent` (rebuilt from `event.event`), `EventText`,
`Amounts`, `ItemLookup` (answer from `items[].name/price`), `EventSettings` (the fields `settings` carries),
`Webhook`, `DiscordPosterName`, `OnlineStatus`, `PluginFiles`, resource `easter-eggs.txt`.

Tests to port (deleted or trimmed on the split branch; take the `origin/main` version):
`DiscordMergeTest`, `DiscordSenderTest`, `DiscordWebhookTest`, `EasterEggsTest`, `EventPostsTest`,
`FakeDiscordTest`, `FakeTransport`, `GraveShotTest`, `LevelPostsTest`, `LevelShotTest`, `PickupWatchTest`,
`PosterNamesTest`, `StatusBoardRestartTest`, `StatusBoardTest`, `WebhookWarningsTest`, `StorageValueTest`,
`DigestTest` (daily digest parts), `WebhooksTest` (poster names, warnings, settings layout),
`LevelEveryTest.combineLevelUpsOnlySeesLevelsThatPass` (the streak half),
`PresenceDebounceTest.titleIsNeverYouAndNeverSwapped` (the payload half),
`GroupBroadcastsTest.theFooterSaysWhereThePostCameFrom`, plus every fixture in
`src/test/resources/contracts/discord-relay-v1/` with a Relay-side contract test.

## Risks

- **No posts until Relay ships.** A group on this GIM build without Relay installed posts nothing to Discord; the
  Settings tab says so. Relay must reach the Plugin Hub first (Board 2026-11-25).
- **Version skew.** Relay ignores a `v` it does not know; GIM ignores a `ready` with an unknown `v` only if a
  later version changes the shape. Bump `v` on any breaking change and keep the old fixtures until both sides
  move.
- **Webhook URLs on the bus.** `ready.channels` and `settings.shared` carry webhook URLs between two plugins in
  the same client process. They are never logged, never sent over the party except inside the leader's signed
  share document (as before), and never written to GIM's config.
