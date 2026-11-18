# GIM Utilities server: design and phased build plan

Status: **plan for Board approval. Nothing here is built yet.** Task RUNELITE-117
(StayPoint `task-a72b5677`), written 2026-11-11.

The server is optional. It gives a group a way to sync that does not depend on
RuneLite Party, plus a web settings page, Discord bot settings and a group
dashboard. RuneLite Party stays as the fallback. Groups that don't turn the
server on see no change.

## 0. Constraints from the Board (spec interview, final)

| Q | A (Board, 2026-11-11) |
| --- | --- |
| Backend? | Cloudflare Workers + D1, free tier. Supabase was rejected because free projects pause after about 7 idle days. |
| Phase 1 scope? | All four: (1) group sync through our server, no Party; (2) web settings page; (3) Discord bot settings; (4) group web dashboard. |
| Party? | Kept as the fallback. Server sync is opt-in per group. |
| Timing? | Build now, on branches. Bump the Plugin Hub only after runelite/plugin-hub#20149 (GIM Utilities, under review) is accepted. |
| How separate? (15:40) | Completely. The server goes in its own new repo. The plugin side lives on its own long-lived branch. GIM Utilities `main` and the Hub PR stay untouched. |
| Merge eventually? (15:45 correction) | Yes. The branch merges into GIM Utilities once the Board has asked players whether they want it. Design for **zero player setup**, a **clean merge path** (regular rebases, opt-in flag), and include a **pitch** (§1). |

Out of scope: anything about players outside the group, and making Party
removal mandatory.

---

## 1. Pitch (for asking players on Discord and Reddit)

> **GIM Utilities can now keep your group synced without RuneLite Party.**
> Turn on one setting. Your group stays connected every time you log in, with
> no Join button and no party to lose when someone hops into a raid party. You
> also get a private web page for your group: every member's bank, the group
> storage log, who's online and on which world, and recent drops, levels and
> deaths. It works on your phone. You can change your group's GIM Utilities
> and Discord settings from that page or with a `/gimu` command in your
> Discord.
> **Setup:** if your group already uses GIM Utilities' group key, there is
> nothing to do but tick the box. **Privacy:** it is off unless you turn it
> on. Only your own group can see your group's data: there are no player
> lookups, no public pages and no search across groups. We store only what
> members choose to share (banks, storage, stats, world, events), under your
> RuneScape names. Live data and events are deleted after 30 days, and
> everything is deleted after 90 days without activity. Any member can delete
> the whole group's data with one button. Like every plugin that uses its own
> server, the Plugin Hub will show a "3rd-party server" warning.

Questions to ask players: would you turn this on? Is the dashboard or the
Discord control the bigger draw? Is 30/90-day retention fine?

---

## 2. Research (cited)

### 2.1 Cloudflare (developers.cloudflare.com, read 2026-11-11 via the Cloudflare docs search)

| Fact | Value on Workers Free | Source |
| --- | --- | --- |
| Worker requests | 100,000/day, resets 00:00 UTC. When exceeded: **Error 1027**, or fail-open per route | `/workers/platform/limits/#daily-requests` |
| Worker CPU | **10 ms per request** | `/workers/platform/limits/` (account plan limits table) |
| Subrequests | 50 external, 1,000 to Cloudflare services per invocation | `/workers/platform/limits/#subrequests`; changelog 2026-02-11 |
| Static asset requests | Free and unlimited | `/workers/platform/pricing/` footnote 3 |
| WebSocket via Worker | The upgrade is 1 request; "WebSocket messages routed through a Worker do not count as requests" | `/workers/platform/pricing/` footnote 2 |
| D1 rows read | 5 million/day | `/d1/platform/pricing/` |
| D1 rows written | **100,000/day**. Deletes count as writes; each index on a written column adds a row | `/d1/platform/pricing/` notes 6 |
| D1 storage | 5 GB total, 10 databases | `/d1/platform/pricing/` |
| D1 enforcement | **Since 2026-09-01, queries fail** once the daily row limit is hit, until 00:00 UTC | changelog `2026-09-01-d1-free-tier-limit-enforcement` |
| Durable Objects | Free plan, **SQLite-backed only**. 100,000 requests/day, 13,000 GB-s/day | `/durable-objects/platform/pricing/` |
| DO WebSocket billing | Incoming messages billed **20:1**; outgoing messages and protocol pings are free. Hibernation avoids duration charges while idle. `setWebSocketAutoResponse` costs no wall time | same page, footnotes 2–3 |
| DO SQLite storage | Same row meters as D1 (5 M read, 100 k written/day) | same page, "SQLite storage backend" |
| Rate limiting binding | `ratelimits` binding, GA 2025-09-19. `simple.period` must be 10 or 60. Limits are **per Cloudflare location and approximate** | `/workers/runtime-apis/bindings/rate-limit/`; changelog `2025-09-19-ratelimit-workers-ga` |
| Web Crypto | Ed25519 `verify`, HKDF, HMAC, SHA-256, and `crypto.subtle.timingSafeEqual` | `/workers/runtime-apis/web-crypto/` |
| Secrets | `wrangler secret put NAME`; `.dev.vars` only applies locally | `github.com/discord/cloudflare-sample-app` README |

### 2.2 Discord (docs.discord.com, read 2026-11-11)

- **Interactions endpoint** (`/developers/interactions/overview`): set an
  *Interactions Endpoint URL* in the app settings. Discord POSTs each
  interaction there, so the bot needs no gateway connection. Each request
  carries `X-Signature-Ed25519` and `X-Signature-Timestamp`. A request that
  fails validation must get **401**. A `PING` (`type: 1`) is answered with
  `200` and `{type: 1}` (PONG).
