# Contributing

## Prerequisites

- **JDK 11–21** for local development. CI (`.github/workflows/build.yml`) and the RuneLite Plugin Hub compile with **Eclipse Temurin JDK 11**, and `build.gradle` enforces `options.release.set(11)`.
- **PowerShell 7 (`pwsh`)** (optional on macOS/Linux) to run the Windows launcher checksum test suite.

## Build, Run, and Test

```sh
./gradlew build     # compile (Java 11 target) and run all JUnit 4 tests
./gradlew test      # run the JUnit 4 test suite only
./gradlew run       # launch RuneLite in --developer-mode --debug with the plugin loaded
```

To run a second isolated client on the same machine for two-account RuneLite Party testing, point `altHome` at a separate directory so each client gets its own `.runelite` folder:

```sh
./gradlew run -PaltHome=/path/to/alt-home
```

To test the Windows launcher's SHA-256 verification logic on any OS with PowerShell 7:

```sh
pwsh -NoProfile -File tools/windows/tests/Checksum.Tests.ps1
```

## Pre-Submission Audit

Before opening a pull request or preparing a Plugin Hub submission, run the mechanical compliance checker from the repository root:

```sh
scripts/preflight.sh --build
```

`scripts/preflight.sh` verifies:
- No forbidden language or runtime features (`java.lang.reflect`, JNI/JNA/`Unsafe`, `ProcessBuilder`/`Runtime.exec`, Java serialization, dynamic classloading, `Thread.sleep`, `awaitTermination`, `java.awt.Desktop`).
- `Gson` and `OkHttpClient` are injected via `@Inject`, never constructed directly or re-declared as transitive dependencies in `build.gradle`.
- `build.gradle` targets Java 11 and pins `runeLiteVersion` rather than using `latest.release`.
- `runelite-plugin.properties` and `LICENSE` (BSD-2-Clause) are valid and no build artifacts or `META-INF/services` files are tracked.

## Architecture and Guidelines

- **Pure engine first:** Keep domain logic (`BuildRule`, `QuestClassifier`, `LampAdvisor`, `GroupCodec`, `GroupRoster`, `GroupSearch`, `StorageDiff`, `FindCommand`, etc.) free of `Client` dependencies and covered by deterministic JUnit tests in `src/test/java`.
- **Golden vectors are read-only:** Never edit expected values in `planner-vectors.json` (218 quest verdicts) or `lamp-vectors.json` (9 lamp cases) to make a test pass; a failing vector indicates a logic regression.
- **Client thread discipline:** Read game state (`Client`, varbits, item containers, `Quest#getState`) on the `ClientThread`, and perform all disk I/O (`.runelite/build-guard/group-cache.json`) off the client thread via the injected `ScheduledExecutorService`.
- **Serverless, opt-in Party sharing:** All group communication goes through RuneLite's built-in `PartyService`. Every group sharing feature must remain opt-in (disabled by default) and send nothing when turned off.
- **Privacy in docs:** Redact player account names before committing any screenshot to `docs/images/` (see `docs/README.md`).
- **Pull requests:** Open pull requests against `main`. Keep `README.md` concise and put detailed setup and multi-client test steps in `SETUP.md`.
