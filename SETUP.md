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

**Group banks, storage and stats**, all off by default. Each one works on its
own (you do not need *Share build with party* for them), and each only shows
you other members' data while you share the same thing yourself.

| Setting | What it sends to your party |
| --- | --- |
| Share bank | When you close your bank: each item and how many, no tabs or slots. Members see your bank as "last seen 2h ago" with its total value. Kept on disk, so it is still there after a restart or when you are offline. |
| Share group storage changes | When you close group storage: what you took and deposited, and what was left. Everyone's panel keeps a log of the last 200 changes. |
| Share stats | Every skill's real and boosted level (shown as `90 (+8)` or `70 (-3)`), HP, prayer, run energy, special attack, your world and where you are (resent every 8 tiles you walk, and once a minute so members see you as online). |
| Share inventory and equipment | What you are carrying and wearing, with counts, for the panel's "Items" list and the search. |
| Storage minimums | Not sent anywhere. One per line, `Shark: 100`. A desktop notification when a group storage close leaves an item below its minimum; once per item until it is restocked. Your own closes always count; other members' count when *Share group storage changes* is on. |
| Share rune pouch, quiver, seed vault | What is in your rune pouch and Dizana's quiver, and your seed vault as of the last time you opened it. |
| Share drops and collection log | Your NPC drops (items worth at least *Drops worth at least*, 10,000 gp by default; 0 sends everything) and each new collection log slot. Kept on disk for everyone, and resent for 7 days whenever someone joins the party. |
| Show members on world map | Not sent anywhere. Marks online members on the world map. Needs *Share stats* on, for you and for them. |

The panel's **Members** list shows each member as *online* (heard from in
the last 3 minutes), *last seen 2h ago*, or *not seen yet*, with their world
and place ("Lumbridge", "Vorkath", "Taverley Dungeon"; a region number where
the bundled table has no name).

**Since you last played** lists the group's drops and new collection log
slots after the last time you were logged in, newest first. Your own are left
out. On the first run it lists the latest ones instead. New collection log
slots are read from the game's chat line, so turn on the in-game setting
*Collection log - New addition notification* (Settings, Chat).

The **Who has it?** box at the top of the Build Guard panel searches members'
last banks, inventories, equipment and group storage by item name (two
letters or more).

The storage log only has visits by members running Build Guard with *Share
group storage changes* on. A change by anyone else is not attributed to
anybody; it just shows up in the next logged visit's "before".

Banks, the storage log, rune pouches, quivers, seed vaults, drops, last seen
times and when you last played are saved in
`.runelite/build-guard/group-cache.json`. Delete that file to forget them.

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
  updating for you, and your own Build Guard panel disappears (unless a group
  bank/storage/stats switch is on).

Group banks, storage and stats (two or more members with the same switch on):

1. **Bank.** Open and close your bank. Within a few seconds a friend's panel
   shows a "Banks (last seen)" line for you: kinds, item count, value, "last
   seen just now". Check the value is roughly what the bank's own value
   shows. Restart the friend's client: your line should still be there.
2. **Group storage.** Open group storage, take 3 of a stack and deposit
   something, close it. Everyone's "Group storage log" should read like
   `<your name> took 3 x Shark, deposited 100 x Coal (just now)`. Then open it and
   only move a stack to another slot: no new log line. Deposit noted items:
   the log should name the item, not "item 1234" or a second entry.
3. **Minimums.** Set `Shark: 1000` (more than storage has), open and close
   storage: one desktop notification. Close it again: no second one.
4. **Stats.** Drink a stat-boosting potion: your Attack/Strength in friends'
   panels reads like `90 (+8)` within a couple of seconds, and counts down as
   it wears off. Pray for a bit: Prayer reads `70 (-3)`. Run energy and
   special attack should follow too, and the world number should match.
5. **Search.** Type `shark` in "Who has it?": every member's bank, inventory,
   equipment and storage stack of sharks, with counts.
6. **Off means off.** Turn each switch off: your friends stop getting that
   kind of update from you (check their panels), and the matching section
   empties in yours.

Members, items, drops and the map (two or more members with the same
switches on):

7. **Online / last seen.** With *Share stats* on for both of you, your friend
   reads `online, world 302, Lumbridge` (their world and place) in your
   **Members** list. Walk into a dungeon or to a boss: the place updates
   within a few seconds. Have them log out: within 3 minutes they read
   `last seen just now`, then `last seen 5m ago`.
8. **Items.** With *Share inventory and equipment*, the **Items** list shows
   `<name> wearing: ...` and `<name> carrying: ...` and follows a gear swap
   within a few seconds.
9. **Rune pouch, quiver, seed vault.** With *Share rune pouch, quiver, seed
   vault*: cast a spell from the pouch and the `rune pouch:` line drops by the
   runes used. Equip Dizana's quiver with arrows in it: a `quiver:` line.
   Open and close the seed vault: `seed vault: N kinds, M seeds (last seen just
   now)`. Search a seed or rune name in "Who has it?": it is found there.
   Check the rune names are right (not "item 1234") and the quiver count
   matches the game.
10. **Drops.** With *Share drops and collection log* on, kill something that
    drops an item worth over 10,000 gp: your friend's **Since you last
    played** (or **Drops and collection log** on a first run) gains
    `<name>: <NPC> dropped ...`. Your own panel does not list your own drops.
    Set *Drops worth at least* to 0: every drop shows.
11. **Collection log.** With the in-game new-addition notification on, get a
    new collection log slot: a `new collection log slot, <item>` line.
12. **Since you last played.** Log out for a few minutes while your friend
    gets a drop, log back in: the drop is listed under **Since you last
    played** once your friend is online. Restart the client: the list is
    still there.
13. **World map.** Turn on *Show members on world map* (with *Share stats*):
    open the world map, an orange dot with your friend's name sits where they
    are, and disappears a few minutes after they log out.

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
- **Banks are as of the last close.** Items moved since then, or a bank never
  opened with *Share bank* on, are not known.
- **The storage log only sees members running Build Guard** with storage
  sharing on. It is a record of what each client saw, not the game's own log.
- **Place names come from a bundled table** (about 970 map regions: cities,
  bosses, raids, minigames, dungeons, areas). A region not in it shows as a
  number rather than a guess.
- **There is no server.** "Since you last played" only has what a member's
  client resent while you were both online; a member who never comes online
  again after you do cannot send you their drops. Drops are NPC drops only
  (not chests, raids rewards or clue caskets), and collection log slots need
  the in-game chat notification.
- **The seed vault is as of the last open.** The rune pouch and quiver are
  live.
- **The world map shows members, not a map in the panel**: the client has no
  map picture a plugin could draw without downloading it.
- **Bank values use RuneLite's cached Grand Exchange prices**, which are what
  the bank's own value display uses; untradeables count as 0.

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
