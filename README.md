# Build Guard

Warns before a quest, lamp or activity gives XP that breaks a capped account's build

> **Status: scaffold.** Nothing is implemented yet. Every piece is a stub that
> throws, so installing this plugin today does nothing at all. The scope lives
> in [`docs/SPEC.md`](docs/SPEC.md) and the engine it will be carved out of is
> mapped in [`docs/SOURCE-MAP.md`](docs/SOURCE-MAP.md).

## What it does

*(Planned.)* If your account is built around a skill you must never train —
a pure, a zerker, a skiller, anything capped on purpose — this tells you
before the experience lands, not after:

- **Your rules, per skill:** no XP, a hard cap, a soft cap or a goal, with the
  experience still left under each one.
- **A verdict per quest, with the reason** — "blocked via Death Plateau:
  3,000 Attack XP" — judged across the whole prerequisite chain.
- **A warning before you start** a quest that would break the build, or one
  that is permanently out of reach because a prerequisite would.
- **Lamp advice:** on the experience lamp's skill-choice interface, the skills
  your rules close are greyed and the ones worth choosing are boxed.

**Build restrictions are about XP, not levels.** A 1-Defence account is blocked
by any quest that *awards* Defence experience, whatever its level requirements
say.

## Settings

Not settled. The scaffold carries the template's single `Enabled` key and
nothing else; see the open decisions in `docs/SPEC.md`.

## Limitations

- **Nothing works yet.** See the status note above.
- Quest data will be bundled at build time, so it is as current as the release
  and no more. It does not update when the wiki does.
- It warns. It never blocks a click, removes a menu option or does anything on
  your behalf.

## Building

```sh
./gradlew build     # compiles and runs the tests
./gradlew run       # launches a developer client with the plugin side-loaded
```

Logging in to the developer client uses a Jagex account:
<https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>

## License

BSD-2-Clause. See [LICENSE](LICENSE).
