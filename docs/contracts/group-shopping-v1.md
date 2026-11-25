# Group Shopping contract, v1

GIM Utilities' Shopping tab (shared shopping lists, quest presets, requests, "Path to" and the shop alert) moves
to a separate Plugin Hub plugin, working name **Group Shopping** (slug `group-shopping-plugin`, repo
`VinnyVanGogh/group-shopping-plugin`; the Board confirms the name). GIM keeps the group: who is in it, what each
member has shared, and the RuneLite Party connection. Group Shopping owns everything the tab did.

This document is the contract between the two. Both sides test against the same fixtures in
`src/test/resources/contracts/group-shopping-v1/` (GIM: `GroupShoppingContractTest`). A change to any message
means a new version (`v: 2`) and new fixtures, never an edit of v1's.

## The split, and why it looks like this

Shopping lists are group-shared with no server: every client holds every list, and changes travel over the
party inside GIM's `VvgGroupIronmanMessage` as four payload kinds (`shop`, `qstate`, `req`, `rqc`). Group
Shopping can't join the party on its own: GIM owns the party (it joins the group's party for the player), and a
second plugin registering its own party message would double the traffic and need its own group key.

So the minimal clean split is:

- **GIM is the transport and the group's memory.** It passes Group Shopping's party payloads both ways
  **unread** and stores Group Shopping's saved state **unread**, in the same `shop` entry of its group cache file
  that the Shopping tab used. GIM knows nothing of lists, presets or requests beyond the four kind names.
- **Group Shopping is everything else**: the list book and its merge rules, quest progress, presets, allocation,
  shop sources, requests and claims, the panel, "Path to" (Shortest Path), the path stop card and the shop alert.

Because the payloads are byte-for-byte the ones GIM 1.x sent, a group mixing the old GIM (with its own Shopping
tab) and the new GIM + Group Shopping still reads each other's lists, and the cache file needs no migration.

## Transport

RuneLite's own `net.runelite.client.events.PluginMessage(namespace, name, data)` on the injected `EventBus`. No
reflection, no shared classes, no dependency either way.

- `namespace` is always `group-shopping`.
- `data` is a `Map<String, Object>` that serialises to the fixture's JSON. Every message carries `v` (1) and
  `source` (`gim-utilities` from GIM, `group-shopping` from Group Shopping). Each side ignores messages whose
  `source` is its own, so neither reads its own messages back.
- Item maps are `{"<item id>": quantity}` with the id as a decimal string, in id order.
- The bus posts synchronously on the poster's thread. GIM posts from the client thread, its executor or Swing;
  Group Shopping must hop to its own thread before touching the client or Swing.

## Handshake

1. On start-up GIM posts `hello`. Group Shopping, on its start-up or on hearing `hello`, posts `ready`.
2. On `ready` (or `request`) GIM marks Group Shopping installed and answers with `snapshot` and, once its cache
   file is read, `restore`. If the cache is read later, GIM sends `restore` then.
3. On shut-down either side posts `bye`. GIM then shows the install card again.

Until GIM has heard `ready`, the Shopping tab shows one card: **"Install Group Shopping for shared shopping lists
(from the Plugin Hub)."** After `ready` it shows one line and an **"Open shopping lists"** button, which sends
`snapshot` and `open`.

Without Group Shopping, GIM drops shopping payloads from the party (it no longer has anything to apply them to)
and keeps the cached `shop` entry untouched, so nothing is lost when Group Shopping is installed later.

## Messages, GIM to Group Shopping

### `hello`

`{v, source, version}`: GIM's version (`UpdateNotes.VERSION`). Fixture `hello.json`.

### `snapshot`

`{v, source, self, members, syncing, sources}`. Fixture `snapshot.json`.

- `self`: this player's name, `""` before login.
- `members`: the group's names in the group's order, this player added when missing.
- `syncing`: true while connected to the group's party. Group Shopping greys out edits that can't reach the
  group, as the tab did.
- `sources`: everything "Who has it?" searches (`GroupRoster.searchSources()`), one object per place:
  `{member, where, items}`. `where` is `bank`, `inventory`, `equipped`, a skilling container label (`log basket`,
  ...), `rune pouch`, `quiver`, `seed vault`, or `storage` (member `Group`, group storage). Group Shopping builds
  its holdings from these exactly as `ShopAllocation.Holdings.from` did: `storage` is the group's, the rest the
  member's.

GIM sends it on `ready`, `request` and `open`, and every 5 game ticks while installed **when it changed**
(an unchanged snapshot is not re-sent).

### `party`

