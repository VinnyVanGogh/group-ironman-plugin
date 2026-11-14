# UI tests and README screenshots

The plugin's own Swing panels are tested by clicking through them in JUnit:
the side panel's tabs, buttons, filters, folds and the Settings tab, and
RuneLite's settings page for this plugin. The same code renders the README
screenshots. Nothing here touches the game: clicks never leave the panel's
component tree, and no input is ever sent to the OS or the client.

```
./gradlew test                 # every test, headless; shots rendered into build/screenshots
./gradlew test -Pheadful       # also the two clipboard tests (needs a display, e.g. the Mac)
./gradlew screenshots          # renders the README shots into docs/images
```

## The driver, and why not Robot or AssertJ Swing

`SwingDriver` (src/test/java/com/vinnyvangogh/testkit/ui) finds components by
their text, clicks them, types into them, reads what is on screen and renders
the tree to a PNG. A click is the button's own `doClick`, or a mouse event
handed straight to the component's listeners. The panel is never put in a
window; the driver lays it out at the sidebar's width itself.

Two alternatives were considered and not used:

- **`java.awt.Robot`** generates "native system input events": `mouseMove`
  "will actually move the mouse cursor", and the constructor throws
  `AWTException` whenever `GraphicsEnvironment.isHeadless()` is true
  ([Robot, Java 11 API](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/java/awt/Robot.html)).
  It would take over the Mac's mouse during a test run and could not run on CI.
- **AssertJ Swing** drives tests through Robot as well. Its docs call GUI tests
  "more intrusive than regular unit tests", say a CI server "probably doesn't
  even have X running", and recommend a VNC server rather than `xvfb-run`
  ([Running AssertJ Swing tests](https://joel-costigliola.github.io/assertj/assertj-swing-running.html)).
  Its last release is 3.17.1, about six years old
  ([Maven Central](https://central.sonatype.com/artifact/org.assertj/assertj-swing)).

Driving the components directly needs no display, so the whole suite runs with
`java.awt.headless=true` (set for the `test` task in build.gradle) on CI and on
the Mac alike.

**What needs a display:** only `pasteReadsTheKeyFromTheClipboardAndOpensTheShareCard`
and `copyPutsTheKeyOnTheClipboard` in `GimPanelUiTest`. A headless JVM has no
system clipboard, so they skip (JUnit `Assume`) unless run with `-Pheadful`.
They put a made-up key on the clipboard and restore what was there.

## What stands in for the client

- **Item icons** come from `ItemManager`, which needs the game cache. A Mockito
  mock returns placeholder tiles with the item's initials and stack number.
- **Skill icons** are RuneLite's own (`SkillIconManager` reads them from the
  client jar).
- **Sprites** (the minimap orb icons) are not loaded: `SpriteManager` is a mock
  that never answers.
- **RuneLite's settings page** is the real `ConfigPanel`, built by
  `ConfigPanelHarness`. That class is package-private with a private Guice
  constructor, and the client jar is signed, so test code cannot join its
  package: the harness reaches it by reflection. This is test code only and
  never ships (the Plugin Hub replaces build.gradle and builds `main` only). Its
  `ConfigManager` is a mock that answers each read from the config interface's
  own defaults, overridden per test.
- **Look and feel** is RuneLite's (`RuneLiteLAF.setup()`), so sizes, fonts and
  colours match the client.

## Scenarios

`GimPanelFixture` builds the panel's `View` the way `GroupIronmanPlugin#redraw`
does, from state a scenario leaves behind: a storage visit (14 sharks
withdrawn, coins deposited), level-ups, a drop, a death, a login, banks, a
member without the plugin (hiscores only), and group settings signed by a
leader, an editor and a member, each with their own key. Every name is made up
(Ember Fox, Moss Kettle, Pebble Wren, Tin Lantern; group Iron Teacups).

When the fake events harness (RUNELITE-138) lands, these scenes are meant to
come from its scenario files instead of being built by hand.

## Adding a UI test

```java
@Test
public void myTabShowsTheThing()
{
	SwingDriver.onEdt(() ->
	{
		GimPanelFixture f = new GimPanelFixture().show(GimPanelFixture.storageScene()).tab("Storage");
		f.ui.type(f.ui.field("Filter storage"), "shark");   // by the field's tooltip
		f.ui.click("Collapse all");                          // a button or label, by its text
		f.ui.assertShows("Moss Kettle");                     // any visible text
	});
}
```

Everything runs on the Swing thread (`SwingDriver.onEdt`). "Visible" means the
component and every parent are visible, so a hidden tab or a folded section
does not count. A failed lookup lists everything that was on screen.

## README screenshots

`ReadmeShots` holds the shot list. Each shot is a name and a fixed height; all
are the sidebar's width (225) and saved at 2x:

| Shot | What it shows |
| --- | --- |
| settings-tab-leader | Settings tab as the leader: editors, editable group settings |
| settings-tab-member | Settings tab as a member: the group's settings, read-only |
| settings-who-posts | The leader's "Who posts what" grid |
| panel-group-hiscores | Group tab, including a member without the plugin (hiscores) |
| storage-log | Log tab with the Storage chip ticked |
| settings-discord-connection | RuneLite settings: Discord: connection, made-up webhooks filled in |
| settings-thresholds-advanced | RuneLite settings: Thresholds and Advanced |

To add one: put the name and height in `SHOTS`, a render method in the map in
`rendersEveryShotAtItsFixedSize`, and run `./gradlew screenshots`.

## Copying this into another plugin

The driver files have no plugin types:

```
scripts/add-ui-driver.sh ~/Documents/dev/runelite_plugins/<plugin>
```

copies `SwingDriver`, `ConfigPanelHarness`, `RuneLiteLook` and the driver's own
test, and prints the build.gradle lines to add (Mockito, headless tests, the
screenshots task). The plugin then writes its own fixture and shot list, as
`GimPanelFixture` and `ReadmeShots` do here.
