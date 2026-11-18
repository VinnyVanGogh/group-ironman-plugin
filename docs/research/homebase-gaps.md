# Group Ironman Homebase: features we lack (2026-11-17)

Source: the Homebase README at commit `63771f4` (bonscot1/Homebase), read
2026-11-17. Listed, not built. Verdicts are one line each. Our own versions
only; no code or text is taken from that plugin.

Built in task-c3ec10ad: shared boss bests, shared quests, quest points and
diaries, Compare (players, leaderboard, group vs group, who benefits most),
Personal bests under Activities.

| Their feature | Verdict |
| --- | --- |
| Combat achievement points and tier on member cards | Worth building: one varp and one varbit read, same sharing switch as quests. |
| Collection log slot counts and pages per member | Worth building as a count only; we already post new slots as events. Per-page sharing is heavy for little. |
| Boss kill counts on member cards | Worth building: KC lines already parse from chat for milestones; a bounded map like bests. |
| House view (furniture, costume room) | Skip: low value to a group, large varbit surface. |
| Goals tab (personal and group targets as bars) | Worth a design pass: overlaps Shopping lists and quest needs; decide the shape before building. |
| Bounties locking group storage until a goal is met | Skip: the plugin cannot lock the storage, so it would only be a promise. |
| Owed-back list for items to return | Worth building as a Requests extension; no new data needed. |
| Group HUD customisation (size, order, compact, world filter) | Partly have the group box overlay; add a compact mode if asked. |
| Map arrows, edge markers, pins, pings with sound | Pins and pings: skip for now, they need input handling on the world map. Arrows and markers: we already mark members on the map. |
| Monster highlights and group tiles | Skip: shared tile and NPC highlighting is a new overlay surface for little group value. |
| Collection log dots in each member's colour on boss pages | Skip: needs widget drawing over the collection log. |
| Per-member colour | Cheap and useful with markers; build alongside any marker work. |
| "Group Ironman secret" word to verify members | We already sign group data with the group key; no gap. |
| Stop-sharing options per data kind | Have: per-kind Share switches. |
| Chat messages for level-ups, requests, goals, pins | Have for events and requests; goals and pins follow their features. |
| Background catch-up for missed data | Have: the log resend and "since you last played". |
| Group rank on the Group Ironman hiscores, "what it would take to pass" | Blocked: RuneLite's `HiscoreEndpoint` has no group ranking, so only a typed-in group comparison is possible. |
