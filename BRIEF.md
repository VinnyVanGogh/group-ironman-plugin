# Build Guard — research & build brief

The authoritative record of what was decided and why. Findings go **in this
file**, under the phase that produced them, so nothing has to be re-derived
later. If a claim here is a measurement, say what was measured and how.

## What this plugin is

<!-- One paragraph, the player-facing goal. -->

## What this plugin is not

<!-- The features you considered and deliberately cut. Name them, because
     "we never thought of it" and "we checked and it is against the rules"
     look identical six months later, and only one of them is safe to
     revisit. -->

---

## Phase 0 — compliance

Read, in order:

1. `AGENTS.md` in this repo (the forbidden-features list at the bottom).
2. <https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features>
3. <https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1>

Then, for each feature in scope, write down: which rule it lands near, and the
precedent in the base client or an accepted hub plugin that shows it is
allowed. A feature with no precedent and no clean rule is not a feature yet.

### Phase 0 findings (completed YYYY-MM-DD)

<!-- Verdict per feature: in scope with precedent / out of scope, and why. -->

### Out of scope — do not implement, do not propose workarounds

<!-- List them. Each with the rule it breaks. -->

---

## Phase 0.5 — measure before building

If the plugin makes a claim about a number the client does not hand you
directly, measure it before designing around it. An estimate written into a
README becomes a promise you cannot keep.

### Phase 0.5 findings (completed YYYY-MM-DD)

<!-- Method, sample size, result, and the error bars. Raw captures stay in
     docs/measurements/ which is gitignored; conclusions live here. -->

---

## Phase 1 — reference reading

Which base-client plugin or hub plugin already does the closest thing, and
what does its source do? Copying the shape of something already accepted is
the cheapest compliance argument there is.

### Phase 1 findings (completed YYYY-MM-DD)

---

## Phase 2 — scope

What ships in v1, what is cut, and what the config surface is.

---

## Phase 3 — quality bar

- Pure logic separated from client access, and unit tested.
- `startUp` / `shutDown` symmetric: everything registered is unregistered.
- Nothing computed in an overlay that could be computed once per tick.
- No unused config keys, fields or imports.

---

## Phase 4 — getting it installed

See the `runelite-hub-submit` skill. Manifest, checklist, PR, stale bot.
