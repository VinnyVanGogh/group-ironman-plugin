# Build Guard: setup and testing

Everything needed to install, configure and test Build Guard. For what the
plugin is, see the [README](README.md).

- [Settings](#settings)
- [Testing with friends](#testing-with-friends)
  - [Testing with friends (Windows)](#testing-with-friends-windows)
  - [Testing with friends (macOS / Linux, or from a checkout)](#testing-with-friends-macos--linux-or-from-a-checkout)
  - [What to look at](#what-to-look-at)
- [Limitations](#limitations)
- [Building and tests](#building-and-tests)

## Settings

**Build rules** is a text box. One rule per line, or separated by commas:

```
Defence: 1
Prayer: none
Strength: goal 99
Ranged: soft
```

| You type | Means |
| --- | --- |
| `Defence: 1` | hard cap: Defence must stay at level 1 |
| `Prayer: none` (or `no xp`) | no XP at all in Prayer |
| `Strength: goal 99` | a goal; informs, never warns |
| `Ranged: soft` | soft cap; informs, never warns |

A line that does not parse (a misspelt skill, a missing colon) is ignored
rather than guessed at.

**Warn on attack style** (on by default; does nothing until you enter rules):
a red chat line when the selected style trains a skill you capped. A hard cap
warns once you have reached it; "no XP" always warns.

**Group (RuneLite Party)**, all off by default:

| Setting | What it sends to your party |
| --- | --- |
| Share build with party | Your rules, and your levels in only the skills those rules name. Also shows the party panel. Off means this client sends nothing and ignores other members' builds. |
| Tell party when at risk | Adds a line like "Alex is at risk of Defence XP (Controlled attack style)". |
| Share gear list | The names of the items you are wearing and carrying, for "who has what". Nothing from your bank. |

Each member has to turn sharing on themselves; nobody's client can read
another's. Everything travels through RuneLite's own Party service, the same
one the built-in Party plugin uses. Build Guard makes no network connections of
its own.

If you turn sharing off while in a party, your client stops sending. Members
keep the last build they received from you until you leave the party.

## Testing with friends

Until Build Guard is on the Plugin Hub, every member runs a **developer
build**: a RuneLite window started from this source, with the plugin already
loaded. It uses your normal RuneLite settings folder (`.runelite` in your
user folder), so your other plugins and settings come along.

### Testing with friends (Windows)

You need nothing installed. You do not need admin rights, and nothing on your
system is changed: everything goes into `%LOCALAPPDATA%\BuildGuard`.

1. **Download `Run-BuildGuard.cmd`** from the release page the group was sent
   (or from `tools/windows/` in this repo).
2. **Double-click it.** A black window prints its progress:
   - `[1/3]` finds Java 11 or newer. If you have none, it downloads Eclipse
     Temurin Java 11 (about 200 MB) and checks its SHA-256 fingerprint
     against the one written in the launcher before unpacking it. If the
     fingerprint does not match, it stops.
   - `[2/3]` downloads the Build Guard source the release was made from.
   - `[3/3]` builds and starts RuneLite. The first run takes a few minutes
     while it downloads Gradle and RuneLite. Later runs reuse all of it and
     start much faster.

   If something fails, the window stays open with the reason in red. Take a
   screenshot of it.
3. **If Windows shows "Windows protected your PC"** (SmartScreen): the
   launcher is a plain text script, not a signed program, so Windows does not
   recognise it. Click **More info**, then **Run anyway**. You can open the
   `.cmd` in Notepad first to read exactly what it does. If you see "Windows
   cannot access the specified file", right-click the file, choose
   **Properties**, tick **Unblock**, click **OK**, and double-click again.
4. **Log in, the first time only.** Jagex Accounts cannot type a password
   into a developer client. Use RuneLite's Jagex account panel instead
   ([RuneLite wiki: Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)):
   - in the RuneLite window that opened, click the **Jagex account** icon on
     the right-hand sidebar;
   - choose to add a Jagex account and log in in the window that opens;
   - pick your character, then click **Play Now** on the login screen.

   On later runs, pick your character and click **Play Now**.

   If you have an old `.runelite\credentials.properties` file from a
   previous developer setup, the wiki recommends deleting it.
5. **Join the same RuneLite Party.** One person opens the built-in **Party**
   panel on the sidebar (if it is missing, turn on the **Party** plugin in
   RuneLite's plugin list) and clicks **Create party**, then shares the
   passphrase it shows. Everyone else clicks **Join party** and types that
   passphrase. Then everyone turns on **Build Guard → Share build with party**
   (and the other two switches if they want). The Build Guard party panel (an
   orange shield on the sidebar) appears once sharing is on.

To stop, close the RuneLite window.

### Testing with friends (macOS / Linux, or from a checkout)

Install a JDK between 11 and 21, then from the repo folder run:

```sh
./gradlew run
```

Log in and join a party exactly as in steps 4 and 5 above.

### What to look at

- With `Defence: 1` in your rules, switch your weapon to **Controlled** or
  **Defensive**: a red Build Guard chat line should appear, once. Switch back
  to Accurate or Aggressive: no message.
- In a party with sharing on, your row in the Build Guard panel should read
  like `Name: Defence capped at 1 (now 1)`, and so should each friend's.
- With **Tell party when at risk** on, switching to Controlled should add an
  "at risk" line in everyone's panel.
- With **Share gear list** on, picking up or equipping an item should show it
  under "Who has what" within a few seconds.
- Turn **Share build with party** off: your friends' panels should stop
  updating for you, and your own Build Guard panel disappears.

## Limitations

- **Quest and lamp warnings are not wired up yet.** The warning today is for
  attack styles only. It does not see quest rewards, lamps or other XP
  sources, and it does not stop XP you get anyway.
- **"Who has what" is a list, not a pure-safety check.** The plugin has no
  item-requirement data, so it cannot tell you whether an item needs more
  Defence than you have. It lists only what members chose to share.
- Party sharing needs everyone to run Build Guard with sharing on. A member
  without it simply does not appear in the panel.
- Quest data is bundled at build time, so it is as current as the release
  and no more.

## Building and tests

```sh
./gradlew build     # compiles and runs the tests
./gradlew run       # launches a developer client with the plugin side-loaded
```

The Windows launcher's checksum step has its own test, runnable with
PowerShell 7 on any OS:

```sh
pwsh -NoProfile -File tools/windows/tests/Checksum.Tests.ps1
```
