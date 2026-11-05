# Changelog

## Unreleased

### Round 2 (Board in-game test, 2026-11-04)

- **No Party panel step.** GIM Utilities joins the group's RuneLite party
  itself through the core `PartyService` when you click **Join group sync**;
  the Party plugin can stay off (and its overlays hidden). It asks for member
  names itself (`PartyDataService`, `CHARACTER_NAME`), which nothing did with
  the Party plugin off. Joining waits for the click: auto-joining parties is
  a rejected Plugin Hub feature.
- **Group key.** 128 random bits, never derived from public facts. *Copy
  share line* puts `[GIMU] <key>` on the clipboard for you to send in group
  chat; members' plugins read it from group chat only, save it and hide it.
  *Group key* in the settings (a password field) is the paste fallback; *New
  key* rotates it. New settings: *Sync with my group* (on), *Group key*.
- A party you joined yourself is never replaced (*In another party; GIM sync
  paused*, with a *Switch* button), and on shutdown only a party GIM
  Utilities joined is left. Nothing is sent to any other party.
- Only names on the in-game group roster (`ClanSettings` for
  `ClanID.GROUP_IRONMAN`) are shown; anyone else is ignored and listed as
  *Not in your group*.
- Offline members keep their last world, place, stats and items, saved across
  restarts: *Offline - last seen W303, Draynor, 2h ago*.
- Group storage: the last contents are shown as a snapshot with when and by
  whom, even when nothing moved; changes are logged on each Save and on close
  ("You deposited 5 Trout"), whether or not sync is on.
- One search engine for the Find tab and `::find`: any word or part of the
  name, any case, with shorthands (`ppot`, `dwh`, `brew`, ...). `::find guam`
  now finds Grimy guam leaf, Guam leaf and Guam potion (unf).
- Side panel redesign: a sync header and five tabs (Group, Banks, Storage,
  Find, Log). Member cards with HP / Prayer / Run / Spec bars, a skills icon
  grid in the skills tab's order and item icon grids; banks and storage as
  filterable icon grids with value; Find as icon rows. Every empty state says
  what to do next.

Split out of Build Guard (VinnyVanGogh/build-guard-plugin, up to its PR #5)
as a plugin of its own, so a group can use the group features without Build
Guard's build and XP warnings.

- New identity: plugin name *GIM Utilities*, package
  `com.vinnyvangogh.groupironman`, config group `gimutilities`, party message
  `VvgGroupIronmanMessage`, saved data in `.runelite/gim-utilities/`. None of
  these overlap Build Guard's, so both plugins run side by side without
  reading each other's settings or decoding each other's messages. Settings
  and saved data are not carried over from Build Guard.
- Not named "Group Ironman" and not config group `groupironman`: the Hub
  plugin *Group Iron Man* (slug `group-ironman`) already has that name and
  uses that group.
- Kept from Build Guard: member banks, the group storage log, live stats with
  boosts, online / last seen and place names, inventories and equipment, rune
  pouch, quiver and seed vault, drops and collection log ("Since you last
  played"), "Who has it?", `::find`, storage minimums and world map markers.
- Removed, staying in Build Guard: build rules, the attack-style warning,
  sharing build rules / at-risk lines / the gear-name list with the party,
  and the quest and lamp engine with its bundled quest and lamp data.
- `::find chat command` is now **off by default**, like every other switch:
  a fresh install does nothing until it is turned on.
- Windows double-click launcher `tools/windows/Run-GimUtilities.cmd`, ported
  from Build Guard's: finds or installs a SHA-256-pinned Java 11, downloads
  this repo's `gim-test-1` tag (or uses the checkout it sits in), and runs
  the client. Keeps everything in `%LOCALAPPDATA%\GimUtilities`.
- Built against RuneLite 1.13.5 (was 1.12.39, inherited from Build Guard).
  On 1.12.39 `./gradlew run` exited with code 255 about 12 seconds in.
