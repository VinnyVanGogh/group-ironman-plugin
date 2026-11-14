# AI Computer-Use Test Rig: Login & Secret Vault, Execution Modes, Options Comparison, and Phased Build Plan

Status: **design and build plan for Board approval — no code in this PR.**
Task: `RUNELITE-140` (StayPoint `task-e5e823ed`), updated `2026-11-14` per Board boundary update (`comment #17135`).

---

## 0. Goal, Top Priority, and Spec Interview

### Top goal (`Board 2026-11-14`)

**Log into Board-chosen test accounts automatically, stage short scripted in-game states, and capture crisp README and Discord screenshots end-to-end** — plus visually test what our JUnit fake-events harness (`RUNELITE-138`) and Swing UI tests (`RUNELITE-139`) cannot reach, in either **`do-it`** (unattended) or **`show-me`** (live-watched, stoppable) mode.

### Why this rig exists alongside our unit, harness, and Swing tests

Our RuneLite plugin testing stack is split into three layers so each layer handles only what the cheaper layer below it cannot:

| Layer | Task | Speed | What it covers | Where it stops |
| --- | --- | --- | --- | --- |
| **1. Fake game events harness** | `RUNELITE-138` (`task-3ee417cc`) | `< 1 s` (JUnit) / instant in dev client | Synthetic `EventBus` scenarios (`ChatMessage`, `StatChanged`, `ItemContainerChanged`, `WidgetLoaded`, `PartyChanged`, `VarbitChanged`) + JSON scenario runner and dev-only `Scenarios` panel | Cannot render native window chrome, real 3D viewport scenes, OS security dialogs, or external apps |
| **2. In-process Swing UI driver** | `RUNELITE-139` (`task-5586ef09`) | `2–10 s` | Headless/headful Swing assertions against isolated `PluginPanel` and `ConfigPanel` trees; detached component-only captures | No live logged-in OSRS scene, no real `::find` chatbox output (`find-chatbox.png`), no live two-client Party sync, no real Discord embeds, no Windows SmartScreen/SAC dialogs |
| **3. AI computer-use desktop rig** *(this plan)* | `RUNELITE-140` (`task-e5e823ed`) | `25–45 s` (1-client shot/check) · `60–90 s` (2-client sync) · `2.5–3.5 min` (cold Win11 launcher) | Screenshots in, policy-gated mouse/keyboard out inside an isolated Docker/VM desktop with VNC: **logs into Board-picked test accounts via macOS Keychain / Jagex session**, runs short scripted in-game steps, captures full-context **README & Discord screenshots**, runs 2-client Party sync checks, and tests the Windows 11 launcher end-to-end | **Enforced boundaries:** Board-allowlisted test accounts only; zero secret leaks anywhere; short scripted sessions with `< 100 ms` kill switch; never solves CAPTCHAs / Turnstile |

### Spec interview (`Board 2026-11-14`, updated `comment #17135`)