- **Deadlines** (`/developers/interactions/receiving-and-responding`): the
  initial response is due **within 3 seconds**, or the token is invalidated.
  Tokens stay valid **15 minutes** for followups. Type **5**
  (`DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE`) acknowledges now and edits later.
- **Permissions** (`/developers/interactions/application-commands`):
  `default_member_permissions` sets the permissions a user needs to run a
  command. `"0"` hides the command from everyone except admins and explicit
  overwrites. "Members with the Administrator permission can use all
  commands." The `applications.commands` scope works without the `bot` scope.
  Commands "do not depend on a bot user in the guild".
- **Worked example**: `github.com/discord/cloudflare-sample-app`, a bot on
  Workers that sets its secrets with `wrangler secret put DISCORD_TOKEN`,
  `DISCORD_PUBLIC_KEY` and `DISCORD_APPLICATION_ID`.

### 2.3 RuneLite and the Hub

- `AGENTS.md` (this repo) §HTTP & JSON: "`@Inject OkHttpClient` to get the HTTP
  client. Do not use `HttpURLConnection`, `java.net.http.HttpClient`, or Apache
  HttpClient." Data & Privacy: "No exposing player information over HTTP" and
  "No crowdsourcing data about other players".
- `PartyService` and `WSClient` (runelite-client, read through this plugin's
  use; see the code map): `changeParty(passphrase)` and `send(...)`. Payloads
  are Gson JSON in a protobuf `Party.Data`. `GroupCodec`'s javadoc records what
  was read for RuneLite 1.12.39.
- **Hub `warning=` field.** `runelite/plugin-hub/plugins/group-ironmen-tracker`
  carries: *"This plugin submits your IP address, and may submit various
  account data, to a 3rd party server not controlled or verified by the
  RuneLite Developers."* (fetched 2026-11-11, commit `343af959…`). BRIEF #18
  already notes that the field belongs in the Hub manifest, not
  `runelite-plugin.properties`.
