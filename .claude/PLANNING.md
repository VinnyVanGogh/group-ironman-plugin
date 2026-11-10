---
scope: committed
---

# Planning answers — GIM Utilities

Decisions that are not derivable from the code, with the date and how long each
is good for. One entry per question; a correction **replaces** its entry rather
than sitting beside it.

`scope: committed`: the origin is `VinnyVanGogh/group-ironman-plugin`, the
user's own account, and nothing here is business or on-call context.

## Why this is its own plugin — durable · 2026-11-04

The Board split the Group Ironman features out of Build Guard so a group can
use them without build and XP warnings. Build Guard keeps the build features;
this repo keeps only the group features. Both are meant to run side by side,
so this plugin's config group, party message type and saved-data folder must
never equal Build Guard's (`PluginIdentityTest` holds them to it).

## Build rule sharing (Build Guard's first GIM part) — durable · 2026-11-04

**Not carried over.** It sends build rules, levels in capped skills and
"at risk" lines, which are build features with no meaning here without the
rules editor. Its gear-name list is superseded by *Share inventory and
equipment*, which sends counts as well as names.

## `::find` default — durable · 2026-11-04

**Off by default**, unlike Build Guard where it was on: the brief asked that a
fresh install has every feature off. It sends nothing either way.

## Display name and config group — durable · 2026-11-04

**Board decided: "GIM Utilities"**, config group `gimutilities`, saved data in
that group's `groupCache` value (moved from `.runelite/gim-utilities/` on
2026-11-09: the Hub bot holds plugins doing file i/o). "Group Ironman" clashes with the Hub's
"Group Iron Man" (slug `group-ironman`) and 20+ `gim-*` plugins exist; no
`gim-util*` slug does. The Java package (`com.vinnyvangogh.groupironman`) and
the party message class (`VvgGroupIronmanMessage`) stay as they are: both are
internal and the message name is the wire contract.

## Repo visibility and test builds — volatile · 2026-11-04

`VinnyVanGogh/group-ironman-plugin` is public. A friend-test Windows launcher
(RUNELITE-73) downloads tag `gim-test-1` once this split merges.
