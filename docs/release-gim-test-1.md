# Release draft: `gim-test-1` (not published)

Draft notes for a GitHub **pre-release** the Board can publish when the group
is ready to test. Nothing here has been published or tagged.

## Before publishing

1. Merge the PR, then tag the merge commit: `git tag gim-test-1 <merge sha>`
   and push the tag. `Run-BuildGuard.cmd` downloads
   `archive/refs/tags/gim-test-1.zip` when it is run on its own, so **the tag
   must exist before anyone double-clicks it.** If a different tag name is
   used, change `$SourceRef` in the `.cmd` to match first.
2. Create the release from that tag, mark it **pre-release**, paste the notes
   below, and attach `tools/windows/Run-BuildGuard.cmd` as an asset. Download
   it from the repo on GitHub (the *Raw* button) or attach it from a checkout;
   it is a text file and works with either line ending.
3. Do **not** attach a jar: a friend running a jar would still need Java and
   the developer-mode flags, which is what the launcher handles.

## Notes to paste

> **Build Guard: Group Ironman test build 1**
>
> A test build for our group. Not on the Plugin Hub yet.
>
> **Windows:** download `Run-BuildGuard.cmd` below and double-click it. It
> needs no admin rights and installs nothing system-wide; everything goes into
> `%LOCALAPPDATA%\BuildGuard`. If Windows says "Windows protected your PC",
> click **More info → Run anyway** (it is an unsigned text script; open it in
> Notepad to read it). Full steps, including the one-time Jagex account login
> and joining the party: see *Testing with friends (Windows)* in the README.
>
> **What to test:** put your rules in Build Guard's settings (e.g.
> `Defence: 1`), turn on *Share build with party*, join the same RuneLite
> Party, and check the Build Guard panel shows everyone's caps. Switch to a
> Controlled or Defensive attack style on a 1 Defence account and check for the
> red warning.

## What the launcher downloads, and how each is checked

| What | From | Check |
| --- | --- | --- |
| Eclipse Temurin JDK 11.0.32.1+1, Windows x64 zip | `github.com/adoptium/temurin11-binaries` (link from `api.adoptium.net`) | SHA-256 `d5008f02174c1ad21c10407cbf104815cb390ec4c263d53946e8d0507d7a77f9`, pinned in the script; refused on mismatch before unpacking |
| Build Guard source at tag `gim-test-1` | `github.com/VinnyVanGogh/build-guard-plugin/archive/refs/tags/gim-test-1.zip` | Pinned by tag only. GitHub does not guarantee archive zips are byte-identical over time, so no hash is pinned. |
| Gradle 8.10 | `services.gradle.org`, by `gradlew.bat` | `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`, checked by the wrapper |
| RuneLite client 1.12.39 and libraries | `repo.runelite.net`, Maven Central, by Gradle | Gradle's normal resolution; versions pinned in `build.gradle` |

Only download links come from these sources. No `.exe` is shipped.

## What was and was not tested before this draft

- **Tested on macOS with PowerShell 7.2.13** (`tools/windows/tests/Checksum.Tests.ps1`):
  the checksum function passes a matching file, refuses one changed byte, a
  missing file and a malformed pin; the copy inside the `.cmd` is identical
  to the tested one; the pinned hash is well-formed; the whole `.cmd` parses
  as PowerShell.
- **Not run on Windows at all.** Not tested: the `cmd.exe` half of the hybrid
  file, the JDK download and unpack, Windows PowerShell 5.1 specifically,
  SmartScreen's behaviour, `gradlew.bat`, and the client starting. The first
  friend to run it is the first real test; ask them for a screenshot of the
  window either way.
