# Build Guard

Warns before a quest, lamp or activity gives XP that breaks a capped account's build

> **Status: early test build.** Not on the Plugin Hub yet. Quest verdicts,
> the quest-start warning and lamp advice are planned, not wired up.

## Features

- **Your build rules, per skill:** no XP, a hard cap, a soft cap or a goal.
- **Attack-style warning:** a red chat line when your selected style trains a
  skill you capped (Controlled or Defensive on a 1 Defence pure, for example).
- **Group features over RuneLite Party**, opt-in and off by default, for a
  Group Ironman team with pures:
  - every member's caps and current levels in one side panel;
  - an "at risk" line when a member's style is about to give them capped XP;
  - a "who has what" list of the items members choose to share;
  - each member's last bank ("last seen 2h ago", with value), kept across
    restarts;
  - a group storage log: who took or deposited what, last 200 changes;
  - live group stats with boosts and drains (`90 (+8)`), HP, prayer, run,
    special attack and world;
  - a "Who has it?" search across banks, inventories, equipment, rune
    pouches, quivers, seed vaults and storage;
  - storage minimums with a desktop notification when stock runs low;
  - who is online, or when they were last seen, and where (world and place
    name), optionally as markers on the world map;
  - each member's inventory, equipment, rune pouch, quiver and seed vault;
  - "Since you last played": members' drops and new collection log slots.

Build Guard only warns. It never blocks a click or acts for you, and it makes
no network connections of its own; party data goes through RuneLite's Party
service.

## Quick start

On Windows, double-click `tools/windows/Run-BuildGuard.cmd` (or the copy on
the release page). It fetches Java if needed and starts RuneLite with Build
Guard loaded. Elsewhere, run `./gradlew run`. Log in with RuneLite's Jagex
account panel, type your rules into Build Guard's settings (e.g.
`Defence: 1, Prayer: none`), and to use the group features join the same
RuneLite Party as your friends and turn on *Share build with party*.

**Full steps, settings and what to test: [SETUP.md](SETUP.md).**

## License

BSD-2-Clause. See [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
