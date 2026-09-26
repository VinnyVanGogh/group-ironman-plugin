---
scope: committed
---

# Planning answers — Build Guard

Decisions that are not derivable from the code, with the date and how long each
is good for. One entry per question; a correction **replaces** its entry rather
than sitting beside it.

`scope: committed` was derived, not asked: `git remote get-url origin` returns
nothing, so this repo has no third-party owner whose shared history these notes
could leak into. Nothing here is business or on-call context — it is all
architecture.

## Package layout for the ported engine — durable · 2026-09-17

**Flat `com.vinnyvangogh.buildguard` in Milestone 1; extract the seam in
Milestone 2.**

The ported engine keeps upstream's package-private visibility, so the port is a
near-literal diff and a failing vector can only mean a porting bug. Splitting
into the scaffold's `rules/` `verdict/` `warning/` `lamp/` packages would force
`public` on roughly 150 members up front, and a red vector would then be
ambiguous between a porting bug and a visibility mistake — the one trade worth
refusing while 227 vectors are the sole acceptance gate.

The split still happens, in M2, as a mechanical refactor with green vectors
underneath it and the facade signatures known from the UI that actually calls
them, rather than guessed now. **The four scaffold subpackages are reserved for
that; they are not abandoned.**

## How much of questpath's `QuestGraph` is ported — durable · 2026-09-17

**Trimmed to what the classifier reads.** `Quest`, `Requirement`, `Reward`,
`Boost`, the two prerequisite lists, `get`/`byName`, the version gate and
`load`/`parse`. Dropped: `Shop`, `Item`, `Alternative`, `Reachability`,
`SkillGate`, `DiaryTask`, `Location`, `DropLocation`, `itemNamed`, `shop`,
`dropLocations`, and `Quest`'s `members`/`start_x`/`start_y`/`items` — roughly
700 of 1171 lines, every one serving shop run, diaries or routing, all out of
scope.

The fat 1.38 MB bundle still ships **unchanged**; Gson ignores the keys the
model no longer names. The version gate stays at 12 and stays load-bearing: a
trimmed model depends on fewer fields, it is not a model that may be fed a
document of unknown shape.

The `diary_tasks`-presence check is **not** kept — it guarded diary routing,
which this plugin has none of.

## Fate of the four throwing stubs in Milestone 1 — durable · 2026-09-17

**Untouched, and `SkeletonStubsTest` stays fully green.** `BuildRules`,
`QuestVerdictEngine`, `QuestStartWarning` and `LampAdvice` are UI-facing
facades; M1 is explicitly engine-only. `QuestStartWarning` has no engine behind
it at all — nothing yet decides when it fires — so wiring it would mean
inventing a firing rule, which the no-fabrication rule forbids.

Each assertion is deleted as its facade becomes real, as that test's own javadoc
says. Ported engine classes live beside the stubs, not behind them.

## Which open decisions Milestone 1 closed — durable · 2026-09-17

**None of them.** Everything under **Open decisions** in `docs/SPEC.md` is
untouched by the three answers above:
how build rules are entered, how quest completion is read, what a verdict may
say with no rules, the config surface beyond the frozen `build-guard` group, and
the Hub question about two plugins sharing one engine. M1 needed none of them
answered, which is why none were.
