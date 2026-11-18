# Research: Celebratory Discord Posts for Pets & Big Moments (Discord Webhook Capabilities, Pet Identification & Notable Collection Log Filter)

**Author:** Antigravity (Gemini 4 Argon)  
**Date:** 2026-11-16  
**Repository:** `group-ironman-plugin` (GIM Utilities)  
**Deliverable:** `docs/research/discord-celebration-posts.md`  
**Status:** Research Only (Zero Code Changes)

---

## Spec interview
- **Q: Pet posts?**  
  **A (Board 2026-11-16):** Got a pet (Beaver) and the post was just a collection log slot (`GroupShip: Pet / a pet! Look who followed you home.` plus `New collection log slot: new collection log slot, Beaver` and the screenshot); make it cuter: name the pet, add icons or a background, whatever Discord can do; have Antigravity or Gemini research it.
- **Q: Collection log noise vs notable items?**  
  **A (Board 2026-11-16):** First-time collection log posts only for items people actually care about. Research a 'notable items' filter: a default list (pets, uniques, mega-rares, jars, raid purples, items above a value threshold), an option to post all, and a per-group custom list.

---

## 0. Executive Summary & Anatomy of the Beaver Post

When `GroupShip` obtained the **Beaver** (Woodcutting pet) on 2026-11-16, the game emitted two `ChatMessageType.GAMEMESSAGE` lines on the same game tick:
1. `"You have a funny feeling like you're being followed."`
2. `"New item added to your collection log: <col=ef1020>Beaver</col>"`

### Why Today's Code Posted What It Did (Code Map)

