# Bug Log

Merged bug fixes in reverse chronological order (newest first).

## 2026-11-03 — Group cache could be overwritten before initial disk load completed (#4)

- **What broke:** `GroupService.startUp()` scheduled `loadCache()` asynchronously on the background executor, while `saveCache()` had no check for whether the initial read of `.runelite/build-guard/group-cache.json` had finished. Any save triggered before `loadCache()` completed (such as an early incoming party message or plugin shutdown) would serialize the not-yet-loaded `GroupRoster` and overwrite the on-disk cache with empty or incomplete data.
- **How it was found:** Inspecting the `loadCache()` / `saveCache()` lifecycle while adding session `lastPlayed` tracking and event persistence in Group Ironman part 3 (`GroupService.java`).
- **Fix:** Added a `volatile boolean cacheLoaded` flag set in `loadCache()`'s `finally` block and made `saveCache()` return early until `cacheLoaded` is `true` (#4, `2a4f3aa`).

## 2026-11-03 — Restarted party member with unopened seed vault could wipe receivers' cached seed vault (#4)

- **What broke:** The game client only populates `InventoryID.SEED_VAULT` while the seed vault interface is open. After a client restart, a member's local `seedVaultAt` is `-1` (unknown) until they open the vault again; naively replacing `MemberExtras` on the next rune pouch or quiver update would overwrite the previously cached seed vault snapshot on other party members' clients with an empty map.
- **How it was found:** Designing and unit-testing `GroupRoster.putExtras` (`GroupRosterPart3Test.aRestartedSenderDoesNotWipeTheSeedVaultWeKnow`).
- **Fix:** `GroupRoster.putExtras` retains the existing `seedVault` and `seedVaultAt` when an incoming `MemberExtras` has `seedVaultAt < 0` and a prior snapshot (`seedVaultAt >= 0`) is already known (#4, `2a4f3aa`).

## 2026-11-03 — Turning off *Share build with party* left stale build rules when group switches kept the panel open (#3)

- **What broke:** `BuildGuardPlugin.updateNavButton()` originally cleared `PartyRoster` only when removing the sidebar navigation button. Once Group Ironman part 2 kept the sidebar button visible whenever any `GroupService` switch was enabled (`config.partyShareBuild() || group.anyOn()`), turning off *Share build with party* while another group switch remained on left stale build rules in `PartyRoster`.
- **How it was found:** Wiring `GroupService.anyOn()` into `BuildGuardPlugin.updateNavButton()` during Group Ironman part 2.
- **Fix:** Clear `PartyRoster` whenever `!config.partyShareBuild()`, regardless of whether the sidebar button stays visible for `group.anyOn()` (#3, `a1fc636`).

## 2026-11-03 — Windows launcher source download 404s on a private repository (#2)

- **What broke:** When `tools/windows/Run-BuildGuard.cmd` is run standalone outside a checkout, step `[2/3]` downloads `archive/refs/tags/gim-test-1.zip` from GitHub without authentication, which fails with HTTP 404 while the repository is private.
- **How it was found:** Checking repository visibility (`gh repo view`) while preparing the `gim-test-1` pre-release draft.
- **Fix:** Added a pre-release blocker note and local-checkout fallback instructions to `docs/release-gim-test-1.md` (#2, `dea7c19`).

## 2026-09-26 — Upstream `BuildRule.blocking()` mutation figure did not match bundled quest graph v12 (`a805a68`)

- **What broke:** Upstream `questpath-plugin` javadoc claimed that replacing `BuildRule.blocking()` with `return blocking;` caused 94 of 218 quest verdicts to disagree with the Go planner, which was stale against the bundled `quests.json` (graph v12).
- **How it was found:** Reproducing the mutation against `PlannerVectorsTest` during Phase 0.5 verification (99 of 218 verdicts disagreed: 58 `BUILD_BLOCKED`, 41 `UNREACHABLE`, 13 `ELIGIBLE`).
- **Fix:** Updated `BuildRule` javadoc and `BRIEF.md` to record the measured 99/218 figure and how to reproduce it (`a805a68`).
