# docs

`images/` holds the screenshots used by the README. Seven of them
(settings-tab-leader, settings-tab-member, settings-who-posts,
panel-group-hiscores, storage-log, settings-discord-connection,
settings-thresholds-advanced) are rendered by `./gradlew screenshots` from
made-up scenes; see [testing-ui.md](testing-ui.md). Regenerate those rather
than replacing them by hand.

**Redact other people's account names before committing a screenshot.** Player
names are visible in chat, on the minimap, over heads and in the opponent
panel. This has caught a real capture before.

`measurements/` is gitignored. Raw captures from a live client name real
accounts; keep the raw data local and write the conclusions into `BRIEF.md`.
