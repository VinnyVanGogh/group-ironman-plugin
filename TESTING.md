# Testing with fake game events

Scenarios let you see what the plugin does when game things happen (a storage
visit, a level-up, a member logging in) in seconds, without playing. A
scenario is a JSON file of made-up RuneLite events. It runs two ways:

- **Headless, in JUnit** (`./gradlew test`): each step is posted on a real
  RuneLite `EventBus` to a rig built from the plugin's own classes, and the
  scenario's `expect` entries check what came out (Discord posts, panel text).
- **Live, in the developer client** (`./gradlew run`): the "Scenarios (dev)"
  sidebar posts the same events into the client's EventBus, on the client
  thread, and the real plugin reacts.

No game input is ever sent and nothing talks to Jagex: an event is a Java
object handed to subscribers. Everything here is under `src/test`. The Plugin
Hub swaps in its own `build.gradle` and compiles `src/main` only, so none of it
can reach the Hub jar.

**Live outputs are real.** A webhook you have set gets real posts, and every
plugin in the client sees the events. Point webhooks at a test channel first.

## Files

| Where | What |
| --- | --- |
| `src/test/java/com/vinnyvangogh/scenarios/` | The harness. The same in every plugin; copied in by `scripts/add-test-harness.sh`. Change it in GIM Utilities and re-copy. |
| `src/test/resources/scenarios/NN-words.json` | This plugin's scenarios. |
| `src/test/java/<plugin package>/*EventKinds.java` | This plugin's constants and event kinds. |
| `src/test/java/<plugin package>/*ScenarioRig.java` | The headless stand-in for the plugin's handlers (GIM: `GimScenarioRig`). |
| `src/test/java/<plugin package>/*ScenariosTest.java` | Runs every scenario file headless. |

## A scenario

```json
{
  "name": "Group storage visit",
  "description": "What it shows, and anything that differs live.",
  "steps": [
    {"kind": "widgetLoaded", "group": "SHARED_BANK", "note": "open group storage"},
    {"kind": "container", "id": "INV_GROUP_TEMP", "items": {"SUPER_RESTORE_4": 20}, "wait": 600},
    {"kind": "widgetClosed", "group": "SHARED_BANK", "wait": 1200}
  ],
  "expect": [
    {"probe": "storage.log", "contains": "You took 14 Super restore(4)"},
    {"probe": "discord", "count": 0}
  ]
}
```

- `kind` picks the event; the other keys are its fields. `wait` is
  milliseconds before the step (live: real time; headless: the rig's clock).
  `note` is shown in the sidebar.
- Wherever a field takes a number, a constant name registered by the plugin
  works too (`"SHARED_BANK"`, `"COINS"`).
- `expect` entries name a probe (a list of text lines the rig exposes) and one
  of `contains`, `absent` or `count`. They run headless only.
- The file is checked when it is read: an unknown kind, a missing or mistyped
  field, or an unknown constant fails with the file, step number and the list
  of what is known.

## Core event kinds

Built from RuneLite's own value classes (`@Value` / `@Data` in runelite-api
and runelite-client), so they need no game behind them.

| Kind | Event | Fields |
| --- | --- | --- |
| `chat` | `ChatMessage` | `type` (ChatMessageType), `message`, `name`, `sender`, `timestamp` |
| `stat` | `StatChanged` | `skill` (Skill), `level`, `xp`, `boosted` (defaults to level) |
| `container` | `ItemContainerChanged` + `FakeItemContainer` | `id`, `items` `{item: qty}` |
| `widgetLoaded` / `widgetClosed` | `WidgetLoaded` / `WidgetClosed` | `group`; closed also `modalMode`, `unload` |
| `varbit` / `varp` | `VarbitChanged` | `varbit` or `varp`, `value` |
| `userPart` | party `UserPart` | `memberId` |
| `tick` | `GameTick` | headless only |
| `gameState` | `GameStateChanged` | `state` (GameState); headless only |

### What can and cannot be faked

