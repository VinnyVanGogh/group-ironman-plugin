# Changelog

## Unreleased

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
- The Windows launcher is not carried over; it builds Build Guard.
- Built against RuneLite 1.13.5 (was 1.12.39, inherited from Build Guard).
  On 1.12.39 `./gradlew run` exited with code 255 about 12 seconds in.
