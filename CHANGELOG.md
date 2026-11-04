# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- `::find <item>` local chatbox command (`groupFindCommand`, on by default) showing current holders and the newest three group storage visits that took or deposited a matching item, capped at five lines and never sent to public chat (#5).
- Group storage move history (newest three matching visits) under holders in the Build Guard panel's "Who has it?" search, plus a `GroupCache` restart round-trip test for the 200-entry storage log (#5).
- Group Ironman part 3 over RuneLite Party (all opt-in, off by default): per-member worn/carried item lists, rune pouch / Dizana's quiver / seed vault sharing (`groupShareExtras`), NPC drops and collection log "Since you last played" feed resent for 7 days on party join (`groupShareDrops`, `groupDropMinValue`), member online / last-seen presence with 1-minute stats heartbeat and bundled region place names (`regions.txt`), and optional world map markers (`groupWorldMap`) (#4).
- Group Ironman part 2 over RuneLite Party (all opt-in, off by default): chunked member bank snapshots on bank close cached to `.runelite/build-guard/group-cache.json` (`groupShareBank`), rolling 200-entry group storage change log (`groupShareStorage`), live real/boosted skill levels and status (`groupShareStats`), "Who has it?" cross-source item search (`groupShareCarried`), and low-stock desktop notifications (`groupLowStock`) (#3).
- Opt-in Group Ironman party build-rule sharing (`partyShareBuild`), local and party attack-style XP warnings (`warnAttackStyle`, `partyShareRisk`), shared gear list (`partyShareGear`), `SETUP.md` testing guide, and double-click Windows test launcher (`tools/windows/Run-BuildGuard.cmd`) with pinned Temurin 11 SHA-256 verification (#2).
- Pure build-rule engine (`BuildRule`, `BuildRuleCodec`, `CombatMath`, `XpTable`, `SkillXp`), quest prerequisite/XP classifier (`QuestGraph`, `QuestClassifier` with 218 golden vectors), and experience lamp advisor (`LampAdvisor`, `LampCatalogue`, `LampVerdict` with 9 golden vectors) ported from `questpath-plugin` (`a805a68`).

### Changed

- Shortened `README.md` to a feature overview and quick start, moving full configuration, friend-testing steps, and limitations to `SETUP.md` (#2).

### Fixed

- Guarded `GroupService.saveCache()` with `cacheLoaded` so early saves before the initial disk read completes cannot overwrite `.runelite/build-guard/group-cache.json` with an empty or partial roster (#4).
- Preserved previously cached member seed vault snapshots in `GroupRoster.putExtras` when a restarted client sends rune pouch or quiver updates before reopening its seed vault (#4).
- Cleared `PartyRoster` whenever *Share build with party* is turned off, even if the sidebar panel remains open for other Group Ironman switches (#3).
- Documented the private-repository 404 limitation and local-checkout fallback for `Run-BuildGuard.cmd` in `docs/release-gim-test-1.md` (#2).
