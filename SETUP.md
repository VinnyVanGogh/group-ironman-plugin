# GIM Utilities: setup and testing

Everything needed to install, configure and test GIM Utilities. For what the
plugin is, see the [README](README.md).

- [Settings](#settings)
- [Running it next to Build Guard](#running-it-next-to-build-guard)
- [Testing with friends](#testing-with-friends)
  - [What to look at](#what-to-look-at)
- [Limitations](#limitations)
- [Building and tests](#building-and-tests)

## Settings

**Group banks, storage and stats**, all off by default, including `::find`.
Each one works on its own, and each only shows you other members' data while
you share the same thing yourself. With every switch off the plugin sends
nothing, collects nothing, and its side panel button is hidden.

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
| ::find chat command | Not sent anywhere. Lets you type `::find dragon hunter wand` in the chatbox (see below). |

Each member has to turn sharing on themselves; nobody's client can read
another's. Everything travels through RuneLite's own Party service, the same
one the built-in Party plugin uses. GIM Utilities makes no network connections
of its own.

The panel's **Members** list shows each member as *online* (heard from in
the last 3 minutes), *last seen 2h ago*, or *not seen yet*, with their world
and place ("Lumbridge", "Vorkath", "Taverley Dungeon"; a region number where
the bundled table has no name).

**Since you last played** lists the group's drops and new collection log
slots after the last time you were logged in, newest first. Your own are left
out. On the first run it lists the latest ones instead. New collection log
slots are read from the game's chat line, so turn on the in-game setting
*Collection log - New addition notification* (Settings, Chat).

The **Who has it?** box at the top of the GIM Utilities panel searches
members' last banks, inventories, equipment and group storage by item name
(two letters or more). Under the holders it lists the newest three storage log
visits that took or deposited a matching item, e.g. `Alex took 1 x Dragon
hunter wand from storage (3d ago)`.

**`::find` in the chatbox** (turn on *::find chat command* first) asks the
same question without opening the panel:

```
::find dragon hunter wand
```

answers, in your chatbox only:

```
GIM Utilities: Dragon hunter wand: Sam bank 1 (2h ago)
GIM Utilities: Alex took 1 x Dragon hunter wand from storage (3d ago)
```

At most five lines; anything past that ends with `N more, see the GIM
Utilities panel`. It matches any part of the name, ignoring case, like the
panel.

It is `::find`, not `!find`, on purpose. A `!` command is ordinary chat: the
game sends the text to public (or clan) chat first, and RuneLite only acts on
it once it comes back, so everyone around you would see what you searched for.
RuneLite catches `::` text in the chatbox before it is sent and hands it to
plugins instead (`ChatInputManager`, which posts a `CommandExecuted` event), so
nothing leaves your client.

What it can and cannot see:

- **Only members running GIM Utilities** with the matching sharing switch on.
- **Only what each client saw, as of its last sync**: a bank as of its last
  close, storage as of the last logged visit, inventories while online.
- **Storage history only covers members with *Share group storage changes*
  on.** A visit by anyone else is not attributed to anybody; it just shows up
  in the next logged visit's "before".
- **Trades and drops between members are not tracked.** If Alex hands Sam the
  wand, `::find` still shows Alex's last bank until Sam's next sync.

Banks, the storage log, rune pouches, quivers, seed vaults, drops, last seen
times and when you last played are saved in
`.runelite/gim-utilities/group-cache.json`. It is one file per computer, not
per group: if you switch groups, delete it to forget the old group's data.

## Running it next to Build Guard

GIM Utilities was split out of Build Guard. Both can be installed at once and
never touch each other:

- **Settings.** GIM Utilities' config group is `gimutilities`; Build Guard's
  is `build-guard`. Turning a switch on in one does not turn it on in the
  other, and nothing is copied across: set GIM Utilities up from scratch.
- **Party messages.** GIM Utilities sends `VvgGroupIronmanMessage`; Build
  Guard sends `BuildGuardGroupMessage`. RuneLite's party relay routes by that
  name, so neither plugin receives or decodes the other's traffic. A member on
  Build Guard's group features and a member on GIM Utilities do not see each
  other: the whole group should use the same one.
- **Saved data.** `.runelite/gim-utilities/` here, `.runelite/build-guard/`
  there. Build Guard's saved banks and storage log are not imported.

## Testing with friends

Until GIM Utilities is on the Plugin Hub, every member runs a **developer
build**: a RuneLite window started from this source, with the plugin already
loaded. It uses your normal RuneLite settings folder (`.runelite` in your
user folder), so your other plugins and settings come along.

1. Install a JDK between 11 and 21, then from the repo folder run
   `./gradlew run`.
2. **Log in.** Jagex Accounts cannot type a password into a developer
   client. Use RuneLite's Jagex account panel instead
   ([RuneLite wiki: Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)):
   click the **Jagex account** icon on the sidebar, add your account, pick
   your character, then click **Play Now**.
3. **Join the same RuneLite Party.** One person opens the built-in **Party**
   panel (turn on the **Party** plugin if it is missing), clicks **Create
   party**, and shares the passphrase. Everyone else clicks **Join party**.
4. **Turn sharing on.** Everyone opens *GIM Utilities* in the plugin settings
   and turns on the switches they want. The GIM Utilities panel (a green badge
   on the sidebar) appears once any switch is on.

There is no Windows double-click launcher in this repo yet; Build Guard's
launcher builds Build Guard, not this.

### What to look at

Banks, storage and stats (two or more members with the same switch on):

1. **Fresh install.** Before turning anything on: no GIM Utilities panel
   button, and `::find shark` gets no `GIM Utilities:` answer.
2. **Bank.** Open and close your bank. Within a few seconds a friend's panel
   shows a "Banks (last seen)" line for you: kinds, item count, value, "last
   seen just now". Restart the friend's client: your line should still be
   there.
3. **Group storage.** Open group storage, take 3 of a stack and deposit
   something, close it. Everyone's "Group storage log" should read like
   `<your name> took 3 x Shark, deposited 100 x Coal (just now)`. Moving a
   stack to another slot adds no line.
4. **Minimums.** Set `Shark: 1000` (more than storage has), open and close
   storage: one desktop notification. Close it again: no second one.
5. **Stats.** Drink a stat-boosting potion: your Attack/Strength in friends'
   panels reads like `90 (+8)` within a couple of seconds, and counts down as
   it wears off.
6. **Search.** Type `shark` in "Who has it?": every member's sharks, with
   counts, then who last took or deposited sharks. Turn on *::find chat
   command* and type `::find shark`: the same answer as `GIM Utilities:`
   lines, and nothing appears in public chat.
7. **Off means off.** Turn each switch off: your friends stop getting that
   kind of update from you, and the matching section empties in yours.

Members, items, drops and the map:

8. **Online / last seen.** With *Share stats* on for both of you, your friend
   reads `online, world 302, Lumbridge` in your **Members** list. Have them
   log out: within 3 minutes they read `last seen just now`.
9. **Items.** With *Share inventory and equipment*, the **Items** list follows
   a gear swap within a few seconds.
10. **Rune pouch, quiver, seed vault.** Cast a spell from the pouch: the
    `rune pouch:` line drops by the runes used. Open and close the seed vault:
    a `seed vault:` line.
11. **Drops and collection log.** Kill something that drops an item worth over
    10,000 gp: your friend's **Since you last played** gains it.
12. **World map.** Turn on *Show members on world map* (with *Share stats*):
    an orange dot with your friend's name sits where they are.
13. **Next to Build Guard.** With both plugins on and both sets of switches
    on, each panel shows only its own plugin's members, and the chat shows no
    errors.

## Limitations

- **Banks are as of the last close.** Items moved since then, or a bank never
  opened with *Share bank* on, are not known.
- **The storage log only sees members running GIM Utilities** with storage
  sharing on. It is a record of what each client saw, not the game's own log.
- **`::find` and "Who has it?" do not follow trades or drops between members.**
- **Place names come from a bundled table** (about 970 map regions). A region
  not in it shows as a number rather than a guess.
- **There is no server.** "Since you last played" only has what a member's
  client resent while you were both online. Drops are NPC drops only (not
  chests, raids rewards or clue caskets), and collection log slots need the
  in-game chat notification.
- **The seed vault is as of the last open.** The rune pouch and quiver are
  live.
- **Bank values use RuneLite's cached Grand Exchange prices**; untradeables
  count as 0.

## Building and tests

```sh
./gradlew build     # compiles and runs the tests
./gradlew run       # launches a developer client with the plugin side-loaded
```
