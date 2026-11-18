# RuneLite in_review sweep (RUNELITE-241, 2026-11-18)

Task: task-a42da2fb. 35 in_review tasks reviewed (spec said 33). Evidence came from PR lists (`pr_list state=all`) on group-ironman-plugin, questpath-plugin, bank-views-plugin, build-guard-plugin, combat-vitals-plugin and runelite-plugin-dev, plus task threads. Nothing was merged by this run: gradle tests could not be run and pr_merge into main is held for the Board.

Counts: merged 0 / mark_done 11 / sent back 0 / cancelled 4 / left for the Board 20.

## Board plan batch 1 (mark_done / cancel)

mark_done (already landed):
- task-775b36c7: group-ironman-plugin #27 merged
- task-13b5a72b: #22 closed, landed in #27
- task-54e5a9d7: #21 closed, landed in #27
- task-cd86d54e: #18 closed, landed in #27
- task-913c851d: landed via #42
- task-133e2ec6, task-f173ec31, task-ba5d7ad1: #13/#14/#12 closed, landed via #16
- task-587d166c: #3 merged
- task-f9ca0362: questpath-plugin #4 merged
- task-9d195d46: runelite-plugin-dev #1 merged

cancel (evidence linked):
- task-1437b21b: superseded by group-ironman-plugin #26 (Filepath API); close open PR #9
- task-8a546fae: Quest Helper bridge removed by questpath-plugin #14
- task-82c019a7: combat-vitals-plugin #15 closed, docs rewritten by #35
- task-b3cbf423: build-guard-plugin #1 closed, GIM features removed by #6

## Left for the Board

Docs-only, skim then merge:
- task-1d43c2aa: bank-views-plugin #7
- task-dec053dc: questpath-plugin #16

High tier, check in a dev client:
- task-0714f81c: runelite-plugin-dev #3 (scenarios sidebar, Paste JSON)
- task-4c7f42be: watchlist, branch `staypoint/task-4c7f42be` (7240213), no PR, main has moved, expect conflicts
- task-edaf96bf: questpath #2 (depends on #1, DB migration)

Branches pushed with no PR (open PR, run `./gradlew test`, merge): task-a1a7115b, task-0650d30c, task-71b6b6d5, task-9136a2fe, task-fe6410bd, task-4f289d28 (may be obsolete after combat-vitals #48/#49), task-e33b3ce5 (shellcheck and live run never done).

Not reviewed, need a human or follow-up run: task-94d83128, task-f8a1988f, task-fcbd9777, task-ee410ea7, task-21ffd3ac, task-aecddc20, task-3025c29f, task-f7d5d637.

Open PRs outside this queue: GIM #81, #85, #86; questpath #17-#21; combat-vitals #50 (sound, high tier).
