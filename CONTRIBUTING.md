# Contributing

## Prerequisites

- **JDK 11–21** for local development. CI (`.github/workflows/build.yml`) and the RuneLite Plugin Hub compile with **Eclipse Temurin JDK 11**, and `build.gradle` enforces `options.release.set(11)`.
- **Python 3** (standard library only) for helper scripts (`tools/tokens.py`, `tools/regions/gen_regions.py`, `scripts/gen-quest-items.py`).
- **PowerShell 7 (`pwsh`)** (optional on macOS/Linux) to run the Windows launcher checksum test suite.

## Build, Run, and Test

```sh
./gradlew build         # compile (Java 11 target) and run all headless JUnit 4 tests
./gradlew test          # run the headless JUnit 4 test suite (renders UI test shots to build/screenshots)
./gradlew test -Pheadful # include the two system-clipboard UI tests that require a display
./gradlew screenshots   # render the deterministic README screenshots into docs/images
./gradlew run           # launch RuneLite in --developer-mode --debug with the plugin loaded
```

Development builds resolve the newest RuneLite client release (`latest.release` from `repo.runelite.net`, re-checked on every build) and print the resolved version before `test` and `run` (`printRuneLiteVersion`). To pin a specific RuneLite release or build from cache:

```sh
./gradlew build -PruneLiteVersion=1.13.6
./gradlew build --offline
```

To run a second isolated client on the same machine for two-account RuneLite Party testing, point `altHome` at a separate directory so each client gets its own `.runelite` folder:

```sh
./gradlew run -PaltHome=/path/to/alt-home
```

To test the Windows double-click launcher (`tools/windows/Run-GimUtilities.cmd`) and its SHA-256 verification logic on any OS with PowerShell 7:

```sh
pwsh -NoProfile -File tools/windows/tests/Checksum.Tests.ps1
```

On Windows (or in `.github/workflows/windows-launcher.yml`), setting `GU_BUILD_ONLY=1` makes `Run-GimUtilities.cmd` run `gradlew.bat --console=plain build` without opening the RuneLite window.

## Pre-Submission Audit and Token Budget

Before opening a pull request or updating a Plugin Hub submission, run the mechanical compliance checker and token estimator from the repository root:

```sh
scripts/preflight.sh --build
python3 tools/tokens.py
```

- `scripts/preflight.sh` verifies:
  - No forbidden language or runtime features (`java.lang.reflect`, JNI/JNA/`Unsafe`, `ProcessBuilder`/`Runtime.exec`, Java serialization, dynamic classloading, `Thread.sleep`, `awaitTermination`, `java.awt.Desktop`).
  - `src/main` never imports the test-only scenario harness (`com.vinnyvangogh.scenarios`).
  - `Gson` and `OkHttpClient` are injected via `@Inject`, never constructed directly or re-declared as transitive dependencies in `build.gradle`.
  - `@ConfigGroup("gimutilities")` is specific and the required third-party IP disclosure `warning` string is present on HTTP toggles.
  - `build.gradle` targets Java 11, `runelite-plugin.properties` and `LICENSE` (BSD-2-Clause) are valid, `icon.png` is a real PNG, and no build artifacts or `META-INF/services` files are tracked.
- `python3 tools/tokens.py` estimates the Plugin Hub auto-review bot's token count across `src/main/java` (which must remain below the 200,000-token limit).

## Testing Harnesses

All test harnesses live under `src/test` and never ship in the Plugin Hub jar:

- **Fake game-event scenarios ([TESTING.md](TESTING.md)):** JSON scenario files in `src/test/resources/scenarios/` run both headlessly in JUnit (`GimScenariosTest` / `GimScenarioRig`) and live in the developer client via the `Scenarios (dev)` sidebar (`ScenariosPlugin`).
- **Headless Swing UI driver ([docs/testing-ui.md](docs/testing-ui.md)):** `SwingDriver` and `ConfigPanelHarness` (`src/test/java/com/vinnyvangogh/testkit/ui`) drive the plugin's sidebar tabs (`GimPanelUiTest`) and RuneLite's `ConfigPanel` (`GimConfigPanelUiTest`) headlessly without `java.awt.Robot`, and render the deterministic README screenshots (`ReadmeShots`).

## Architecture and Guidelines

- **Pure engine first:** Keep domain logic (`GroupCodec`, `GroupRoster`, `GroupSearch`, `StorageDiff`, `StorageSession`, `EventRouter`, `LevelThreads`, `EventThreads`, `StatusBoard`, `GroupGovernance`, `ShopAllocation`, `WaveTracker`, `GroupCompare`, `BenefitFinder`, etc.) free of live `Client` dependencies and covered by deterministic JUnit tests in `src/test/java`.
- **Client thread, EDT, and I/O discipline:**
  - Read game state (`Client`, varbits, item containers, clan channels, `Quest#getState`) on the `ClientThread`.
  - Perform all HTTP calls asynchronously on OkHttp's thread pool (`enqueue()`), never on the client thread.
  - Perform all disk I/O off the client thread via the injected `ScheduledExecutorService` and RuneLite's Filepath API (`getPluginDirectory()` / `PluginFiles` in `.runelite/plugin-data/gim-utilities/`).
  - Build heavy sidebar models (`Banks`, `Storage`) off the Swing Event Dispatch Thread, coalesce redraw bursts (`RedrawCoalescer`), and update item grids in place (`ItemGridView`).
- **Configuration stability:** Never rename `@ConfigGroup("gimutilities")` or any published `@ConfigItem` `keyName` without a migration (`ConfigLayoutTest` and `ConfigFitTest` enforce section membership, key preservation, and label widths). Every sharing and third-party switch must default to off (`PluginIdentityTest`).
- **Privacy in docs:** Redact player account names before committing any live capture to `docs/images/` (or render from made-up fixtures via `./gradlew screenshots`; see [docs/README.md](docs/README.md)). Never commit secrets, webhook URLs, or personal data.
- **Pull requests:** Open pull requests against `main`. Keep `README.md` concise and put detailed setup and multi-client verification steps in [SETUP.md](SETUP.md).
