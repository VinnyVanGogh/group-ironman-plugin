# Research: GIM Group Sync Backend — Supabase vs. Cloudflare Compared Every Way

**Task:** `RUNELITE-505` (`task-12fd0ebc`)  
**Date:** 2026-11-20  
**Repository:** `VinnyVanGogh/group-ironman-plugin` (*GIM Utilities*)  
**Scope:** Read-only audit of GIM Utilities' sync requirements across `main` (`8e575a4`) and `origin/server-sync` (`8991422`, PR #88), followed by a head-to-head architectural, financial, security, RuneLite compliance, operational, and migration comparison between **Supabase** (Postgres + PostgREST + Realtime + Edge Functions) and **Cloudflare** (Workers + SQLite Durable Objects / D1) for **one shared project/account serving all groups**.

---

## Executive Summary & Verdict

**Recommendation: Keep Cloudflare (`Workers + SQLite-backed Durable Objects`, deployed on a dedicated Cloudflare account isolated from Sepco). Do not switch to Supabase.**

1. **Why Supabase was revisited — and what actually breaks on Supabase Free:** In `RUNELITE-117` (2026-11-11), Supabase was rejected because Free projects pause after 7 days of inactivity. Re-evaluating **one shared Supabase project serving all groups** confirms that **inactivity pausing is not the real blocker** once active groups query Postgres every few days (or a bi-weekly keep-alive query runs). Instead, **Supabase Free fails immediately on Realtime fan-out message billing**: Supabase bills **1 message sent + 1 message per subscribed recipient** (`supabase.com/docs/guides/realtime/pricing`). At our measured active cadence (`14,410` frames/group/day from `server/test/hub.test.ts`), **1 to 2 active 5-member groups** (`1.08M–2.16M` billed messages/month) exhaust the entire `2,000,000`/month Free Realtime quota, and even under a casual fleet-average profile (`100.8k` messages/group/month), Supabase Free caps out at **~19 groups**.
2. **50× to 370× Cost Asymmetry at Scale:** Because Jagex's Third-Party Client Guidelines and RuneLite Plugin Hub rules strictly forbid charging players or paywalling plugin features, backend costs come permanently out of the author's pocket. Cloudflare bills incoming WebSocket messages at a **20:1 ratio**, charges **$0 for outgoing WebSocket fan-out**, and charges **$0 for bandwidth/egress**. At **1,000 groups**, Cloudflare costs **$5.26–$8.14/mo** vs. **$275–$2,780/mo** on Supabase. At **10,000 groups**, Cloudflare costs **$8.95–$75.23/mo** vs. **$2,737–$27,930+/mo** on Supabase.
3. **Client Token Budget & Already-Built Code:** `main` is currently at **`251,450` bot-estimated tokens** against the Plugin Hub's hard `200,000`-token reviewer-blocking limit (`R05`, tracked in `RUNELITE-343` / PR #119), and `build=standard` forbids external SDKs (`supabase-kt`, Netty, Phoenix clients). On `origin/server-sync` (PR #88), the complete Cloudflare server (`server/`, 25 passing tests) and zero-dependency Java client (`ServerKeys.java`, `ServerDialer.java`, `ServerLink.java` — `923` lines total) **are already built and tested**. Moving to Supabase would require replacing that thin WebSocket client with three custom Java subsystems (Edge Function JWT mint/refresh + Phoenix Channels WebSocket state machine + PostgREST snapshot persistence), adding ~50–90% more Java code against a constrained token budget.
4. **Security & Group Isolation:** On Cloudflare, every group is physically isolated in its own SQLite database (`env.GROUPS.getByName(groupId)`), authenticated *before* the HTTP `101` WebSocket upgrade via `HMAC-SHA256(PEPPER, groupAuth)`. A bug cannot leak rows across groups because no shared table exists. On Supabase, all groups share multi-tenant Postgres tables guarded by Row Level Security (RLS), custom headers fail to authenticate Realtime WebSockets, and Edge Function custom JWTs remain valid until expiry on key rotation unless checked against Postgres state.

---

## 0. What GIM Sync Needs Today (Read-Only Codebase Audit)

### 0.1 Current Client & Server Code Locations