| Question | Board decision |
| --- | --- |
| **What is the rig for, and what is the #1 priority?** | **Top goal:** log in on test accounts and take the **README and Discord screenshots itself** (faster, higher-fidelity real in-game shots than detached Swing crops or manual staging). More broadly: quick Playwright-style visual and E2E checks across all owned RuneLite plugins (`GIM Utilities`, `Quest Route`, `Combat Vitals`, `Bank Views`, `Build Guard`), two-client Party sync checks (`docs/group-settings-manual-test.md`), and the clean-VM Windows launcher flow. |
| **Is automated in-game input allowed?** | **Yes — strictly on test accounts explicitly chosen by the Board** (Board owns the account and accepts Jagex's automated-input risk on those test accounts). Never on main or un-allowlisted accounts; **short scripted test sessions only** (`90 s` default / `180 s` hard cap), never open-ended gameplay or grinding, with an instant **kill switch**. |
| **How does the operator run or watch it?** | **Two modes:** **`do-it`** (runs unattended in the background and attaches screenshots + verdict to StayPoint) and **`show-me`** (opens a live VNC/HUD window so the Board watches each step as it happens and can pause/stop it at any instant). |
| **How are login credentials handled?** | Read at run time from the **macOS Keychain** (or an enrolled **Jagex Launcher / RuneLite developer session** stored in Keychain) into process memory only. **Never printed, logged, written to files, rendered in screenshots, or included in StayPoint task docs/comments.** |
| **Concrete workloads in scope?** | **`W1` (Top goal):** Automated login + live in-game README (`docs/images/*.png`) & Discord screenshot capture. **`W2`:** Plugin panels, 8-section config, and two-client GIM Party sync E2E (`docs/group-settings-manual-test.md`). **`W3`:** In-game overlays (`EventToastOverlay`, `GroupBoxOverlay`, world map markers, `::find` chatbox) over a real logged-in viewport (combining real actions + `RUNELITE-138` scenario triggers for rare drops/streaks). **`W4`:** Live Discord test-server visual check & capture (7 category poster names, embeds, stitched `LevelShot`/`GraveShot`, `StatusBoard` `PATCH` edits). **`W5`:** Windows launcher (`tools/windows/Run-GimUtilities.cmd`) E2E on a clean Windows 11 VM (Mark of the Web, SmartScreen, enforced Smart App Control, PowerShell bypass, first build). |

### Open design defaults (approve as-is or override when signing Slice 1)

| Decision | Recommended default | Alternative |
| --- | --- | --- |
| **Repo placement** | **Dedicated repo `VinnyVanGogh/runelite-computer-use-rig`** (shared across all 5+ RuneLite plugins; zero files in any plugin's `src/main`) | Subdirectory inside `group-ironman-plugin` or `staypoint` |
| **Primary AI driver** | **StayPoint MCP tool server (`rig-mcp`) inside Claude Code runs** (`$0` marginal cash under the Board's existing Claude Max `$200/mo` personal seat), plus `--api` (`computer_20251124`, Sonnet 5.5 at `~$0.07–$0.09/run`) when Max weekly quota is tight | Direct Anthropic Messages API (`ANTHROPIC_API_KEY`) only |
| **Where each desktop runs** | **Linux `arm64` Docker (`Xvfb` + `Mutter` + `x11vnc` + `noVNC`) on this M2 Max Mac** for `W1`–`W4` (`28 GiB` free disk available today; direct local Keychain access + `< 0.5 s` `show-me` viewer); **Windows 11 Pro x64 KVM (`dockurr/windows`) on the x86_64 home server** over Tailscale for `W5` | Run `W1`–`W5` on the x86_64 home server once racked (Mac pushes ephemeral Keychain session in RAM over Tailscale; zero secrets at rest on server) |
| **StayPoint screenshot storage** | **Committed PNGs in `docs/images/*.png`** when refreshing README shots (`W1`), and **local StayPoint run artifacts (`~/.staypoint/artifacts/<task>/<run>/`)** via StayPoint slice `R-SP1` (`task_attach` + `staypoint.doc/v1` `image` block, with day-1 inline WebP HTML contact sheet fallback) for transient QA runs | Commit transient QA screenshots to a scratch branch or S3/R2 |

---

## 1. Existing Work and Code Map

### 1.1 Related StayPoint tasks and PRs surveyed

- **`RUNELITE-138` (`task-3ee417cc`, PR #53):** Fake game events harness (`src/test/resources/scenarios/*.json` + dev-only `Scenarios` sidebar panel). Pairs directly with **`W1`/`W3`**: once the rig logs a test character into a real Gielinor scene, clicking a `RUNELITE-138` scenario can deterministically fire rare events (e.g., a 10M drop, HCIM life lost, or Prayer `23 → 24 → 25` combined streak) on top of a genuine logged-in client without grinding for hours.
- **`RUNELITE-139` (`task-5586ef09`, PR #55):** Headless/headful Swing UI tests and isolated panel renders. Fast for regression gating on every commit (`< 10 s`), while `RUNELITE-140` (`W1`) produces full-context logged-in README & Discord shots (`find-chatbox.png`, `group-events.png`, `panel-group.png`, `member-bank.png`, `storage-snapshot.png`, `discord-*.png`) and multi-client verification.
- **`task-4ed2d296` & PR #37 (`docs/settings-shots-2`), PRs #46–#51:** Manual screenshot captures and recent UI updates (collapsible banks, one-click *Turn on sharing*, shared Discord webhooks, real 28-slot inventory). Several `docs/images/*.png` files (`storage-log.png` at `3.5 KB`, `find-panel.png` at `4.7 KB`, `group-sync.png` at `10.9 KB`, plus missing live Discord embed screenshots) have drifted behind `main` (`3d87b5a`) because manual capture takes 15–25 minutes per pass.
- **Two-account manual test (`docs/group-settings-manual-test.md:1-123`):** Currently requires ~25 minutes of human clicking across two `./gradlew run` / `-PaltHome` clients logged into two GIM characters (`A` leader, `B` member). Automating this in `W2` turns a 25-minute manual chore into a ~75-second `do-it` or `show-me` run.
- **`RUNELITE-95` (`task-d97ec163`, PR #10), PR #44 (`docs/windows-sac`), PR #50 (`gim-test-3`):** Added `GU_BUILD_ONLY=1` on GitHub Actions `windows-latest` and documented the Windows 11 Smart App Control PowerShell route (`SETUP.md:271-281`), leaving real Explorer `ZoneId=3`, SmartScreen, enforced SAC, and `[3/3]` GUI launch to **`W5`**.
- **`GENERAL-38` (`task-01047f3a`, M2 Max SSD audit) & `GENERAL-82` (`task-1c7e2734`, x86_64 mini PC home box):** Re-checked on `2026-11-14` (`df -h /System/Volumes/Data`): this Mac now has **`28 GiB` available** (up from `8.1 GiB` earlier today), which is plenty for the `~2.2 GB` Linux `arm64` rig container right away, while `GENERAL-38` cache pruning still reclaims headroom and `GENERAL-82` covers the `11–15 W` x86_64 KVM host for Windows 11 (`W5`).

### 1.2 Code map (`group-ironman-plugin` @ `main` `3d87b5a`, RuneLite `1.13.6`)

| Concern | File & lines | Relevance to the computer-use rig |
| --- | --- | --- |
| Developer client entrypoint | `src/test/java/com/vinnyvangogh/groupironman/GroupIronmanPluginTest.java:13-20` | Calls `ExternalPluginManager.loadBuiltin(GroupIronmanPlugin.class)` then `RuneLite.main(args)` (`src/test` only — never packaged in the Hub jar). |
| Gradle `run` & `-PaltHome` isolation | `build.gradle:24-76` | Pins `runeLiteVersion = '1.13.6'` (`:24`), sets `-Duser.home=${project.altHome}` (`:71-73`) so Client A and Client B run simultaneously in isolated `tmpfs` homes without touching `~/.runelite`. |
| README screenshot targets (`16` PNGs) | `README.md:18-161`; `docs/images/*.png` | Exact `16` image paths (`panel-group.png`, `member-bank.png`, `storage-snapshot.png`, `find-panel.png`, `find-chatbox.png`, `storage-log.png`, `group-events.png`, `group-sync.png`, and `8` `settings-*.png` shots) regenerated by **`W1`** — plus new Discord embed captures (`W4`). |
| Screenshot privacy & redaction rule | `docs/README.md:5-10` | Hard repo rule: *"Redact other people's account names before committing a screenshot."* Enforced mechanically in `W1` via pre-seeded `Entity Hider` + `Public Chat: Off` + post-capture RSN/secret scrubber. |
| Two-account Party sync test | `docs/group-settings-manual-test.md:1-123` | 11-section leader/editor/opt-out/webhook-isolation test across Client A + Client B (`-PaltHome`), automated end-to-end by **`W2`**. |
| Windows player launcher & SAC docs | `tools/windows/Run-GimUtilities.cmd:1-233`; `SETUP.md:202-206, 243-299` | Downloads Windows **x64** Temurin 11 (`:33-34`), verifies SHA-256, fetches `$SourceRef`, and launches RuneLite (`:222`). Target of **`W5`** against SmartScreen and enforced SAC. |
| Sidebar panel, 8-section config & overlays | `GroupIronmanPanel.java:72-80`; `GroupIronmanConfig.java:30-80`; `EventToastOverlay.java`; `GroupBoxOverlay.java`; `MemberMapMarkers.java` | Live visual and screenshot targets across `W1`–`W3`. |
| Discord webhooks & stitched shots | `DiscordSender.java:35-76`; `LevelShot.java`; `GraveShot.java`; `StatusBoard.java` | Posts to 7 category webhooks (`Town Crier`, `World Hopper`, `Party Pete`, `Skillcape Seller`, `Death's Office`, `Gielinor Banker`, `Varrock Herald`); rendered and captured inside real Discord in **`W4`**. |

---

## 2. Safety Boundary, Zero-Leak Credential Vault, Modes, and Kill Switch

### 2.1 Hub compliance & account-risk boundary (`Board 2026-11-14`)

1. **100% Plugin Hub separation (`runelite-compliance` verdict: outside plugin jar).** Every line of computer-use, login injection, VNC, and `InputGate` code lives in its own standalone repository (`VinnyVanGogh/runelite-computer-use-rig`). Zero automation, reflection, or input code enters `src/main` of `group-ironman-plugin` or any other Hub plugin.
2. **Board risk ownership + mechanical main-account firewall:**
   - Jagex's [Rules of Old School RuneScape](https://legal.jagex.com/docs/rules/rules-of-old-school-runescape) forbid automated game input on any account. Per `Board 2026-11-14` (`comment #17135`), the Board explicitly authorizes automated login and short scripted in-game input **on test accounts the Board selects**, accepting account/IP enforcement risk on those test accounts in exchange for fast, authentic automated testing and screenshot generation.
   - To make it **mechanically impossible** for an agent or misconfigured scenario to touch the Board's personal main or real Group Ironman characters, `rigd` enforces a host-only allowlist (`~/.config/rig/allowed-accounts.json`, writable only by the Board outside agent worktrees) pinning allowed `<slug>` aliases to exact `JX_CHARACTER_ID` and `JX_DISPLAY_NAME` values, and runs every container against an empty RAM-backed `tmpfs` home (`--tmpfs /tmp/rig-home:rw,size=256m`) — **`~/.runelite` is never mounted**.
3. **Short scripted test sessions only — never open-ended play:**
   - Every live-account scenario must declare an explicit step list, a `max_turns` cap (default `15`, hard ceiling `25`), and a hard in-world wall-clock TTL (`default 90 s`, ceiling `180 s`). When the scenario completes or hits its TTL, `rigd` logs out and destroys the container immediately.
4. **Hard anti-CAPTCHA / Cloudflare Turnstile stop:**
   - Jagex gates interactive `account.jagex.com` web logins behind **Cloudflare Turnstile** and email/2FA. The rig **never** solves, clicks through, waits out, or bypasses any CAPTCHA or Turnstile challenge. Instead, it uses pre-enrolled Jagex Account session tokens (`§2.2`) that skip `account.jagex.com` completely; if a session token ever expires or any Turnstile / re-auth window appears, `rigd` halts immediately (`SessionExpiredNeedsBoardRefresh`) and prompts the Board to refresh the token interactively once.

### 2.2 Zero-leak credential architecture (macOS Keychain → JVM RAM)

How RuneLite authenticates Jagex Accounts without typing passwords: when launched by the official Jagex Launcher (or RuneLite's developer *Jagex Account* sidebar flow documented in [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)), RuneLite reads `JX_SESSION_ID`, `JX_CHARACTER_ID`, and `JX_DISPLAY_NAME` (plus optional `JX_ACCESS_TOKEN` / `JX_REFRESH_TOKEN`) from process environment variables and presents a single character-bound **"Play Now"** button — no username or password prompt, and no `account.jagex.com` browser flow. (The RuneLite wiki explicitly warns against leaving plaintext `.runelite/credentials.properties` files on disk.)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ONE-TIME HUMAN ENROLLMENT (Board on Mac — never run by an agent)             │
│   1. Board signs test account in once via Jagex Launcher / RuneLite dev auth │
│   2. `rig auth enroll gim-test-a --from-running-session` (or masked TTY)     │
│      → confirms RSN on TTY, stores JSON blob in macOS login Keychain:        │
│        service = "staypoint.rig.test-account.gim-test-a"                     │
│      → adds {slug, character_id, display_name} to allowed-accounts.json      │
└───────────────────────────────────────┬──────────────────────────────────────┘
                                        │ at test run start (`rig run ... --account gim-test-a`)
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ HOST `rig-runner` (outside LLM context; zero stdout/log echo)                │
│   • Verifies `gim-test-a` + `JX_CHARACTER_ID` in `allowed-accounts.json`     │
│   • Reads Keychain via `/usr/bin/security find-generic-password -w` into RAM │
│   • Streams `{JX_SESSION_ID, JX_CHARACTER_ID, JX_DISPLAY_NAME}` over         │
│     loopback Unix socket (or mTLS Tailscale stream) into `rigd` RAM          │
└───────────────────────────────────────┬──────────────────────────────────────┘
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ `rigd` INSIDE CONTAINER (`tmpfs` `/tmp/rig-home` — zero disk persistence)    │
│   1. Spawns `java` directly with `JX_*` in child `execve` `env`              │
│      (NEVER Gradle `-P`/`jvmArgs`, NEVER `.runelite/credentials.properties`) │
│   2. Atomic `rig_login("gim-test-a")` macro runs BEFORE first screenshot:    │
│      clicks `Play Now` → clicks Lobby `CLICK HERE TO PLAY` (skipping the     │
│      OSRS lobby `"You last logged in from: <WAN IP>"` screen!) → waits for   │
│      `GameState.LOGGED_IN` in-world                                          │
│   3. First screenshot returned to LLM is already in Gielinor — zero secrets  │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Six mechanical non-leak guarantees (*"never printed, logged, written to files, screenshots or task comments"*)

| Channel | Mechanical control in `rig-runner` & `rigd` |
| --- | --- |
| **1. LLM context & tool I/O** | Models see only symbolic slugs (`account: "gim-test-a"`). No tool returns environment variables, Keychain payloads, webhook URLs, or group keys. Even if a legacy unmigrated test account uses username/password fallback, `rigd` types it inside `rig_login()` with screenshots suspended — the LLM never knows the secret. |
| **2. Terminal stdout/stderr & logs** | All child-process stdout/stderr, `audit.jsonl`, and StayPoint `check_runs` tails pass through a streaming `RedactingWriter` initialized with the exact loaded secret strings (plus patterns for `JX_[A-Z_]+`, `discord.com/api/webhooks/\S+`, emails, and the host's public IPv4/IPv6), replacing any match with `[REDACTED]`. Core dumps are disabled (`ulimit -c 0`, `-XX:-CreateCoredumpOnCrash`), and `JX_*` never appears in `ps aux` (`cmd.Env` only, never CLI flags). |
| **3. Files on disk & git** | Container `$HOME` and `$RUNELITE_DIR` mount on RAM `tmpfs` (`--tmpfs /tmp/rig-home:rw,noexec,nosuid,size=256m`), destroyed when the container exits. Only scrubbed output images (`docs/images/*.png`) or `$TMPDIR` report bundles leave the container. |
| **4. Screenshots (LLM frames, VNC captures & README PNGs)** | (a) `rig_login()` completes **both** the login screen and the OSRS Lobby welcome screen (`InterfaceID` `378`, which displays *"You last logged in ... from: `<WAN IP>`"*) **before** `rig_screenshot` is unlocked. (b) Defense-in-depth `ScreenScrubber`: if the client ever drops back to `LOGIN_SCREEN`, `LOGGING_IN`, or widget `378`, `rigd` blacks out the login/IP regions prior to PNG encoding. (c) In `ConfigPanel` screenshots (`settings-discord-connection.png`, `settings-group.png`), real test webhook URLs and live group keys are replaced with synthetic display placeholders (`••••••••`) or left blank per `README.md`. (d) Pre-seeded `settings.properties` enables **Entity Hider** (hide random non-party players + overhead chat) and sets **Public Chat = Off**, **Private Chat = Off**, **Trade = Off**, preventing random bystanders from leaking RSNs (`docs/README.md:5-10`). |
| **5. StayPoint `task_doc`, `task_comment` & PR bodies** | Before calling `task_doc`, `task_attach`, `task_comment`, or `pr_body`, `rig-runner` scans every outgoing text/JSON/HTML payload against the loaded secret set, webhook regexes, email patterns, and WAN IP; any hit aborts upload fail-closed (`SecretLeakBlocked`). |
| **6. Remote home-server runs** | No Keychain replica or secret file ever sits on the Linux/Windows home server. `rig-runner` on the Mac reads macOS Keychain at run start, sends the token over WireGuard/Tailscale (`100.x.y.z`) into `rigd` RAM for the 60-second session, and `rigd` zeroes it on teardown or kill switch. |

### 2.3 Two execution modes (`do-it` vs. `show-me`) and the `< 100 ms` Kill Switch

| Property | **`do-it` mode (`--mode do-it`, default in CI / unattended StayPoint runs)** | **`show-me` mode (`--mode show-me`, interactive live supervision)** |
| --- | --- | --- |
| **Desktop visibility** | Headless in background (`Xvfb` inside Docker); **never steals macOS cursor or window focus** while the Board is working | Automatically opens a dedicated local window (`http://127.0.0.1:<port>/show-me.html` via `noVNC` WebSocket at `60 fps`, or private Tailscale HTTPS URL remotely) before Step 1 starts |
| **On-screen HUD** | Recorded into `*-audit.png` frames for post-run review | Live top bar above the `1280×800` viewport showing: **`[STOP / KILL (Esc×2)]`**, **`[Pause / Step]`**, account badge (`gim-test-a`), step description (`3/7: Open Group Storage`), countdown timer (`00:34 / 01:30`), and a **target crosshair preview** drawn `500 ms` *before* each click lands |
| **Pacing** | Full speed (`~60 ms` gate overhead; finishes 1-client run in `~25–45 s`) | Human-watchable cadence (`500 ms` pre-click highlight; optional `--step-confirm` waits for `Space` before each action) |
| **Board takeover / stop** | Instant via `rig stop` CLI or cancelling the StayPoint run | Clicking **`[STOP]`**, pressing `Esc` twice, running `rig stop`, **or moving/clicking inside the VNC canvas** immediately trips the Kill Switch (`PausedOrKilledByBoard`) so the rig never fights the Board's mouse |
| **Artifacts produced** | Scrubbed `docs/images/*.png` (if screenshot task) + StayPoint `task_doc` report + audit gallery | Same artifacts, plus clear stop reason/step if halted early by the Board |

#### Kill Switch guarantees (`< 100 ms` hard cut, independent of the LLM)

The Kill Switch lives in `rigd` and `rig-runner`, **not** inside the AI prompt loop:
1. **Four triggers:** (a) red **`STOP`** button in the `show-me` HUD (`POST /kill` over loopback WebSocket), (b) CLI `rig stop` (touches host control socket `~/.staypoint/run/rig.kill`), (c) physical VNC input takeover tripwire in `show-me` mode, and (d) StayPoint run cancel / `SIGTERM` / session TTL expiry (`default 90 s` in-world, `5 min` overall).
2. **Immediate mechanical action:** Atomically flips `InputGate` state to `KILLED` (dropping any in-flight `xdotool` / `SendInput` syscall in `< 5 ms`), sends `SIGKILL` / `docker kill` to the container (`< 80 ms`), unmounts `tmpfs` `/tmp/rig-home` (erasing session RAM), cancels any pending LLM API stream, and writes disposition `KilledByBoard` to the StayPoint report.

### 2.4 Policy-scoped `InputGate` (`allow_scripted` vs. `deny` canvas)

Every mouse and keyboard action still passes through `rigd`'s deterministic `InputGate` before reaching X11 (`xdotool`) or Win32 (`SendInput`):

1. **Always-on global guards (all scenarios):**
   - Rejects everything when `state != ACTIVE` (`KILLED`, `PAUSED_BY_BOARD`, or TTL expired).
   - Hard-aborts the run if any window matches password-manager, browser-auth, or CAPTCHA/Turnstile titles (`1Password|Keychain Access|Bitwarden|account\.jagex\.com|Verify you are human|Cloudflare`).
   - Restricts clicks/keys to the scenario's allowlisted window classes (`net-runelite-client-RuneLite`, owned Swing dialogs, Chromium Discord test channel, or Windows `explorer.exe`/`powershell.exe`/`smartscreen.exe`).
   - Blocks clicks on dangerous OSRS account/world surfaces even in live runs (e.g., RuneLite's *Jagex Account* add/remove buttons, bond pouch / store links) via UI region exclusions.
2. **Per-scenario `canvas_policy`:**
   - **`canvas_policy: "allow_scripted"` (`W1` live README/Discord shots, `W2` 2-client sync, `W3` live overlays):** Requires a valid `test_account` on `allowed-accounts.json`. Permits clicks, drags, keystrokes, and `::find` chatbox commands inside that test character's RuneLite `Canvas` and sidebar within the scenario's strict `max_turns` and `max_in_world_s` budget.
   - **`canvas_policy: "deny"` (`W4` Discord viewer, `W5` Windows 11 launcher, and offline `ConfigPanel` checks):** Queries the OS window tree (`XQueryTree` for OpenJDK's heavyweight `XCanvasPeer` on Linux; `EnumChildWindows` for `SunAwtCanvas` on Windows; [Oracle AWT Heavyweight/Lightweight Mixing](https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_hw_lw.html)) and refuses any coordinate inside `expand(R_canvas, 8 px)` — keeping zero-canvas defense-in-depth whenever a scenario has no reason to touch the game world.

---

## 3. Options Compared (With Citations, Cost Math, and Speed)

### 3.1 Desktop & VM options

| Option | Stack & architecture | Covers `W1`–`W4` (Live login, README/Discord shots, 2-client sync)? | Covers `W5` (Win11 launcher, SAC, SmartScreen)? | Cold / warm start | Disk & RAM footprint | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| **A. Trimmed Linux X11 Docker container** *(derived from Anthropic's reference container)* | Ubuntu 24.04 (`linux/arm64` on Mac, `linux/amd64` on x86_64) + `Xvfb` (`1280×800` or `1600×900` for 2-client) + `Mutter` + `x11vnc` + `noVNC` (`show-me` HUD) + Temurin JDK 11 + Chromium + `rigd` ([`anthropics/anthropic-quickstarts` `computer-use-demo`](https://github.com/anthropics/anthropic-quickstarts/tree/main/computer-use-demo)) | **Yes — all four (`W1`–`W4`)** at native CPU speed; background-safe in `do-it` and instant window in `show-me` | **No** (Linux kernel cannot run `.cmd`, SmartScreen, or WDAC/SAC) | `< 2 s` container + `5–8 s` RuneLite login-ready (warm Gradle cache) | `~2.2 GB` image; `~2.0 GB` RAM (`~3.4 GB` for 2-client sync) | **Recommended primary rig for `W1`–`W4`** |
| **A2. Native macOS GUI (`java.awt.Robot` / Quartz on host screen)** | Runs RuneLite directly on the Board's live macOS desktop without a container | Technically runs RuneLite, **but hijacks the physical mouse/keyboard**, breaks if the Board moves the cursor in `do-it` mode, and puts the Board's live Slack/Teams/Keychain/main client windows one misclick away | **No** | `5–8 s` | `0 GB` image; `1.5 GB` RAM | **Reject** (unsafe & unusable in `do-it` mode; Option A isolates cursor + `tmpfs` at near-zero overhead) |
| **B. Unmodified Anthropic `computer-use-demo` image** | Ubuntu 22.04 + `Xvfb` (`1024×768`) + `Mutter` + Firefox + LibreOffice + Streamlit + ungated `bash` tool ([`computer-use-demo`](https://github.com/anthropics/anthropic-quickstarts/tree/main/computer-use-demo)) | Partial — lacks JDK 11, defaults to `1024×768` (`20 px` too narrow for RuneLite `765 px` canvas + `248 px` panel + `28 px` bar), and exposes an ungated `bash` tool | **No** | `< 3 s` | `~3.5+ GB` image | **Borrow `Xvfb`+`Mutter`+`noVNC` + `loop.py` image pruning; do not run stock** |
| **C. macOS VM (`Virtualization.framework` via Tart)** | macOS guest on Apple Silicon via [`cirruslabs/tart`](https://tart.run/) (`--vnc`) | Yes, **but** Apple Silicon only (cannot run on Linux home server) | **No** | `~8–15 s` boot | **`30–50 GB`** OCI image (exceeds `28 GiB` currently free on Mac) + **hard 2-VM Apple SLA cap** ([Apple Virtualization docs](https://developer.apple.com/documentation/virtualization)) | **Reject** (`15×` heavier than Option A for identical Swing/Java rendering) |
| **D. Windows 11 Pro x64 KVM container (`dockurr/windows`) on x86_64 Linux** | QEMU + `/dev/kvm` + VirtIO + built-in `noVNC` (`:8006`) + QMP snapshots ([`dockur/windows`](https://github.com/dockur/windows)) | Yes, though heavier than Option A for 30s screenshot runs | **Yes — 100% real Windows 11 x64** (`explorer.exe`, `ZoneId=3`, SmartScreen, WDAC/SAC `VerifiedAndReputablePolicyState`, PowerShell 5.1, x64 Temurin 11) | **`3–5 s` warm restore** via QMP `loadvm` (`45–75 s` cold boot) | `~28–40 GB` `qcow2` + CoW overlay; `4–6 GB` RAM | **Recommended rig for `W5` (Windows launcher E2E) on the x86_64 home server** |
| **E. Windows 11 ARM VM on this M2 Max Mac (Parallels / UTM)** | Apple `Virtualization.framework` (`arm64` Win11 + Prism x64 emulation) or QEMU TCG (`x86_64` software emulation) | Possible, wastes Mac RAM/SSD | **Degraded:** M2 lacks nested virt (`M3+` required on macOS 15+: [`isNestedVirtualizationSupported`](https://developer.apple.com/documentation/virtualization/vzgenericplatformconfiguration/isnestedvirtualizationsupported)); Prism ≠ true x64 player OS, and `35 GB` VM doesn't fit `28 GiB` free SSD | `5–10 s` (ARM) / `2+ min` (TCG) | `25–50 GB` disk; `6–8 GB` RAM | **Fallback only** (external USB4 SSD if no x86_64 host / cloud VM) |
| **F. Ephemeral Cloud Windows 11 VM (Azure `Standard_D4s_v5`)** | 4 vCPU / 16 GiB Windows 11 24H2 Pro over Tailscale VNC, deallocated after run | High WAN latency for everyday plugin checks; cloud datacenter IPs trigger Jagex/Cloudflare blocks for game login | **Yes** (real Windows 11 Pro SAC + SmartScreen; unlike GitHub Actions `windows-latest` Server OS) | `90–180 s` start/stop | `0 GB` local; **`~$0.06` per 10-min launcher run** (`$0.376/hr` on-demand, [Azure pricing](https://azure.microsoft.com/en-us/pricing/details/virtual-machines/windows/)) | **Good zero-hardware backup for `W5` only** *(never used for Jagex game logins because Jagex/Cloudflare blocks datacenter IPs)* |

#### Critical Windows 11 Smart App Control (SAC) nuance for `W5` (verified against Microsoft Learn)

Fresh Windows 11 installs start Smart App Control in **Evaluation Mode** (`HKLM\SYSTEM\CurrentControlSet\Control\CI\Policy\VerifiedAndReputablePolicyState = 2`), which silently logs execution and **blocks nothing** ([Microsoft Learn — Smart App Control](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/smart-app-control/overview)). A naive clean-VM check would get a **false green** while real players in **Enforcement Mode (`1`)** get blocked. Option D therefore keeps two QMP snapshots:
1. **`snap-smartscreen` (`State = 0/2`):** Tests `ZoneId=3` download → Explorer double-click → *"Windows protected your PC"* → *More info* → *Run anyway* → Java 11 SHA-256 + Gradle build + RuneLite window opening.
2. **`snap-sac-enforced` (`State = 1`, rebooted):** Proves Explorer double-clicking `ZoneId=3` `Run-GimUtilities.cmd` **is blocked** by SAC first, then proves the `SETUP.md:278` Start → PowerShell one-liner (`iwr -UseBasicParsing ... -OutFile $f; cmd /c $f`, no `Zone.Identifier`) succeeds and opens RuneLite.

### 3.2 AI driver options & exact cost per run

#### Image token math (`1280 × 800` single-client, `1600 × 900` two-client)

Anthropic bills computer-use screenshots at `(width × height) / 750` input tokens with zero tool surcharge ([Claude Computer Use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/computer-use-tool); [Claude Vision](https://platform.claude.com/docs/en/build-with-claude/vision); [Claude Pricing](https://platform.claude.com/docs/en/about-claude/pricing)):

- **`1280 × 800` (default 1-client rig):** $1{,}024{,}000 / 750 \approx \mathbf{1{,}365\text{ tokens/image}}$. Comfortably fits fixed-mode OSRS (`765 px`) + expanded `PluginPanel` (`~248 px`) + toolbar (`28 px`) = `~1,041 px` at 1:1 native pixels (zero coordinate scaling drift).
- **`1600 × 900` (2-client Party sync layout — or two `:99`/`:100` `1280×800` displays switched via `rig_select_client("A"|"B")`):** Switching between two `1280×800` Xvfb displays (`:99` for Client A, `:100` for Client B) keeps every screenshot at `1,365` tokens and native 1:1 scale while letting `show-me` display both clients side-by-side in the browser!
- **Three cost rules (~95% savings vs. naive loops):** (1) `only_n_most_recent_images = 3` (older screenshots replaced by 1-line text tombstones, borrowed from `computer_use_demo/loop.py`), (2) ephemeral prompt caching on system prompt + tools, and (3) deterministic C/Go/Python helpers (`rig_wait_for_window`, `rig_login`, `rig_crop_widget`) so builds, logins, and exact pixel crops burn **zero** LLM turns.

#### Per-run cost comparison (`2026-11-14` verified rates)

*Rates ([`platform.claude.com/docs/en/about-claude/pricing`](https://platform.claude.com/docs/en/about-claude/pricing)): **Sonnet 5.5** `$2.00` in / `$2.50` cache write / `$0.10` cache read / `$10.00` out per MTok; **Opus 5.5** `$4/$5/$0.20/$20`; **Haiku 5.5** `$0.10/$0.125/$0.01/$0.50`.*

| Driver / Model | **Live login + README/overlay check** *(12 turns @ `1280×800`)* | **Full 16-shot README + Discord refresh** *(20 turns, scripted crops)* | **2-client Party sync (`W2`) or Win11 launcher (`W5`)** *(18–22 turns)* | **100 runs / month** |
| --- | --- | --- | --- | --- |
| **D1. StayPoint `rig-mcp` inside Claude Code (Max `$200/mo` seat)** *(Recommended default)* | **`$0.00` marginal** *(~$0.08 list equivalent)* | **`$0.00` marginal** *(~$0.14 list equivalent)* | **`$0.00` marginal** *(~$0.13–$0.16 list equivalent)* | **`$0.00` marginal** |
| **D2. Direct Anthropic API — Claude Sonnet 5.5** (`computer_20251124`) | **`$0.084`** (`~8¢`) | **`$0.142`** (`~14¢`) | **`$0.129–$0.155`** (`~13–16¢`) | **`$8.40 / mo`** |
| **D3. Direct Anthropic API — Claude Opus 5.5** (`computer_20251124`) | **`$0.168`** (`~17¢`) | **`$0.284`** (`~28¢`) | **`$0.258–$0.310`** (`~26–31¢`) | **`$16.80 / mo`** |
| **D4. Direct Anthropic API — Claude Haiku 5.5** | **`$0.005`** (`0.5¢`) | **`$0.008`** (`< 1¢`) | **`$0.008`** (`< 1¢`) | **`$0.48 / mo`** |

### 3.3 Speed breakdown (why live screenshot & plugin runs finish in ~30–45 seconds)

| Stage | **1-client Live Login + Shot/Check (`W1`/`W3`, warm)** | **2-client Party Sync + README Refresh (`W1`/`W2`, warm)** | **Windows 11 Clean Launcher E2E (`W5`, snapshot)** |
| --- | --- | --- | --- |
| 1. Container / VM ready | `1.5 s` (`docker run` + `tmpfs`) | `2.0 s` (dual Xvfb `:99` + `:100`) | `3–5 s` (`qmp loadvm snap-sac-enforced`) |
| 2. RuneLite launch + `rig_login` (`Play Now` → in-world, 0 LLM turns) | `9–13 s` (warm Gradle classpath + Jagex session login) | `14–18 s` (Client A + Client B launched in parallel) | `120–180 s` *(real player 200 MB Temurin 11 + Gradle download, polled by `rigd`, 0 LLM turns)* |
| 3. LLM + scripted steps (`1.8–2.5 s`/turn + `70 ms` capture) | `8–12` turns = **`16–26 s`** | `18–22` turns = **`38–55 s`** *(replaces `25 min` manual test!)* | `10–16` turns = **`20–35 s`** |
| 4. `ScreenScrubber` + deterministic PNG crop + teardown | `1.5 s` | `2.5 s` | `2.0 s` |
| **Total wall-clock (`do-it` mode)** | **`~28–42 seconds`** | **`~57–78 seconds`** *(vs. `25 min` by hand)* | **`~2.5–3.5 minutes`** |

---

## 4. Where It Runs: This Mac vs. The Home Server

| Dimension | **This Mac (`Apple M2 Max`, `64 GiB` RAM)** *(verified `2026-11-14`)* | **The Home Server (`x86_64` Linux box over Tailscale)** *(from `GENERAL-82`)* |
| --- | --- | --- |
| **Free disk today** | **`28 GiB` free** on `/System/Volumes/Data` (`df -h` re-measured `2026-11-14 12:26 PST`, up from `8.1 GiB` earlier today) — **ready right now** for the `~2.2 GB` Option A container (`GENERAL-38` cache prune still recommended for comfortable margin) | `256–512 GB` NVMe (`512 GB` recommended for two `35 GB` Windows 11 snapshots + Docker cache; **purchase/rack status: Unverified — Board to confirm before Slice 4**) |
| **macOS Keychain & residential IP** | **Direct local Keychain read** (`/usr/bin/security`) + normal residential ISP IP (avoids Cloudflare/Jagex datacenter blocks that hit cloud VMs) | Reads zero local secrets; `rig-runner` on the Mac streams ephemeral `JX_*` into `rigd` RAM over Tailscale at run start; shares residential ISP IP on LAN |
| **`show-me` live mode UX** | **Zero-latency localhost window** (`127.0.0.1`), instant `Esc×2` / `STOP` button | Low-latency LAN/Tailscale HTTPS window (`https://<home-box>.<tailnet>.ts.net`) |
| **Linux Docker (`Option A`, `W1`–`W4`)** | Native `linux/arm64` via `Docker.app`; fast M2 Max single-core speed for Swing + OSRS software/OpenGL renderer | Native `linux/amd64` Docker Engine (`32 GiB` DDR4 recommended if co-hosting Option A + Option D simultaneously) |
| **Windows 11 x64 KVM (`Option D`, `W5`)** | No hardware nested virt on M2 (`M3+` required); cannot run fast native x64 KVM | **Native `/dev/kvm`** — restores clean Windows 11 x64 snapshots in `3–5 s` |

**Recommended placement:**
1. **Run `W1`–`W4` (Option A Linux `arm64` Docker) right on this M2 Max Mac starting in Slice 1 (`R1`):** `28 GiB` free disk is already sufficient, Keychain access is local, residential IP avoids Jagex/Cloudflare datacenter flags, `do-it` runs silently in the background without touching the macOS cursor, and `show-me` pops up locally in `< 0.5 s`.
2. **Run `W5` (Option D Windows 11 x64 KVM) on the `GENERAL-82` x86_64 home server** (or wake an x86_64 desktop via WoL, or use an Azure `D4s_v5` Windows 11 VM at `~$0.06/run` strictly for `W5` where no Jagex login is needed). Because `rig-mcp` talks to `RIG_URL`, offloading `W1`–`W4` to the home server later is a one-line env change while keeping secrets exclusively in the Mac's Keychain.

---

## 5. How a Run Is Started (`do-it` / `show-me`) and Reported in StayPoint

### 5.1 Starting a run (CLI, coding task, or StayPoint QA task)

```bash
# 1. Unattended ('do it') — logs in via Keychain, refreshes README + Discord shots, attaches report:
rig run scenarios/gim-readme-and-discord-shots.yaml --mode do-it --account gim-test-a

# 2. Watched live ('show me') — opens live 60fps HUD window with crosshair preview & red STOP button:
rig run scenarios/gim-two-client-party-sync.yaml --mode show-me --account gim-test-a --account-b gim-test-b

# 3. Emergency stop from anywhere (< 100 ms):
rig stop
```

Inside StayPoint, runs start either as a standalone task (`"Run the rig in do-it mode on gim-test-a to refresh README & Discord screenshots after PR #51"`) or as a pre-Ship-Review verification step inside a UI/overlay PR before calling `staypoint_ship_review`.

### 5.2 Declarative scenario example (`rig/scenarios/gim-readme-and-discord-shots.yaml`)

```yaml
id: gim-readme-and-discord-shots
environment: linux-runelite-1280x800
mode_default: do-it                       # overridable via --mode show-me
auth:
  account_slot_a: required                # verified against ~/.config/rig/allowed-accounts.json
  keychain_prefix: "staypoint.rig.test-account."
privacy_seed:
  entity_hider_hide_others: true          # prevents random player RSNs in docs/images/*.png
  public_chat: "off"
  private_chat: "off"
  trade_chat: "off"
  redact_config_fields: ["webhookUrl", "onlineWebhookUrl", "dropsWebhookUrl", "progressWebhookUrl", "deathsWebhookUrl", "storageWebhookUrl", "digestWebhookUrl", "groupKey"]
canvas_policy: allow_scripted
limits:
  max_turns: 20
  max_in_world_s: 120
steps:
  - login: { slot: "a" }                  # atomic rig_login: Play Now -> Lobby skip -> LOGGED_IN
  - run_harness_scenario: "full-gim-roster-and-storage"   # seeds rich deterministic roster + rare events
  - in_game_script: |
      1. Open Bank or Group Storage if nearby, verify real item icons populate Group, Banks, and Storage tabs, and capture `panel-group.png`, `member-bank.png`, `storage-snapshot.png`, and `storage-log.png`.
      2. Open Find tab, search `shark`, capture `find-panel.png`, then run `::find shark` in the chatbox and capture `find-chatbox.png` + `group-events.png` (toast banner + group box visible).
      3. Open GIM Utilities config and capture scrubbed `settings-overview.png` and all 7 section shots (`webhooks`/`groupKey` masked).
      4. Switch to the test Discord channel viewer (`SEND_MESSAGES=false`) and verify/capture the live embeds (`Town Crier`, `Party Pete`, `Skillcape Seller` combined Prayer streak, `Gielinor Banker`, `World Hopper` board).
exports:
  readme_dir: "docs/images"               # scanned by ScreenScrubber + RSN/secret gate before write
```

### 5.3 How results and screenshots attach to StayPoint

StayPoint's `task_doc` (`staypoint.doc/v1`) currently supports text/table/callout blocks without a binary `task_attach` image tool. We handle both README deliverables and QA runs cleanly:

1. **Committed README & docs screenshots (`W1`):** Written straight into the task worktree's `docs/images/*.png` (after passing `ScreenScrubber` + bystander-RSN check) so the PR diff on GitHub shows the exact updated images side-by-side.
2. **StayPoint Slice `R-SP1` (native per-run screenshot attachments in `VinnyVanGogh/staypoint`):** Adds `task_attach(path, label, kind, justification)` (`~/.staypoint/artifacts/<task_id>/<run_id>/<seq>-<slug>.webp`, EXIF stripped, max `5 MB`/image, `25`/run, scanned for secrets, served authenticated on loopback/Tailscale) + `image` and `gallery` blocks in `staypoint.doc/v1` + a Ship Review thumbnail strip.
3. **Day-1 zero-blocker fallback:** Before `R-SP1` lands, `rigd` emits a `staypoint.doc/v1` step/verdict/cost/gate summary **plus** a self-contained HTML contact sheet (`inline 800 px WebP` frames + click/crosshair audit overlays).

---

## 6. Verified Assumptions

| # | Assumption | How checked (`2026-11-14`) | Status |
| --- | --- | --- | --- |
| **A1** | RuneLite accepts Jagex Account sessions via `JX_SESSION_ID`, `JX_CHARACTER_ID`, and `JX_DISPLAY_NAME` environment variables (the same mechanism used by the Jagex Launcher), bypassing legacy password boxes and plaintext `credentials.properties` | [RuneLite Wiki — Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts); `SETUP.md:202-206` | **Verified** |
| **A2** | macOS `/usr/bin/security find-generic-password -s <service> -w` retrieves a Keychain secret directly to stdout without exposing secret material in `ps` argv | macOS `security(1)` & [Apple Keychain Services docs](https://developer.apple.com/documentation/security/keychain-services) | **Verified** |
| **A3** | Jagex/Cloudflare gates `account.jagex.com` interactive web logins with Cloudflare Turnstile and blocks datacenter/VPN IPs, whereas residential ISP launches using an active `JX_SESSION_ID` go straight to RuneLite's `Play Now` screen | Jagex support / RuneLite developer auth docs (`2026-11-14`) | **Verified** |
| **A4** | OSRS displays the player's last-login WAN IP on the post-login Lobby welcome widget (`InterfaceID` `378`) before clicking `CLICK HERE TO PLAY` | Standard OSRS lobby UI behavior; mitigated by completing Lobby entry inside `rig_login()` before unlocking `rig_screenshot` + `ScreenScrubber` widget mask | **Verified** |
| **A5** | `README.md` currently embeds `16` PNGs under `docs/images/*.png`, and `docs/group-settings-manual-test.md` defines an 11-part two-client (`-PaltHome`) Party sync test taking ~25 min by hand | Inspected `README.md:18-161`, `docs/images/`, `docs/group-settings-manual-test.md` | **Verified** |
| **A6** | This `Apple M2 Max` Mac (`64 GiB` RAM) currently has **`28 GiB` free** on `/System/Volumes/Data` (`12:26 PST`, up from `8.1 GiB`), enough to build and run the `~2.2 GB` Linux `arm64` rig container immediately | Measured via `df -h /System/Volumes/Data` (`2026-11-14`) | **Verified** |
| **A7** | OpenJDK `java.awt.Canvas` creates a native heavyweight child peer (`XCanvasPeer` on X11; `SunAwtCanvas` on Win32) distinct from lightweight Swing `PluginPanel`s, and `1280×800` (`1,365` tokens/image) fits fixed-mode RuneLite (`~1,041 px`) at 1:1 scale | [Oracle AWT Heavyweight/Lightweight Mixing](https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_hw_lw.html); [Claude Vision](https://platform.claude.com/docs/en/build-with-claude/vision) | **Verified** |
| **A8** | Current Anthropic API rates: Sonnet 5.5 `$2/$10` (`$0.10` cache read), Opus 5.5 `$4/$20`, Haiku 5.5 `$0.10/$0.50` per MTok; `rig-mcp` inside Claude Max runs at `$0` marginal cash | [Claude Pricing](https://platform.claude.com/docs/en/about-claude/pricing); StayPoint `RESEARCH-79` | **Verified** |
| **A9** | Apple Silicon M2 lacks hardware nested virtualization (`M3+` required on macOS 15+), `Run-GimUtilities.cmd` pins Windows **x64** Temurin 11 (`:33-34`), and Windows 11 Smart App Control starts in silent Evaluation Mode (`State = 2`) unless set to Enforcement Mode (`State = 1`) | [Apple `isNestedVirtualizationSupported`](https://developer.apple.com/documentation/virtualization/vzgenericplatformconfiguration/isnestedvirtualizationsupported); [Microsoft Learn — SAC](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/smart-app-control/overview) | **Verified** |
| **A10** | How long a Jagex `JX_SESSION_ID` remains valid across container restarts before requiring `JX_REFRESH_TOKEN` rotation or re-enrollment | Community launcher docs report long-lived `JX_SESSION_ID` sessions, but exact TTL is undocumented by Jagex | **Unverified — measure in Slice 1/2** *(store optional refresh metadata in Keychain and fail fast with `rig auth refresh <slug>` if expired)* |
| **A11** | Whether the `GENERAL-82` x86_64 mini PC home server is already purchased and online | Checked StayPoint tasks through `2026-11-14` | **Unverified — Board to confirm before Slice 4 (`W5`)** |

---

## 7. Threat Model

| # | Threat | Impact | Mechanical mitigation | Residual risk |
| --- | --- | --- | --- | --- |
| **T1** | **Rig accidentally logs into or clicks inside the Board's personal main or real GIM account** | Unintended automated input on a main character | `~/.runelite` is never mounted; `rig-runner` only reads `staypoint.rig.test-account.<slug>` Keychain items matching `JX_CHARACTER_ID` + `JX_DISPLAY_NAME` in host-only `~/.config/rig/allowed-accounts.json` | Zero unless the Board manually enrolls a main character into `allowed-accounts.json` |
| **T2** | **Credential, `JX_SESSION_ID`, group key, Discord webhook, or home WAN IP leaks into logs, files, screenshots, or task comments** | Account hijack, webhook spam, or home IP doxxing in a public repo/PR | Keychain → RAM → direct `java` `env` (no Gradle `-P`/`jvmArgs`, no `credentials.properties`); `tmpfs` `$HOME`; `rig_login()` skips login & Lobby IP widget (`378`) before first screenshot; `ScreenScrubber` masks config secrets; `RedactingWriter` + pre-upload StayPoint scanner blocks leaks fail-closed | Near zero; verified by adversarial leak tests `T-S1`..`T-S5` before any real token is loaded |
| **T3** | **Random bystander player RSN or overhead chat photobombs a committed `docs/images/*.png` screenshot** (`docs/README.md:5-10`) or injects prompt text | Violates repo privacy rule or attempts vision prompt injection | Container pre-seeds RuneLite `Entity Hider` (hide non-party players + 2D overhead text) and `Public Chat / Private Chat / Trade = Off`; `rig-mcp` exposes **no `bash` tool**; export gate rejects non-allowlisted RSN strings | Minimal; PR diff still gets visual check on Ship Review card |
| **T4** | **Runaway or stuck LLM loop behaves like a repetitive bot in-world** | Heightens Jagex anti-cheat scrutiny or wastes quota | Hard `max_turns` (`15–25`), hard in-world wall-clock TTL (`90–180 s` → auto-logout + `docker kill`), `< 100 ms` 4-way Kill Switch, and `show-me` mouse takeover tripwire | Board-accepted test-account risk on short 60s sessions |
| **T5** | **Residential WAN IP linkage across test and main accounts** | Jagex enforcement can correlate accounts sharing a home IP | Board explicitly accepted this risk for test accounts (`comment #17135`); risk minimized by keeping live sessions `< 90 s`, running pure UI/launcher checks (`W4`, `W5`, offline `ConfigPanel`) with `canvas_policy: "deny"` and zero game login, and never running repetitive training loops | Inherent to testing on a real OSRS world from home ISP; explicitly owned by the Board |
| **T6** | **Expired Jagex session triggers `account.jagex.com` / Cloudflare Turnstile / 2FA** | Violates hard anti-CAPTCHA policy or hangs unattended run | `InputGate` denylist detects `account.jagex.com` / Turnstile / login failure immediately, **never** clicks or waits on Turnstile, aborts cleanly, and notifies Board to run `rig auth refresh <slug>` | 30-second manual refresh by Board when a token eventually expires |
| **T7** | **`noVNC` port exposed off-machine** | Outsider views or clicks inside a logged-in test character session | Bind `127.0.0.1` only + per-run 128-bit random VNC token; remote access strictly via `tailscale serve` (never Funnel, zero router port forwards) | Tailnet compromise only |
| **T8** | **Windows 11 Smart App Control false-pass in Evaluation Mode (`2`)** | Broken `Run-GimUtilities.cmd` ships to Windows 11 friends | `W5` tests `snap-sac-enforced` (`State = 1`) negative-first (`ZoneId=3` double-click **must be blocked** before PowerShell one-liner passes) | Caught automatically on Windows snapshot updates |

---

## 8. Test Plan (Adversarial, Leak, and Kill-Switch Cases First)

Every secret-protection, allowlist, kill-switch, and gate invariant in §2 and §7 is proven **mechanically with synthetic canary tokens (`JX_SESSION_ID=CANARY_SECRET_99182`) before any real Jagex session is ever enrolled** (`Slice 1`):

1. **`T-S1` (Canary token never reaches stdout, stderr, logs, or `ps`):** Inject `JX_SESSION_ID=CANARY_SECRET_99182` into `rigd` with a deliberately crashing dummy Java app that dumps `System.getenv()` to stdout and stderr → verify `CANARY_SECRET_99182` occurs **0 times** across `audit.jsonl`, container logs, `ps aux`, `/tmp`, and StayPoint check-run output (`[REDACTED]` instead).
2. **`T-S2` (`tmpfs` & zero disk persistence):** Run a full dummy login session, kill the container with `SIGKILL`, and scan host filesystem + container image diff → **0 files** created outside `$TMPDIR` report output; `.runelite/credentials.properties` never exists.
3. **`T-S3` (Pre-world login & Lobby WAN-IP screen blocked from screenshots):** Render a mock OSRS login canvas and a mock Widget `378` Lobby banner displaying `"You last logged in earlier today from: 203.0.113.42"`; invoke `rig_screenshot` → verify `rig_login` blocks/masks both screens and `203.0.113.42` + login boxes **never appear** in any saved or LLM-bound PNG.
4. **`T-S4` (Config secret & webhook redaction in screenshots + StayPoint payloads):** Open a test `ConfigPanel` containing `https://discord.com/api/webhooks/123/SECRET_HOOK` and group key `GIM_KEY_SECRET`; capture screenshot + build `task_doc` → verify secret fields are masked in the PNG and pre-upload text scanner blocks any unredacted webhook/token string (`SecretLeakBlocked`).
5. **`T-S5` (Un-allowlisted / main account rejected before Keychain read):** Request `--account main-iron` (absent from `allowed-accounts.json`, or mismatched `JX_CHARACTER_ID`) → `rig-runner` aborts immediately with `AccountNotAllowlisted` (`exit 2`).
6. **`T-S6` (`< 100 ms` Kill Switch under active click loop):** Start an active scripted input loop; trigger each of the 4 kill paths (`rig stop`, `show-me` HUD `STOP` button, `show-me` physical VNC mouse move tripwire, and `SIGTERM`) → verify synthetic input stops in `< 20 ms` and container terminates in `< 100 ms` with `0` subsequent X11 events.
7. **`T-S7` (In-world TTL hard cutoff):** Set `max_in_world_s: 3` on a test session → verify `rigd` automatically terminates the session at `3.0 s` even if the runner tries to keep clicking.
8. **`T-S8` (Cloudflare Turnstile / `account.jagex.com` hard stop):** Map a window titled `"Verify you are human — Cloudflare"` or `"Sign in to your Jagex account"` → `rigd` immediately aborts (`TurnstileOrAuthGateStopped`) with **0 mouse/key events** sent to that window.
9. **`T-S9` (`canvas_policy: "deny"` refusal + dynamic sidebar collapse):** On a synthetic `JFrame` + heavyweight `java.awt.Canvas`, verify clicks inside `expand(R_canvas, 8)` are refused (`RefusedCanvas`), clicks in `R_sidebar` succeed, and collapsing the sidebar widens `R_canvas` and refuses the newly covered pixels on the very next click without restarting `rigd`.
10. **`T-S10` (`show-me` HUD crosshair & step preview):** Launch in `--mode show-me`, verify loopback WebSocket streams `60 fps` desktop + `500 ms` pre-click crosshair overlay, and verify `--step-confirm` blocks execution until confirmed.
11. **`T-S11` (Bystander RSN guard for README exports):** Verify container `settings.properties` boots RuneLite with `entityhider` hiding other players/2D chat and `Public/Private/Trade = Off`, and verify export scanner flags an injected non-group RSN in mock chat.
12. **`T-S12` (Windows 11 SAC negative-before-positive proof, `Slice 4`):** On `snap-sac-enforced`, Explorer double-clicking `ZoneId=3` `Run-GimUtilities.cmd` **must fail** with the SAC block before the `SETUP.md:278` PowerShell bypass runs and **must pass** afterward.

---

## 9. Phased Build Plan (Re-Ordered Around Top Goal — Ready to Sign)

Slices are ordered so **automated Keychain login, `do-it` / `show-me` modes, the Kill Switch, and automatic README + Discord screenshot generation (`W1` + `W4`) ship first** (`R1` → `R2`), with StayPoint native image blocks (`R-SP1`) in parallel, cross-plugin packs (`R3`) next, and Windows 11 KVM (`R4`) independent of plugin work.

| Slice | Repo | Work kind | Depends on | What ships |
| --- | --- | --- | --- | --- |
| **Slice 1 (`R1`)** | `VinnyVanGogh/runelite-computer-use-rig` *(new)* | `coding` | Board approves plan | Linux `1280×800` X11/`noVNC` container (`tmpfs` home), `show-me` live HUD + `do-it` headless runner, `< 100 ms` 4-way Kill Switch, macOS Keychain `rig auth enroll` + `allowed-accounts.json` + `RedactingWriter` + `ScreenScrubber` + policy-scoped `InputGate`, and adversarial tests `T-S1`..`T-S11` green in CI |
| **Slice 2 (`R2`)** *(Top Goal)* | `runelite-computer-use-rig` + `group-ironman-plugin` (`docs/images/` refresh PR) | `coding` | `R1` (uses `RUNELITE-138` scenarios when merged) | Atomic `rig_login` (`JX_*` RAM injection + Lobby WAN-IP skip), `rig-mcp` (`$0` marginal on Claude Max) + `--api` Sonnet 5.5 loop, **automated refresh of all 16 GIM `docs/images/*.png` README shots + live Discord test-server embed captures (`W1` + `W4`)**, and the **2-client Party sync E2E scenario (`W2`)** replacing the 25-min manual test |
| **Slice 3 (`R-SP1`)** | `VinnyVanGogh/staypoint` | `coding` | None *(parallel with `R1`/`R2`)* | Native StayPoint screenshot attachments: `task_attach` MCP tool (`~/.staypoint/artifacts/<task>/<run>/`, EXIF stripped, secret-scanned), `staypoint.doc/v1` `image` & `gallery` blocks, and Ship Review thumbnail strip |
| **Slice 4 (`R3`)** | `runelite-computer-use-rig` | `coding` | `R2` | Reusable multi-plugin screenshot & smoke packs for `Quest Route`, `Combat Vitals`, `Bank Views`, and `Build Guard` (`do-it` & `show-me` scenarios) |
| **Slice 5 (`R4`)** | `runelite-computer-use-rig` + `group-ironman-plugin` (`SETUP.md` if needed) | `coding` | `R1` + x86_64 KVM host (`GENERAL-82` home box or Azure `D4s_v5` fallback) | **W5** Windows 11 clean-machine KVM rig (`dockurr/windows`, Win32 `SunAwtCanvas` `InputGate`, QMP `snap-smartscreen` & `snap-sac-enforced`, `T-S12`) proving `Run-GimUtilities.cmd` end-to-end |

---

### Ready-to-sign task briefs

#### Task R1 — `Computer-use rig Slice 1: Linux X11 container, 'do it' & 'show me' modes, <100ms kill switch, Keychain vault & zero-leak refusal tests`

```markdown
Goal: Scaffold `VinnyVanGogh/runelite-computer-use-rig` and build the container, `do-it` / `show-me` viewer, `< 100 ms` Kill Switch, macOS Keychain secret injector, and policy-scoped `InputGate` — proving all 11 adversarial security/leak/kill-switch tests (`T-S1`..`T-S11` in `docs/plans/computer-use-rig.md` §8) with synthetic canary tokens before any live account is used.

Repo: VinnyVanGogh/runelite-computer-use-rig (new personal repo; main branch, PR only, never merge).
Reference doc: `VinnyVanGogh/group-ironman-plugin` `docs/plans/computer-use-rig.md`.

Scope:
1. Container (`docker/linux-rig/Dockerfile`, `linux/arm64` + `linux/amd64`):
   - Ubuntu 24.04 + `Xvfb` (`1280x800x24` `:99`, plus optional second display `:100` for 2-client tests) + `Mutter`/`Fluxbox` + `x11vnc` (`127.0.0.1` only, random 128-bit token) + `noVNC` + Eclipse Temurin JDK 11 + Chromium + `xdotool` + `xwininfo`.
   - Always mounts `$HOME` and `$RUNELITE_DIR` on RAM `tmpfs` (`size=256m`, destroyed on exit); never mounts host `~/.runelite`.
2. Keychain vault & zero-leak pipeline (`rig` CLI + `rigd` daemon, cyclomatic complexity <= 10):
   - `rig auth enroll <slug> [--from-running-session]` (interactive Board-only command): stores `{JX_SESSION_ID, JX_CHARACTER_ID, JX_DISPLAY_NAME}` in macOS Keychain (`staypoint.rig.test-account.<slug>`) via `/usr/bin/security` and records `{slug, character_id, display_name}` in `~/.config/rig/allowed-accounts.json`.
   - Runtime secret pipe: verifies `<slug>` against `allowed-accounts.json`, reads Keychain into RAM, passes `JX_*` strictly via child `java` `execve` `env` (never CLI args, never Gradle `-P`/`jvmArgs`, never files).
   - `RedactingWriter` + `ScreenScrubber` + pre-upload StayPoint scanner guaranteeing secrets, webhooks, group keys, bystander RSNs, and home WAN IPs never appear in stdout, logs, files, screenshots, or task comments.
3. Execution modes (`--mode do-it` | `--mode show-me`), `< 100 ms` Kill Switch & `InputGate`:
   - `do-it` runs headless in background without touching macOS cursor; `show-me` opens local `show-me.html` HUD (`60 fps` VNC, step banner, `500 ms` pre-click crosshair preview, `[STOP]` & `[Pause/Step]` buttons, and physical VNC mouse-movement takeover tripwire).
   - 4-way Kill Switch (`rig stop`, HUD `STOP`, VNC mouse takeover, `SIGTERM` / `max_in_world_s` TTL) halts input in `< 20 ms` and kills container + `tmpfs` in `< 100 ms`.
   - `InputGate` enforces Turnstile/CAPTCHA/password-manager hard stop (`0` clicks on `account.jagex.com` or Cloudflare Turnstile) and per-scenario `canvas_policy` (`allow_scripted` for allowlisted test accounts vs `deny` using `XCanvasPeer` + `8 px` moat).
4. Write and pass adversarial tests `T-S1`..`T-S11` (`computer-use-rig.md` §8) in GitHub Actions CI.

## Spec interview
- Q: In-game input, modes, and credentials? A (Board 2026-11-14): in-game input allowed on Board-chosen test accounts; two modes ('do it' unattended and 'show me' live-watched with stop/kill switch); credentials read from macOS Keychain / Jagex session at run time and never printed, logged, written to files, screenshots or task comments.
```

#### Task R2 — `Computer-use rig Slice 2 (Top Goal): automated login + live GIM README & Discord screenshot pipeline + 2-client Party sync`

```markdown
Goal: Ship the `rig-mcp` + Anthropic API computer-use driver, atomic `rig_login` (Keychain `JX_*` → `Play Now` → Lobby skip → in-world), and GIM Utilities' automated **README + Discord screenshot generator** (`W1` + `W4`) plus the **2-client Party sync test** (`W2`) (`docs/plans/computer-use-rig.md` §2.2, §5).

Depends on: Task R1 merged.
Repos: `VinnyVanGogh/runelite-computer-use-rig` + screenshot refresh PR on `VinnyVanGogh/group-ironman-plugin` (PR only, never merge).

Scope:
1. Atomic `rig_login(slot)` & pre-seeded privacy profile:
   - Launches RuneLite dev classpath directly on `java` with RAM `JX_*` env + pre-seeded `settings.properties` (`Entity Hider` hiding non-party players/overhead text, `Public/Private/Trade = Off`).
   - Clicks `Play Now` and clicks through OSRS Lobby Widget `378` (`CLICK HERE TO PLAY`, hiding last-login WAN IP) *before* unlocking `rig_screenshot`, aborting immediately if a session token is expired or hits Turnstile (`rig auth refresh <slug>`).
2. `rig-mcp` server (`$0` marginal on Claude Max) + `--api` Sonnet 5.5 runner (`computer_20251124`, `only_n_most_recent_images = 3`, prompt caching, HTML contact sheet + `staypoint.doc/v1` report):
   - Exposes gated tools only (`rig_start`, `rig_login`, `rig_select_client`, `rig_screenshot`, `rig_click`, `rig_move`, `rig_scroll`, `rig_type`, `rig_key`, `rig_crop_export`, `rig_stop` — NO `bash` tool).
3. Ship and verify three flagship GIM scenarios (in both `do-it` and `show-me` modes):
   - `gim-readme-shots.yaml` (`W1`): logs in, combines real bank/group-storage/`::find` actions with `RUNELITE-138` scenario events, masks webhook/groupKey config fields, and regenerates crisp PNGs for `docs/images/*.png`.
   - `gim-discord-shots.yaml` (`W4`): posts test events across GIM Utilities' 7 webhook categories (`Town Crier`, `World Hopper`, `Party Pete`, `Skillcape Seller`, `Death's Office`, `Gielinor Banker`, `Varrock Herald`), opens the locked test Discord channel (`SEND_MESSAGES=false`), visually verifies embeds + `PATCH` edits + stitched `LevelShot`/`GraveShot`, and exports clean Discord showcase screenshots.
   - `gim-two-client-party-sync.yaml` (`W2`): runs Client A (`:99`) + Client B (`:100`, `-PaltHome`) through the leader/editor/opt-out/webhook-isolation checks in `docs/group-settings-manual-test.md` in ~60–90 seconds.

## Spec interview
- Q: Top goal? A (Board 2026-11-14): the rig logs into Board-chosen test accounts and takes the README and Discord screenshots itself (faster, better shots), in 'do it' or 'show me' mode with Keychain login and zero secret leaks.
```

#### Task R-SP1 — `StayPoint: per-run screenshot attachments (task_attach MCP tool + staypoint.doc/v1 image block + Ship Review gallery)`

```markdown
Goal: Let StayPoint runs attach scrubbed PNG/WebP screenshots to a task and render them in Task Documents and Ship Review cards without committing transient QA captures to git (`docs/plans/computer-use-rig.md` §5.3).

Repo: VinnyVanGogh/staypoint (personal, PR to main, never merge).

Scope:
1. Storage & HTTP route:
   - Store validated PNG/JPEG/WebP files under `~/.staypoint/artifacts/<task_id>/<run_id>/<seq>-<slug>.<ext>`, served authenticated on StayPoint's web server (`GET /api/tasks/{id}/artifacts/{run_id}/{filename}`, `CSP: default-src 'none'; img-src 'self'`, `X-Content-Type-Options: nosniff`).
   - Validate magic bytes (refuse SVG/HTML), strip EXIF/ancillary text chunks (so metadata never smuggles secrets), cap at `5 MB`/file and `25` files/run.
2. MCP tool `task_attach` (`effect = dev_write`, own task only) + `staypoint.doc/v1` `image` & `gallery` blocks + Ship Review thumbnail strip.
3. Adversarial tests first: `$TMPDIR` symlink escape refused; disguised SVG/HTML `.png` refused; EXIF text metadata stripped; cross-task artifact URL in `staypoint.doc/v1` refused; `26th` attachment refused.

## Spec interview
- Q: How should computer-use rig runs report results? A (Board 2026-11-14): StayPoint task with screenshots attached, zero secret leaks, and no QA image bloat in plugin git repos.
```

#### Task R3 — `Computer-use rig Slice 3: Multi-plugin README screenshot & visual smoke packs (Quest Route, Combat Vitals, Bank Views, Build Guard)`

```markdown
Goal: Extend `runelite-computer-use-rig` (`R2`) beyond GIM Utilities with declarative screenshot-refresh and 30-second visual smoke scenarios across the remaining owned RuneLite plugins (`docs/plans/computer-use-rig.md` §0, §9).

Depends on: Task R2 merged.
Repo: VinnyVanGogh/runelite-computer-use-rig.

Scope:
1. Parameterize plugin checkout mounting, Gradle cache sharing, and `settings.properties` seeding so any owned RuneLite plugin repository runs with `rig run --repo <path> <scenario>.yaml` in `do-it` or `show-me` mode.
2. Author scenario packs (`scenarios/quest-route/`, `scenarios/combat-vitals/`, `scenarios/bank-views/`, `scenarios/build-guard/`) covering sidebar/config visual fit, logged-in overlay checks on Board-allowlisted test accounts, and one-command `docs/images/` README screenshot regeneration.

## Spec interview
- Q: Plugin scope? A (Board 2026-11-14): general across our RuneLite plugins, not only GIM Utilities; quick ~30-second Playwright-style checks and automated README screenshots.
```

#### Task R4 — `Computer-use rig Slice 4: Windows 11 KVM clean-machine rig for Run-GimUtilities.cmd (MoTW, SmartScreen, Smart App Control Enforced, first-run GUI)`

```markdown
Goal: Prove `tools/windows/Run-GimUtilities.cmd` and `SETUP.md`'s exact player instructions end-to-end on a real Windows 11 Pro x64 VM with Mark of the Web (`ZoneId=3`), SmartScreen, and enforced Smart App Control (`docs/plans/computer-use-rig.md` §3.1, §4, §8 `T-S12`).

Depends on: Task R1 merged + x86_64 KVM host (`RIG_URL` on the `GENERAL-82` home server, or an ephemeral Azure `Standard_D4s_v5` Windows 11 24H2 Pro VM — zero Jagex credentials needed for `W5`).
Repo: VinnyVanGogh/runelite-computer-use-rig (plus doc fixes in `group-ironman-plugin` `SETUP.md` if live testing uncovers friction).

Scope:
1. Windows 11 KVM target (`docker/win11-rig/docker-compose.yml` using `dockurr/windows` with `/dev/kvm`, loopback `noVNC` `:8006`, QMP socket, zero credentials):
   - Two golden QMP snapshots on clean Windows 11 24H2 x64: `snap-smartscreen` (SmartScreen on, SAC eval/off) and `snap-sac-enforced` (`VerifiedAndReputablePolicyState = 1`, rebooted and enforcing).
   - Lightweight Win32 `rigd-agent` enforcing `canvas_policy: "deny"` (`SunAwtCanvas` child `HWND` refused; screenshot-only once RuneLite opens) + `< 100 ms` Kill Switch + `show-me` viewer forwarding.
2. Two E2E scenarios (`T-S12` negative-first):
   - `win11-launcher-smartscreen.yaml`: restores `snap-smartscreen` (`~4 s`), downloads `Run-GimUtilities.cmd` (`ZoneId=3`), double-clicks in Explorer, navigates *"Windows protected your PC"* → *More info* → *Run anyway*, waits via `rig_wait_for_window` through `[1/3]..[3/3]`, and captures the opened RuneLite window + GIM Utilities icon.
   - `win11-launcher-sac-enforced.yaml`: restores `snap-sac-enforced`, double-clicks `ZoneId=3` `Run-GimUtilities.cmd`, **asserts Smart App Control blocks it**, then runs the exact Start → PowerShell one-liner in `SETUP.md:278` and verifies RuneLite opens cleanly.

## Spec interview
- Q: Windows launcher scope? A (Board 2026-11-14): test the Windows launcher end-to-end on a clean Windows VM — Smart App Control, SmartScreen, the PowerShell route, and first build opening RuneLite, watchable in 'show me' or unattended in 'do it'.
```

---

## 10. Citations (All Verified `2026-11-14`)

1. **RuneLite Wiki — Using Jagex Accounts** (developer-mode Jagex Account auth panel, `JX_SESSION_ID` / `JX_CHARACTER_ID` / `JX_DISPLAY_NAME` session flow, and deprecation warning against plaintext `.runelite/credentials.properties`): <https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>
2. **Apple Developer Documentation — Keychain Services & macOS `security(1)` CLI** (`find-generic-password` / `add-generic-password` OS credential storage): <https://developer.apple.com/documentation/security/keychain-services>
3. **Jagex — Rules of Old School RuneScape** & **Third-Party Client Guidelines**: <https://legal.jagex.com/docs/rules/rules-of-old-school-runescape> · <https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1>
4. **RuneLite Wiki — Rejected or Rolled-Back Features** & **RuneLite Source (`ClientUI.java`, `ContainableFrame.java`, `PluginPanel.java`)**: <https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features> · <https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/ui>
5. **Oracle Java SE Tutorial — Mixing Heavyweight and Lightweight Components** (`java.awt.Canvas` native peer `XCanvasPeer` / `SunAwtCanvas` inside Swing `JFrame`): <https://docs.oracle.com/javase/tutorial/uiswing/misc/trans_hw_lw.html>
6. **Anthropic — Computer Use Tool Documentation** (`computer_20250124` / `computer_20251124`, actions, coordinate scaling, prompt-injection & container isolation guidance): <https://platform.claude.com/docs/en/agents-and-tools/tool-use/computer-use-tool>
7. **Anthropic — Vision Documentation** (image token formula `width × height / 750` and resolution caps): <https://platform.claude.com/docs/en/build-with-claude/vision>
8. **Anthropic — Claude API Pricing** (Haiku 5.5, Sonnet 5.5, Opus 5.5 input, output, and prompt-cache write/read per-MTok rates): <https://platform.claude.com/docs/en/about-claude/pricing>
9. **Anthropic — Computer Use Reference Implementation (`anthropics/anthropic-quickstarts/computer-use-demo`)** (`Dockerfile`, `Xvfb` + `Mutter` + `x11vnc` + `noVNC`, `computer_use_demo/loop.py` `only_n_most_recent_images`): <https://github.com/anthropics/anthropic-quickstarts/tree/main/computer-use-demo>
10. **Apple Developer Documentation — `Virtualization` Framework & `VZGenericPlatformConfiguration.isNestedVirtualizationSupported`** (M3+ nested virtualization requirement on macOS 15+; 2 concurrent macOS guest limit): <https://developer.apple.com/documentation/virtualization/vzgenericplatformconfiguration/isnestedvirtualizationsupported>
11. **Cirrus Labs — Tart macOS & Linux Virtualization on Apple Silicon** (`--vnc`, OCI image sizing, 2-VM macOS SLA limit): <https://tart.run/> · <https://github.com/cirruslabs/tart>
12. **Dockur Windows (`dockur/windows`)** (QEMU + `/dev/kvm` containerized Windows 11 with built-in `noVNC` `:8006` and QMP): <https://github.com/dockur/windows>
13. **Microsoft Learn — Smart App Control Overview & Application Control for Windows** (Clean-install Evaluation Mode `2` vs Enforcement Mode `1` under `HKLM\SYSTEM\CurrentControlSet\Control\CI\Policy\VerifiedAndReputablePolicyState`, `Zone.Identifier` Mark of the Web): <https://learn.microsoft.com/en-us/windows/security/application-security/application-control/smart-app-control/overview>
14. **Microsoft Azure — Windows Virtual Machines Pricing** (`Standard_D4s_v5` pay-as-you-go & Spot rates): <https://azure.microsoft.com/en-us/pricing/details/virtual-machines/windows/>