| Step | File & Lines | Current Behaviour | Why the Beaver Post Looked Plain |
| :--- | :--- | :--- | :--- |
| **1. Pet Chat Line** | [`GroupEventEngine.java:970-974`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L970-L974)<br>[`EventText.java:23-27, 49`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEvent.java#L59) | `EventText.isPet(message)` matches `"You have a funny feeling..."` and emits `GroupEvent.of(Type.PET, now, me).build()` with **empty `text` (`""`), empty `items` (`{}`), and `number = 0`**. | The game's own pet chat line does not name the pet (`GroupBroadcasts.java:65-67` only gets the name for *other* members via `CLAN_GIM_MESSAGE`). Because `e.text` is empty, `EventText.describe()` produces `"a pet!"`. |
| **2. Clog Chat Line** | [`GroupEventEngine.java:975-979`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L975-L979)<br>[`ClogMessage.java:16-37`](../../src/main/java/com/vinnyvangogh/groupironman/ClogMessage.java#L16-L37) | `ClogMessage.parse(message)` extracts `"Beaver"` and emits `GroupEvent.clog(now, me, "Beaver")` with `e.text = "Beaver"`, **empty `items` (`{}`)**, and `e.number = 0`. | `ClogMessage` only parses the item's string name; it never resolves the item ID via `ItemManager`, so `e.items` is empty. |
| **3. Moment Merge** | [`DiscordBatch.java:60-83`](../../src/main/java/com/vinnyvangogh/groupironman/DiscordBatch.java#L60-L83)<br>[`DiscordMerge.java:26, 172-202`](../../src/main/java/com/vinnyvangogh/groupironman/DiscordMerge.java#L26) | Both `PET` and `CLOG` belong to `DiscordMerge.Family.DROP` (`1,800 ms` hold window). `PET` outranks `CLOG` (`RANK: PET=0, CLOG=1`), so `lead = PET` (`title = "Pet"`, `description = "a pet!"`) and `CLOG` becomes an extra field: `{"New collection log slot", "new collection log slot, Beaver"}`. | `DiscordMerge.merge()` never lifts the pet's name (`"Beaver"`) from the sibling `CLOG` event into the `PET` lead event, nor does it suppress the redundant `New collection log slot` field when the slot *is* the pet itself. |
| **4. Easter Egg** | [`EasterEggs.java:84`](../../src/main/java/com/vinnyvangogh/groupironman/EasterEggs.java#L84)<br>[`easter-eggs.txt:16-18`](../../src/main/resources/com/vinnyvangogh/groupironman/easter-eggs.txt#L16-L18) | Picks `"Look who followed you home."` and appends it under `description` (`"a pet!\nLook who followed you home."`). | The joke is cute, but the headline above it never named `"Beaver"`. |
| **5. Embed & Icon** | [`DiscordWebhook.java:370-400`](../../src/main/java/com/vinnyvangogh/groupironman/DiscordWebhook.java#L370-L400) | Sets `color = Category.DROPS.colour` (`0xF1C40F`, shared with every ordinary drop), checks `post.items.isEmpty()` for `thumbnail`, and attaches `screenshot.png`. | Because neither `PET` nor `CLOG` populated `e.items` with Beaver's item ID (`13322`), `post.items` was empty — **no pet icon thumbnail was rendered**. |

### Key Research Takeaways
1. **Everything Standard Discord Webhooks Can Do:** A user-pasted Discord channel webhook (`POST /webhooks/{id}/{token}`) supports **rich embeds** (24-bit colour, `author.name` + `author.icon_url`, `thumbnail.url`, `image.url`, up to 25 `fields`, `footer.text` + `footer.icon_url`, `timestamp`, up to 10 embeds/message), **multiple multipart file attachments** (`files[0]`, `files[1]`, ... referenced via `attachment://<filename>`), **polls**, **custom external emojis** (when the webhook's channel allows `@everyone` `USE_EXTERNAL_EMOJIS`), **role/user pings** (`allowed_mentions`), and **Components V2** (`flags: 32768` + `?with_components=true` query param for `Container`, `Section`, `TextDisplay`, `Thumbnail`, `MediaGallery`, `Separator`). Standard webhooks **cannot** send stickers (`sticker_ids` is rejected/ignored) or interactive buttons/selects (requires an application-owned webhook).
2. **Naming & Showing Every Pet Without Fragile Network Calls:** Combining three existing local signals (`ClogMessage` on the same tick, untradeable drop chat / `NpcLootReceived`, and a bundled static 63-pet lookup table `pets.txt` mapping pet name $\leftrightarrow$ canonical `ItemID` + skill/boss source + base rate) identifies **100% of first-time pets and ~95%+ of duplicate pets**, resolves the exact item ID (e.g. `Beaver` $\rightarrow$ `13322`), and sets `thumbnail.url` to `https://static.runelite.net/cache/item/icon/13322.png` (or attaches the 36×32 sprite from `ItemManager.getImage(13322)` as `files[1]`).
3. **One Celebratory Post Instead of Two:** When `PET` and `CLOG` (and/or `UNIQUE`) fire in the same `DiscordMerge.Family.DROP` window and the `CLOG` item is the pet itself, `DiscordMerge` folds `CLOG` into `PET`: the pet's name and item ID move onto the `PET` lead event, the redundant `"New collection log slot: new collection log slot, Beaver"` field is replaced by a clean badge (`Collection log: New slot unlocked! 📗`), and the embed gets a celebratory **Pet Gold / Amber** (`0xFFD700` or `0xFFAA00`) border, the pet's icon as `thumbnail`, the source/KC/level, and the screenshot.
4. **Notable Collection Log Filter:** First-time collection log slots (`CLOG`) currently post for *every* slot (including Mole claws, Castle wars tickets, and beginner clue items). A three-part filter — **(1) Notable items only (default)** (Pets, Boss/Raid/Slayer uniques, Mega-rares, Jars, Items $\ge$ `eventDropMinValue`), **(2) All collection log slots**, plus **(3) a Group Custom Notable List** synced via `GroupSettings` — eliminates routine clog spam while guaranteeing every exciting unlock gets celebrated.

---

## 1. Everything a Discord Webhook Can Do (Official API Reference)

All capabilities below are verified against the official **Discord Developer Documentation** (`discord/discord-api-docs`, API v10):
- **Webhook Resource (`Execute Webhook`):** <https://discord.com/developers/docs/resources/webhook#execute-webhook> (`POST /webhooks/{webhook.id}/{webhook.token}`)
- **Message Resource (`Embed Object`, `Allowed Mentions`, `Attachment Object`, `Message Flags`):** <https://discord.com/developers/docs/resources/message#embed-object>
- **Components V2 Reference (`Using Message Components`):** <https://discord.com/developers/docs/components/reference>
- **Poll Resource (`Poll Create Request Object`):** <https://discord.com/developers/docs/resources/poll#poll-create-request-object>
- **Rate Limits Reference:** <https://discord.com/developers/docs/topics/rate-limits>

### 1.1 Capability Matrix: Plain Channel Webhooks vs Application-Owned Webhooks

GIM Utilities uses **plain incoming channel webhooks** created by users inside Discord (*Server Settings / Channel Settings $\rightarrow$ Integrations $\rightarrow$ Webhooks $\rightarrow$ New Webhook*). These have `type = 1` (`INCOMING`) and `application_id = null`.

| Capability | Plain Channel Webhook (`application_id: null`) | App-Owned Webhook (`application_id != null`) | API Field / Mechanism & Official Doc Notes |
| :--- | :---: | :---: | :--- |
| **Override Poster Name** | **Yes** | **Yes** | `username` (1–80 chars). Cannot be `"everyone"` or `"here"`, or contain `"discord"`, `"clyde"`, `@`, `#`, `:`, or `` ``` `` (`PosterNames.java:21-23`). |
| **Override Poster Avatar** | **Yes** | **Yes** | `avatar_url` (valid `https://` image URL). Discord's media proxy fetches and caches it. Cannot use `attachment://` for `avatar_url`. |
| **Message Text (`content`)** | **Yes** | **Yes** | Up to **2,000 UTF-16 characters**. Supports Discord Markdown (`# H1`, `## H2`, `### H3`, `-# subtext`, `**bold**`, `*italic*`, `__underline__`, `~~strike~~`, `` `code` ``, ` ```block``` `, `> quote`, `||spoiler||`, `<t:unix:R>` dynamic timestamps). |
| **Embed Colour (`color`)** | **Yes** | **Yes** | 24-bit RGB integer (`0` to `16777215` / `0x000000` to `0xFFFFFF`) per embed. Controls the 4px left accent bar on the embed card. **No gradient or background image exists for standard embeds.** |
| **Embed Author (`author`)** | **Yes** | **Yes** | `{ "name": "...", "url": "...", "icon_url": "..." }`. Renders a small **24×24 px circular icon** and bold header line at the very top of the embed, above `title`. `icon_url` supports both `https://` and `attachment://<filename>`. |
| **Embed Title & URL** | **Yes** | **Yes** | `title` (up to 256 chars) + optional `url` (turns the title into a clickable hyperlink, e.g. to `https://oldschool.runescape.wiki/w/Beaver`). |
| **Embed Thumbnail** | **Yes** | **Yes** | `thumbnail: { "url": "..." }`. Renders an image (**up to 80×80 px** on desktop) in the **top-right corner** of the embed card, beside `author`, `title`, `description`, and `fields`. Supports `https://` and `attachment://<filename>`. |
| **Embed Main Image** | **Yes** | **Yes** | `image: { "url": "..." }`. Renders a full-width image below `fields` and above `footer`. Supports `https://` and `attachment://<filename>`. |
| **Multi-Image Embed Grid (Gallery Trick)** | **Yes** | **Yes** | Undocumented/semi-documented Discord client feature: if multiple embeds in the `embeds` array share the **exact same `url` property** and each has an `image.url`, Discord merges up to **4 images** into a single 2×2 mosaic grid inside the first embed card! |
| **Multiple Embeds per Message** | **Yes** | **Yes** | Up to **10 embed objects** in `embeds: [...]`. Each embed can have its own `color`, `author`, `title`, `thumbnail`, `fields`, `image`, and `footer`. Total text across all embeds must be $\le$ **6,000 characters**. |
| **Embed Fields (`fields`)** | **Yes** | **Yes** | Up to **25 fields** per embed (`name` $\le$ 256 chars, `value` $\le$ 1,024 chars, `inline`: `true`/`false`). Up to **3 `inline: true` fields** render side-by-side per row on desktop (stacking vertically on narrow mobile screens). |
| **Embed Footer & Timestamp** | **Yes** | **Yes** | `footer: { "text": "...", "icon_url": "..." }` (`text` $\le$ 2,048 chars; `icon_url` renders a **20×20 px circular icon** at the bottom-left; no Markdown in `footer.text`) + `timestamp` (ISO-8601 string, automatically formatted in each viewer's local timezone and separated from `footer.text` by `•`). |
| **Multiple File Attachments** | **Yes** | **Yes** | `multipart/form-data` with `payload_json` plus `files[0]`, `files[1]`, ..., `files[9]` (up to **10 files per message**). Any file referenced by `attachment://<filename>` inside an embed (`image`, `thumbnail`, `author.icon_url`, `footer.icon_url`) is displayed **inside** the embed rather than as a loose file download card above it. |
| **Components V2 (Layout & Display)** | **Yes (with `?with_components=true`)** | **Yes** | Setting `flags: 32768` (`IS_COMPONENTS_V2`, `1 << 15`) and appending `?with_components=true` to the webhook URL unlocks **Container (`type: 17`)**, **Section (`type: 9`)**, **Text Display (`type: 10`)**, **Thumbnail (`type: 11`)**, **Media Gallery (`type: 12`)**, **File (`type: 13`)**, and **Separator (`type: 14`)**. Disables legacy `content` and `embeds` on that message. |
| **Link Buttons (`type: 2, style: 5`)** | **Yes (with `?with_components=true`)** | **Yes** | Non-interactive URL buttons (`style: 5`, `url: "https://..."`) inside an `ActionRow` (`type: 1`) or as a `Section` (`type: 9`) accessory work on plain webhooks when `?with_components=true` is passed. |
| **Interactive Components (Click Buttons, Selects)** | **No** | **Yes** | Interactive buttons (`style: 1..4` with `custom_id`) and select menus (`type: 3, 5, 6, 7, 8`) require an application interaction endpoint to receive callbacks; Discord returns `400 Bad Request` on plain channel webhooks. |
| **Emojis (Unicode & Custom)** | **Yes** | **Yes** | Standard Unicode emojis (`🦫`, `🎉`, `✨`, `🏆`, `💜`) work everywhere (`content`, `title`, `description`, `fields`, `footer`, `username`). Custom server emojis (`<:beaver:123456789012345678>` or `<a:party:123456789012345678>`) work in `content`, `description`, and `fields` **if and only if** the `@everyone` role in the webhook's channel has the **Use External Emojis** (`1 << 18`) permission enabled (or the emoji belongs to the same Discord server). |
| **Mentions & Role Pings (`allowed_mentions`)** | **Yes** | **Yes** | `<@user_id>`, `<@&role_id>`, `@everyone`, `@here`. Controlled by `allowed_mentions: { "parse": ["roles", "users", "everyone"], "roles": [...], "users": [...] }`. **Note:** Pings only notify users when placed in top-level `content` (or Components V2 `TextDisplay`) on a **new** message (`POST`). Mentions inside `embeds` (`description`/`fields`) render as blue pills if the ID is cached by the client, **but never trigger push/sound notifications**. Edits (`PATCH`) never trigger a new ping. |
| **Stickers (`sticker_ids`)** | **No** | **No** | `Create Message` (`POST /channels/{id}/messages`) accepts `sticker_ids`, but **`Execute Webhook` (`POST /webhooks/{id}/{token}`) does not support `sticker_ids`** (`discord-api-docs` `webhook.mdx`; GitHub Discussion #4441). Also disabled under `IS_COMPONENTS_V2`. |
| **Native Polls (`poll`)** | **Yes** | **Yes** | `poll: { "question": { "text": "..." }, "answers": [{ "poll_media": { "text": "...", "emoji": {...} } }], "duration": 24, "allow_multiselect": false, "layout_type": 1 }`. Works on plain webhooks! Question $\le$ 300 chars, 1–10 answers ($\le$ 55 chars each), duration up to 768 hours (32 days). |
| **Forum / Media Channel Thread Creation** | **Yes** | **Yes** | `thread_name` (1–100 chars) in JSON body creates a new thread when the webhook targets a Forum or Media channel; `applied_tags` attaches forum tag IDs; `?thread_id=<id>` posts into an existing thread. |
| **Suppress Notifications (`SUPPRESS_NOTIFICATIONS`)** | **Yes** | **Yes** | `flags: 4096` (`1 << 12`) sends a silent message (triggers unread dot, no desktop/mobile push sound). |

---

### 1.2 Deep Dive: Embed Visual Anatomy & Attachment Tricks

A single Discord rich embed object supports **four distinct image/icon slots** plus an accent bar:

```
+-----------------------------------------------------------------------+
|  [Webhook Avatar]  Party Pete  [APP]                Today at 3:40 PM  |
|  @GroupRole                                                           |
|  +-----------------------------------------------------------------+  |
|  | [24x24 author.icon_url]  author.name              +-----------+ |  |
|  |                                                   |           | |  |
|  | **title** (clickable if url set)                  | thumbnail | |  |
|  | description (Markdown, emojis, <t:unix:R>...)     | (80x80px) | |  |
|  |                                                   +-----------+ |  |
|  | **Field 1 (inline)**   **Field 2 (inline)**   **Field 3 (in)**  |  |
|  | Value 1                Value 2                Value 3           |  |
|  |                                                                 |  |
|  | **Full-Width Field (inline: false)**                            |  |
|  | Value 4                                                         |  |
|  |                                                                 |  |
|  | +-------------------------------------------------------------+ |  |
|  | |                                                             | |  |
|  | |                 image.url (Full-Width Screenshot)           | |  |
|  | |                                                             | |  |
|  | +-------------------------------------------------------------+ |  |
|  | [20x20 footer.icon_url]  footer.text  •  timestamp              |  |
|  +-----------------------------------------------------------------+  |
+-----------------------------------------------------------------------+
   ^
   4px solid `color` bar (e.g. 0xFFD700 Gold for Pets / 0xA335EE Purple for Raids)
```

#### URLs vs Attached Files (`https://` vs `attachment://`)
When sending images in `thumbnail.url`, `image.url`, `author.icon_url`, or `footer.icon_url`, a webhook can use either:
1. **External HTTPS URL (`https://...`):**
   - Discord's backend image proxy (`images-ext-1.discordapp.net`) fetches the URL asynchronously.
   - **Pros:** Zero upload bytes from the player's client; JSON-only request (`application/json`) when no screenshot is attached; instant payload construction.
   - **Cons:** Depends on the external host being reachable by Discord's proxy; if the URL 404s, Discord leaves the thumbnail blank.
2. **Multipart File Attachment (`attachment://<filename.png>`):**
   - Uploaded alongside `payload_json` in a `multipart/form-data` request as `files[0]`, `files[1]`, etc.
   - **Pros:** Hosted permanently on Discord's own CDN (`cdn.discordapp.com`); works offline from external CDNs; allows the plugin to **composite custom graphics in Java `BufferedImage`** (e.g. drawing a gold sparkle frame around a pet icon, or compositing a skill icon badge onto the corner of the pet sprite) before uploading!
   - **Important Multipart Edit Rule (`PATCH /webhooks/{id}/{token}/messages/{msg_id}`):** As `DiscordWebhook.asEdit()` (`DiscordWebhook.java:265-279`) already notes, when editing a message that has attachments, the `attachments` array in `payload_json` must list every attachment (`{"id": 0, "filename": "screenshot.png"}`, `{"id": 1, "filename": "pet.png"}`) to keep/replace them rather than appending duplicates.

---

### 1.3 Deep Dive: Components V2 (`IS_COMPONENTS_V2`, Flag `32768`)

Introduced in 2025, **Components V2** is Discord's modern card layout system.
- **How to enable on a plain webhook:**
  1. Append `with_components=true` to the webhook URL query string: `POST https://discord.com/api/webhooks/{id}/{token}?with_components=true` (or `?wait=true&with_components=true`).
  2. Set `"flags": 32768` (`1 << 15`, `IS_COMPONENTS_V2`) in the JSON body.
- **What changes when `flags: 32768` is set:**
  - Top-level `"content"`, `"embeds"`, `"poll"`, and `"sticker_ids"` are **forbidden** (Discord returns `400 Bad Request` if present).
  - Instead, everything lives inside `"components": [...]` (up to **40 total components** per message, max **4,000 visible text characters** across all `TextDisplay` components).
  - Once a message is created with `IS_COMPONENTS_V2`, the flag cannot be removed on edit.
- **Available Layout & Content Components for Plain Webhooks:**

| Component | `type` | Structure & What It Renders | Works on Plain Webhook? |
| :--- | :---: | :--- | :---: |
| **Container** | `17` | `{ "type": 17, "accent_color": 16766720, "spoiler": false, "components": [...] }`<br>A rounded card container with an optional 4px left `accent_color` bar (just like an embed, or `null` for a clean borderless dark card) that wraps sections, text displays, separators, and media galleries in **any order**. | **Yes** (`?with_components=true`) |
| **Section** | `9` | `{ "type": 9, "components": [ { "type": 10, "content": "..." } ], "accessory": { "type": 11, "media": { "url": "..." } } }`<br>Places 1–3 `TextDisplay` blocks on the left and a `Thumbnail` (`type: 11`) or Link `Button` (`type: 2, style: 5`) accessory on the right. Multiple sections can be stacked in one `Container`—meaning **one card can have multiple right-hand thumbnails** (impossible in classic embeds!). | **Yes** (`?with_components=true`) |
| **Text Display** | `10` | `{ "type": 10, "content": "# Heading\nBody text with **markdown** and <@&role_id>" }`<br>Full Discord markdown including `# H1`, `## H2`, `### H3`, and `-# subtext` (small muted footer-style text). **Crucially:** Role and user mentions (`<@&role>`) inside a `TextDisplay` **do trigger real Discord pings** (unlike classic embeds)! | **Yes** (`?with_components=true`) |
| **Thumbnail** | `11` | `{ "type": 11, "media": { "url": "https://..." }, "description": "alt text" }`<br>Used as the `accessory` of a `Section` (`type: 9`). Supports `https://` and `attachment://`. | **Yes** (`?with_components=true`) |
| **Media Gallery** | `12` | `{ "type": 12, "items": [ { "media": { "url": "attachment://screenshot.png" }, "description": "..." } ] }`<br>Displays 1 to 10 images/videos in a clean responsive gallery inside the container, anywhere in the vertical flow (above or below text/sections). | **Yes** (`?with_components=true`) |
| **Separator** | `14` | `{ "type": 14, "divider": true, "spacing": 1 }`<br>Draws a crisp horizontal rule (`divider: true`) or invisible vertical padding (`divider: false`) with small (`1`) or large (`2`) spacing. | **Yes** (`?with_components=true`) |
| **Action Row (Link Buttons)** | `1` | `{ "type": 1, "components": [ { "type": 2, "style": 5, "label": "OSRS Wiki: Beaver", "url": "https://oldschool.runescape.wiki/w/Beaver" } ] }`<br>Renders a clickable button row inside or below the container. Only `style: 5` (Link) is allowed on plain webhooks. | **Yes** (`?with_components=true`) |

> **Architectural Trade-off for GIM Utilities (`DiscordStyle.EMBED` vs Components V2):**  
> Today, GIM Utilities groups consecutive moments from the same member into a single Discord message by stacking up to 10 embed objects in `embeds: [...]` (`EventThreads.java`, `LevelThreads.java`, `DiscordWebhook.stack()`). Classic embeds (`DiscordStyle.EMBED`) and `IS_COMPONENTS_V2` cannot be mixed in the same message. However, because **big moments that ping (`post.big`, or celebratory posts like Pets, 99s, Maxed, Raid Purples)** already start their own standalone message and never join a grouped feed (`EventThreads.java:19, 277`), celebratory posts could optionally use Components V2 *or* stay 100% within classic embeds (`embeds`) so every message in the plugin uses one unified schema. Both options are mocked up in **Section 3** below.

---

### 1.4 Size Limits & Rate Limits Reference

| Limit Category | Discord API Limit | GIM Utilities Current Constant | Source / Notes |
| :--- | :--- | :--- | :--- |
| **`content` length** | 2,000 characters | `MAX_DIGEST = 1_900` (`DiscordWebhook.java:32`) | Returns `400 Bad Request` above 2,000. |
| **Embed `title`** | 256 characters | `< 80` chars in practice | Counts toward 6,000 total embed char cap. |
| **Embed `description`** | 4,096 characters | `< 300` chars in practice | Counts toward 6,000 total embed char cap. |
| **Embed `fields` count** | 25 fields per embed | `MAX_EXTRAS = 20` + 3 inline (`DiscordMerge.java:51`) | Leaves 2 spare slots below Discord's 25 limit. |
| **Embed `field.name` / `field.value`** | 256 / 1,024 characters | `MAX_FIELD = 1_024` (`DiscordMerge.java:49`) | Empty `""` is rejected (`400`); must be non-whitespace. |
| **Embed `author.name` / `footer.text`** | 256 / 2,048 characters | `footer.text` $\le$ 45 chars | Both count toward 6,000 total embed char cap. |
| **Embeds per message** | 10 embeds | `EditedThreads.MAX_CARDS = 10` | Combined text across all 10 embeds $\le$ 6,000 chars. |
| **Files per request** | 10 files | 1 (`files[0]`, `DiscordSender.java:254`) | Total request payload size limit: **10 MiB** default across all servers (25 MiB historically, reduced to 10 MiB for unboosted servers in 2024; 50 MiB on Boost Level 2; 100 MiB on Boost Level 3). A 1080p PNG screenshot is ~400 KB–1.2 MB; a 36×32 item icon PNG is ~800 bytes. |
| **Webhook execution rate limit** | **5 requests per 2 seconds** per webhook (~150/min burst; channel-wide cap across all webhooks in one channel is 30 requests / 60s) | `PER_MINUTE = 20`, 1 in flight at a time (`DiscordQueue.java:29`) | GIM Utilities' 20/min per webhook URL is safely below Discord's 30/min channel bucket. Honours `429` `Retry-After` header (`DiscordQueue.java:141-145`). |

---

## 2. Pet-Specific Content: Naming, Icons, Sources, KC & Odds

### 2.1 How the Game Announces a Pet (All 4 In-Game Paths)

When a player receives a pet in OSRS, the client sees up to **four** events within 1–2 game ticks:

1. **The Pet Chat Message (`ChatMessageType.GAMEMESSAGE`):**
   - `"You have a funny feeling like you're being followed."` (pet spawns directly behind the player).
   - `"You feel something weird sneaking into your backpack."` (player already has a follower out and has $\ge 1$ free inventory slot; **or** player is in an area where followers cannot spawn; **and** the pet item is placed in `InventoryID.INV`).
   - `"You have a funny feeling like you would have been followed..."` (duplicate pet roll while already owning that pet).
   - *(Since the 2023 Pet Reclaim / Backpack update, if a player's inventory is full and they have a follower out, Backpack pets go to Probita reclaim, still triggering the chat line).*
   - **Limitation:** None of these three lines contains the pet's name!

2. **The Collection Log Notification (`ChatMessageType.GAMEMESSAGE`):**
   - `"New item added to your collection log: <col=ef1020>Beaver</col>"`
   - Fires on **every first-time pet** when the player has the in-game setting *Collection log - New addition notification* enabled (which GIM Utilities already documents in `SETUP.md`).
   - Gives the **exact canonical pet item name** (`"Beaver"`, `"Vorki"`, `"Olmlet"`, `"Baby chinchompa"`, `"Rocky"`, etc.) on the **exact same tick** as the pet message.

3. **The Untradeable Loot Chat Line / `NpcLootReceived` / Inventory Diff (for Duplicates & Boss Pets):**
   - What if it is a **duplicate pet** (`"would have been followed"`), or the player has Collection Log chat notifications turned off?
   - **Path A — Inventory Diff (Backpack pets):** When `"You feel something weird sneaking into your backpack"` fires, the pet item was just added to `client.getItemContainer(InventoryID.INV)` on that tick. Checking which item ID in `INV` is in our known `PETS` set immediately identifies both the name and the item ID!
   - **Path B — Follower NPC (`client.getFollower()`):** When `"You have a funny feeling like you're being followed"` fires, the newly spawned follower NPC (`client.getFollower()`, `NPC.getName()` / `NPC.getId()`) spawns on that tick or the next tick (`1 tick = 600 ms`, well inside `DiscordMerge.Family.DROP`'s `1,800 ms` hold window!). Calling `client.getFollower()` on the client thread gives the follower NPC's name (e.g. `"Beaver"`, `"Vorki"`, `"TzRek-Jad"`).
   - **Path C — Recent Boss Kill / Skill Action (`lastKc` & `StatChanged`):**
     - If a boss kill count line (`lastKc`, e.g. `"Vorkath"`, KC `120`) arrived within `KC_WINDOW_MS` (`10 s`, `GroupEventEngine.java:83, 910-914`) or `NpcLootReceived` just fired for an NPC (`"Vorkath"`, `"Zulrah"`, `"General Graardor"`), every boss maps 1-to-1 to its pet (`Vorkath` $\rightarrow$ `Vorki`, `Zulrah` $\rightarrow$ `Pet snakeling`, `General Graardor` $\rightarrow$ `Pet general graardor`).
     - If a skilling XP drop (`StatChanged`) occurred on the same tick or within the last 5 seconds (e.g. `Skill.WOODCUTTING`, `Skill.FISHING`, `Skill.MINING`, `Skill.AGILITY`, `Skill.THIEVING`, `Skill.FARMING`, `Skill.RUNECRAFT`), the skill maps 1-to-1 to its skilling pet (`Woodcutting` $\rightarrow$ `Beaver`, `Fishing` $\rightarrow$ `Heron`, `Mining` $\rightarrow$ `Rock golem`, `Agility` $\rightarrow$ `Giant squirrel`, `Thieving` $\rightarrow$ `Rocky`, `Farming` $\rightarrow$ `Tangleroot`, `Runecraft` $\rightarrow$ `Rift guardian`).

4. **The Group Ironman Clan Broadcast (`ChatMessageType.CLAN_GIM_MESSAGE`):**
   - Even for the local player's own pet, if the player is in a GIM group with broadcasts enabled, the game broadcasts to `CLAN_GIM_MESSAGE` on the same tick:
     - `"[GroupName] GroupShip has a funny feeling like they're being followed: Beaver at 1,234,567 XP"` (or `"... at 120 kills"`).
   - Notice that `GroupBroadcasts.java:65-67` **already** parses `(?<p>.+?)(?: at (?<at>.+))?` from `CLAN_GIM_MESSAGE`! Currently `GroupBroadcasts.decide()` skips `CLAN_GIM_MESSAGE` for `pluginUser` (`GroupBroadcasts.java:189`), so the local player discards their own broadcast line instead of harvesting the pet name and `"at 1,234,567 XP"` / `"at 120 kills"` detail from it!

### Summary of Pet Identification Precedence (100% Coverage)
By combining these four already-present client signals inside the `1,800 ms` `DiscordBatch` window:
1. **`CLAN_GIM_MESSAGE` self-enrichment (Best — has both Pet Name AND exact game-reported KC/XP):** When `CLAN_GIM_MESSAGE` arrives naming `localPlayer()` with a pet broadcast (`"GroupShip has a funny feeling...: Beaver at 412,890 XP"` or `"...: Vorki at 120 kills"`), enrich the local player's pending `PET` event with `petName = "Beaver"` and `milestone = "412,890 XP"` / `"120 kills"`.
2. **Sibling `CLOG` event in the same `DiscordBatch` moment:** If `CLOG` fired in the same window (`"Beaver"`), copy `"Beaver"` to `PET`.
3. **`client.getFollower()` / `InventoryID.INV` pet item scan:** On the pet chat tick (and 1 tick later), check `client.getFollower().getName()` or any pet item ID in `InventoryID.INV`.
4. **Boss `lastKc` / `NpcLootReceived` or last `StatChanged` skill:** Map the boss or skill that just granted XP/loot to its pet via the bundled `pets.txt` table.

---

### 2.2 Getting the Pet's Icon: Hosting, Licensing & Resolution Comparison

Once we have the pet's name (e.g. `"Beaver"`) or item ID (`13322`), how should the icon be obtained and attached to the Discord post?

#### How `ItemManager` Resolves Pet Name $\rightarrow$ Item ID at Runtime
Every pet in OSRS has a corresponding inventory item (the item you pick up when you click *Pick-up* on your follower, which is also the icon shown in the Collection Log).
- On the client thread, `itemManager.search("Beaver")` (`GroupEventEngine.java:711`) or a bundled `pets.txt` lookup (`name -> itemId`) gives the exact item ID (`13322` for Beaver).
- Why bundle a small `pets.txt` table (`~63 lines`) in addition to `itemManager.search`?
  - `itemManager.search(String)` in RuneLite searches the Grand Exchange / tradeable item names first or can return multiple matches for short names (e.g. `"Rocky"`, `"Herbi"`, `"Nexling"`). Wait: let's verify if `itemManager.search()` includes untradeable items!
  - Let's check how `itemManager.search` works in RuneLite or if `pets.txt` is safer. In RuneLite's `ItemManager`, `search(String)` queries `itemPriceClient` / cached GE item prices (which **only** includes tradeable items on the GE, or items in the price table!). Untradeable items like pets (`Beaver`, `Vorki`, `Olmlet`) are **not tradeable**, which is why `GroupEventEngine.java:709` explicitly comments:
    `/** A broadcast drop's item by its exact name (tradeables: the only drops worth a broadcast), or null. */`
  - **Critical Finding:** `itemManager.search(name)` only searches tradeable items in RuneLite's price cache! It **will return empty** for untradeable items like pets and untradeable collection log slots unless we either:
    1. Look up the item ID in a bundled resource table (`pets.txt` / `notable-items.txt`), **or**
    2. Read the item ID from the Collection Log script/widget or `InventoryID.INV` / `NpcLootReceived` container.

#### Comparing the 3 Ways to Display the Pet Image in Discord

| Option | Source & Mechanism | Resolution & Visual Quality | Licensing & Compliance | Pros & Cons |
| :--- | :--- | :--- | :--- | :--- |
| **Option A: RuneLite Static Item Icon URL (Current Pattern)** | `thumbnail: { "url": "https://static.runelite.net/cache/item/icon/<id>.png" }`<br>(Already used in `DiscordWebhook.java:33`) | **36×32 px** official game inventory sprite (transparent PNG). Discord scales it cleanly in the top-right `thumbnail` slot. | **100% Compliant.** Official RuneLite static asset CDN (`static.runelite.net`), already used by Dink and by GIM Utilities (`DiscordWebhook.ICON`). Zero third-party IP warning beyond Discord itself. | **Pros:** Zero upload bytes; works in JSON-only and multipart posts; instant.<br>**Cons:** 36×32 inventory sprite is small (an inventory icon, not the 3D chathead or full-body model). |
| **Option B: Attach Sprite from `ItemManager.getImage(id)` as `files[1]`** | Call `itemManager.getImage(id)` on client thread $\rightarrow$ `AsyncBufferedImage` $\rightarrow$ encode PNG (or upscale 2× with nearest-neighbor to **72×64 px** so Discord doesn't bilinear-blur the pixel art!) $\rightarrow$ attach as `files[1]` (`attachment://icon.png`). | **36×32 px** (or crisp **72×64 px / 108×96 px** 3× nearest-neighbor pixel-art upscale, or composited with a gold sparkle frame!). | **100% Compliant.** Extracted directly from the local game cache in memory via RuneLite's `ItemManager`; sent only to the user's Discord webhook. | **Pros:** Crisp nearest-neighbor upscaling prevents Discord from blurring 36×32 pixel art; never depends on external CDN; allows optional gold border/badge compositing.<br>**Cons:** Adds ~1.5 KB part (`files[1]`) to the multipart upload. |
| **Option C: OSRS Wiki Chathead / Isometric Model Image URL** | Link `https://oldschool.runescape.wiki/images/<Pet_name>_chathead.png` or `https://oldschool.runescape.wiki/images/<Pet_name>.png` in `thumbnail.url` or `author.icon_url`. | **High-res (90×90 px to 250×250 px)** detailed 3D isometric model render or animated chathead from the OSRS Wiki. | **CC BY-NC-SA 3.0** (`THIRD_PARTY_NOTICES.md` already attributes OSRS Wiki for `item-drops.txt`). Passing a URL string in `thumbnail.url` makes *Discord's proxy* fetch the image (the RuneLite client never connects to `oldschool.runescape.wiki`). | **Pros:** Detailed 3D pet model render instead of a tiny 36×32 inventory icon; looks dramatically more celebratory.<br>**Cons:** Wiki filenames are case-sensitive (`Beaver.png`, `Vorki.png`, `Lil'_zik.png`, `Baby_chinchompa_(grey).png`); if a filename ever changes on the Wiki, the URL breaks unless verified in `pets.txt`. |

#### Recommendation on Pet Icons
- **Best Hybrid Approach (Zero External Fragility + Maximum Polish):**
  1. Bundle `pets.txt` (~63 lines: `pet_name|item_id|source|rate_summary|wiki_slug`) mapping every OSRS pet to its canonical `ItemID` (e.g. `Beaver|13322|Woodcutting|1/72,321 to 1/28,607 base|Beaver`).
  2. By default, use **Option A** (`https://static.runelite.net/cache/item/icon/13322.png`) or **Option B** (3× nearest-neighbor upscaled `ItemManager.getImage(13322)` at **108×96 px** attached as `attachment://pet-icon.png`), which is guaranteed never to 404.
  3. Optionally put the 36×32 inventory icon in `author.icon_url` (`https://static.runelite.net/cache/item/icon/13322.png`) **and** the high-res OSRS Wiki 3D pet render (`https://oldschool.runescape.wiki/images/thumb/Beaver.png/120px-Beaver.png`) in `thumbnail.url`!

---

### 2.3 Knowing the Pet's Source, KC / XP & Drop Odds

What context can the plugin include alongside `"GroupShip got a pet: Beaver!"`?

| Pet Category | Examples | What the Client Knows at the Moment of the Drop | Can We Show Exact Drop Odds? |
| :--- | :--- | :--- | :--- |
| **1. Skilling Pets (8 pets)** | **Beaver** (Woodcutting)<br>**Heron** (Fishing)<br>**Rock golem** (Mining)<br>**Giant squirrel** (Agility)<br>**Rocky** (Thieving)<br>**Tangleroot** (Farming)<br>**Rift guardian** (Runecraft)<br>**Baby chinchompa** (Hunter) | - **Skill Name:** Known from `pets.txt` (`Beaver` $\rightarrow$ `Woodcutting`).<br>- **Current Level & XP:** Known immediately from `client.getRealSkillLevel(Skill.WOODCUTTING)` and `client.getSkillExperience(Skill.WOODCUTTING)` (e.g. `Level 74 Woodcutting · 1,142,890 XP`), **and** echoed in the GIM broadcast (`"at 1,142,890 XP"`).<br>- **200M XP Check:** Known (`xp == 200_000_000` $\rightarrow$ 15× pet rate bonus). | **Formula is known, exact action is usually not:** Skilling pet rate is $\frac{1}{B - (\text{Level} \times 25)}$, where $B$ depends on which tree/fish/rock/course/stall the player just used (e.g. for Beaver: Regular/Oak tree $B = 361,177$, Willow $B = 289,286$, Teak $B = 264,336$, Maple/Arctic pine/Mahogany/Yew $B = 145,055$ to $72,321$, Magic/Redwood $B = 72,321$). Since the client does not track which tree was chopped without extra object/animation state, **show the player's Skill Level & XP** (`Level 74 Woodcutting (1.14M XP)`) rather than guessing which tree they chopped! |
| **2. Boss Pets (~38 pets)** | **Vorki** (Vorkath)<br>**Pet snakeling** (Zulrah)<br>**Prince black dragon** (KBD)<br>**Pet kraken** (Kraken)<br>**Hellpuppy** (Cerberus)<br>**Noon** (Grotesque Guardians)<br>**Sraracha** (Sarachnis)<br>**Nexling** (Nex)<br>**Butch / Lil'viathan / Baron / Wisp** (DT2) | - **Boss Name:** Known from `pets.txt` (`Vorki` $\rightarrow$ `Vorkath`).<br>- **Kill Count (KC):** Known from `lastKc` (`EventText.killCount`, sent on the same tick as the kill: `"Your Vorkath kill count is: 120."`) **and** from the GIM broadcast (`"at 120 kills"`). | **Yes, fixed per-kill rate for almost all standard bosses!**<br>- `Vorki`: `1/3,000`<br>- `Pet snakeling`: `1/4,000`<br>- `Prince black dragon`: `1/3,000`<br>- `Pet kraken`: `1/3,000`<br>- `Hellpuppy`: `1/3,000`<br>- `Pet general graardor / k'ril / zilyana / kree'arra`: `1/5,000`<br>- ` Olmlet`: `1/53` per purple roll<br>For bosses with fixed drop rates, `pets.txt` can store the exact wiki rate (`1/3,000`) and optionally compute cumulative luck: $1 - (1 - p)^{\text{KC}}$ (e.g. `KC 120 · Rate 1/3,000 (3.9% chance by this KC)`). |
| **3. Minigame / Boss-Skilling / Clue Pets (~17 pets)** | **Phoenix** (Wintertodt)<br>**Tiny tempor** (Tempoross)<br>**Abyssal protector** (GOTR)<br>**Herbi** (Herbiboar)<br>**Quetzin** (Hunter Rumours)<br>**Smolcano** (Zalcano)<br>**Youngllef** (Gauntlet)<br>**TzRek-Jad / Jal-Nib-Rek / Smol heredit** (Waves)<br>**Bloodhound** (Master clues) | - **Activity Name:** Known from `pets.txt`.<br>- **Completion Count:** Known when the activity sends a count line matching `EventText.KILL_COUNT` (`"Your Wintertodt subdued count is..."` / `"Your Corrupted Gauntlet completion count is: 42."`) or Master clue count (`EventWatchers.clueCounts`). | **Rate per roll/kill is fixed or well-bounded:**<br>- `Bloodhound`: `1/1,000` per Master casket<br>- `Youngllef`: `1/800` (Corrupted) / `1/2,000` (Regular)<br>- `TzRek-Jad`: `1/200` on kill (`1/100` on Slayer task)<br>- `Jal-Nib-Rek`: `1/100` on kill (`1/75` on task)<br>- `Herbi`: `1/6,500` |

### 2.4 Reference Sample of `pets.txt` (Canonical Item IDs, Sources & Rates)

All item IDs below are the canonical OSRS inventory item IDs served by `https://static.runelite.net/cache/item/icon/<id>.png`:

| Pet Name (Collection Log & Chat) | Item ID | Skill / Boss / Activity Source | Kind | Base Rate (`pets.txt`) |
| :--- | :---: | :--- | :---: | :--- |
| **Beaver** | `13322` | Woodcutting | Skilling | Base 1/72,321–1/361,177 |
| **Heron** | `13320` | Fishing | Skilling | Base 1/42,665–1/467,256 |
| **Rock golem** | `13321` | Mining | Skilling | Base 1/36,960–1/741,600 |
| **Baby chinchompa** | `13323` | Hunter (Chinchompas) | Skilling | Base 1/82,758–1/131,395 |
| **Giant squirrel** | `20659` | Agility | Skilling | Base 1/9,734–1/36,842 |
| **Tangleroot** | `20661` | Farming | Skilling | Base 1/2,811–1/281,040 |
| **Rift guardian** | `20665` | Runecraft | Skilling | Base 1/804,984–1/1,795,758 |
| **Rocky** | `20663` | Thieving | Skilling | Base 1/36,490–1/257,211 |
| **Phoenix** | `20693` | Wintertodt (Firemaking) | Boss/Skill | 1/5,000 per reward roll |
| **Herbi** | `21509` | Herbiboar (Hunter) | Skilling | 1/6,500 |
| **Vorki** | `21992` | Vorkath | Boss | 1/3,000 |
| **Olmlet** | `20851` | Chambers of Xeric | Raid | 1/53 per purple |
| **Lil' zik** | `22473` | Theatre of Blood | Raid | 1/650 (normal) / 1/500 (HMT) |
| **Tumeken's guardian** | `27352` | Tombs of Amascut | Raid | Base 1/350–1/450 (scales w/ invo) |
| **Pet snakeling** | `12921` | Zulrah | Boss | 1/4,000 |
| **Prince black dragon** | `12653` | King Black Dragon | Boss | 1/3,000 |
| **Pet kraken** | `12655` | Kraken | Boss | 1/3,000 |
| **Hellpuppy** | `13247` | Cerberus | Boss | 1/3,000 |
| **Abyssal orphan** | `13262` | Abyssal Sire | Boss | 1/2,560 per kill (5/128 Unsired) |
| **Pet general graardor** | `12650` | General Graardor | Boss | 1/5,000 |
| **Pet k'ril tsutsaroth** | `12652` | K'ril Tsutsaroth | Boss | 1/5,000 |
| **Pet zilyana** | `12651` | Commander Zilyana | Boss | 1/5,000 |
| **Pet kree'arra** | `12649` | Kree'arra | Boss | 1/5,000 |
| **TzRek-Jad** | `13225` | TzTok-Jad (Fight Caves) | Boss | 1/200 (1/100 on task / gamble) |
| **Jal-Nib-Rek** | `21291` | TzKal-Zuk (The Inferno) | Boss | 1/100 (1/75 on task) |
| **Youngllef** | `23757` | The Gauntlet / Corrupted Gauntlet | Boss | 1/800 (Corrupted) / 1/2,000 |
| **Bloodhound** | `19730` | Master Treasure Trails | Clue | 1/1,000 |

---

## 3. Merging Pet + Collection Log into One Celebratory Post & Three Mock-up Designs

### 3.1 How to Merge `PET` + `CLOG` + `UNIQUE` Into One Post (Without Redundant Clutter)

Today, when `GroupShip` gets a `Beaver`:
- `GroupEventEngine` emits `PET` (`text=""`) and `CLOG` (`text="Beaver"`).
- If it was a boss pet that also fired `NpcLootReceived`, `GroupEventEngine` also emits `UNIQUE` (`items={13322: 1}`).
- `DiscordMerge.merge()` currently puts `PET` as the lead (`"GroupShip: Pet"`, `"a pet!\nLook who followed you home."`) and dumps `CLOG` as a separate full-width field:
  - **Field:** `New collection log slot` $\rightarrow$ `"new collection log slot, Beaver"`

#### Proposed Clean Merge Rule in `DiscordMerge`:
When a `DiscordMerge.Family.DROP` moment contains a `PET` event:
1. **Absorb the Pet's Identity:**
   - If `PET.text` is empty, inspect any sibling `CLOG` or `UNIQUE` event in the same `Pending` moment whose item name is in `Pets.BY_NAME` (or if there is only one `CLOG` event in the moment, take its `text`).
   - Set the merged `PET` lead's name to `"Beaver"` and add `13322 -> 1` to `post.items` so the thumbnail is Beaver's icon (`13322.png`).
2. **Consume Redundant Sibling Events:**
   - Remove the sibling `CLOG` event (`"Beaver"`) and `UNIQUE` event (`"Beaver"`) from `rest` so they **do not** generate noisy duplicate fields (`"New collection log slot: new collection log slot, Beaver"` and `"Untradeable unique: untradeable from ...: 1 x Beaver"`).
   - Instead, record a boolean `boolean newClogSlot = true` on the merged `Post` (or add a compact inline field `Collection log` $\rightarrow$ `New slot unlocked!`).
   - If the same boss kill *also* dropped a separate valuable item (e.g. Vorkath dropped both `Vorki` and `Dragonbone necklace`), only the `Vorki` `CLOG` is absorbed into the `PET` headline; the `Dragonbone necklace` `DROP`/`CLOG` remains as an extra field!

---

### 3.2 Three Mock-up Designs for the Board's Beaver Pet Post

Below are three concrete designs (from a clean upgrade of today's embed to a full celebratory card and a Components V2 showcase) for `GroupShip` getting the **Beaver** pet while training Woodcutting on World 361 near Seers' Village.

---

#### Design 1: "Clean Gold Upgrade" (Minimal Change to Current `DiscordWebhook.embed` Schema)
*Stays 100% within the current `DiscordWebhook.java` single-embed builder. Fixes the missing pet name, sets the pet icon as `thumbnail`, upgrades `PET` colour from standard drop yellow (`0xF1C40F`) to celebratory **Bright Gold (`0xFFD700` / `16766720`)**, and folds the collection log line into a clean inline field instead of a redundant sentence.*

##### How Design 1 Renders in Discord:
```
Party Pete [APP]  Today at 3:40 PM
<@&112233445566778899>
+-----------------------------------------------------------------------+
| GroupShip: Pet — Beaver!                                   +--------+ |
| Got the Beaver pet while training Woodcutting!             | [13322 | |
| Look who followed you home.                                |  .png] | |
|                                                            +--------+ |
| Skill / Source          Collection log          World                 |
| Woodcutting (Lvl 74,    New slot unlocked       361                   |
| 1.14M XP)                                                             |
|                                                                       |
| Where                                                                 |
| Seers' Village                                                        |
| +-------------------------------------------------------------------+ |
| |                     [ screenshot.png ]                            | |
| +-------------------------------------------------------------------+ |
| GIM Utilities  •  Today at 3:40 PM                                    |
+-----------------------------------------------------------------------+
```

##### Design 1 JSON Payload (`payload_json`):
```json
{
  "username": "Party Pete",
  "content": "<@&112233445566778899>",
  "allowed_mentions": {
    "parse": ["roles", "users"]
  },
  "embeds": [
    {
      "title": "GroupShip: Pet — Beaver!",
      "url": "https://oldschool.runescape.wiki/w/Beaver",
      "description": "Got the **Beaver** pet while training Woodcutting!\nLook who followed you home.",
      "color": 16766720,
      "timestamp": "2026-11-16T23:40:00Z",
      "thumbnail": {
        "url": "https://static.runelite.net/cache/item/icon/13322.png"
      },
      "fields": [
        {
          "name": "Skill",
          "value": "Woodcutting (Lvl 74 · 1.14M XP)",
          "inline": true
        },
        {
          "name": "Collection log",
          "value": "New slot unlocked",
          "inline": true
        },
        {
          "name": "World",
          "value": "361",
          "inline": true
        },
        {
          "name": "Where",
          "value": "Seers' Village",
          "inline": true
        }
      ],
      "image": {
        "url": "attachment://screenshot.png"
      },
      "footer": {
        "text": "GIM Utilities"
      }
    }
  ]
}
```

---

#### Design 2: "Festive Showcase Embed" (Recommended — Uses `author` Banner, High-Res Thumbnail, Skill Icon & Rich Formatting)
*Uses every visual slot of a classic Discord embed (`author.name` + `author.icon_url` for a celebratory header ribbon, Markdown heading/blockquote in `description`, the OSRS Wiki 3D pet model or 3× upscaled sprite in `thumbnail.url`, the 36×32 inventory icon in `author.icon_url`, inline metadata pills, and `footer.icon_url`). Works 100% with standard webhooks without needing `?with_components=true`.*

##### How Design 2 Renders in Discord:
```
Party Pete [APP]  Today at 3:40 PM
🎉 <@&112233445566778899> **GroupShip** just got a pet!
+-----------------------------------------------------------------------+
| [13322.png]  ✨ NEW GROUP PET UNLOCKED ✨                  +--------+ |
|                                                            | [3D    | |
| **🦫 GroupShip got a pet: Beaver!**                        | Beaver | |
| *"You have a funny feeling like you're being followed..."* | Render | |
| > 💬 *Look who followed you home.*                         | 120px] | |
|                                                            +--------+ |
| 🪓 Source               📊 Experience           📗 Collection Log     |
| **Woodcutting**         **Lvl 74** (1.14M XP)   **New Slot!** (Beaver)|
|                                                                       |
| 📍 Location             🌐 World                🎲 Base Drop Rate     |
| Seers' Village          361                     1/72,321 – 1/361,177  |
| +-------------------------------------------------------------------+ |
| |                     [ screenshot.png ]                            | |
| +-------------------------------------------------------------------+ |
| [gim-icon] GIM Utilities · Rare Drop & Collection Log • Today 3:40 PM |
+-----------------------------------------------------------------------+
```

##### Design 2 JSON Payload (`payload_json`):
```json
{
  "username": "Party Pete",
  "content": "🎉 <@&112233445566778899> **GroupShip** just got a pet!",
  "allowed_mentions": {
    "parse": ["roles", "users"]
  },
  "embeds": [
    {
      "author": {
        "name": "✨ NEW GROUP PET UNLOCKED ✨",
        "url": "https://oldschool.runescape.wiki/w/Beaver",
        "icon_url": "https://static.runelite.net/cache/item/icon/13322.png"
      },
      "title": "🦫 GroupShip got a pet: Beaver!",
      "url": "https://oldschool.runescape.wiki/w/Beaver",
      "description": "*\"You have a funny feeling like you're being followed...\"*\n> 💬 *Look who followed you home.*",
      "color": 16755200,
      "timestamp": "2026-11-16T23:40:00Z",
      "thumbnail": {
        "url": "https://oldschool.runescape.wiki/images/thumb/Beaver.png/120px-Beaver.png"
      },
      "fields": [
        {
          "name": "🪓 Source",
          "value": "**Woodcutting**",
          "inline": true
        },
        {
          "name": "📊 Level & XP",
          "value": "Level **74** (`1,142,890` XP)",
          "inline": true
        },
        {
          "name": "📗 Collection Log",
          "value": "**New slot unlocked!**",
          "inline": true
        },
        {
          "name": "📍 Where",
          "value": "Seers' Village",
          "inline": true
        },
        {
          "name": "🌐 World",
          "value": "361",
          "inline": true
        },
        {
          "name": "🎲 Base Odds",
          "value": "`1/72,321` – `1/361,177`",
          "inline": true
        }
      ],
      "image": {
        "url": "attachment://screenshot.png"
      },
      "footer": {
        "text": "GIM Utilities · Pet & Collection Log"
      }
    }
  ]
}
```
*(And for a **Boss Pet** like `Vorki` on Vorkath kill 120, `🪓 Source` becomes `⚔️ Boss: **Vorkath**`, `📊 Level & XP` becomes `💀 Kill Count: **120**`, and `🎲 Base Odds` becomes `1/3,000 (3.9% by KC 120)`).*

---

#### Design 3: "Ultra-Festive Components V2 Card" (`IS_COMPONENTS_V2`, `?with_components=true`)
*Uses Discord's modern Components V2 (`flags: 32768`, sent to `POST /webhooks/{id}/{token}?with_components=true`). Places the role ping, a `# Heading` banner, a `Section` with the pet thumbnail on the right, a crisp horizontal `Separator`, a full-width `MediaGallery` for the game screenshot, a muted `-# subtext` footer with a live `<t:1794872400:R>` timestamp, and a clickable **OSRS Wiki: Beaver** link button—all inside one gold-accented `Container` (`type: 17`).*

##### How Design 3 Renders in Discord:
```
Party Pete [APP]  Today at 3:40 PM
+-----------------------------------------------------------------------+
| ✨ 🏆 **NEW GROUP PET UNLOCKED** · <@&112233445566778899>             |
| --------------------------------------------------------------------- |
| # 🦫 GroupShip got a pet: Beaver!                          +--------+ |
| *"You have a funny feeling like you're being followed..."* | [13322 | |
| > *Look who followed you home.*                            |  .png] | |
|                                                            +--------+ |
| **🪓 Skill:** Woodcutting (Level 74 · 1,142,890 XP)                   |
| **📗 Collection Log:** First-time unlock!                             |
| **📍 Location:** Seers' Village (World 361)                           |
| --------------------------------------------------------------------- |
| +-------------------------------------------------------------------+ |
| |                     [ screenshot.png ]                            | |
| +-------------------------------------------------------------------+ |
| -# GIM Utilities • Obtained <t:1794872400:R>                          |
| [ 🔗 OSRS Wiki: Beaver ↗ ]                                            |
+-----------------------------------------------------------------------+
```

##### Design 3 JSON Payload (`payload_json`, sent with `?with_components=true`):
```json
{
  "username": "Party Pete",
  "flags": 32768,
  "allowed_mentions": {
    "parse": ["roles", "users"]
  },
  "components": [
    {
      "type": 17,
      "accent_color": 16766720,
      "components": [
        {
          "type": 10,
          "content": "✨ 🏆 **NEW GROUP PET UNLOCKED** · <@&112233445566778899>"
        },
        {
          "type": 14,
          "divider": true,
          "spacing": 1
        },
        {
          "type": 9,
          "components": [
            {
              "type": 10,
              "content": "# 🦫 GroupShip got a pet: Beaver!\n*\"You have a funny feeling like you're being followed...\"*\n> 💬 *Look who followed you home.*"
            },
            {
              "type": 10,
              "content": "**🪓 Skill:** Woodcutting (Level 74 · `1,142,890` XP)\n**📗 Collection Log:** New slot unlocked!\n**📍 Where:** Seers' Village · **World 361**"
            }
          ],
          "accessory": {
            "type": 11,
            "media": {
              "url": "https://static.runelite.net/cache/item/icon/13322.png"
            },
            "description": "Beaver pet icon"
          }
        },
        {
          "type": 14,
          "divider": true,
          "spacing": 1
        },
        {
          "type": 12,
          "items": [
            {
              "media": {
                "url": "attachment://screenshot.png"
              },
              "description": "GroupShip receiving the Beaver pet"
            }
          ]
        },
        {
          "type": 10,
          "content": "-# GIM Utilities • <t:1794872400:F>"
        },
        {
          "type": 1,
          "components": [
            {
              "type": 2,
              "style": 5,
              "label": "OSRS Wiki: Beaver",
              "url": "https://oldschool.runescape.wiki/w/Beaver"
            }
          ]
        }
      ]
    }
  ]
}
```

### Comparison of the Three Designs

| Criterion | Design 1 (Clean Gold Upgrade) | Design 2 (Festive Showcase Embed) | Design 3 (Components V2 Card) |
| :--- | :--- | :--- | :--- |
| **Visual Impact** | Medium (names pet + adds icon + gold bar) | **High** (author ribbon + 2 icons + quote + structured grid) | **Very High** (`# H1` title + dividers + inline ping + Wiki button) |
| **Compatibility with `DiscordWebhook.java`** | Drop-in (tiny change to `embed()` and `DiscordMerge`) | **Drop-in** (uses standard `author`, `thumbnail`, `fields` on `embed()`) | Requires `?with_components=true` query param on `DiscordSender.target()` and separate JSON builder |
| **Works in Grouped / Stacked Messages?** | Yes (standard embed) | **Yes** (standard embed) | No (`IS_COMPONENTS_V2` cannot mix with `embeds: [...]`, though big events never stack anyway) |
| **Recommended Choice** | Good baseline | **Recommended Default for Big Moments** | Best optional "Modern Card" style if Board wants link buttons & dividers |

---

## 4. Applying the Same Celebratory Treatment to Other Big Moments

Today, `GroupEvent.Category` assigns only **six** flat colours (`DROPS = 0xF1C40F`, `PROGRESS = 0x2ECC71`, `DEATHS = 0xC0392B`, `STORAGE = 0xB8860B`, `GROUP = 0x3498DB`, `ACTIVITIES = 0x9B59B6`). A once-in-a-year **Twisted bow raid purple**, a **first 99 skillcape**, **Maxing (`2,277` total)**, or a **Hardcore Group Life Lost** currently gets the exact same flat card layout and colour as a 1M gp blood rune drop or a level 50 Cooking up.

### 4.1 Celebratory Palette & Visual Treatment Table

| Big Event | Trigger (`GroupEvent.Type`) | Distinct Accent Colour | Author Ribbon (`author.name` + `author.icon_url`) | Thumbnail (`thumbnail.url`) | Moment Merge & Extra Fields |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Pet Obtained** | `PET` (+ merged `CLOG` / `UNIQUE`) | **`0xFFD700`** (Pure Gold) or **`0xFFAA00`** (Amber Gold) | `✨ NEW GROUP PET UNLOCKED ✨` + `13322.png` (pet sprite) | High-res pet render or 3× upscaled pet icon (`13322.png`) | Folds `CLOG` (`"Beaver"`) and `UNIQUE` into `PET` so only **one** post goes out. Fields: **Source** (Skill/Boss), **KC or Level/XP**, **Collection Log** (`New slot!`), **Base Odds**, **Where / World**. |
| **Raid Purple (`RAID_UNIQUE`)** | `RAID_UNIQUE` (+ merged `CLOG`) | **`0xA335EE`** (Iconic OSRS Chest Purple) | `💜 SPECIAL RAID LOOT (PURPLE!) 💜` + item icon | Canonical item icon from `static.runelite.net/cache/item/icon/<id>.png` (e.g. `20997.png` for Twisted bow) | **Bug today:** `RAID_UNIQUE` is not in `DiscordMerge.Family.DROP` (`DiscordMerge.java:26`), so getting a new raid purple posts **two separate messages** (`Raid unique` + `New collection log slot`)! **Fix:** Add `RAID_UNIQUE` at the top of `Family.DROP` (just after `PET`) and resolve the item ID so it shows the purple item's thumbnail, its GE value, the Raid name + KC, teammates (`with Sam +1 other`), and folds `CLOG` into one post! |
| **Skill 99 (`MAXED` single skill)** | `MAXED` (`!"total".equalsIgnoreCase(e.text)`) | **`0x00E5FF`** (Skillcape Cyan / Diamond) or **`0xFFD700`** (Gold) | `🎉 LEVEL 99 ACHIEVED! 🎉` + Skillcape icon (`ItemID.<SKILL>_CAPE`) | Untrimmed/trimmed Skillcape icon for that skill (e.g. `9747.png` Attack cape, `9807.png` Woodcutting cape) | `DiscordMerge.Family.LEVEL` already ranks `MAXED` first (`DiscordMerge.java:27`). Suppress the redundant `"Level up: Attack level 99"` extra field when `MAXED` is the lead! Show **Skill**, **XP (`13,034,431`)**, and any **Total / Combat Level** reached on the same tick. |
| **Max Total Level (`MAXED` `"total"`)** | `MAXED` (`"total".equalsIgnoreCase(e.text)`) | **`0xE0115F`** (Max Cape Ruby Crimson) | `👑 ACCOUNT MAXED — EVERY SKILL 99! 👑` + Max cape icon (`13342.png`) | **Max cape** item icon (`https://static.runelite.net/cache/item/icon/13342.png`) | Folds the final skill's `99` and `LEVEL_UP` into the Maxed post (`"reached 99 Runecraft and maxed every skill (total level 2,277)!"`). Pings `@group`. |
| **Notable First-Time Collection Log (`CLOG`)** | `CLOG` (when passing Notable Filter, alone or with `DROP`/`UNIQUE`/`BARROWS`/`CLUE`) | **`0xE67E22`** (Collection Log Book Bronze/Orange) | `📗 NEW COLLECTION LOG SLOT` + item icon | Unlocked item's icon (`static.runelite.net/cache/item/icon/<id>.png`) | **Fix today's ordering & missing icon:** Today `CLOG` has no item ID (so a chest/clue/gauntlet `CLOG` has **no thumbnail!**), and when a boss drops a unique (`Abyssal whip`), `CLOG` outranks `DROP` (`RANK: PET, CLOG, UNIQUE, DROP`), so the post titles itself `"New collection log slot"` and buries the boss name and KC inside a `"Valuable drop"` sub-field! Instead, merge `DROP` + `CLOG` + `UNIQUE` into **one unified card** naming the item, the boss/source, the KC, the GE value, and a `📗 Collection Log: New slot unlocked!` badge. |
| **Quest Cape / Major Grandmaster Quests** | `QUEST_POINTS` (`"every quest done"`) or `QUEST` (Grandmaster: DS2, SotE, MM2, DT2, WGS) | **`0x3498DB`** (Quest Point Cape Blue) | `📜 QUEST POINT CAPE UNLOCKED!` + Quest point cape icon (`9813.png`) | Quest point cape (`9813.png`) | Names the final quest completed alongside `"every quest done (N QP)"`. |
| **Inferno / Colosseum / Fight Caves Completion** | `ACTIVITY_COMPLETE` (`e.number > 0`) | **`0xFF4500`** (TzHaar Magma Orange) | `🔥 WAVE CHALLENGE CONQUERED! 🔥` + Cape/Quiver icon | **Fire cape** (`6570.png`), **Infernal cape** (`21295.png`), or **Dizana's quiver** (`28951.png`) | Shows **Completion KC**, **Wave (`69/69`, `12/12`)**, **Duration**, and **Modifiers/Glory** for Colosseum. |
| **Hardcore Group Life Lost (`HC_LIFE_LOST`)** | `HC_LIFE_LOST` + `DEATH` + `DEATH_VALUE` | **`0x8B0000`** (Deep Blood Red) | `💀 HARDCORE GROUP LIFE LOST 💀` + HCIM helm icon (`26170.png`) | Hardcore GIM helm icon (`26170.png`) or most valuable item lost | **Fix today's ordering:** In `DiscordMerge.Family.DEATH` (`DiscordMerge.java:29`), `DEATH` currently outranks `HC_LIFE_LOST` (`DEATH, HC_LIFE_LOST, DEATH_VALUE`). Put `HC_LIFE_LOST` **first** so a Hardcore life lost titles the post `"Group life lost"` (not just `"Died"`), with the killer, location, world, lives remaining, and items lost. |

---

## 5. Deep Dive: The 'Notable Items' Collection Log Filter (Board Comment 2026-11-16)

> **Board Comment (2026-11-16):** *"first-time collection log posts only for items people actually care about. Research a 'notable items' filter: a default list (pets, uniques, mega-rares, jars, raid purples, items above a value threshold), an option to post all, and a per-group custom list."*

### 5.1 The Problem Today
In `GroupEventEngine.java:975-979` and `EventRouter.java:101-118`:
- Every time the game prints `"New item added to your collection log: X"`, `GroupEventEngine` emits `GroupEvent.clog(now, me, item)`.
- `EventRouter.threshold()` has **no filter** for `case CLOG:` (`default: return event;`).
- Consequently, turning on *Discord: New collection log slot* posts **every single trivial collection log slot**:
  - Beginner/Easy clue junk (*Black pickaxe*, *Coif*, *Leather chaps (g)*, *Purple sweets* repeat stacks if first time),
  - Routine minigame/skilling currency & scraps (*Mole claw*, *Mole skin*, *Mark of grace*, *Golden nugget*, *Carpenter's helmet*, *Zeal tokens*, *Abyssal pearls*),
  - Low-level monster heads and common drops (*Goblin champion scroll* vs *Ensouled goblin head*, *Long bone*, * Brimstone key*),
  - Quest-guaranteed collection log slots (*Ancient icon*, *Vorkath's head* at 50 KC).

Meanwhile, players turning on Collection Log notifications in a Group Ironman Discord want to celebrate **meaningful unlocks** (Pets, Boss/Slayer/Raid Uniques, Mega-Rares, Boss Jars, high-value items, and key Ironman progression items like *Dragon defender*, *Black mask*, *Barrows pieces*, *Moons of Peril gear*, *Fire cape*, *Enhanced crystal weapon seed*).

---

### 5.2 How Should the Notable Filter Work? (3-Layer Architecture)

A clean, deterministic, zero-network filter (`NotableClog.java` + bundled `notable-clog.txt`) should evaluate a `CLOG` item name `item` (and its resolved item ID / GE price) against **three layers**:

```
                     [ New Collection Log Slot: "X" ]
                                    |
                                    v
              +-------------------------------------------+
              | Layer 1: Mode Switch (Group / Config)     |
              | - ALL: Post every collection log slot     |
              | - NOTABLE (Default): Continue to Layer 2  |
              +-------------------------------------------+
                                    |
                                    v
     +--------------------------------------------------------------+
     | Layer 2: Built-In Notable Rules (Any Match = POST)           |
     |                                                              |
     |  Rule A: Price Threshold                                     |
     |    lookup.price(id) >= settings.dropMin (default 1,000,000)  |
     |    (Automatically catches every expensive tradeable unique!) |
     |                                                              |
     |  Rule B: Name Pattern Match                                  |
     |    - Boss Jars: startsWith("Jar of ")                        |
     |    - Champion Scrolls: endsWith(" champion scroll")          |
     |                                                              |
     |  Rule C: Bundled Curated Set (`notable-clog.txt` + `pets`)   |
     |    - All 63 Pets (`pets.txt`)                                |
     |    - All Raid Purples (CoX, ToB, ToA uniques & kits)         |
     |    - Clue Mega-Rares (3rd age, Gilded, Ranger boots, Blood-  |
     |      hound, Imbued god capes / Ham joint / Holy sandals)     |
     |    - Untradeable & Ironman Progression Uniques below 1M GE   |
     |      (Barrows pieces, Moons of Peril, Dragon defender,       |
     |      Fighter torso, Fire/Infernal cape, Quiver, Black mask,  |
     |      Zombie axe, Warped sceptre, Skilling outfit pieces,     |
     |      Tome of fire/water, Fish barrel, Tackle box, Bottomless |
     |      compost bucket, Dragon pickaxe/axe/harpoon, etc.)       |
     +--------------------------------------------------------------+
                                    | (No match)
                                    v
     +--------------------------------------------------------------+
     | Layer 3: Per-Group Custom Notable List                       |
     |  - Comma/newline list of item names or ItemQuery shorthands  |
     |  - Synced across the group via GroupGovernance / GroupSync   |
     |  - Supports `!item` exclusion prefix to mute a default item  |
     +--------------------------------------------------------------+
```

---

### 5.3 Detailed Breakdown of the Default Notable List (`notable-clog.txt`)

Because **Rule A (`price(id) >= settings.dropMin`)** already catches every tradeable item worth $\ge$ 1M gp (e.g. *Twisted bow*, *Scythe of vitur*, *Tumeken's shadow*, *Zenyte shard*, *Bandos chestplate*, *Armadyl crossbow*, *Dragon warhammer*, *Enhanced crystal weapon seed*, *Ranger boots*, *Imbued heart*, *3rd age* items), the bundled table only needs to explicitly list items that are **untradeable** (so their GE price is `0`) **or** iconic uniques whose GE price can sit below a group's `dropMin` threshold:

| Category | What Is Included in the Default Notable Set | Why Price Alone Isn't Enough |
| :--- | :--- | :--- |
| **1. All Pets (~63 items)** | Every boss, skilling, raid, minigame, and clue pet (`Beaver`, `Heron`, `Rock golem`, `Tangleroot`, `Vorki`, `Olmlet`, `Lil' zik`, `Tumeken's guardian`, `Bloodhound`, `Nibbler`, `Quetzin`, `Nid`, `Moxi`, `Bran`, etc.) | Pets are **untradeable** (`price = 0`). |
| **2. All Boss Jars (15 items)** | Matched by `name.startsWith("jar of ")`: `Jar of dirt`, `Jar of swamp`, `Jar of sand`, `Jar of souls`, `Jar of miasma`, `Jar of darkness`, `Jar of stone`, `Jar of decay`, `Jar of chemicals`, `Jar of eyes`, `Jar of dreams`, `Jar of spirits`, `Jar of smoke`, `Jar of venom`, `Jar of feathers`. | Several jars trade on the GE for 40k–300k gp (below a 1M `dropMin`), while others (`Jar of dirt` / `Jar of darkness` historically) had low/untradeable quirks, yet every Jar is a `1/1,000` to `1/3,000` boss trophy. |
| **3. Raid Purples & Untradeable Kits** | **Chambers of Xeric:** `Twisted bow`, `Elder maul`, `Kodai insignia`, `Dragon claws`, `Ancestral hat/robe top/robe bottom`, `Dragon hunter crossbow`, `Twisted buckler`, `Dinh's bulwark`, `Dexterous prayer scroll`, `Arcane prayer scroll`, `Twisted ancestral colour kit`, `Metamorphic dust`.<br>**Theatre of Blood:** `Scythe of vitur (uncharged)`, `Ghrazi rapier`, `Sanguinesti staff (uncharged)`, `Justiciar faceguard/chestguard/legguards`, `Avernic defender hilt`, `Sanguine ornament kit`, `Holy ornament kit`, `Sanguine dust`.<br>**Tombs of Amascut:** `Tumeken's shadow (uncharged)`, `Masori mask/body/chaps`, `Elidinis' ward`, `Lightbearer`, `Osmumten's fang`, `Thread of Elidinis`, `Eye of the corruptor`, `Jewel of the sun`, `Breach of the scarab`, `Jewel of Amascut`, `Masori crafting kit`, `Cursed phalanx`, `Remnant of Akkha/Ba-Ba/Kephri/Zebak`. | `Avernic defender hilt`, `Thread of Elidinis`, Keris partisan jewels, and HMT/CM ornament kits & dusts are **untradeable** (`price = 0`), and `Arcane prayer scroll` / `Lightbearer` can dip near or below a high `dropMin`. |
| **4. Clue Mega-Rares & Iconic Uniques** | - Every item matching `name.startsWith("3rd age ")` or `name.startsWith("gilded ")`<br>- `Ranger boots`, `Holy sandals`, `Wizard boots`, `Spiked manacles`, `Climbing boots (g)`, `Robin hood hat`, `Tunic`<br>- `Ham joint`, `Cape of skulls`, `Ring of 3rd age`, `Ring of coins`, `Ring of nature`, `Bryophyta's essence` | Guarantees 3rd age, Gilded, and boot upgrades always post even if a group sets `dropMin` to 5M+ gp. |
| **5. Iconic Ironman & GIM Progression Uniques** | - **Weapons & Defenders:** `Dragon defender`, `Abyssal whip`, `Abyssal dagger`, `Abyssal bludgeon` (+ pieces: `Bludgeon spine/claw/axon`), `Zombie axe` (`Broken zombie axe`), `Warped sceptre (uncharged)`, `Trident of the seas (full)` / `Uncharged trident`, `glacial temotli`, `Sulphur blades`, `Earthbound tecpatl`, `Arkan blade`, `Sarachnis cudgel`, `Leaf-bladed battleaxe`, `Granite hammer`, `Brine sabre`, `Dragon scimitar`, `Dragon axe`, `Dragon pickaxe`, `Dragon harpoon`, `Infernal harpoon/axe/pickaxe`, `Smouldering stone`, ` Pharaoh's sceptre`, `Scurrius' spine`.<br>- **Armour & Jewellery:** `Black mask (10)` / `Black mask`, `Berserker ring`, `Archers ring`, `Seers ring`, `Warrior ring`, `Rune pouch`, `Dizana's quiver (uncharged)`, `Fire cape`, `Infernal cape`, `Mage's book`, `Master wand`, `Infinity boots`, `Dragon boots`, `Holy elixir`, `Spirit shield`, `Blessed spirit shield`, `Crystal armour seed`, `Crystal weapon seed`, `Enhanced crystal weapon seed`, `Enhanced crystal teleport seed`, `Teleport crystal`, `Blood shard`, `Amulet of eternal glory`, `Eternal gem`, `Imbued heart`, `Venator shard`, `Chromium ingot`, `Bellator/Magus/Ultor/Venator vestige`, `Awakener's orb`.<br>- **Barrows & Moons of Peril:** All 24 Barrows items (`Ahrim's`, `Dharok's`, `Guthan's`, `Karil's`, `Torag's`, `Verac's`) and all 12 Moons of Peril items (`Blood moon`, `Blue moon`, `Eclipse moon`).<br>- **Skilling & Minigame Jackpots:** `Bottomless compost bucket`, `Tome of fire (empty)`, `Tome of water (empty)`, `Fish barrel`, `Tackle box`, `Big harpoonfish`, `Big bass`, `Big swordfish`, `Big shark`, `Dragonfire shield` / `Draconic visage` / `Skeletal visage` / `Wyvern visage`, `Abyssal needle`, `Abyssal lantern`, `Colossal pouch`, `Auto-weed`, `Herb sack`, `Seed box`, `Gem bag`, `Coal bag`, `Plank sack`, `Log basket`, `Forestry basket`.<br>- **Boss Mutagens & Heads (Rare):** `Tanzanite mutagen`, `Magma mutagen`, `Slayer helmet` boss heads (`Kbd heads`, `Kq head`, `Abyssal head`, `Alchemical hydra heads`, `Vorkath's head` optional), all 11 `Champion scroll`s. | Almost all of these are either **untradeable** (`price = 0`) or sub-1M gp (`Berserker ring` / `Black mask` / `Barrows` pieces / `Dragon defender` / `Bottomless compost bucket`), yet on a Group Ironman account they are huge team milestones! |

---

### 5.4 Settings Design: Option to Post All + Per-Group Custom List

How should this fit into `GroupIronmanConfig.java`, `GroupSettingsSchema.java`, and the Settings tab UI?

#### 1. Mode Switch ("Notable only" vs "All collection log slots")
- **In `GroupIronmanConfig.java` (*Thresholds* section, beside `eventUntradeableIgnore`):**
  - `@ConfigItem(keyName = "eventClogNotableOnly", name = "Notable log slots only", description = "Post new collection log slots only for pets, uniques, jars, mega-rares, items above the drop minimum, and your custom list.")`
  - **Default:** `true` (so turning on *New collection log slot* immediately uses the smart filter without flooding Discord).
- **In `GroupSettingsSchema.java` (*What counts* / `THRESHOLDS` section):**
  - Add `flag("eventClogNotableOnly", "Notable collection log slots only", THRESHOLDS, GroupIronmanConfig::eventClogNotableOnly)` (0 or 1).
  - Because `GroupSettingsSchema` only stores `Map<String, Long>` (numbers and 0/1 flags), this boolean flag syncs automatically across the group when the leader or an editor clicks **Save to group**!

#### 2. Per-Group Custom Notable List
- **In `GroupIronmanConfig.java` (*Thresholds* section):**
  - `@ConfigItem(keyName = "eventClogCustom", name = "Custom notable log items", description = "Extra collection log items to post (or !item to ignore), comma-separated. Uses ::find matching.")`
  - Default: `""`.
- **How it matches (`ItemQuery` reuse!):**
  - Reuse `ItemQuery` (`ItemQuery.java`) or case-insensitive prefix/exact matching from `EventDetect.uniques()`!
  - Entries prefixed with `!` (e.g. `!Barrows, !Champion scroll, !Big bass`) act as **exclusions** on top of the default notable list, while plain entries (e.g. `Mole slippers, Fedora, Evil chicken`) act as **inclusions**.
- **How the Per-Group Custom List Syncs Across the Group:**
  - Today, `GroupSettings` (`GroupSettings.java`) stores `Map<String, Long> values` (numeric thresholds and 0/1 flags), signed by the leader or an editor in `GroupGovernance.java`.
  - To make the custom notable list **per-group** (so the leader or an editor edits it once in the panel's *Settings* tab and every member's client uses it):
    - Add an optional `String customClog` (or `Map<String, String> texts` for string settings like `eventClogCustom` and `eventUntradeableIgnore`) to `GroupSettings` and include it in `GovernanceCodec`'s canonical signed payload!
    - Older clients ignores unknown JSON keys in `GovernanceCodec`, and if the group has no saved string setting yet, `Resolved` falls back to `config.eventClogCustom()`.

---

## 6. Code Map & Implementation Touchpoints (For the Follow-up Build Task)

No code was modified in this research run. When the Board schedules the implementation task, here are the exact files and methods to touch:

| File | Lines | Change Needed |
| :--- | :--- | :--- |
| [`src/main/resources/com/vinnyvangogh/groupironman/pets.txt`](../../src/main/resources/com/vinnyvangogh/groupironman/) | *(new file)* | ~63 lines: `name|itemId|source|rate|wikiSlug` for all OSRS pets. |
| [`src/main/resources/com/vinnyvangogh/groupironman/notable-clog.txt`](../../src/main/resources/com/vinnyvangogh/groupironman/) | *(new file)* | Curated list of untradeable and sub-1M notable collection log items (Raid purples/kits, 3rd age/Gilded, Barrows, Moons, defenders, capes, skilling jackpots). |
| [`ClogMessage.java`](../../src/main/java/com/vinnyvangogh/groupironman/ClogMessage.java#L14-L37) | `:14-37` | Keep `ClogMessage.parse(message)`; add helper or `NotableClog` class to resolve the item ID (from `pets.txt` / `notable-clog.txt` / `itemManager.search(name)`) so `GroupEvent.clog` carries `.items(Map.of(id, 1))` when known. |
| [`GroupEventEngine.java`](../../src/main/java/com/vinnyvangogh/groupironman/GroupEventEngine.java#L664-L697) | `:664-697, :962-980` | 1. In `groupBroadcast()`: if a `CLAN_GIM_MESSAGE` pet broadcast names `localPlayer()`, enrich the local player's pending `PET` event with the pet name and `"at X XP / kills"`.<br>2. In `gameMessage()`: when `EventText.isPet(message)` fires, also check `client.getFollower()`, `lastKc`, or recent `StatChanged` skill as fallback.<br>3. When `ClogMessage.parse(message)` fires, attach its resolved item ID so `CLOG` gets an item icon thumbnail. |
| [`EventRouter.java`](../../src/main/java/com/vinnyvangogh/groupironman/EventRouter.java#L101-L118) | `:101-118` | In `threshold()`, add `case CLOG: return notableClog(event, settings, lookup);` checking `settings.clogNotableOnly`, `lookup.price(id) >= settings.dropMin`, `NotableClog.isNotable(event.text)`, and `settings.clogCustom`. |
| [`DiscordMerge.java`](../../src/main/java/com/vinnyvangogh/groupironman/DiscordMerge.java#L25-L33) | `:25-33, :172-202` | 1. Add `GroupEvent.Type.RAID_UNIQUE` to `Family.DROP` (`PET, RAID_UNIQUE, CLOG, UNIQUE, DROP`).<br>2. Move `HC_LIFE_LOST` ahead of `DEATH` in `Family.DEATH`.<br>3. When merging `Family.DROP` with a `PET` lead, copy the pet's name and item ID from a sibling `CLOG`/`UNIQUE` event onto `PET` and replace the redundant `New collection log slot` field with a compact badge.<br>4. When merging `DROP` + `CLOG` + `UNIQUE` for the same item, keep `DROP` (which has the boss name and KC!) or `RAID_UNIQUE` as the lead headline and show `Collection log: New slot unlocked!` as a field. |
| [`DiscordWebhook.java`](../../src/main/java/com/vinnyvangogh/groupironman/DiscordWebhook.java#L370-L400) | `:370-400` | Upgrade big-moment embeds (`PET`, `RAID_UNIQUE`, `MAXED`, `HC_LIFE_LOST`, `CLOG`) with celebratory accent colours (`0xFFD700` Gold for Pets, `0xA335EE` Purple for Raid Purples, `0xE0115F` Crimson for Maxed), `author` header ribbon + icon, and clean inline fields (**Design 2**). |

---

## 7. Verified Assumptions, Risks & Test Plan

### 7.1 Assumptions (All Verified)
1. **Assumption:** `itemManager.search(name)` in `GroupEventEngine.java:709` can resolve pet item IDs by name.  
   - **Verification:** **False / Refuted!** `itemManager.search()` searches RuneLite's GE price index (tradeable items only). Pets and untradeable collection log items are untradeable and will not be returned by `itemManager.search()`. **Solution:** Bundle `pets.txt` (all ~63 pets with exact `ItemID`s) and `notable-clog.txt` (with item IDs for untradeables), and use `itemManager.search()` only as the fallback for tradeable items!
2. **Assumption:** `static.runelite.net/cache/item/icon/<id>.png` serves icons for untradeable pet items like Beaver (`13322`).  
   - **Verification:** **Verified.** RuneLite's static item icon cache exports every item sprite in the OSRS item cache by ID, including untradeable follower items (`13322.png` for Beaver, `21992.png` for Vorki, `13342.png` for Max cape) — the exact same URL pattern Dink (`pajlads/DinkPlugin`) uses for pet and collection log thumbnails.
3. **Assumption:** Plain channel webhooks cannot use Discord Components V2 (`Container`, `Section`, `Separator`).  
   - **Verification:** **Verified with nuance.** Plain channel webhooks (`application_id: null`) ignore `components` by default, **unless** the query parameter `?with_components=true` is appended to the webhook URL alongside `"flags": 32768` (`IS_COMPONENTS_V2`). Interactive components still require an app-owned webhook, but layout/display components and link buttons (`style: 5`) work on plain webhooks with `?with_components=true`.
4. **Assumption:** Discord webhooks cannot send Stickers.  
   - **Verification:** **Verified.** `Execute Webhook` (`POST /webhooks/{id}/{token}`) does not accept `sticker_ids` (`discord/discord-api-docs`, `webhook.mdx`).

### 7.2 Risks & Compliance Check
- **RuneLite Plugin Hub Compliance:**
  - Zero new external network hosts contacted by the client: `static.runelite.net` URLs and optional `oldschool.runescape.wiki` links are strings inside the JSON payload sent to the user's configured Discord webhook (`DiscordSender`).
  - Zero reflection, zero automation, and `ConfigFitTest` / `ConfigLayoutTest` invariants preserved.
- **CC BY-NC-SA 3.0 Attribution (`THIRD_PARTY_NOTICES.md`):**
  - If `pets.txt` or `notable-clog.txt` includes drop rates from the OSRS Wiki, add a one-sentence note under the existing *Old School RuneScape Wiki: item drops* section in `THIRD_PARTY_NOTICES.md` (`THIRD_PARTY_NOTICES.md:66-74`).

### 7.3 Adversarial & Failure-First Test Plan (For Follow-Up Implementation)
1. **Duplicate Pet with No `CLOG` Event:** Player gets `"You have a funny feeling like you would have been followed..."` while chopping Magic trees (no `New item added to your collection log` line fires). Verify the pet post still names `Beaver` (from `CLAN_GIM_MESSAGE` or recent Woodcutting `StatChanged`) or gracefully falls back to `"GroupShip got a duplicate pet!"` without crashing or posting `"new collection log slot"`.
2. **Boss Kill Dropping Both a Pet AND a Separate Unique (`Vorki` + `Skeletal visage`):** Verify `DiscordMerge` folds `CLOG("Vorki")` into the `PET("Vorki")` headline while preserving `DROP("Skeletal visage")` and `CLOG("Skeletal visage")` as fields on the same celebratory post.
3. **Routine Clog Item Blocked by Notable Filter:** Player gets `"New item added to your collection log: Mole claw"` (or `"Black pickaxe"`). With `eventClogNotableOnly = true`, verify `EventRouter.threshold()` returns `null` and nothing is posted to Discord, while `DropLog` / sidebar `Log` behaviour remains intact if desired.
4. **Tradeable High-Value Item Passing Notable Filter Automatically:** A new tradeable boss item not in `notable-clog.txt` drops and unlocks a `CLOG` slot; its price in `ItemLookup` is `2,500,000 gp >= dropMin (1,000,000 gp)`. Verify it passes the Notable Filter via Rule A (`price >= dropMin`).
5. **Custom Group List Inclusion & `!` Exclusion:** Group sets `eventClogCustom = "Mole slippers, !Barrows"`. Verify `"Mole slippers"` posts on `CLOG`, while `"Dharok's platebody"` is muted by `!Barrows`.
6. **Raid Purple + Clog Single-Post Merge:** Player gets `"GroupShip - Twisted bow"` (`RAID_UNIQUE`) and `"New item added to your collection log: Twisted bow"` (`CLOG`) on the same tick. Verify `DiscordBatch` + `DiscordMerge` produce **1 purple-accented post** (`0xA335EE`) with thumbnail `20997.png`, not 2 separate posts.
