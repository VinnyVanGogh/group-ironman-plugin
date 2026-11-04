# GIM Utilities

Share banks, group storage changes, stats and drops with your Group Ironman
team over RuneLite Party.

> **Status: early test build.** Not on the Plugin Hub yet. Split out of Build
> Guard, which keeps its build and XP warnings; the two can run side by side.

## Features

Everything is opt-in and off by default. Each member turns on what they want
to share, and only sees other members' data for what they share themselves.

- each member's last bank ("last seen 2h ago", with value), kept across
  restarts;
- a group storage log: who took or deposited what, last 200 changes;
- live group stats with boosts and drains (`90 (+8)`), HP, prayer, run,
  special attack and world;
- who is online, or when they were last seen, and where (world and place
  name), optionally as markers on the world map;
- each member's inventory, equipment, rune pouch, quiver and seed vault;
- "Since you last played": members' drops and new collection log slots;
- a "Who has it?" search across all of the above, and the `::find <item>`
  chat command that answers in your own chatbox only;
- storage minimums with a desktop notification when stock runs low.

No server and no network calls of its own: party data goes only through
RuneLite's Party service.

## Quick start

Run `./gradlew run`, log in with RuneLite's Jagex account panel, join the same
RuneLite Party as your group, and turn on the switches you want under
*GIM Utilities* in the plugin settings.

**Full steps, settings and what to test: [SETUP.md](SETUP.md).**

## License

BSD-2-Clause. See [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
