# GIM Utilities sync server

The optional sync server for the GIM Utilities RuneLite plugin. It keeps a group in sync the
moment each member logs in, with no RuneLite Party and no "Join Group" click. RuneLite Party stays
the fallback: if this server is off, unreachable or over quota, the plugin behaves exactly as it
does without it.

Design: `docs/plans/gim-server.md` in the plugin repo, as amended by the Board on 2026-11-18
(live socket first, as minimal as possible on Cloudflare's free plan).

## How it works

- **One SQLite-backed Durable Object per group** (`GroupHub`), named by the group id. The free
  plan offers only the SQLite backend.
- **One hibernatable WebSocket per member** (`GET /v1/groups/{groupId}/live`). The object
  hibernates between messages; `setWebSocketAutoResponse("ping","pong")` answers keepalives
  without waking it.
- **The server pushes.** A member's payload is relayed to every other member at once (outgoing
  messages are free). Nobody polls.
- **Stored state for members who log in later.** The latest of each snapshot kind per member
  (bank parts, settings, roles and so on: written on change only) and the latest live state
  (`stats`, `carried`, `extras`: written at most once per member and kind every 5 minutes, plus a
  final write at logout). Events are relayed live only and never stored.
- **No D1.** All of a group's data lives in its own object, so there is one write meter to watch.
  No indexes beyond primary keys.
- **Payloads are opaque** after a shape check mirroring the plugin's `GroupCodec` caps
  (`src/protocol.ts`), so a new payload kind needs no server deploy.

### Auth: the existing group key, never sent

The plugin's 128-bit group key (already shared through GIM chat) is the only credential. The client
derives two values with HKDF-SHA256 and sends those; the key itself never leaves the client.

```
groupId   = base32( HKDF(key, info="gimu/v1/id", 16 bytes) )      // in the URL
groupAuth = base64url( HKDF(key, info="gimu/v1/auth", 32 bytes) )  // Authorization: Bearer
```

The first member to connect creates the group; the object stores only `HMAC(PEPPER, groupAuth)`.
Later connects must match it (constant-time compare), or get `401`. `X-Gimu-Name` carries the
member's RuneScape name. `test/fixtures/keys.json` holds vectors the plugin's `ServerKeysTest` and
this server's `keys.test.ts` both reproduce.

## Budget per active group per day (Board 2026-11-18)

Measured by `test/hub.test.ts` ("budget: …"): 5 members, each online 4 h a day in two sessions,
worst realistic cadence (stats every 5 s, carried every 30 s, a bank every 10 minutes), against a
real SQLite database counting rows changed and alarms the way Cloudflare bills them.

| Meter | Free plan (per account, per day) | One group, measured | Groups that fit |
| --- | --- | --- | --- |
| Worker requests (one per socket upgrade) | 100,000 | 10 | ~10,000 |
| Durable Object requests (upgrades + incoming messages ÷ 20) | 100,000 | **731** (14,410 frames) | **~136** |
| DO duration (only while JS runs; hibernation-eligible idle is free) | 13,000 GB-s | ~4 GB-s (est. 2 ms × 14,410 × 0.125 GB) | ~3,000 |
| SQLite rows written | 100,000 (if shared with D1, see below) | **262** | ~380 |
| Storage | 5 GB | ~0.5 MB (5 banks + state) | ~10,000 |

Binding meter: **Durable Object requests**. A client that sends at most one frame per member every
10 s instead of 5 s halves it (~270 groups). Past free, Workers Paid is **$5/month** and includes
1 M DO requests and 400,000 GB-s per month, then $0.15 per million requests: ~1,400 such groups
for the $5 minimum.

`scripts/loadtest.ts` runs the same traffic against a live Worker. Against `wrangler dev`
(2026-11-18): 5 sockets, one frame per member every 5 s for 1 minute: 60 frames, every frame
delivered to all 4 other members, 12 of 12 auto-response pongs, a wrong auth refused with `401`.

**Measure on deploy (Board action; deploys are prod writes):** run
`node scripts/loadtest.ts https://<worker>.workers.dev 5 60 5` and read the account's Durable
Objects metrics (requests, rows written, duration) and D1 metrics before and after. Whether DO
SQLite rows written share D1's 100k/day meter is **not stated in Cloudflare's docs**; until it is
measured, the budget above assumes they share it.

Sources (read 2026-11-18): developers.cloudflare.com/durable-objects/platform/pricing (requests,
duration, 20:1 WebSocket messages, "idle and eligible for hibernation are not billed"),
/durable-objects/concepts/durable-object-lifecycle (10 s to hibernate; timers and pending I/O
prevent it), /workers/platform/pricing.

## Quotas, rate limits and kill switches

- Per socket: a token bucket of 20 frames, refilled one every 2 s; past it the frame is dropped and
  the client is told `{"t":"e","c":"rate"}`.
- Per group per UTC day: 20,000 frames (then the socket closes `4029`), 3,000 rows written (then
  payloads are still relayed but no longer stored), 300 connects (then `429` with `Retry-After` to
  00:00 UTC). One group can't burn more than ~1% of the free DO requests or ~3% of the rows written.
- At most 8 member names per group (GIM's max is 5). A second socket for the same name replaces the
  first, which is closed `4001` ("signed in elsewhere").
- `SYNC_ENABLED` (a var in `wrangler.jsonc`): anything but `"true"` refuses new sockets with `503`.
  Clients stay on RuneLite Party and their own Discord webhooks; nothing errors in game.

## Retention and deletion (Board 2026-11-11)

A daily alarm per group: stored state older than 30 days is deleted; a member not seen for 90 days
is deleted; a group with no activity for 90 days is wiped entirely (`deleteAll`). Any member can
delete the whole group's data at once (`{"t":"del"}`); every connected client is closed `4010`.

## Privacy

Stored: the RuneScape names of members who turned server sync on, and the latest state they chose
to share (banks, group storage, stats, carried items, world, settings). Not stored: IP addresses,
the group key, webhook URLs, events, anything about players outside the group. There are no
lookups, no lists of groups and no public pages. The operator (the Board) can read the stored data
and answers deletion requests.

## Develop

Node 24+ runs the TypeScript directly.

```
npm install
npm test                 # node --test, real SQLite via node:sqlite
npm run typecheck
echo 'PEPPER=dev-only' > .dev.vars
npx wrangler dev --port 8799
node scripts/loadtest.ts http://127.0.0.1:8799 5 1 5
```

## Deploy (Board only)

```
npx wrangler secret put PEPPER     # 32+ random bytes; never change it (it would lock out every group)
npx wrangler deploy
```