- **Precedent: Group Ironmen Tracker** (`christoabrown/group-ironmen-tracker`,
  21,557 installs per the Board's compliance session). Its README says: "Just
  one person will go to the site and create a group, the credentials it
  provides is shared with your group". It tracks "Inventory, equipment, bank,
  rune pouch, seed vault, and shared bank", plus skills, world position, HP,
  prayer, energy, world, quests, diaries and the collection log. Its server
  (`christoabrown/group-ironmen`, BSD-2) uses Postgres and is self-hostable
  with docker-compose. Its auth scheme isn't documented in either README, so
  ours is designed from first principles (§4.2), not copied.

---

## 3. Code map: the plugin today (main @ `a9f1e3b`)

All paths are under `src/main/java/com/vinnyvangogh/groupironman/`.

| Concern | Where | What the server branch does with it |
| --- | --- | --- |
| Party message type registered | `GroupIronmanPlugin.java:108` `wsClient.registerMessage(VvgGroupIronmanMessage.class)`, `:172` unregister | Unchanged. Party stays the fallback |
| Party state | `GroupIronmanPlugin.java:197` `onPartyChanged` → `GroupService.java:890` | Unchanged |
| Party adapter | `GroupService.java:237-262` `startUp` builds `GroupSync.Party` over `partyService` | Pattern copied for a `GroupTransport` seam (S-P1) |
| Every send | `GroupService.java:325, 681, 870, 877, 1106, 1139, 1165, 1182, 1232` `partyService.send(new VvgGroupIronmanMessage(payload))` | Routed through one `GroupTransport.send(payload)`. This is the only edit to existing send sites |
| Receive, sender check | `GroupService.java:907-930` `sender()`: drops own echo, non-group party, unnamed member, off-roster name (`GroupMembers.check`) | Server receive path calls the same check, with the name the **server** vouches for (the member token's RSN) |
| Receive handler | `GroupService.java:932` `onVvgGroupIronmanMessage` | Body split into `receive(String name, String payload)` and shared by both transports |
| Wire format | `GroupCodec.java:39+`: `PROTOCOL = 1`, `MAX_PAYLOAD_CHARS = 6000`, `{"v","k",...,"ts"}`; `decode` at `:384` tolerates `v >= 1` and unknown kinds | **Reused verbatim.** The server stores and relays codec strings, so one decoder serves both paths |
| Outbox switches | `GroupOutbox.java:19-48` (bank/storage/stats/carried/extras/drops), `:105` group events | Same switches gate the server path. Nothing extra is shared |
| Group key | `GroupKey.java:25` 32 hex (128-bit), `:38` `SecureRandom`, `:72` share line `[GIMU] <key>`, `:124` passphrase `gimu:<group>:<key>` | **The server credential is derived from this key** (§4.2), so a group that already has a key needs no new setup |
| Key distribution | `GroupService.java:552-593`: a `[GIMU]` line in `CLAN_GIM_CHAT` is saved by every member's client. `:619` hides it from the chatbox | Reused as is. This is the zero-setup channel |
| Roster | `GroupService.java:788-812` `readRoster()` from `ClanSettings(ClanID.GROUP_IRONMAN)`, channel fallback | Sent to the server on join as the claimed roster (§4.3) |
| Sync state and header | `GroupSync.java:32-47` states; `SyncHeader.java:52-85` | New states `SERVER_CONNECTED`, `SERVER_DEGRADED` (falls back to Party) |
| HTTP precedent | `DiscordSender.java:196-230` `okHttp(OkHttpClient http, Gson gson)` with `newCall(...).enqueue` | The server client copies this: injected client, `enqueue`, never the client thread |
| Local files | `GroupService.java:1388-1450` cache through RuneLite `Filepath` (main `6c7036a`, PR #26) | The offline queue persists in the same cache. No new file I/O |
| Config | `GroupIronmanConfig.java:29` `GROUP = "gimutilities"` (frozen), `:51` `groupSync`, `:48` `KEY_GROUP_KEY` | New keys (frozen once published): `serverSync` (bool, default **false**, with the 3rd-party warning text), `serverAllowRemoteSettings` (bool, default false) |
| Group roles and synced settings | **Open PR #19** (`staypoint/task-913c851d`), BRIEF #21 "Shared settings, signed roles" | The server settings model **adopts #19's roles** (leader, editors). If #19 changes, §4.6 follows it |
| Tests | `src/test/.../GroupSyncTest`, `GroupCodecTest`, `GroupKeyTest`, `GroupMembersTest`, `DiscordSenderTest`, `FakeDiscordTest` | `FakeDiscordTest` is the model for a `FakeGimServerTest` (an OkHttp `MockWebServer`-style fake, test scope only) |

---

## 4. Design

### 4.1 Two repos, one long-lived branch

- **New repo `gim-utilities-server`** (proposed name; under
  `VinnyVanGogh/`). It holds the Worker (TypeScript), D1 migrations, the
  Durable Object, static web assets (dashboard and settings) and the Discord
  command registration script. **Nothing server-side lands in the plugin
  repo.**
- **Plugin branch `server-sync`** in `group-ironman-plugin`, cut from `main`.
  - Each build task is its own `staypoint/task-*` branch and PR **into
    `server-sync`**. *Blocker to clear first:* StayPoint's `open_pr` only
    allows `main` as a base today. The Board either allows `server-sync` as a
    base for these tasks, or they open as drafts titled "→ server-sync" that
    a human retargets.
  - **Tracking main without merging it:** after each `main` merge, rebase
    `server-sync` onto `main` (`git rebase origin/main`, force-with-lease on
    `server-sync` only, done by the Board or a Board-approved run). This keeps
    the eventual merge a fast-forward-sized diff.
  - **Diff budget, so rebases stay cheap:** existing files get only the
    `GroupTransport` seam (§3: the send sites, the receive split, two config
    keys, header states). Everything else lives in new classes (`GimServer*`).
  - **Merging is a no-op for non-opted groups:** with `serverSync=false` the
    plugin never builds a server client and makes no request. A test asserts
    zero HTTP calls with the flag off (T-P1).

### 4.2 Identity and auth: zero setup from the existing group key

The 128-bit group key (`GroupKey`) already reaches every member through GIM
chat. The server never sees the key itself. The client derives values from it
with HKDF-SHA256 (`javax.crypto.Mac`, standard JDK, no reflection):

```
groupId   = base32( HKDF(key, info="gimu/v1/id")[0..15] )      // public-ish handle, unguessable
groupAuth = HKDF(key, info="gimu/v1/auth")                       // 32-byte secret proof
```

- **The server stores** `groupId` and `HMAC(PEPPER, groupAuth)`. `PEPPER` is a
  wrangler secret, so a leaked D1 dump can't be replayed against the API.
- **Join, and create-on-first-join:**
  `POST /v1/groups/{groupId}/join {groupAuth, rsn, roster[], client:"gimu/<ver>"}`.
  - If the group doesn't exist, the first opted-in member creates it (zero
    setup) and becomes `leader` (adopting PR #19's roles).
  - Otherwise `groupAuth` is checked in constant time
    (`crypto.subtle.timingSafeEqual`).
  - The response is a **member token**: 32 random bytes. Only its SHA-256 is
    stored. Every later request sends `Authorization: Bearer <token>`.
- **No key yet?** The existing "Create group key" button plus one paste of the
  share line into GIM chat. That is today's flow, unchanged, and still the
  only manual step. An optional "one-click link" (§4.7) is a dashboard
  convenience, not a requirement.
- **Rotation:** "New key" (existing, `SyncHeader.java:20`) also calls
  `POST /v1/groups/{groupId}/rotate {newGroupId, newGroupAuth}` with a leader
  token. The server moves the group to the new id and **revokes every member
  token**. Members re-join automatically once their client picks up the new
  share line from GIM chat.

### 4.3 Members, roster and forged members

- A member row is `(group, rsn_key, token_hash, role, first_seen, last_seen, revoked_at)`.
  `rsn_key` is the normalised name (`GroupMembers.key`).
- **The roster is a quorum, not trust in one client.** Each join reports
  `roster[]` from `ClanSettings`. An RSN can join only if it appears on the
  roster reported by **the creating member or at least one other active
  member**. The first member is accepted alone, which is unavoidable: the
  key holder is the group.
- **One live token per RSN.** A second join for the same RSN replaces the
  token and notifies that member's client, which shows "Signed in elsewhere".
  So a forger with the key can't silently duplicate a member.
- **Removed member:** when two or more members' reported rosters stop listing
  an RSN, the server revokes that RSN's token and hides its data from reads.
  The data is deleted at the next retention sweep. The panel suggests "New
  key", because the removed player still knows the old key. A new key goes
  only to GIM chat, which the removed player is no longer in.

### 4.4 API surface (v1)

Base `https://gimu.<board-domain>/v1` (a `workers.dev` URL until a domain is
chosen). The base URL is a **constant in the plugin**, not a setting: no
user-entered URLs.

| Method and route | Auth | Body cap | Purpose |
| --- | --- | --- | --- |
| `POST /groups/{gid}/join` | groupAuth | 8 KB | Create-or-join, returns member token |
| `POST /groups/{gid}/rotate` | token (leader) | 1 KB | New key: move the group and revoke all tokens |
| `POST /groups/{gid}/push` | token | **64 KB** | Batch of codec payloads `[{seq, payload}]`, each ≤ `MAX_PAYLOAD_CHARS` (6,000) |
| `GET /groups/{gid}/pull?since=<cursor>` | token | none | Others' payloads since the cursor (the polling path) |
| `GET /groups/{gid}/live` (WebSocket upgrade) | token | 64 KB per message | Live path through the group's Durable Object (§4.5) |
| `GET /groups/{gid}/settings` | token | none | Group settings and this member's overrides, with an ETag |
| `PUT /groups/{gid}/settings` | session (role-checked) | 16 KB | Web or bot edits (§4.6) |
| `POST /groups/{gid}/session` | groupAuth *or* a one-time login code | 1 KB | Web session: HttpOnly, Secure, SameSite=Strict cookie, 12 h |
| `POST /groups/{gid}/login-code` | token | none | Short code, 10 min, single use, for "Open my settings page" |
| `POST /groups/{gid}/discord-link-code` | token (leader or editor) | none | Code for `/gimu link` (§4.8) |
| `DELETE /groups/{gid}` | session or token, **any member**, plus typed confirmation | none | Delete the group's data (§4.10) |
| `POST /discord/interactions` | Ed25519 signature | 16 KB | Discord bot |
| Static `/`, `/g/` | none (the page itself holds no data) | none | Dashboard and settings SPA. Data loads only after a session exists |

Rules that apply to every route:

- **Versioning.** The path prefix is `/v1`. Payloads keep `GroupCodec`'s own
  `v`. The server **stores payloads opaquely** after validation: it parses the
  JSON, requires `v >= 1` and a known `k`, and checks size and list caps
  mirroring `GroupCodec`'s `MAX_*`. So new kinds need no server deploy. Sending
  `426` with `minClient` lets the server retire old plugin versions.
- **Replays.** Every push item carries a per-member monotonically increasing
  `seq`. The server drops any `seq <= last_seq` for that member. Snapshots
  (`bank`, `stats`, `carried`, `extras`) are last-write-wins by the payload's
  own `ts`, so a replayed old snapshot never overwrites a newer one. TLS
  covers transit. A captured token is handled like a leaked key (§6).
- **Errors.** JSON bodies `{error, retryAfter?}`. `429` and `503` always carry
  `Retry-After`.

### 4.5 Live updates: Durable Object WebSocket, with polling as the fallback

The budget is the deciding factor. Assume a member online about 4 h/day who
pushes about 1 batch/min:

| Path | Worker req/member/day | Members that fit in 100 k/day |
| --- | --- | --- |
| HTTP push 1/min + poll 1/min | ~480 | **~200 (about 50 groups)** |
| DO WebSocket (1 upgrade + 240 incoming msgs ÷ 20) | 1 Worker req + ~13 DO req | **thousands.** D1 writes become the limit (below) |

- **One Durable Object per group** (`GroupHub`, SQLite-backed as the free plan
  requires). It holds members' hibernatable WebSockets, fans each incoming
  payload out to the other members (outgoing messages are free), and keeps
  the latest snapshot per member and kind in memory and in DO storage.
- **Pings:** `setWebSocketAutoResponse("ping","pong")`, so keepalives cost
  nothing.
- **D1 is the durable store, written in coalesced batches.** At most one
  snapshot row per member and kind every **5 min** (an alarm flush, plus a
  flush on disconnect). Events are appended as they come, capped per group.
  Estimate: 4 members × 4 kinds × 12 flushes/h × 4 h ≈ 770 rows plus ≈ 100
  events, about **900 rows written/group/day**, so about **110 active groups**
  before D1's 100 k writes/day. *Unverified:* whether DO SQLite writes share
  D1's 100 k meter or have their own. Keep DO storage writes to the same
  coalesced cadence, and measure in S1.
- **HTTP polling** (`/pull`, 60 s, with the cursor) stays as the fallback for
  networks that block WebSockets, and is what S1 ships first.

### 4.6 D1 schema (migration `0001_init.sql`)

```sql
CREATE TABLE groups (
  id            TEXT PRIMARY KEY,          -- groupId (HKDF)
  auth_hmac     BLOB NOT NULL,             -- HMAC(PEPPER, groupAuth)
  created_at    INTEGER NOT NULL,
  last_active   INTEGER NOT NULL,
  disabled      INTEGER NOT NULL DEFAULT 0,-- kill switch per group
  quota_day     TEXT,                      -- 'YYYY-MM-DD' UTC
  quota_writes  INTEGER NOT NULL DEFAULT 0,
  quota_reqs    INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE members (
  group_id    TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  rsn_key     TEXT NOT NULL,               -- normalised RSN (personal data)
  rsn_display TEXT NOT NULL,
  token_hash  BLOB,                        -- SHA-256(member token)
  role        TEXT NOT NULL DEFAULT 'member', -- leader | editor | member (PR #19)
  last_seq    INTEGER NOT NULL DEFAULT 0,
  roster_seen TEXT,                        -- JSON list of HMAC(groupId, rsn_key) from this member's last
                                           -- reported roster: quorum check (§4.3) without storing
                                           -- names of members who have not opted in
  first_seen  INTEGER NOT NULL, last_seen INTEGER NOT NULL,
  revoked_at  INTEGER,
  PRIMARY KEY (group_id, rsn_key)
);
CREATE UNIQUE INDEX members_token ON members(token_hash);
CREATE TABLE snapshots (                   -- latest only, one row per member+kind
  group_id TEXT NOT NULL, rsn_key TEXT NOT NULL, kind TEXT NOT NULL, -- bank|stats|carried|extras|storage_state
  ts INTEGER NOT NULL, payload TEXT NOT NULL,                        -- codec JSON, <= 64 KB (bank parts joined)
  PRIMARY KEY (group_id, rsn_key, kind),
  FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);
CREATE TABLE events (                      -- drops, clog, storage visits, group events
  group_id TEXT NOT NULL, id INTEGER NOT NULL, rsn_key TEXT NOT NULL,
  ts INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL,
  PRIMARY KEY (group_id, id),
  FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);
CREATE INDEX events_ts ON events(group_id, ts);
CREATE TABLE settings (                    -- group doc + per-member overrides
  group_id TEXT NOT NULL, scope TEXT NOT NULL,  -- 'group' | 'member:<rsn_key>'
  doc TEXT NOT NULL, version INTEGER NOT NULL, updated_by TEXT, updated_at INTEGER NOT NULL,
  PRIMARY KEY (group_id, scope),
  FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);
CREATE TABLE discord_links (
  guild_id TEXT PRIMARY KEY, group_id TEXT NOT NULL UNIQUE REFERENCES groups(id) ON DELETE CASCADE,
  channel_id TEXT, editor_role_id TEXT, linked_by TEXT, linked_at INTEGER NOT NULL
);
CREATE TABLE codes (                       -- login + discord-link codes, single use
  code_hash BLOB PRIMARY KEY, group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  rsn_key TEXT, purpose TEXT NOT NULL, expires_at INTEGER NOT NULL
);
```

D1 needs `PRAGMA foreign_keys = ON` for the cascades. **Verify this in S1.**
If it isn't honoured, delete the group's rows table by table in one `batch()`.

**Retention** (a daily Cron Trigger; the free plan allows 5 per account):

| Data | Kept for |
| --- | --- |
| Events | 30 days |
| Snapshots | Until replaced, or 30 days after a member's `last_seen` |
| Revoked members | Deleted at the next sweep |
| Whole group | Deleted after **90 days** with no member activity |
| Codes | Deleted on use or expiry |

The sweep deletes in bounded batches (≤ 5,000 rows/run), because deletes
count as writes.

### 4.7 Plugin behaviour: server vs Party

- **Opt-in.** `serverSync` (default off). Its description carries the
  AGENTS.md third-party warning. While it's off, the code path is identical
  to `main`.
- **Transport seam.** `GroupTransport { void send(String payload); }` with
  `PartyTransport` (today's code) and `ServerTransport`. Received payloads go
  through one `receive(name, payload)`. For the server path, `name` is the
  RSN the server attached, which then passes the same
  `GroupMembers.check(name)` against the local roster: defence in depth.
- **Which transport, in order:**
  1. Server healthy: send through the server only.
  2. Server unreachable, rate-limited or quota-exhausted (any non-2xx, 1027,
     or a socket failure) and the member is in the group's Party: send through
     Party (today's behaviour) and queue for the server.
  3. Neither: queue.

  Receiving accepts both. **Duplicates resolve on `(sender, kind, ts)`.**
  Snapshots are newest-`ts`-wins; events dedupe on `(sender, ts, t)`.
- **Migration from Party** needs no step. Members who tick the box move to
  the server, and members who haven't are still reached over Party. A mixed
  group works because every opted-in client still listens on Party when it's
  in the group party.
- **Offline queue.** Snapshots coalesce to the latest per kind; events are
  capped at 200. The queue persists in the existing `Filepath` group cache.
  Backoff is exponential (1 s → 5 min) and honours `Retry-After`.
- **Threading.** Only `enqueue` on the injected `OkHttpClient`; the WebSocket
  comes from the same client (`newWebSocket`). Callbacks hand results to
  `clientThread.invokeLater` before touching game state. No new threads or
  executors beyond RuneLite's injected `ScheduledExecutorService`.
- **No-click connect.** The Party path needs a Join click every session
  (`SyncHeader.java:15`; RuneLite won't let plugins rejoin parties). The
  server path connects on login once `serverSync` is on. That is a network
  call, not a game action or a party join, so BRIEF's "joining a party
  without a click" rule doesn't apply. Recorded in BRIEF #22.
- **Remote settings** are applied only if the member ticked
  `serverAllowRemoteSettings`. The plugin pulls the settings doc (with an
  ETag, on login and every 10 min, or pushed over the live socket) and writes
  allowed keys through `ConfigManager.setConfiguration(GROUP, key, value)`.
  - **Allowlist:** event toggles, overlay toggles, thresholds, quiet hours,
    combine minutes, poster style.
  - **Never remote:** `serverSync`, `serverAllowRemoteSettings`, `groupSync`,
    the group key, and **every webhook URL** (those are secrets and are never
    sent to the server).

### 4.8 Discord bot (HTTP interactions on the same Worker)

- **Install** with the `applications.commands` scope only. No bot user is
  needed: settings are edited, nothing is posted. Posting stays on the
  member's own webhooks.
- **Commands** (all under `/gimu`, `default_member_permissions = MANAGE_GUILD`,
  so only server managers and admins see them by default):
  - `/gimu link code:<code>`: binds this guild to the group with a code the
    plugin or web page made (`discord-link-code`, 10 min, single use, leader
    or editor only). One guild maps to one group.
  - `/gimu settings`: an ephemeral message with select menus and buttons for
    the allowlisted group settings. Each component interaction re-checks the
    caller's permissions and that the guild is linked.
  - `/gimu editors role:<role>`: optionally lets a role edit (stored
    `editor_role_id`). Otherwise only Manage Server can edit.
  - `/gimu unlink`
- **Verification:** `crypto.subtle.verify("Ed25519", DISCORD_PUBLIC_KEY, sig, timestamp + body)`.
  On failure → `401`. The timestamp must be within 5 min (replay guard).
  `PING` → `{type:1}`. Anything that might pass 3 s → type 5, then a followup
  within 15 min.
- **Secrets:** `DISCORD_PUBLIC_KEY`, `DISCORD_APPLICATION_ID`, `DISCORD_TOKEN`
  (only used to register commands, from a script) via `wrangler secret put`.

### 4.9 Web pages (Workers static assets, mobile-first)

- One small SPA (no framework needed; plain TS + CSS) at `/g/`. Static asset
  requests are free.
- **Login, two routes:**
  - **(a) Group link** `https://…/g/#<base32 key>`. The key stays in the
    **URL fragment**, which browsers never send to servers or logs. The page
    derives `groupId` and `groupAuth` in WebCrypto (HKDF), then calls
    `/session`. The plugin's "Open dashboard" button builds this link.
  - **(b) Member login code** from "Open my settings page" in the plugin. This
    gives a *member-scoped* session, which is required to edit that member's
    own overrides and for role-checked group edits.
  - Discord OAuth is **deferred** (phase S6, optional). It isn't needed for
    zero setup and would add PII (Discord ids).
- **Dashboard:** members (online/world/last seen), each bank (search), group
  storage and its log, recent events. It reads only the session's group.
- **Settings:** checkboxes and dropdowns generated from one schema shared with
  the Worker (`settings-schema.json`). It mirrors the allowlist in §4.7 and
  the config keys in `GroupIronmanConfig.java`.
- **Headers:** `Cache-Control: no-store` on every API response;
  `X-Robots-Tag: noindex` and `<meta name="robots" content="noindex">`; a
  strict CSP (`default-src 'self'`); no third-party scripts or analytics.

### 4.10 Delete my group's data

- Any member (token or member session) can call `DELETE /groups/{gid}` and
  type the group name to confirm. The group's DO wipes its storage
  (`deleteAll`), its D1 rows go in one `batch()`, and its Discord link is
  removed. The server returns a count, and every connected client gets a
  `deleted` frame and turns `serverSync` off locally.
- Deleting does **not** revoke the key: re-opting in creates a fresh, empty
  group, which is the expected behaviour.

### 4.11 Abuse, DoS and free-tier exhaustion

Order of defences:

1. **Per-IP `ratelimits` binding** on unauthenticated routes (`join`,
   `session`, `interactions`): 10 per 60 s. It is approximate and
   per-location, so it's a speed bump, not a guarantee.
2. **Per-token limit** (`ratelimits`, keyed by `gid:rsn`): 30 pushes per 60 s.
3. **Per-group daily quota.** Held in the DO and mirrored in
   `groups.quota_*`: **2,000 Worker requests and 3,000 D1 row writes per group
   per UTC day.** Past it → `429 Retry-After: <until 00:00 UTC>`, and clients
   fall back to Party. One group can't take more than ~3% of the account.
4. **Account guard.** If the D1 daily-limit error appears (§2.1), the Worker
   stops all D1 writes for the day and serves from DO memory. Clients see
   `503` and fall back to Party.
5. **Kill switches.**
   - `SYNC_ENABLED` var: global off, clients fall back to Party.
   - `groups.disabled`: one group off.
   - Route fail mode **closed** (Cloudflare returns 1027, which the client
     treats as unreachable).
6. **Caps.** Body caps per route (§4.4), at most 8 members per group (GIM max
   is 5; headroom for renames), at most 1 DO per group, events capped per
   group per day (500).

### 4.12 Secrets (all through `wrangler secret put`, none in the repo)

| Secret | Use |
| --- | --- |
| `PEPPER` | HMAC on stored `groupAuth` |
| `SESSION_KEY` | Signs session cookies |
| `CODE_KEY` | HMAC for stored login and link codes |
| `DISCORD_PUBLIC_KEY`, `DISCORD_APPLICATION_ID`, `DISCORD_TOKEN` | Discord bot |

`.dev.vars` is git-ignored. CI deploys with a scoped Cloudflare API token
stored as a GitHub secret. **Deploys are a Board action** (they are prod
writes).

### 4.13 Privacy

- **Stored:** RSNs of opted-in members (personal data), their banks, storage
  state and log, stats, world and position, carried items, events, settings,
  and the Discord guild, channel and role ids if linked.
- **Not stored:** IP addresses (none persisted; Cloudflare's own logs aside),
  webhook URLs, the group key, Jagex or RuneLite account data, and anything
  about non-members. Each member's reported roster is kept only as keyed
  hashes (`roster_seen`), so group members who have not opted in are never
  stored by name.
- **Who can see it:** holders of a valid session or token for that one group.
  There are no lookup-by-name routes, no cross-group queries and no public
  pages. Server operators can read D1; the README and pitch say so.
- **How long:** see §4.6. Deletion is available on demand (§4.10).
- **Where:** D1's default location. The `us` jurisdiction option exists
  (changelog 2026-10-02) if the Board wants it.

---

## 5. Verified assumptions

| # | Assumption | How checked | Status |
| --- | --- | --- | --- |
| A1 | Free tier: 100 k Worker req/day, 10 ms CPU | CF limits page | Verified |
| A2 | D1 free: 100 k writes/day, hard-fails since 2026-09-01 | CF pricing and changelog | Verified |
| A3 | DOs on free (SQLite), WS incoming 20:1, outgoing free | CF DO pricing | Verified |
| A4 | Workers can verify Ed25519 natively | CF Web Crypto table | Verified |
| A5 | The Discord bot needs no gateway; signature headers; 3 s / 15 min | Discord docs | Verified (algorithm named only by header and sample) |
| A6 | Existing key is 128-bit, auto-distributed via GIM chat | `GroupKey.java:25,38`; `GroupService.java:552-593` | Verified |
| A7 | Codec payloads carry `v`, `k`, `ts` and tolerate unknown kinds | `GroupCodec.java:199,219,306,384-425,530` | Verified |
| A8 | HKDF/HMAC available to the plugin without new deps | `javax.crypto.Mac` is JDK standard | Verified by knowledge; **compile it in S-P1** |
| A9 | 10 ms CPU fits HKDF + HMAC + JSON for 64 KB | Not measured | **Unverified**: measure in S1 with `wrangler dev` and the CPU metric |
| A10 | D1 honours `ON DELETE CASCADE` | Not checked | **Unverified**: S1 test; fallback in §4.6 |
| A11 | DO SQLite writes share D1's 100 k meter | Docs give the same numbers, not whether they are pooled | **Unverified**: designed as if pooled |
| A12 | Hub reviewers accept auto-connect HTTP sync with a `warning=` | Precedent: group-ironmen-tracker manifest | Verified for precedent; the review outcome is open |
| A13 | RuneLite's `OkHttpClient` supports `newWebSocket` | OkHttp API; `WSClient` itself uses OkHttp WebSocket (`GroupCodec` javadoc) | Verified |
| A14 | Group Ironmen Tracker's auth scheme | Both READMEs read; not documented | **Unknown**: not relied on |

## 6. Threat model

| Threat | Impact | Mitigation | Residual |
| --- | --- | --- | --- |
| **Leaked group key** | Attacker can join as an RSN on the roster and read the group | Quorum roster (§4.3); one token per RSN, so the real member is told "signed in elsewhere"; "New key" rotates and revokes all tokens; the new key spreads only via GIM chat | Window until rotation. Same as today's Party key |
| **Member removed from the group** | Old member keeps reading | Roster-drop revocation (2+ members' rosters); the panel prompts "New key"; data hidden, then deleted | They saw data while they were a member |
| **Forged member** (key holder claims another RSN) | Fake data under a real name | The RSN must be on a quorum roster; one live token per RSN; receivers re-check `GroupMembers.check` | A key holder can still impersonate an RSN until that member logs in. Accepted: key holders are the group |
| **Replayed payload** | Stale overwrite or duplicate events | `seq` per member; snapshots newest-`ts`-wins; event dedupe; Discord timestamp window | None material |
| **Group-key brute force** | Take over a group | 128-bit key: infeasible. `groupId` is not derived from the name; per-IP rate limit on `join` and `session`; constant-time compare; peppered storage | None material |
| **Dashboard public by mistake** | Group data exposed | No public mode exists; every read needs a session; the key lives only in the fragment; `noindex`, `no-store`; no list or search routes | A member pasting the link publicly = leaked key → rotate |
| **Discord user without permission changes settings** | Unwanted settings | `default_member_permissions=MANAGE_GUILD`; server-side re-check of member permissions and `editor_role_id` on **every** component interaction (never trust the hidden command alone); guild ↔ group link needs a leader or editor code; settings allowlist excludes secrets and opt-ins | A guild admin can always edit. That's intended |
| **Free-tier exhaustion by one group** | Everyone loses sync | Per-group daily quota (§4.11), per-token and per-IP limits, D1 guard, clients fall back to Party | A distributed attacker can still burn the 100 k Worker requests. Fail-closed plus Party fallback limits the damage; the paid plan ($5/mo) is the escape hatch, a Board decision |
| Stolen member token (e.g. from the RuneLite config sync) | Read and write as that member | Tokens are stored in RuneLite config like the key today; revoked on rotation; one per RSN | Same exposure class as the existing key |
| Malicious payload rendered on the dashboard (XSS) | Session theft | Server validates codec JSON shape; the SPA renders with `textContent` only; strict CSP | Low |

## 7. Tests, adversarial first

**Server (`gim-utilities-server`, vitest + `@cloudflare/vitest-pool-workers`):**

1. T-S1: `join` with the wrong `groupAuth` → 401, constant-time path, rate
   limit triggers after 10/min from one IP.
2. T-S2: a forged RSN not on any active member's roster → 403; the first
   member alone → 201 + leader.
3. T-S3: a second join for the same RSN revokes the first token (the old token
   → 401 on `push`).
4. T-S4: replay. The same `seq` twice → second dropped; an older `ts`
   snapshot after a newer one → ignored.
5. T-S5: oversize body (64 KB + 1) → 413; payload with `v=0`, unknown `k`, or
   a list over its cap → 422.
6. T-S6: cross-group read. Group A's token on group B's routes → 404 (not
   403, so groupIds aren't confirmed).
7. T-S7: per-group quota exhaustion → 429 with `Retry-After` to 00:00 UTC;
   other groups unaffected.
8. T-S8: D1 daily-limit error simulated → writes stop, reads served, 503 to
   pushes.
9. T-S9: Discord bad signature → 401; stale timestamp → 401; `PING` → PONG;
   a component click from a member without Manage Server or the editor role →
   ephemeral refusal and no change.
10. T-S10: `rotate` by a non-leader → 403; by the leader → all tokens revoked.
11. T-S11: `DELETE` wipes every table for that group only (row counts before
    and after for two groups).
12. T-S12: the retention sweep deletes ≤ 5,000 rows/run and only expired rows.
13. T-S13: the settings `PUT` rejects non-allowlisted keys (webhook URL,
    `serverSync`) → 422.
14. T-S14: a session cookie for group A can't read group B; a login code works
    once only and expires after 10 min.
15. Then the happy paths: join → push → pull/live fan-out; settings round
    trip; dashboard reads.

**Plugin (`server-sync` branch, JUnit, `FakeGimServer` on a local socket in test scope):**

1. T-P1: `serverSync=false` → **zero** HTTP requests and identical Party
   behaviour (guards the merge).
2. T-P2: server 500, 1027 or timeout → falls back to Party while in the group
   party, queues otherwise; backoff honours `Retry-After`.
3. T-P3: a payload from the server whose RSN isn't on the local roster →
   dropped (`GroupMembers.check`).
4. T-P4: the same event over Party and server → recorded once.
5. T-P5: a remote setting for a non-allowlisted key, or with
   `serverAllowRemoteSettings=false` → not applied.
6. T-P6: the offline queue is capped (200 events, latest snapshot per kind)
   and survives a restart through the `Filepath` cache.
7. T-P7: no network call on the client thread (assert via a fake executor and
   that only `enqueue` is used).
8. T-P8: HKDF vectors give identical `groupId` and `groupAuth` in Java and in
   the TS Worker and SPA (a shared fixture file).
9. Then the happy path: join, push, receive, the header shows
   `SERVER_CONNECTED`.

**In-game (Board, two accounts; never automated):** tick the box on both,
log in, see the other's bank without pressing Join, then untick and confirm
the Party path still works.

## 8. Phased build tasks (each ships alone, behind the opt-in)

Server tasks live in `gim-utilities-server`. Plugin tasks are PRs into
`server-sync`.

| Phase | Repo | Ships | Depends on | Done when |
| --- | --- | --- | --- | --- |
| **S0** | server | Repo scaffold: wrangler config (D1, DO SQLite class, `ratelimits`, cron, static assets), `0001_init.sql`, CI (typecheck, vitest), README with the privacy section | Board approves this plan, repo name | `wrangler dev` serves `/v1/health`; CI green |
| **S1** | server | Auth and sync over HTTP: `join`, `rotate`, `push`, `pull`, `DELETE`, quotas, rate limits, retention cron. T-S1…S8, S10–S12. Measures A9–A11 | S0 | Tests pass; CPU per request measured and recorded |
| **S-P1** | plugin | `GroupTransport` seam + `PartyTransport`. **No behaviour change** (T-P1 on today's path) | none | All existing tests pass; diff touches only the send and receive sites |
| **S-P2** | plugin | `ServerTransport` over HTTP: config keys, HKDF, join, push, poll, fallback, offline queue, header states. T-P2–P4, P6–P8 | S1, S-P1 | Board two-account in-game check over HTTP |
| **S2** | server | `GroupHub` DO WebSocket live path with hibernation, auto-response pings and coalesced D1 flush | S1 | Load test: 5 sockets × 1 msg/min for 1 h within the quotas |
| **S-P3** | plugin | Live socket via `OkHttpClient.newWebSocket`, polling fallback | S2, S-P2 | In-game check: updates appear within seconds |
| **S3** | server | Web dashboard (read-only): fragment-key login, sessions, members, banks, storage, events, mobile layout. T-S6, T-S14 | S1 | Board opens it on a phone |
| **S4** | server | Settings page + settings API + schema. T-S13 | S3 | Round trip of every allowlisted key |
| **S-P4** | plugin | Remote settings apply (allowlist, opt-in), "Open dashboard" and "Open my settings page" buttons. T-P5 | S4, S-P2 | In-game: a web change shows up in the plugin config |
| **S5** | server | Discord bot: interactions endpoint, `/gimu link`, `settings`, `editors`, `unlink`, command registration script. T-S9 | S4 | Board links a test guild; a non-manager is refused |
| **S6** (optional) | server | Discord OAuth login for the dashboard | S5 | Board decides |
| **H** | plugin | Hub release: merge `server-sync` → `main` (Board), README "Server sync" and privacy section, Hub manifest `warning=` | **plugin-hub#20149 accepted**, player feedback on §1 | Board merges and opens the Hub PR |

**Docs to write in H:**

- BRIEF Phase 0 is already done (#22–#25).
- README privacy section.
- Proposed Hub `warning=`: *"Optional server sync submits your IP address and
  your group's shared data (banks, storage, stats, world, events) to a 3rd
  party server not controlled or verified by the RuneLite Developers. Off by
  default."*

## 9. What would make this a bad idea (and what can't be ruled out)

- **Hub reviewers reject auto-connect HTTP sync even with a warning.**
  Precedent says no (Group Ironmen Tracker), but reviewers change. This is
  why the Hub bump waits for #20149.
- **Growth past ~110 active groups** hits D1 writes on the free plan. Then the
  choice is the paid plan ($5/mo, a Board decision) or longer flush intervals.
  Measure before promising "free forever" to players.
- **Operating a service with personal data (RSNs)** means someone answers
  deletion requests and outages. The Board is the operator; the README must
  say so.
- **Players may not want it:** the Board's poll (§1) decides before H.