| Location | Ref / Status | What Exists Today |
| :--- | :--- | :--- |
| **`main` (Production / Hub PR #20149)** | `8e575a4` | **Serverless P2P over RuneLite `PartyService` (`WSClient`).** Core classes under `src/main/java/com/vinnyvangogh/groupironman/`: `GroupIronmanPlugin.java:164,466` (registers `VvgGroupIronmanMessage`), `GroupSync.java:21-156` (passphrase join/leave state machine), `GroupKey.java:23-134` (128-bit hex key + `[GIMU]` GIM chat distribution), `GroupService.java:1111,1726-1888` (all sends, roster-verified receives, tick coalescing), `GroupOutbox.java:17-162` (per-feature opt-in gates), and 6 wire codecs (`GroupCodec`, `GovernanceCodec`, `ShoppingCodec`, `ActivityCodec`, `MemberProgressCodec`, `BoardBeacon`). Requires a **manual `"Join group sync"` click every session** (`SyncHeader.java:15`) because auto-joining `PartyService` is a rejected Hub feature (`BRIEF.md:142-148`, `plugin-hub#19833`). |
| **`origin/server-sync` (Built Server + Client)** | `8991422` (PR #88, `task-235f05b5`, 2026-11-18) | **Complete v1 Cloudflare Worker + SQLite Durable Object sync server & Java client.** Server code lives in `server/` (`src/index.ts`, `src/hub.ts`, `src/keys.ts`, `src/protocol.ts`, `wrangler.jsonc`, `scripts/loadtest.ts`, `test/hub.test.ts` — 25/25 Node SQLite tests passing). Plugin client lives in `ServerKeys.java` (104 lines), `ServerDialer.java` (103 lines), `ServerLink.java` (716 lines, 20 JUnit tests in `ServerLinkTest` + `ServerKeysTest`), plus `GroupIronmanConfig.java` (`serverSync`) and `GroupService.java` dual-transport wiring. Targets placeholder `wss://gim-utilities-server.vinnyvangogh.workers.dev` (`ServerLink.java:244`); **not yet deployed to production**. |
| **`VinnyVanGogh/gim-utilities-server` (GitHub)** | **Not created yet** | Verified via GitHub search (2026-11-20) and `task-235f05b5` comment #20840 (2026-11-18): the standalone server repository has not been created yet; `server/` sits on `origin/server-sync` awaiting repo split and `wrangler deploy`. |
| **Planned Server Extensions (Docs / Backlog)** | `docs/plans/gim-server.md` & `RUNELITE-248` | `docs/plans/gim-server.md` Phases S3–S5 plan a mobile web dashboard (`/g/`), remote group settings page, and `/gimu` Discord HTTP interactions bot (`Ed25519` verified). `RUNELITE-248` (`task-e06d155a`) plans relaying Discord events through the sync server so webhooks live server-side and multi-client events post once. Note: `origin/server-sync` intentionally simplified `gim-server.md`'s Workers+D1+DO design to **Workers + SQLite DO only (no D1)** so there is only one storage write meter (`server/wrangler.jsonc:2-3`). |

### 0.2 Complete Wire Message Inventory (`main` @ `8e575a4`)

All group payloads ride as JSON strings inside `VvgGroupIronmanMessage` (Party) or `{"t":"b","d":[...]}` batch frames (Server WebSocket), each carrying `"v": 1` and a kind tag `"k"`. Across the six codecs on `main`, there are **19 distinct `"k"` message kinds**:

| # | `"k"` Tag | Codec (`file:line`) | Trigger & Cadence | Hard Cap | Typical Payload Size | Server Persistence (`protocol.ts`) |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `bank` | `GroupCodec.java:325` | Bank close (`WidgetClosed` `BANKMAIN`) when contents changed | `<= 16` parts × `6,000` chars (`1,600` items max) | `3.5–9 KB` total (1–2 parts for 400–800 items) | **Durable** (`bank:0`..`bank:p`, on change) |
| 2 | `carried` | `GroupCodec.java:386` | `INV` / `WORN` / `LOOTING_BAG` / skilling container / charge change; `>= 5` ticks (`3.0s`) | `6,000` chars (`64` items, `28` raw slots, `3` containers) | `0.6–1.2 KB` | **Live** (coalesced `<= 1` write / `5 min` + logout) |
| 3 | `storage` | `GroupCodec.java:433` | Verified Group Storage `"Saving..."` modal (`293:1`) or Save click (`StorageSession`) | `6,000` chars (`500` items each in `took`/`dep`/`now`) | `1.0–4.0 KB` | **Durable** (`storage`, on change) |
| 4 | `stats` | `GroupCodec.java:443` | `StatChanged`, run/spec/prayer/poison/world/region change, `>= 8` tiles moved (`>= 3` ticks / `1.8s`), or `60s` Party heartbeat | `6,000` chars (23 skill pairs + vitals + coords + status) | `0.65–0.85 KB` | **Live** (coalesced `<= 1` write / `5 min` + logout) |
| 5 | `extras` | `GroupCodec.java:478` | Rune pouch / Dizana's quiver varbit or `SEED_VAULT` container change (`>= 5` ticks / `3.0s`) | `6,000` chars (`8` pouch, `2` quiver, `250` seed vault) | `0.2–2.5 KB` (`~0.5 KB` typical) | **Live** (coalesced `<= 1` write / `5 min` + logout) |
| 6 | `events` | `GroupCodec.java:494` | Live occurrence across 34 traveling `GroupEvent.Type` codes (`GroupEvent.java:55-141`), plus join backfill (`<= 300` events, `EventBackfill.java:22`) | Split into `<= 6,000`-char chunks (`<= 100` events/msg) | `0.15–0.3 KB` live; `2–18 KB` on backfill | **Relay-only** on `server-sync` (`slot = null`) |
| 7 | `posted` | `GroupCodec.java:766` | Non-plugin teammate broadcast posted to Discord (`GroupBroadcasts.java`) | `6,000` chars (`<= 20` keys × `200` chars) | `0.1–0.3 KB` | **Relay-only** (`slot = null`) |
| 8 | `roles` | `GovernanceCodec.java:101` | Leader claim or editor change (ECDSA P-256 signed) | `12,000` chars (`<= 5` editors) | `0.35–0.6 KB` | **Durable** (`roles`, on change) |
| 9 | `gset` | `GovernanceCodec.java:106` | Leader/editor `"Save to group"` settings update (ECDSA signed) | `12,000` chars (`<= 400` keys, `50` history lines) | `1.0–4.0 KB` | **Durable** (`gset`, on change) |
| 10 | `member` | `GovernanceCodec.java:111` | Member public key + Discord posting opt-outs changed | `12,000` chars | `0.2–0.35 KB` | **Durable** (`member`, on change) |
| 11 | `gweb` | `GovernanceCodec.java:78` | Leader shares/updates 7 category Discord webhooks (ECDSA signed) | `12,000` chars (`7` URLs × `<= 300` chars) | `0.4–1.8 KB` | **Durable** (`gweb`, on change) |
| 12 | `shop` | `ShoppingCodec.java:121` | Shared Shopping list created, edited, renamed, or removed | `12,000` chars (`<= 200` items) | `0.3–2.0 KB` | **Durable** (`shop`, on change)* |
| 13 | `qstate` | `ShoppingCodec.java:143` | Bundled quest state change (polled every `50` ticks / `30s`) | `12,000` chars (`<= 300` quest names) | `0.5–2.5 KB` | **Durable** (`qstate`, on change) |
| 14 | `req` | `ShoppingCodec.java:85` | Member item request created, updated, auto-sealed (`5` ticks), or closed | `12,000` chars (`<= 200` items) | `0.25–1.0 KB` | *(Landed on `main` post-branch; needs `DURABLE_KINDS` slot `req:<id>`)* |
| 15 | `rqc` | `ShoppingCodec.java:110` | Member `"I'll get it"` claim toggled on a request item | `12,000` chars | `0.12–0.18 KB` | *(Needs `DURABLE_KINDS` or CRDT claim slot)* |
| 16 | `activity` | `ActivityCodec.java:38` | Fight Caves / Inferno / Colosseum wave change or exit clear frame | `2,048` chars | `0.15–0.45 KB` | Slotted client-side (`ServerLink.java:265`), relay-only server-side |
| 17 | `pbs` | `MemberProgressCodec.java:40` | New boss Personal Best chat line captured (`PbCapture.java`) | `12,000` chars (`<= 150` bosses) | `0.2–1.5 KB` | *(Landed on `main` post-branch; needs `DURABLE_KINDS` slot `pbs`)* |
| 18 | `ach` | `MemberProgressCodec.java:49` | Full Quest Points, 170+ quests, or Diary tier change (polled every `30s`) | `12,000` chars (`<= 250` quests) | `1.5–4.5 KB` | *(Landed on `main` post-branch; needs `DURABLE_KINDS` slot `ach`)* |
| 19 | `board` | `BoardBeacon.java:26` | Sent every `100` ticks (`60s`) by the 1 client keeping the Discord Live Board | `256` chars | `0.05 KB` (`45` chars) | **Relay-only** (`slot = null`) |

*\*Note on `protocol.ts:828`: `shop` currently stores under single slot `"shop"` per member rather than `"shop:" + id`, and `req`, `rqc`, `pbs`, and `ach` were merged to `main` after `origin/server-sync` was cut — a minor follow-up to note regardless of backend.*

### 0.3 Write/Read Frequency, Coalescing & Measured Traffic Profiles

On `origin/server-sync`, `ServerLink.java` (`:249, 447-573`) enforces strict client-side batching and deduplication:
- **Minimum frame interval:** `MIN_GAP_MS = 5_000` (at most **1 outbound WebSocket frame every 5 seconds** per connected member, batching up to `32` payloads / `120 KB`).
- **Slot deduplication:** Slotted payloads (`bank:p`, `stats`, `carried`, `extras`, `activity`, `roles`, `gset`, `gweb`, `member`, `shop`, `qstate`) overwrite older unsent payloads in the 5-second queue and are **dropped completely** if identical to `lastSent.get(slot)`.
- **Server-side write coalescing (`server/src/hub.ts:622-632`):** `LIVE_KINDS` (`stats`, `carried`, `extras`) are relayed in real time to online groupmates on every frame, **but persisted to SQLite at most once per member per kind every 5 minutes (`LIVE_WRITE_MS = 300,000 ms`)**, plus one forced flush (`"p": 1`) at logout (`ServerLink.stop(true)`). `DURABLE_KINDS` write only when the JSON string changes.
- **Zero polling:** Clients never poll on a timer while connected; keepalives use RFC 6455 WebSocket pings every `30 s` (`ServerDialer.java:128`) plus `setWebSocketAutoResponse("ping", "pong")` (`server/src/index.ts:256`).

To ensure our cost math in §2 reflects both stress conditions and realistic fleet averages, we carry **two explicit traffic profiles** per group:

| Metric (per Group) | **Profile A: Measured Heavy Benchmark** (`server/test/hub.test.ts:276-314`) | **Profile B: Typical Fleet-Average GIM Group** (Realistic active Hub install mix) |
| :--- | :--- | :--- |
| **Roster & Playtime** | **5 members**, each online **4 h/day** in 2 sessions (`20` member-hours/day) | **3.5 active members**, each online **2 h/day** (`7` member-hours/day) |
| **Connects / Session Joins** | `10` connects/day (`300` / month) | `7` connects/day (`210` / month) |
| **Client -> Server Frames** | **`14,410` / day** (`432,300` / month; continuous 1 frame / `5s` while online) | **`1,680` / day** (`50,400` / month; effective state change every `15s` after `lastSent` dedup) |
| **Avg Concurrent Online Peers** | `2.5` members online together (`1.5` peers receiving each frame; peak `4` peers) | `2.0` members online together (`1.0` peer receiving each frame) |
| **Server -> Client Outbound Frames** | `21,615` / day (`648,450` / month; `57,640`/day if all 5 overlap) | `1,680` / day (`50,400` / month) |
| **Billed Supabase Broadcast Msgs** *(1 sent + N received)* | **`36,025` / day** (**`1,080,750` / month**; `2.16M`/mo if all 5 play together) | **`3,360` / day** (**`100,800` / month**) |
| **Billed Cloudflare DO Requests** *(connects + inbound ÷ 20; outbound free)* | **`731` / day** (**`21,930` / month**) | **`91` / day** (**`2,730` / month**) |
| **Coalesced DB Snapshot Writes** | **`262` rows / day** (**`7,860` / month**, measured in `hub.test.ts`) | **`110` rows / day** (**`3,300` / month**) |
| **Peak Prime-Time Connections** | `~1.0` concurrent socket per group across fleet (`5` max for 1 group) | `~0.6` concurrent socket per group across fleet |
| **Monthly Egress & Stored State** | `~0.80 GB` egress/mo; `~0.5 MB` SQLite (`~1.0 MB` Postgres w/ indexes) | `~0.13 GB` egress/mo; `~0.5 MB` SQLite (`~1.0 MB` Postgres) |

### 0.4 Groups × Members Estimate

- **Group size:** OSRS Group Ironman teams hold **2 to 5 players** (`MAX_MEMBERS = 8` in `server/src/hub.ts:381` to accommodate RSN changes and replacements).
- **Ecosystem scale (`docs/research/gim-competitor-comparison.md:48-72`):** *Group Ironmen Tracker* (`groupiron.men`) leads web-backed GIM plugins with **23,828 active installs** (~32,000 peak, representing ~6,000–8,000 groups), followed by *Group Iron Man* (~15,544), *Group Iron Panel* (8,144), and *GIMP* (4,585). Evaluating **10, 100, 1,000, and 10,000 groups** spans private beta (`10`), early launch (`100`), popular Hub plugin (`1,000`, ~3,500–5,000 opted-in installs), and category dominance surpassing *Group Ironmen Tracker*'s all-time peak (`10,000`, ~35,000–50,000 opted-in installs).

### 0.5 Auth Model (Group Key) & RuneLite 3rd-Party-Server Warning

- **Zero-setup credential (`GroupKey.java` & `ServerKeys.java`):** The first member generates a 128-bit (`32`-hex) random secret via `SecureRandom` (`GroupKey.java:38`) and pastes `[GIMU] <key>` once into in-game `CLAN_GIM_CHAT`. Teammates' clients auto-save the key from GIM chat (`GroupService.java:998-1053`) and hide the line via `chatFilterCheck` (`:1060-1081`). The raw key **never leaves the client**. Using JDK `javax.crypto.Mac` (`HmacSHA256`), `ServerKeys.java` (`:61-83`) runs RFC 5869 HKDF-SHA256 (empty salt) to derive:
  - `groupId = base32(HKDF(key, info="gimu/v1/id", 16 bytes))` — 26-char public routing ID.
  - `groupAuth = base64url(HKDF(key, info="gimu/v1/auth", 32 bytes))` — 43-char secret bearer proof.
  - The server stores only `HMAC-SHA256(PEPPER, groupAuth)` (`server/src/hub.ts:395-400, 485-497`) and compares in constant time.
- **RuneLite 3rd-party-server warning (`AGENTS.md`, Hub `R15`, `BRIEF.md` #18/#22/#26):**
  1. **Config item disclosure (`AGENTS.md` §Config):** Every `@ConfigItem` enabling third-party network traffic must default to **`false`** (opt-in) and carry a `warning` prompt. On `main`, `DiscordWebhook.WARNING` (`:27-28`) uses the exact `AGENTS.md` text; on `origin/server-sync`, `ServerLink.WARNING` (`:245-247`) sets: *"Off by default. Sends your IP address, your name and what you share with your group to the GIM Utilities sync server, a 3rd-party server not controlled or verified by RuneLite developers"*.
  2. **Plugin Hub manifest disclosure (`runelite/plugin-hub/plugins/gim-utilities`):** Does **not** go in `runelite-plugin.properties` (`BRIEF.md:190-205`). When `server-sync` eventually merges after `plugin-hub#20149` is accepted, `plugins/gim-utilities` in `runelite/plugin-hub` adds `warning=This plugin can send your IP address and what you share with your group to the GIM Utilities sync server, and your own events to a Discord webhook you enter. Both are optional and off by default, and both are 3rd parties not controlled or verified by the RuneLite Developers.` (`BRIEF.md` #26 on `origin/server-sync`), matching precedent on `plugins/group-ironmen-tracker`. *(Note: StayPoint preflight `R15` also checks `@PluginDescriptor(warning = ...)` when non-RuneLite URLs exist in `src/main/java`.)*

---

## 1. Free-Tier Limits & Failure Modes (One Shared Project/Account)

All figures below are sourced from official Supabase (`supabase.com/pricing`, `supabase.com/docs/guides/realtime/limits`, `supabase.com/docs/guides/realtime/pricing`, `supabase.com/docs/guides/functions/limits`, `supabase.com/docs/guides/platform/org-based-billing`) and Cloudflare (`developers.cloudflare.com/workers/platform/pricing/`, `/workers/platform/limits/`, `/durable-objects/platform/pricing/`, `/durable-objects/platform/limits/`, `/d1/platform/pricing/`, `/kv/platform/pricing/`) documentation, verified **2026-11-20**.

### 1.1 Supabase Free Plan (`$0/mo`, up to 2 active projects per org)

| Resource / Meter | Free-Tier Quota | Exact Behavior When Limit Is Reached | Max Active GIM Groups Supported on Free |
| :--- | :--- | :--- | :--- |
| **Postgres Database Size** | **500 MB** per project | Grace period warning email; if still over 500 MB (or disk hits 95% with WAL), database enters **Read-Only Mode**: `SELECT` works, all `INSERT`/`UPDATE`/`DELETE` fail with `cannot execute INSERT in a read-only transaction` until pruned or upgraded. | **~500–1,000 groups** (`~0.5–1.0 MB`/group) |
| **Egress (Bandwidth)** | **5 GB uncached** + **5 GB cached** / month (org-wide) | Past grace period, Fair Use enforcement blocks API traffic across Data API, Auth, and Edge Functions with HTTP **`402 Payment Required`** (`exceed_egress_quota`) until monthly billing cycle reset. | **~6 groups** (Profile A) / **~38 groups** (Profile B) |
| **Realtime Concurrent Connections** | **200 peak** concurrent WebSockets | New WebSocket handshakes / `phx_join` attempts past 200 concurrent sockets are **rejected** (`too_many_connections`). At prime time (`~4` online in 5-man groups), 50 simultaneous teams fill the entire cluster. | **~50–200 groups** (depends on prime-time overlap) |
| **Realtime Messages** | **2,000,000 / month** (`100 msgs/s` rate cap; `256 KB` max Broadcast payload; `1 MB` Postgres Changes) | **Binding Free-tier bottleneck.** Every Broadcast frame counts as **1 sent + N received**. Exceeding `100 msgs/s` drops/throttles frames; exceeding `2M/mo` triggers Fair Use restriction / halt until monthly cycle reset. | **~1.8 groups** (Profile A) / **~19 groups** (Profile B) |
| **Realtime Presence** | `20` Presence msgs/s global; **`5` calls per `30s`** per client; `10` keys/object | Calls exceeding `5 / 30s` per client are rate-limited/dropped on the socket. | Sufficient for join/leave only, never vitals |
| **Edge Function Invocations** | **500,000 / month** (`2s` CPU time, `150s` wall clock, `256 MB` RAM) | Exceeding CPU/wall clock returns HTTP **`546`** / **`504`**; exceeding monthly invocations or org egress returns HTTP **`402`** upstream. | **~3,300 groups** if called only for daily JWT mint (`150`/group/mo); **~3.8 groups** if called per write |
| **Automated Backups** | **None (`0` days)** | Manual `supabase db dump` / `pg_dump` only. Hardware or operator mistake loses data unless self-backed-up. | N/A |
| **Inactivity Pausing** | Pauses after **7 days** of low/zero **Postgres DB activity** | Project shuts down compute; API/Realtime connections fail until manually resumed in Dashboard (data kept up to **1 year** before deletion; warning email sent ~7 days prior). | **Avoided by a busy multi-group project** *if* clients read/write Postgres (see note below) |

> **Answering the Board's Inactivity Question Directly:** Does one shared Supabase project for all groups avoid the 7-day inactivity pause? **Yes — with one architectural catch.** Supabase measures *user Postgres database activity* (PostgREST reads/writes or SQL queries), not static requests or dashboard views. Once `>= 1` group logs in at least once every 7 days and executes a PostgREST snapshot read/upsert (or a scheduled GitHub Action runs `SELECT 1` every Monday/Thursday), the project will never pause. *The catch:* if a design used **only** ephemeral Realtime Broadcast + stateless JWTs without touching Postgres for 7 days, Supabase could still mark the database idle. More importantly, **pausing is moot because the `2M/month` Realtime message quota caps Supabase Free at ~2 to 19 groups.**

### 1.2 Cloudflare Workers Free Plan (`$0/mo`, account-wide daily limits resetting `00:00 UTC`)

| Resource / Meter | Free-Tier Quota | Exact Behavior When Limit Is Reached | Max Active GIM Groups Supported on Free |
| :--- | :--- | :--- | :--- |
| **Workers Requests** | **100,000 / day** (**shared across all Workers on the account**, e.g. Sepco!) | Returns Cloudflare **Error `1027`** (fail-closed default) until `00:00 UTC`; **never auto-charges**. WebSocket upgrade = `1` request; frames over an open WebSocket = **`0` Worker requests**. Static assets (`/`, `/g/`) = **free & unlimited**. | **~10,000 groups** (`10` WS upgrades/group/day) — *use a dedicated CF account so Sepco is never touched* |
| **Workers CPU & RAM** | **10 ms CPU** / invocation (`128 MB` RAM; `50` external subrequests) | Invocation terminated (`Error 1102` CPU time exceeded). Waiting on network/SQLite I/O does **not** count against CPU time. | Unlimited if per-request crypto + JSON stays `< 10 ms` *(A9 in `gim-server.md`: measure on deploy)* |
| **Durable Objects Requests** | **100,000 / day** (*SQLite-backed DOs only* on Free) | **Binding Free-tier meter under Profile A.** Incoming WebSocket frames are billed **20:1** (`20` client frames = `1` DO request). **Outgoing WebSocket fan-out frames = `0` (free).** Protocol pings & `setWebSocketAutoResponse` = **`0` (free)**. Past `100k/day`, DO ops fail with an exception until `00:00 UTC` (client degrades to RuneLite Party). | **~136 groups** (Profile A: `731` DO req/day) / **~1,098 groups** (Profile B: `91` DO req/day) |
| **DO Compute Duration & WebSocket Hibernation** | **13,000 GB-s / day** (`128 MB` isolate; max `32,768` hibernatable sockets/DO; `32 MiB` max WS msg) | With `ctx.acceptWebSocket()`, the DO **hibernates after ~10s idle** and incurs **0 duration billing** while sockets stay open at Cloudflare's edge; `serializeAttachment` (`<= 2 KB`) preserves per-socket state across hibernation. Past `13k GB-s/day`, fails until `00:00 UTC`. | **~3,000–3,500 groups** (Profile A: `~3.7 GB-s`/group/day) |
| **DO SQLite Storage & Row Ops** | **50M rows read / day**; **100,000 rows written / day**; **5 GB** total (`10 GB`/object on Paid) | Every index update, `DELETE`, and `setAlarm()` counts as 1 row written (`origin/server-sync` uses primary keys only, zero secondary indexes). Past `100k` writes/day, SQLite writes throw until `00:00 UTC` (`hub.ts:605` catches quota and continues live memory relay!). | **~381 groups** (Profile A: `262` writes/day) / **~909 groups** (Profile B: `110` writes/day) |
| **Cloudflare D1** *(if used)* | **5M rows read / day**; **100,000 rows written / day**; **5 GB** across 10 DBs (`500 MB`/DB) | Hard-fails write queries once `100k/day` is reached until `00:00 UTC`. **UNVERIFIED:** Cloudflare docs do not explicitly state whether DO SQLite's `100k/day` write meter and D1's `100k/day` write meter are separate or pooled on Free; `origin/server-sync` dropped D1 to avoid splitting the write budget. | ~110–380 groups |
| **Workers KV** *(if used)* | `100,000` reads/day; **`1,000` writes/day**; `1,000` deletes/day; `1 GB` | Fails past `1,000` writes/day until `00:00 UTC` + eventual consistency (`~60s`). Completely unsuitable for live group sync state. | **~3–9 groups** (unusable for sync writes) |
| **Inactivity Pausing** | **Never pauses** | Serverless isolates & SQLite DOs wake on demand in milliseconds after months of zero traffic (`90`-day idle retention alarm is our own privacy rule, not a platform pause). | Unlimited |

**Key Free-Tier Difference:** When Cloudflare hits a daily Free limit, it fails cleanly until **`00:00 UTC` the same night** (at most hours away, during which `ServerLink` silently falls back to RuneLite Party). When Supabase hits a monthly Free limit (Messages or Egress), the project stays degraded or **`402`-blocked for the remainder of the monthly billing cycle** (up to 30 days).

---

## 2. Cost Comparison at 10 / 100 / 1,000 / 10,000 Groups

### 2.1 Paid-Plan Rate Cards Used (Verified 2026-11-20)

- **Cloudflare Workers Paid (`$5.00/mo` base minimum):** Includes `10M` Worker req/mo (`+$0.30/M`), `30M` CPU-ms/mo (`+$0.02/M`), `1M` DO req/mo (`+$0.15/M` — where `1M` DO req = **`20M` inbound WebSocket frames**), `400,000` DO GB-s/mo (`+$12.50/M GB-s`), `25B` SQLite rows read/mo (`+$0.001/M`), `50M` SQLite rows written/mo (`+$1.00/M`), `5 GB` SQLite storage (`+$0.20/GB-mo`), **`$0.00/GB` egress**.
- **Supabase Pro (`$25.00/mo` base, Spend Cap OFF):** Includes `$10/mo` compute credit (covers default `Micro` shared 2-vCPU / `1 GB` RAM; `Small` dedicated-RAM 2 GB is `$15/mo` = `+$5` net; `Medium` 4 GB is `$60/mo` = `+$50` net; `Large` 8 GB is `$110/mo` = `+$100` net), `8 GB` DB disk (`+$0.125/GB-mo`), `250 GB` egress (`+$0.09/GB`), `500` peak Realtime connections (`+$10.00` per `1,000`-connection package, **rounded up to whole packages**), `5,000,000` Realtime messages (`+$2.50` per `1,000,000`-message package, **rounded up to whole packages**), `2,000,000` Edge Function invocations (`+$2.00/M`). Pro Realtime rate cap with Spend Cap off is **`2,500 msgs/sec`**; exceeding `2,500 msgs/sec` requires **Team (`$599/mo`)** or Enterprise.

### 2.2 Monthly Total Cost Table (Best-Case Supabase Broadcast + PostgREST vs. Cloudflare DO)

| Scale | **Cloudflare — Profile A** *(Heavy `hub.test.ts`)* | **Cloudflare — Profile B** *(Typical Fleet Avg)* | **Supabase — Profile A** *(Heavy `hub.test.ts`)* | **Supabase — Profile B** *(Typical Fleet Avg)* | Cost Ratio *(Supabase ÷ Cloudflare)* |
| :--- | ---: | ---: | ---: | ---: | :--- |
| **10 groups** *(~35–50 players)* | **$0.00 / mo** *(Free: 7.3% DO req, 2.6% writes)* | **$0.00 / mo** *(Free: 0.9% DO req, 1.1% writes)* | **$40.00 / mo** *(Pro `$25` + `$15` for `10.8M` msgs; exceeds Free `2M` msgs & `5 GB` egress)* | **$0.00 / mo** *(Free: `1.01M`/`2M` msgs, `1.3`/`5 GB` egress)* | **Tie ($0) casual / ∞× heavy** |
| **100 groups** *(~350–500 players)* | **$0.00 / mo** *(Free: 73% DO req, 26% writes; or `$5.00` Paid for headroom)* | **$0.00 / mo** *(Free: 9.1% DO req, 11% writes)* | **$285.00 / mo** *(Pro `$25` + `$260` for `108.1M` msgs)* | **$40.00 / mo** *(Pro `$25` + `$15` for `10.1M` msgs)* | **57× – ∞×** |
| **1,000 groups** *(~3.5k–5k players)* | **$8.14 / mo** *(Paid `$5` + `$3.14` for `21.93M` DO req; writes/GB-s/storage `$0`)* | **$5.26 / mo** *(Paid `$5` + `$0.26` for `2.73M` DO req; fits ~909 groups on Free!)* | **$2,779.50 / mo** *(Pro `$25` + Small `+$5` + `$2,690` for `1.08B` msgs + `$10` conns + `$49.50` egress)* | **$275.00 / mo** *(Pro `$25` + `$240` for `100.8M` msgs + `$10` conns)* | **52× – 341×** |
| **10,000 groups** *(~35k–50k players)* | **$75.23 / mo** *(Paid `$5` + `$32.75` DO req + `$8.88` GB-s + `$28.60` for `78.6M` writes; `$0` egress)* | **$8.95 / mo** *(Paid `$5` + `$3.95` for `27.3M` DO req; `33M` writes & `5 GB` included; `$0` egress!)* | **$27,930+ / mo** *(Exceeds Pro `2,500 msg/s` cap at `~4,170 msg/s` avg! `$27,008` msgs + `$698` egress + `$100` conns + Large compute)* | **$2,737.25 / mo** *(Pro `$25` + Medium `+$50` + `$2,507.50` for `1.01B` msgs + `$60` conns + `$94.50` egress)* | **306× – 371×** |

### 2.3 Why the Gap Is 300×+ (And Why Postgres Changes Would Be Even Worse)

1. **833×–1,667× per-frame realtime billing multiplier:** One inbound frame to a 5-member group (`1.5` to `4` peers online) consumes **`0.05` Cloudflare DO requests** (`$0.0000000075`) vs. **`2.5` to `5.0` Supabase Realtime messages** (`$0.00000625` to `$0.0000125`).
2. **Dual-write tax on Supabase:** Because Supabase Realtime Broadcast is ephemeral, clients must *also* send `262` HTTP `POST /rest/v1/snapshots` requests/group/day to Postgres so offline teammates see banks and stats later. At 1,000–10,000 groups, that adds `262k–2.62M` TLS/PostgREST/RLS SQL transactions per day onto single-node Postgres compute.
3. **What if Supabase used `Postgres Changes` instead of `Broadcast`?** Every `stats`/`carried` upsert would stream through Postgres logical replication WAL into Elixir, which impersonates each subscribed member's JWT and runs an RLS `SELECT` check *per subscriber* on a single-threaded replication pipeline (`supabase.com/docs/guides/realtime/postgres-changes#scaling-and-performance`). At just 100–300 groups, WAL + RLS CPU saturates Micro/Small Postgres instances before message overages even finish billing.

---

## 3. Realtime Model Comparison

| Dimension | Supabase: `Postgres Changes` | Supabase: `Broadcast` + `Presence` | Cloudflare: Durable Object WebSocket (`GroupHub`) |
| :--- | :--- | :--- | :--- |
| **Mechanism** | Postgres WAL logical replication -> Elixir node -> per-subscriber RLS check -> Phoenix WS | Client pushes Phoenix JSON frame -> Elixir cluster pub/sub -> subscribed peers (no DB write) | Client pushes JSON frame -> Group's single DO isolate -> synchronous loop `other.send()` + coalesced local SQLite write |
| **End-to-End Latency** | **High (`100–400+ ms`)**: HTTP write + Postgres commit + WAL poll + RLS check + WS push | **Low (`20–80 ms`)**: in-memory Elixir Erlang distribution mesh | **Ultra-low (`10–50 ms` regional, `80–150 ms` cross-ocean)**: all groupmates share the *same* in-memory V8 isolate |
| **Ordering Guarantee** | Commit-order per table, **can lag or drop** payloads `> 1 MB` under WAL backpressure | Causal per sender per Elixir node; **no ordering across Broadcast vs. separate PostgREST snapshot reads** (race window on join!) | **Strict linearizable total order per group**: single-threaded actor processes `open()`, `message()`, and SQLite writes sequentially with zero race between live relay and stored snapshot |
| **Fan-Out Ceiling** | Bottlenecked by DB CPU (`O(writes × subscribers)` RLS checks) | High (`100 msg/s` Free, `500–2,500 msg/s` Pro across *entire project*), billed per recipient | Up to `32,768` sockets & `~500–1,000 msg/s` **per individual group DO** (we only ever have `<= 8` sockets per group, so group 1 never contends with group 10,000!) |
| **Presence Accuracy** | Manual heartbeat table (expensive) | Built-in CRDT (`5 calls / 30s` cap), separate state sync stream | Native & exact: `ctx.getWebSockets()` is the literal list of open TCP/WS connections in that DO (`hi` on join, `j` on open, `l` on close, `4001` on duplicate RSN) |
| **Reconnect & Catch-Up** | Must issue separate REST `GET` query with timestamp cursor after `phx_join` | Broadcast loses all frames while disconnected; client must run separate PostgREST `SELECT` + dedupe against live stream | Atomic in `hub.open()` (`server/src/hub.ts:528-533`): socket upgrade immediately sends `{"t":"hi","n":online}` followed by all stored snapshots before live frames resume |

---

## 4. Security for a Public Client That Cannot Hold Secrets

Because GIM Utilities is an open-source RuneLite Hub plugin (`BSD-2-Clause`), any URL or `SUPABASE_ANON_KEY` compiled into the jar is public knowledge. Security rests entirely on the group's 128-bit `groupKey` (never transmitted; HKDF-derived into public `groupId` and secret `groupAuth`).

### 4.1 Auth Architecture Comparison

1. **Supabase Pattern A — Edge Function Mints Custom JWT (Viable, Recommended if Using Supabase):**
   - Client calls `POST /functions/v1/join` with `{groupId, groupAuth, rsn}` and `apikey: <SUPABASE_ANON_KEY>`.
   - Edge Function holds `PEPPER` and `SUPABASE_JWT_SECRET` (or asymmetric signing key) in Edge Secrets, verifies `HMAC-SHA256(PEPPER, groupAuth)` in constant time against a `private.group_secrets` table (inaccessible to PostgREST!), checks max 8 members, and signs a short-lived (`1h`) JWT: `{role: "authenticated", group_id: groupId, rsn_key: who, key_ver: 1, exp: ...}`.
   - Client passes `Bearer <jwt>` to PostgREST and `access_token: <jwt>` inside Realtime `phx_join`. Both PostgREST RLS and `realtime.messages` Private Channel RLS validate `(auth.jwt() ->> 'group_id')` natively without creating `auth.users` rows (`supabase.com/docs/guides/realtime/authorization`).
   - **Weakness on Key Rotation / Revocation:** Stateless JWTs stay valid until `exp` (`1h`) unless every RLS policy joins against `groups.key_ver` (adding per-query DB overhead), and an already-joined Realtime Broadcast channel stays open until `exp` triggers re-evaluation!
2. **Supabase Pattern B — Hashed Key Custom Header (`x-group-auth`) in RLS (Broken for Realtime):**
   - PostgREST exposes HTTP headers via `current_setting('request.headers', true)::json ->> 'x-group-auth'`, allowing an RLS policy to check `extensions.hmac(header_val, pepper_from_vault, 'sha256') = auth_hmac`.
   - **Fatal flaw:** **Supabase Realtime WebSockets do not forward custom HTTP headers into `request.headers` during `realtime.messages` RLS checks** — Realtime authorization only evaluates JWT claims from `access_token`! Furthermore, running `pgcrypto` HMAC inside Postgres RLS on every query burns DB CPU and requires storing `PEPPER` inside `vault.decrypted_secrets` rather than outside the database.
3. **Cloudflare Worker + Durable Object (`origin/server-sync` — Strongest):**
   - Client sends `Authorization: Bearer <groupAuth>` and `X-Gimu-Name: <rsn>` on the `GET /v1/groups/{groupId}/live` WebSocket upgrade (`ServerDialer.java:134-138`).
   - `GroupHub.fetch()` calls `hub.admit()` *before* accepting the WebSocket (`server/src/index.ts:288-295`), verifying `HMAC-SHA256(env.PEPPER, groupAuth)` where `PEPPER` lives exclusively in Wrangler secrets (a leaked SQLite file cannot be replayed!).

### 4.2 Abuse, Rate Limiting, Key Rotation & Data Isolation

| Security Control | Supabase (Shared Multi-Tenant Project) | Cloudflare (`GroupHub` DO per Group) |
| :--- | :--- | :--- |
| **Data Isolation Between Groups** | **Logical (Shared Tables + RLS):** Every group's banks, stats, and settings sit in the same `public.snapshots` table. One missing `USING` clause, permissive policy mistake, or `SECURITY DEFINER` view bug exposes **all groups' data**. | **Physical (Database-per-Group):** Group A and Group B live in completely separate SQLite databases (`env.GROUPS.getByName(groupId)`). Cross-group SQL leakage is **architecturally impossible**, and there is no API to list DOs. |
| **Abuse & Rate Limiting** | **Weak without external infra:** `pgrst.db_pre_request` runs *inside Postgres* (abusive requests still burn connection pool slots & DB CPU). Realtime Broadcast has **no per-channel rate limit** — one malicious client spamming `100 msgs/s` exhausts the Free project's global Realtime rate limit for every group! Edge Function rate limiting requires adding third-party Upstash Redis. | **Multi-layered & Zero-Cost:** (1) Edge `ratelimits` binding (`10/60s` per IP before DO wake); (2) In-memory per-socket token bucket (`20` burst, `1 / 2s` refill in `Attachment`, `0` DB writes); (3) Per-group UTC daily quotas (`20,000` frames -> `4029`, `3,000` writes -> relay-only, `300` connects -> `429 Retry-After`); (4) `SYNC_ENABLED` kill switch. One abusive group can burn at most `~1%` of Free DO requests. |
| **Key Rotation & Member Kick** | Clicking `"New key"` generates a new `groupKey` -> new HKDF `groupId` + `groupAuth`. Old group sits until 90-day cron sweep, **or** if rotating in-place, stateless JWTs keep working until `exp` unless `key_ver` is checked on every DB call. | Clicking `"New key"` derives a brand-new HKDF `groupId` (new DO) immediately; `{"t":"del"}` or rotation in `hub.ts:674` synchronously closes all connected sockets (`4010`) and calls `ctx.storage.deleteAll()` in `< 5 ms`. Duplicate RSN login immediately kicks the old socket (`4001 replaced`). |

---

## 5. Java / RuneLite Client Fit & Plugin Hub Compliance

### 5.1 Hard RuneLite Plugin Hub Constraints (`AGENTS.md` & `scripts/preflight.sh`)

1. **`build=standard` (`runelite-plugin.properties:7`, `build.gradle:17-20, 78-80`):** At submission, the Plugin Hub packager **discards `build.gradle`** and compiles `src/main/java` against RuneLite's standard dependency set unless custom Maven dependencies are justified and audited line-by-line by maintainers.
2. **Forbidden / Mandatory Clients (`R06`, `R09`, `R10`, `R11`, `R16`):**
   - **Must use** `@Inject okhttp3.OkHttpClient` (`okHttpClient.newBuilder()` allowed for timeouts/pings; `new OkHttpClient()`, `java.net.http.HttpClient`, `HttpURLConnection`, Apache HttpClient, Ktor, and Netty are **bot-blocking rejections**).
   - **Must use** `@Inject com.google.gson.Gson` (`new Gson()` / Jackson / `kotlinx.serialization` rejected or disallowed).
   - **Zero reflection (`R06`)** and **pure Java 11 (`R11`)** (`javax.crypto.Mac` `HmacSHA256` is standard JDK 11).
   - **Non-blocking I/O (`R16`):** Only `enqueue()` and `okHttpClient.newWebSocket()`, marshaling back via `clientThread.invokeLater(...)`.
3. **200k Token Ceiling (`R05` — Critical Active Constraint):** Measured via `python3 tools/tokens.py` on `main` (`8e575a4`, 2026-11-20), `src/main/java` currently sits at **`251,450` bot-estimated tokens** (raw `chars/4`: `219,602`; limit `200,000`, target `< 190,000`; active shrink PR #119 / `RUNELITE-343`). **Every single line of client networking code directly competes with user-facing GIM features for the 200k Hub token budget.**

### 5.2 Client Implementation Fit: Supabase vs. Cloudflare

| Client Aspect | Supabase (Pure Java `OkHttpClient` + `Gson`) | Cloudflare (`origin/server-sync` as Built) |
| :--- | :--- | :--- |
| **Official Java SDK?** | **None.** Supabase only maintains `supabase-kt` (Kotlin + Ktor + Coroutines + `kotlinx.serialization`), which fails `build=standard`, `R09`, and Java-11-only packaging. Community Java wrappers pull heavy Jackson/OkHttp/Reactor dependencies. **Must hand-roll raw protocol code.** | **Not needed.** Speaks our own minimal JSON wire protocol directly over `OkHttpClient.newWebSocket()` + `Gson`. |
| **Subsystems Required in Java** | **3 separate subsystems:** <br>1. **Auth Client:** `POST /functions/v1/join` via `enqueue()`, JWT expiry tracking, scheduled refresh before `exp`, pushing `access_token` frame to open WS. <br>2. **Phoenix Channels v1.0.0 WS Client:** `phx_join` handshake with `join_ref`/`ref` state machine, mandatory **30s JSON `heartbeat` timer** (Phoenix drops sockets after ~60s without JSON heartbeats even if TCP/RFC-6455 pings succeed!), `phx_reply` error parsing, `broadcast` envelope wrapping. <br>3. **PostgREST Snapshot Client:** `POST /rest/v1/snapshots` (`Prefer: resolution=merge-duplicates`) every 5 min / bank close / logout + `GET /rest/v1/snapshots` on join + merge ordering with live WS frames. | **1 subsystem (already written & passing 20 tests):** <br>- `ServerKeys.java` (`104` lines): RFC 5869 HKDF-SHA256. <br>- `ServerDialer.java` (`103` lines): `http.newBuilder().pingInterval(30, SECONDS)` + `Retry-After` parser. <br>- `ServerLink.java` (`716` lines): single WebSocket state machine (`OFF`/`CONNECTING`/`CONNECTED`/`WAITING`/`REFUSED`), 5s slot coalescer, `1s -> 5m` backoff, `00:00 UTC` quota sleep. |
| **Estimated Code & Token Cost** | **`~1,400–1,800` lines** (`~14,000–19,000` Hub tokens), 3 failure surfaces (Auth HTTP + PostgREST HTTP + Elixir WS) | **`923` lines exact** (`~9,200` Hub tokens), **already written**, 1 failure surface |
| **WebSocket Allowed on Hub?** | **Yes** via `OkHttpClient.newWebSocket()` (`PartyService`/`WSClient` itself uses OkHttp WebSockets; `BRIEF.md` A13) | **Yes** via `OkHttpClient.newWebSocket()` (`ServerDialer.java:140`) |

---

## 6. Operations, Observability, Portability & GDPR

| Operational Area | Supabase (Postgres + Realtime + Edge Functions) | Cloudflare (Workers + SQLite Durable Objects) | Winner |
| :--- | :--- | :--- | :---: |
| **Deploys & CI** | Multi-part: `supabase db push` (SQL migrations), `supabase functions deploy` (Deno), plus Dashboard/config.toml Realtime settings. | **Single atomic command:** `npx wrangler deploy` deploys Worker routing + DO class + static SPA (`/g/`) together in `< 5s`. Secrets via `wrangler secret put PEPPER`. | **Cloudflare** |
| **Schema Migrations** | **Standard central SQL migrations** (`supabase/migrations/*.sql`) applied once atomically across all groups. Easy to add columns, backfill, or inspect via Table Editor / `psql`. | **Lazy per-object migrations:** Each DO runs `CREATE TABLE IF NOT EXISTS` / `PRAGMA user_version` upgrades inside `constructor` (`blockConcurrencyWhile`) when that group next connects. Trivial for additive changes (`origin/server-sync` has 3 tiny KV-like tables storing opaque JSON), harder for destructive cross-group SQL rewrites. | **Supabase** |
| **Backups & PITR** | **Free: None (`0` days).** Pro (`$25/mo`): automated daily backups (7-day retention). Point-in-Time Recovery (PITR) costs **`+$100/mo`** extra (7-day window). | **Built-in WAL streaming + 30-day PITR** on SQLite-backed Durable Objects (replicated across nearby colos + object storage WAL; Cloudflare D1 also includes 7-day Free / 30-day Paid Time Travel). | **Cloudflare** |
| **Monitoring & Logs** | **Free: 1-day log retention.** Pro: 7-day retention (`$60/mo` per external Log Drain; Team `$599/mo` for 28 days). Built-in Postgres Prometheus/Grafana metrics & Realtime inspector. | **Workers Observability (Logs + Traces):** **7-day retention on both Free (`0.5 GB/day`) and Paid (`50 GB/mo` included, `+$0.25/GB`)**, plus per-DO request/row/duration analytics (`wrangler.jsonc:143` `"observability": {"enabled": true}`). | **Cloudflare** *(on Free/low cost)* |
| **Region & Latency** | **Single primary Postgres region** (e.g. `us-east-1`): EU/AU players pay `120–280 ms` RTT on every PostgREST write & JWT/RLS channel join unless Read Replicas (`$60+/mo` each) are added. Realtime Elixir nodes are global. | **330+ edge colos for Worker + Auto-placed DO per group:** Each group's DO spawns automatically in the Cloudflare region closest to the **first member who creates the group** (US group gets US DO, EU group gets EU DO, AU group gets AU DO — `locationHint` optional!). | **Cloudflare** *(per-group locality!)* |
| **Vendor Lock-In** | **Low (Open Source):** Standard PostgreSQL 15/17 + PostgREST + Deno; self-hostable via `docker compose`. | **Medium-High Runtime Coupling, Mitigated in Code:** DO runtime is Cloudflare-specific (`workerd` open-source locally), **but `origin/server-sync` isolates 100% of business/SQLite logic in pure runtime-free TypeScript (`server/src/hub.ts`, tested under `node:sqlite`)**, leaving `index.ts` as a 140-line adapter! | **Supabase** |
| **Data Export & Deletion** | **Bulk export trivial** (`pg_dump`); per-group deletion is `DELETE FROM groups WHERE id = $1` (`ON DELETE CASCADE`, creates Postgres dead tuples cleaned by autovacuum). | **Per-group deletion is instant & physical** (`ctx.storage.deleteAll()` deletes the DO's entire SQLite storage + alarm in one call). **Bulk cross-group export requires an index** (DO namespaces have no runtime `listAllIds()` API unless group IDs are logged to D1/KV or exported via CF REST API). | **Tie** *(Supabase for bulk dump; CF for group wipe)* |
| **GDPR & Privacy** | Standard **GDPR DPA + SCCs** included; EU region pinning (`eu-central-1`); RSNs are pseudonymous personal data. Operator can view rows in Table Editor. | Standard **Cloudflare GDPR DPA + SCCs + EU-U.S. DPF** incorporated into ToS (`cloudflare.com/trust-hub/`); AES-256 at rest; optional `.jurisdiction("eu")` subnamespace; no IPs stored; auto-wipes snapshots at `30d` and idle groups at `90d` (`hub.ts:384-385`) + 1-click member delete (`{"t":"del"}`). | **Tie** |

---

## 7. Zero-Downtime Migration Path

### 7.1 Today's Reality (`0` Production Server Groups Yet)

Today (`2026-11-20`), `main` uses **RuneLite Party only**, and `origin/server-sync` (`ServerLink.BASE_URL = "wss://gim-utilities-server.vinnyvangogh.workers.dev"`) has **not been deployed or merged to `main` yet**. Therefore:
- **Party -> Server (either backend):** Already zero-downtime in `origin/server-sync`. Opted-in members connect to the server while staying subscribed to `PartyService` when in the group party; mixed groups (some on `serverSync=true`, some on Party) sync seamlessly, and duplicate payloads resolve idempotently via newest-`ts`-wins snapshots and `GroupEvent.sameAs` (`EventBackfill.java:41`).

### 7.2 Future Zero-Downtime Migration (Live Cloudflare DO <-> Supabase)

If GIM Utilities launches on Cloudflare DOs and later needs to migrate live groups to Supabase (or vice versa) **with zero downtime and without waiting weeks for every player to update via the Plugin Hub**:

1. **Key & Auth Continuity (Zero User Action):** Both backends derive `groupId` (`HKDF "gimu/v1/id"`) and `groupAuth` (`HKDF "gimu/v1/auth"`) from the same 128-bit `groupKey` and verify `HMAC-SHA256(PEPPER, groupAuth)`. Copy the exact **`PEPPER`** secret to the target backend: every existing group's credentials match on day one with **zero key rotation**.
2. **Self-Healing Client Cache (Why GIM Sync Rarely Needs Bulk DB ETL):** Every GIM Utilities client persists the whole group's banks, storage log, shopping lists, roles, and events locally in `.runelite/plugin-data/gim-utilities/group-cache.json` (`GroupService.java:2404-2430`) and re-sends its live state + bank on session/join. Even an empty target database self-hydrates as members log in!
3. **3-Phase Cutover Behind `gim-utilities-server.<custom-domain>`:**
   - *Prerequisite before v1 launch:* Use a custom subdomain we own (e.g. `wss://gimu-sync.<domain>/v1/...`) for `ServerLink.BASE_URL` rather than `*.workers.dev`, so DNS / edge routing stays under our control forever.
   - **Phase 1 — Dual-Write / Snapshot Seed (Days 1–7):** Add a lightweight `ctx.waitUntil()` background hook in `GroupHub.persist()` (or a one-shot export endpoint authenticated by an admin secret, iterating known `groupId`s recorded in a 1-column registry) that upserts `grp`, `members`, and `snap` rows into Supabase Postgres.
   - **Phase 2 — Edge WebSocket Adapter for Legacy Clients (Day 7):** Because deployed RuneLite jars speak the `GroupHub` 6-frame protocol (`t: "b"|"del"|"hi"|"j"|"l"|"e"`), keep the thin Cloudflare Worker at `gimu-sync.<domain>` acting as a stateless protocol translator (or ship Plugin vNext speaking the new backend directly while legacy clients hit `/v1/`).
   - **Phase 3 — Cutover & Automatic Party Fallback:** Flip traffic; any transient `503`/`1012` WebSocket close triggers `ServerLink`'s `1s` jittered reconnect (`ServerLink.java:875-897`) while `PartyService` + `group-cache.json` keep the in-game UI uninterrupted.

---

## 8. Master Decision Table & Final Recommendation

| Criterion | Weight | **Supabase** *(Postgres + Realtime + Edge Fn)* | **Cloudflare** *(Workers + SQLite DO)* | Notes |
| :--- | :---: | :---: | :---: | :--- |
| **1. Free-Tier Headroom** | High | **2 / 10** (`~2–19` groups; killed by `2M` msg fan-out & `5 GB` egress; `402` month-long block) | **9 / 10** (`~136` heavy – `~909` casual groups; resets nightly `00:00 UTC`; never pauses) | Use a dedicated Cloudflare account so Sepco's `100k/day` Worker pool is untouched. |
| **2. Paid Cost at 1k–10k Groups** | **Critical** | **1 / 10** (`$275–$2,780/mo` @ 1k; `$2,737–$27,930+/mo` @ 10k) | **10 / 10** (`$5.26–$8.14/mo` @ 1k; `$8.95–$75.23/mo` @ 10k; `$0` egress) | Plugin monetization is banned by Jagex/Hub; `$2,700/mo` is unsustainable out-of-pocket. |
| **3. Realtime Latency & Ordering** | High | **6 / 10** (Broadcast fast, but split from PostgREST persistence; no single-actor ordering) | **10 / 10** (Single actor per group = linearizable relay + local SQLite in one hop, auto-placed per group's region) | US, EU, and AU GIM teams each get a DO anchored in their own region automatically. |
| **4. Public-Client Security & DoS** | High | **5 / 10** (Shared tables + RLS blast radius; custom headers break Realtime; weak native rate limits) | **9.5 / 10** (Physical DB-per-group; pre-upgrade HMAC; zero-write memory token bucket + daily quotas) | Eliminates multi-tenant cross-group leak risk entirely. |
| **5. RuneLite Client & Token Fit** | **Critical** | **4 / 10** (No Java SDK; requires 3 custom protocols in `~1,500+` lines while `main` is at `251k/200k` tokens) | **10 / 10** (`923` lines, single WS protocol, **already written & passing tests on `origin/server-sync`**) | Saves ~5,000–10,000 Hub tokens and weeks of re-implementation + review risk. |
| **6. Ops, Backups & Portability** | Medium | **7 / 10** (Wins open-source Postgres + SQL migrations; loses Free backups `0d` & Free logs `1d`) | **8.5 / 10** (Single `wrangler deploy`, 30d WAL PITR, 7d Free logs; `hub.ts` already decoupled from runtime) | Add a custom domain before v1 launch so `BASE_URL` is never locked to `*.workers.dev`. |
| **Weighted Overall Score** | — | **3.8 / 10** | **9.6 / 10** | **Decision: Ship Cloudflare (`origin/server-sync`).** |

### Recommended Action Plan for the Board
1. **Stay on Cloudflare (`Workers + SQLite Durable Objects`)** using the implementation already built on `origin/server-sync` (PR #88).
2. **Isolate from Sepco:** Deploy `gim-utilities-server` under a **dedicated Cloudflare account** (Free tier to start, `$5/mo` Paid once active groups pass ~120) so GIM traffic can never touch Sepco's account-wide `100,000/day` Worker request quota.
3. **Use an owned custom domain** (e.g. `wss://sync.<our-domain>/v1`) for `ServerLink.BASE_URL` before merging `server-sync` to `main`, guaranteeing future backend portability without waiting on a Plugin Hub update.
4. **Update `protocol.ts` `DURABLE_KINDS`** before S0 deploy to include the post-branch `main` kinds (`req`, `rqc`, `pbs`, `ach`) and key multi-list kinds by ID (`shop:<id>`, `req:<id>`).

---

## Appendix A: Minimal Supabase Schema, RLS & Edge Function Sketch (If Supabase Were Chosen)

Per the task brief, if Supabase were ever selected despite the cost trade-offs, **do not use `Postgres Changes` and do not use custom headers for Realtime**. Use **Edge Function Custom JWTs + Realtime Private Broadcast + Coalesced PostgREST Snapshot Upserts**:

### A.1 PostgreSQL Schema & RLS (`supabase/migrations/0001_gim_sync.sql`)

```sql
-- Secret auth hashes live in a private schema never exposed by PostgREST
CREATE SCHEMA IF NOT EXISTS private;

CREATE TABLE public.groups (
  id          TEXT PRIMARY KEY CHECK (id ~ '^[a-z2-7]{26}$'),
  key_ver     INTEGER NOT NULL DEFAULT 1,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_active TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE private.group_secrets (
  group_id    TEXT PRIMARY KEY REFERENCES public.groups(id) ON DELETE CASCADE,
  auth_hmac   TEXT NOT NULL -- hex HMAC-SHA256(PEPPER, groupAuth)
);

CREATE TABLE public.members (
  group_id    TEXT NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
  rsn_key     TEXT NOT NULL CHECK (length(rsn_key) BETWEEN 1 AND 12),
  rsn_display TEXT NOT NULL CHECK (length(rsn_display) BETWEEN 1 AND 12),
  last_seen   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (group_id, rsn_key)
);

CREATE TABLE public.snapshots (
  group_id    TEXT NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
  rsn_key     TEXT NOT NULL,
  slot        TEXT NOT NULL CHECK (length(slot) BETWEEN 1 AND 32), -- e.g. 'bank:0', 'stats', 'gset'
  payload     TEXT NOT NULL CHECK (length(payload) <= 12000),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (group_id, rsn_key, slot)
);

ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.snapshots ENABLE ROW LEVEL SECURITY;

-- Helper extracting custom JWT claims minted by /functions/v1/join (no auth.users rows needed)
CREATE OR REPLACE FUNCTION public.jwt_group_id() RETURNS TEXT
  LANGUAGE sql STABLE AS $$ SELECT coalesce(auth.jwt() ->> 'group_id', '') $$;

CREATE OR REPLACE FUNCTION public.jwt_rsn_key() RETURNS TEXT
  LANGUAGE sql STABLE AS $$ SELECT coalesce(auth.jwt() ->> 'rsn_key', '') $$;

-- 1. PostgREST RLS: a member can read/delete only their own group, and write only their own RSN's slots
CREATE POLICY "group_read_snapshots" ON public.snapshots
  FOR SELECT TO authenticated
  USING (group_id = public.jwt_group_id());

CREATE POLICY "member_upsert_own_snapshots" ON public.snapshots
  FOR INSERT TO authenticated
  WITH CHECK (group_id = public.jwt_group_id() AND rsn_key = public.jwt_rsn_key());

CREATE POLICY "member_update_own_snapshots" ON public.snapshots
  FOR UPDATE TO authenticated
  USING (group_id = public.jwt_group_id() AND rsn_key = public.jwt_rsn_key())
  WITH CHECK (group_id = public.jwt_group_id() AND rsn_key = public.jwt_rsn_key());

CREATE POLICY "any_member_delete_group" ON public.groups
  FOR DELETE TO authenticated
  USING (id = public.jwt_group_id());

-- 2. Supabase Realtime Private Channel RLS (secures Broadcast & Presence on topic 'group:<groupId>')
CREATE POLICY "group_realtime_receive" ON realtime.messages
  FOR SELECT TO authenticated
  USING (
    realtime.messages.extension IN ('broadcast', 'presence')
    AND realtime.topic() = 'group:' || public.jwt_group_id()
  );

CREATE POLICY "group_realtime_send" ON realtime.messages
  FOR INSERT TO authenticated
  WITH CHECK (
    realtime.messages.extension IN ('broadcast', 'presence')
    AND realtime.topic() = 'group:' || public.jwt_group_id()
  );

-- 3. Retention sweep via pg_cron (30d snapshots, 90d idle groups)
-- SELECT cron.schedule('gim-retention', '15 3 * * *', $$
--   DELETE FROM public.snapshots WHERE updated_at < now() - interval '30 days';
--   DELETE FROM public.groups WHERE last_active < now() - interval '90 days';
-- $$);
```

### A.2 Edge Function JWT Mint (`supabase/functions/join/index.ts` Sketch)

```typescript
// Verifies HKDF groupAuth against private.group_secrets using PEPPER (Edge secret),
// creates the group on first join, enforces <= 8 members, and mints a 1h HS256 JWT
// signed with SUPABASE_JWT_SECRET for PostgREST + Realtime private channels.
import { createClient } from "jsr:@supabase/supabase-js@2";
import * as jose from "jsr:@panva/jose@6";

Deno.serve(async (req) => {
  const { groupId, groupAuth, rsn } = await req.json();
  // 1. Validate regexes (GROUP_ID /^[a-z2-7]{26}$/, GROUP_AUTH /^[A-Za-z0-9_-]{43}$/, RSN 1..12 chars)
  // 2. Compute mac = HMAC-SHA256(Deno.env.get("PEPPER")!, base64urlDecode(groupAuth))
  // 3. Call SECURITY DEFINER RPC via service_role client: admit_group_member(groupId, mac, rsn)
  //    -> constant-time compare (or insert on first join), check member count <= 8, return key_ver
  // 4. Sign custom JWT with role='authenticated', group_id=groupId, rsn_key=nameKey(rsn), exp=now+3600s
  const secret = new TextEncoder().encode(Deno.env.get("JWT_SIGNING_SECRET")!);
  const token = await new jose.SignJWT({ role: "authenticated", group_id: groupId, rsn_key: rsn.toLowerCase() })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setIssuedAt()
    .setExpirationTime("1h")
    .sign(secret);
  return Response.json({ access_token: token, expires_in: 3600 });
});
```

---

## Appendix B: Verification Log & Sources

### B.1 Verified vs. Unverified Claims

| Claim / Fact | Status | How Checked |
| :--- | :---: | :--- |
| `VinnyVanGogh/gim-utilities-server` repo does not exist yet; server code sits in `server/` on `origin/server-sync` (PR #88) | **Verified** | GitHub search (2026-11-20), `git ls-tree origin/server-sync`, `task-235f05b5` comment #20840 |
| `main` (`8e575a4`) has 19 wire kinds (`k`) and `251,450` bot-estimated tokens (`limit 200,000`) | **Verified** | Read all 6 `*Codec.java` files + ran `python3 tools/tokens.py` (2026-11-20) |
| Measured 5-member active traffic = `14,410` frames/day, `731` DO requests/day, `262` SQLite writes/day | **Verified** | Read `server/test/hub.test.ts:276-314` & `server/README.md:48-58` on `origin/server-sync` |
| Supabase Realtime Broadcast bills **1 sent + 1 per subscribed client receiving**; Free = `2M/mo`, Pro = `5M/mo` + `$2.50/M` | **Verified** | Official Supabase Realtime Pricing docs (2026-11-20) |
| Supabase Realtime Peak Connections: Free = `200`, Pro = `500` + `$10 / 1,000` package; msg/s cap = `100` Free, `500–2,500` Pro | **Verified** | Official Supabase Realtime Limits & Peak Connections docs (2026-11-20) |
| Supabase Free DB `500 MB` -> Read-Only Mode; `5 GB` egress -> HTTP `402`; `0` backups; `1d` log retention; pauses after `7d` DB inactivity | **Verified** | Official Supabase Pricing, Backups, Logs & Fair Use docs (2026-11-20) |
| Supabase Realtime WebSockets evaluate JWT `access_token` only, **not** custom HTTP headers in `request.headers` | **Verified** | Official Supabase Realtime Protocol & Authorization docs (2026-11-20) |
| Cloudflare DO Free: SQLite-only, `100k` req/day (`20:1` inbound WS, `0` outbound, `0` pings), `13k GB-s/day`, `50M` reads/day, `100k` writes/day, `5 GB` | **Verified** | Official Cloudflare Durable Objects Pricing & Limits docs (2026-11-20) |
| Cloudflare Workers Paid (`$5/mo`): `10M` Worker req, `1M` DO req (`+$0.15/M`), `400k GB-s` (`+$12.50/M`), `50M` SQLite writes (`+$1/M`), `$0` egress | **Verified** | Official Cloudflare Workers & DO Pricing docs (2026-11-20) |
| Whether Cloudflare DO SQLite's `100k/day` Free write meter is pooled with or separate from D1's `100k/day` write meter | **UNVERIFIED** | Not stated in Cloudflare docs; `origin/server-sync` drops D1 so only one meter is used; measure via `scripts/loadtest.ts` on deploy |
| Exact Worker CPU-ms consumed by HKDF/HMAC + 64–128 KB JSON validation against Workers Free `10 ms` CPU cap | **UNVERIFIED** | `gim-server.md` A9; must be measured via Cloudflare Observability CPU metrics after first deploy |
| Whether Supabase counts Phoenix `heartbeat` frames (every 30s) toward the `2M`/`5M` monthly Realtime message quota | **UNVERIFIED** | Supabase docs specify Broadcast, Presence, and Postgres Changes messages count; heartbeat billing is not explicitly stated (omitted from our Supabase cost totals, so Supabase costs above are conservative lower bounds) |

### B.2 Primary Official Sources Cited (Retrieved 2026-11-20 unless noted)

1. **Supabase Pricing & Plan Quotas:** `https://supabase.com/pricing` & `https://supabase.com/docs/guides/platform/org-based-billing` (retrieved 2026-11-20).
2. **Supabase Realtime Pricing & Fan-Out Message Counting:** `https://supabase.com/docs/guides/realtime/pricing` & `https://supabase.com/docs/guides/platform/manage-your-usage/realtime-messages` & `.../realtime-peak-connections` (retrieved 2026-11-20).
3. **Supabase Realtime Limits, Quotas & Payload Caps:** `https://supabase.com/docs/guides/realtime/limits` (retrieved 2026-11-20).
4. **Supabase Realtime Protocol (`phx_join`, `access_token`) & Private Channel RLS (`realtime.messages`):** `https://supabase.com/docs/guides/realtime/protocol` & `https://supabase.com/docs/guides/realtime/authorization` (retrieved 2026-11-20).
5. **Supabase PostgREST Custom Headers, `db_pre_request` Rate Limiting & Custom JWT RLS:** `https://supabase.com/docs/guides/api/securing-your-api` & `https://supabase.com/docs/guides/database/postgres/row-level-security` (retrieved 2026-11-20).
6. **Supabase Edge Functions Limits (`2s` CPU, `256 MB`, `150s`/`400s` wall clock):** `https://supabase.com/docs/guides/functions/limits` (retrieved 2026-11-20).
7. **Supabase Inactivity Pausing, Read-Only Mode, Backups, PITR & GDPR DPA:** `https://supabase.com/docs/guides/platform/backups`, `https://supabase.com/docs/guides/platform/logs`, `https://supabase.com/docs/guides/platform/read-only-mode`, & `https://supabase.com/legal/dpa` (retrieved 2026-11-20).
8. **Cloudflare Workers Pricing & Account Limits:** `https://developers.cloudflare.com/workers/platform/pricing/` & `https://developers.cloudflare.com/workers/platform/limits/` (retrieved 2026-11-11 & 2026-11-20).
9. **Cloudflare Durable Objects Pricing, SQLite Storage, 20:1 WebSocket Billing & Hibernation:** `https://developers.cloudflare.com/durable-objects/platform/pricing/`, `https://developers.cloudflare.com/durable-objects/platform/limits/`, & `https://developers.cloudflare.com/durable-objects/concepts/durable-object-lifecycle/` (retrieved 2026-11-18 & 2026-11-20).
10. **Cloudflare Durable Objects 30-Day PITR, Data Security & Jurisdiction (`eu`/`us`):** `https://developers.cloudflare.com/durable-objects/reference/data-location/`, `https://developers.cloudflare.com/durable-objects/platform/data-security/`, & `https://www.cloudflare.com/trust-hub/gdpr/` (retrieved 2026-11-20).
11. **Cloudflare D1, Workers KV & Observability (Workers Logs) Pricing:** `https://developers.cloudflare.com/d1/platform/pricing/`, `https://developers.cloudflare.com/kv/platform/pricing/`, & `https://developers.cloudflare.com/workers/observability/logs/` (retrieved 2026-11-20).
12. **RuneLite Plugin Hub Packaging, Guidelines & Precedent:** `https://github.com/runelite/plugin-hub` (`README.md`, `plugins/group-ironmen-tracker` commit `343af959`, retrieved 2026-11-11 & 2026-11-20), `https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features`, & `AGENTS.md` / `BRIEF.md` / `docs/plans/gim-server.md` in this repository.