`{v, source, member, payload}`: a Group Shopping payload one roster member sent over the party, exactly as it
arrived. Fixture `party.json`. Group Shopping applies it as `ShoppingSync.receive(member, payload)` did.

### `joined`

`{v, source}`: someone joined or the party changed. Group Shopping resends every list, request, claim and this
player's quest progress, removals included (`ShoppingSync.resendAll`). Fixture `joined.json`.

### `restore`

`{v, source, cache}`: the cache file's `shop` entry as JSON text (`{"lists":[...],"reqs":[...],"claims":[...],
"hist":[...],"qp":{...}}`, exactly what `ShoppingSync.toCache` wrote), or `null` when there is none. Group
Shopping reads it as `ShoppingSync.fromCache` did. Fixtures `restore.json`, `restore-empty.json`.

### `add`

`{v, source, kind, itemId, itemName, quantity}`: the Gear tab's "Add to shopping list" (`kind: "list"`, a custom
list for this player alone, named after the item) or "Ask the group" (`kind: "request"`, a request of one).
GIM resolves the name to an id first. Fixtures `add-list.json`, `add-request.json`.

### `open`

`{v, source, view: "lists"}`: show Group Shopping's panel on the group's lists. Fixture `open.json`.

### `bye`

`{v, source}`: GIM is stopping. Fixture `bye.json`.

## Messages, Group Shopping to GIM

### `ready`

`{v, source, version}`. Fixture `ready.json`.

### `request`

`{v, source}`: send a fresh `snapshot` now. Fixture `request.json`.

### `send`

`{v, source, payload}`: a payload for the party. GIM accepts only the four shopping kinds and payloads up to
16 KB, queues at most 200 (dropping the oldest), and sends them in order on the next game tick while connected,
the same tick the Shopping tab's `flush` ran. Fixture `send.json`.

Group Shopping keeps the tab's rules: a change marks the list, it sends on change only, and this player's quest
progress goes only while some list is for them.

### `store`

`{v, source, cache}`: Group Shopping's whole saved state as JSON text, in the `toCache` shape above. GIM keeps it
as the cache file's `shop` entry and writes the file. A `store` that arrives before GIM has read its file wins
over the file. Fixture `store.json`.

### `event`

`{v, source, type: "REQUEST", member, kind, list, detail, itemId?}`: one of **this player's** request events,
which GIM shows and shares like any group event (banner "Shopping requests", Discord "Shopping requests" via
Discord Relay, the Log, the Group box). Fixtures `event-made.json`, `event-claimed.json`.

- `kind`: 1 made, 2 claimed, 3 done (`RequestBoard.KIND_*`).
- `list`: the request's name. `detail`: for made, the first three items as `"500 Shark, 20 Prayer potion(4)"`
  plus `" and N more"`; for claimed, `"<item> for <owner>"`; for done, empty.
- `itemId`: claimed only.
- GIM refuses an event whose `member` is not this player, or any other `type` or `kind`: Group Shopping can never
  speak for another member.

The owner's client still seals request items the group holds (every 5 ticks, from `snapshot`) and sends the
done event once; a hand close sends no done event.

### `bye`

`{v, source}`. Fixture `bye-from-group-shopping.json`.

## What stays in GIM

- The party, the group key, the roster and everything members share (banks, carried items, storage).
- The cache file and its `shop` entry (kept unread).
- `GroupEvent.Type.REQUEST`, its words (`RequestBoard.describe`) and its two switches, `discordRequest` and
  `overlayRequest`: request events are group events.
- The Gear tab; its "Add to shopping list" / "Ask the group" now send `add`.
- Quest and diary reading for member cards and Compare (`readAchievements`), now on its own 50-tick poll.

## Config migration

The Shopping settings leave GIM's config interface. Their stored values stay in the profile under GIM's group;
Group Shopping reads them **read-only** once on first run (`ConfigManager.getConfiguration("gimutilities", key)`,
and `getRSProfileConfiguration` for the per-character key) and copies them into its own group when its own key
is unset. GIM never deletes them.

| Group | keyName | Type | Default | Group Shopping home |
|---|---|---|---|---|
| `gimutilities` | `shoppingShopAlert` | boolean | `false` | "Shop alert": a chat line near a shop selling something a list needs, once per visit |
| `gimutilities` | `overlayPathStop` | boolean | `false` | "Path stop card": on screen while pathing, the stop, what to get there and for whom (was also a GIM Quick setting) |
| `gimutilities` (RS profile) | `shoppingOpen` | String (`ShopFolds` keys) | unset | The panel's open lists and items, per character |

The saved lists themselves need no migration: they stay in GIM's cache file and reach Group Shopping by `restore`.

## Parity checklist

| GIM Shopping feature (origin/main) | New home |
|---|---|
| Custom lists: name, members, items, item search (tradeable matches, best first) | Group Shopping panel |
| Quest preset lists (`shopping-presets.json`, `quest-items.json`) with picks, needs counted per member's quest progress | Group Shopping; quest progress travels as `qstate` via `send`/`party` |
| Rename / remove a list, newest revision wins, 40-list book, 30-day tombstones | Group Shopping (`ShoppingSync` rules) |
| "Who did what" history (20 lines) | Group Shopping, saved via `store` |
| Allocation: each member's need against what members and storage hold, untradeables from own holdings only | Group Shopping, holdings from `snapshot.sources` |
| Where to get it: shops (with levels), drops (`item-drops.txt`), quest sources | Group Shopping (`ShopSources`, `ItemDrops`) |
| Requests: make, rename, remove, close, close one item, claim / unclaim | Group Shopping; party via `send`/`party` |
| Auto-close request items the group holds, "done" once | Group Shopping (owner's client) + `event` kind 3 |
| Request events: made, claimed, done, as banners, Discord, Log, Group box | GIM, from `event` |
| Open lists / items remembered per character | Group Shopping (`shoppingOpen` migration) |
| "Path to" a stop through Shortest Path (PluginMessage), "Path to next" nearest first, redraw after moving far, path cleared when its item is done | Group Shopping (`ItemPaths`, `PathSession`, `ShortestPathBridge`, `banks.tsv`) |
| Path stop card overlay | Group Shopping (`overlayPathStop` migration) |
| Shop alert chat line | Group Shopping (`shoppingShopAlert` migration) |
| Gear tab "Add to shopping list" / "Ask the group" | GIM button, `add` message, Group Shopping makes it |
| Lists kept across restarts | GIM cache `shop` entry via `store` / `restore` |
| A joiner gets everything | GIM `joined` → Group Shopping resends |
| Mixed groups (old GIM with the tab, new GIM + Group Shopping) | Same payload kinds and JSON, passed unread |

## Port list

Copy these from GIM `origin/main` (at `da4a0b2`) into Group Shopping, then rename the package:

- Main: `ShoppingTab`, `ShoppingSync`, `ShoppingCodec`, `ShoppingList`, `ShoppingBoard`, `ShopAllocation`,
  `ShopSources`, `ShopCards`, `ShopPresets`, `ShopNeeds`, `ShopFolds`, `ShopVisits`, `ItemPaths`, `PathStopPlan`,
  `PathStopOverlay`, `PathSession`, `ShortestPathBridge`, `ItemDrops`, `QuestData`, `QuestProgress`,
  `QuestStates`, `RequestBook`, `RequestCards`, `RequestList`, `RequestClaim`, and `RequestBoard` (the cards,
  rows and `reached`; GIM keeps only `describe` and the `KIND_*` constants).
- From `GroupService`: the "Shopping tab" and "Requests" sections (`shoppingInputs`, `addShoppingList` ...
  `sealRequests`, `shoppingItemIds`, the quest/level half of `pollQuests`, `shopAlert`) and the shopping half of
  `GroupIronmanPlugin.shoppingView`. Helpers they call that GIM keeps (`GroupMembers.key`, `Amounts`,
  `ItemCounts.longValue`, `GroupCodec.header/flag/string`, `GroupSearch.Source`, `Ui`) are copied too.
- Resources: `banks.tsv`, `item-drops.txt`, `quest-items.json`, `shopping-presets.json`.
- Tests: `ShoppingSyncTest`, `ShoppingCodecTest`, `ShoppingClient`, `ShoppingTabTextTest`, `ShoppingTabUiTest`,
  `ShopAllocationTest`, `ShopSourcesTest`, `ShopFoldsTest`, `ItemDropsTest`, `QuestDataTest`, `RequestsTest`,
  `ItemPathsTest`, `ItemPathsAlKharidTest`, `PathSessionTest`, `PathStopPlanTest`, `ShortestPathBridgeTest`, the
  `shoppingScene` of `GimPanelFixture`, and this contract's fixtures.

## Risks

- **Until Group Shopping ships**, a GIM user sees only the install card: no lists, requests, Path to, path card or
  shop alert. Their lists are safe in the cache and come back on install.
- **Mixed groups after Group Shopping ships**: members on new GIM without Group Shopping drop list payloads, so
  they neither see nor relay lists; members with it get everything on `joined`.
- **Request events** now depend on Group Shopping sending `event`; GIM refuses any not of this player's own.
- **Order**: Group Shopping should be on the Hub before (or with) the GIM update that removes the tab.