| Event | Live in the dev client | Why |
| --- | --- | --- |
| ChatMessage, StatChanged, ItemContainerChanged, WidgetLoaded/Closed, VarbitChanged | Yes | Plain values. A handler that only reads the event reacts fully. `ChatMessage.messageNode` is null: a plugin that edits the chat line will log an error and skip it. |
| A handler that also reads `client` | Partly | It sees the real game: `client.getLocalPlayer()` is null logged out, `getVarbitValue` is the real value, not the event's. Log in first. |
| GameTick, GameStateChanged | Headless only | Every plugin would run an extra tick, or think you logged out. |
| ActorDeath, NpcLootReceived | Not at all | They carry a live `Actor`/`NPC`; plugins compare it to `client.getLocalPlayer()`, which a stand-in never is. Fake the plugin's next step instead (GIM: `groupEvent`). |
| Party messages (`PartyMemberMessage` subclasses) | Headless only | A plugin that checks the sender in `PartyService` (GIM does) drops a made-up member id: it is not in the party. |
| Clan/Group channel, friends list | Headless only | Read from `client.getClanChannel(...)`, not from an event's fields. |

## Add an event kind

One factory, registered in the plugin's `*EventKinds`:

```java
kinds.add("bossKill", f -> new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
    "Your " + f.string("boss") + " kill count is: " + f.integer("count") + ".", null, 0));
```

`f.string`, `f.integer`, `f.longValue`, `f.bool`, `f.enumValue`, `f.items`,
`f.named`, `f.longs` and `f.strings` read fields with clear errors; `f.fail`
makes your own. A kind the live plugin cannot receive is wrapped with
`EventFactory.headless("why", factory)`, and the sidebar skips it with that
reason. Core kinds cannot be replaced: `add` refuses a name already taken.

## Add a scenario

1. Write `src/test/resources/scenarios/NN-words.json` (two digits, lower-case
   words: the order the sidebar lists them).
2. Give it a `description` and at least one `expect` (the scenarios test
   insists on both).
3. `./gradlew test`. The scenarios test picks up every file; a failure prints
   each expectation that did not hold and the lines the probe saw.
4. Live: `./gradlew run`, log in, open "Scenarios (dev)", pick it, Run (with
   waits) or Step (one event per click). Reload re-reads the files after
   `./gradlew processTestResources`.

## GIM Utilities

`GimEventKinds` adds interface, inventory and item constants and three kinds
for what is not a RuneLite event: `groupEvent` (a detected drop or death, as
the engine builds it), `groupChannel` (who the Group channel shows online, and
who runs the plugin) and `settingsSave` (a member saving group settings, sent
over a fake party). `GimScenarioRig` subscribes to the events and calls the
classes `GroupService` and `GroupEventEngine` call: `StorageSession`,
`ProgressTracker`, `LevelPosts` with a fake Discord transport, `EventRouter`,
`ChannelPosts`, `GovernanceSync`. Probes: `discord`, `overlay`, `storage.log`,
`settings`, `settings.saves`.

Not covered by the rig: merging a moment's events into one post
(`DiscordBatch`), screenshots, and anything the engine reads from the client.
Those keep their own unit tests and the in-game checklist.

| Scenario | Live |
| --- | --- |
| 01 group storage visit | Yes. The Storage tab logs it. |
| 02 level-up streak | Yes, for levels above your real one. |
| 03 valuable drop, 04 death | Headless only (NpcLootReceived / ActorDeath). |
| 05 member login/logout | Headless only (Group channel). |
| 06 settings save from a member | Headless only (party). |

## Another plugin

```sh
bash scripts/add-test-harness.sh ../quest-route-plugin
```

copies the harness (and this file, if the target has none) into another
plugin's `src/test`, refuses to overwrite a copy edited there, and prints the
remaining steps: an `*EventKinds`, two lines in the `*PluginTest` launcher, a
rig and a scenarios test. Why a copy and not a shared library: a library is a
dependency the Plugin Hub would have to verify, for code that never runs in
the Hub jar anyway.
