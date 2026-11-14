#!/usr/bin/env bash
# Copies the Swing UI test driver (docs/testing-ui.md) into another plugin repo.
#
#   scripts/add-ui-driver.sh <plugin repo>
#
# Copies src/test/java/com/vinnyvangogh/testkit/ui/*.java as they are (they hold no
# plugin types), refuses to overwrite files that differ, and prints the build.gradle
# lines the plugin needs. Test sources only: nothing here reaches a Plugin Hub jar.
set -euo pipefail

if [ $# -ne 1 ]; then
	echo "usage: $0 <plugin repo>" >&2
	exit 2
fi

here="$(cd "$(dirname "$0")/.." && pwd)"
target="$(cd "$1" && pwd)"
rel="src/test/java/com/vinnyvangogh/testkit/ui"

if [ ! -f "$target/build.gradle" ] || [ ! -f "$target/runelite-plugin.properties" ]; then
	echo "$target is not a RuneLite plugin repo (no build.gradle or runelite-plugin.properties)" >&2
	exit 1
fi
if [ "$target" = "$here" ]; then
	echo "That is this repo." >&2
	exit 1
fi

mkdir -p "$target/$rel"
for f in SwingDriver.java SwingDriverTest.java ConfigPanelHarness.java RuneLiteLook.java; do
	src="$here/$rel/$f"
	dst="$target/$rel/$f"
	if [ -f "$dst" ] && ! cmp -s "$src" "$dst"; then
		echo "skip $rel/$f: differs from this copy; diff them by hand" >&2
		continue
	fi
	cp "$src" "$dst"
	echo "copied $rel/$f"
done

cat <<'GRADLE'

Add to the plugin's build.gradle:

  dependencies {
      testImplementation 'org.mockito:mockito-core:5.14.2'
  }

  tasks.named('test', Test) {
      systemProperty 'java.awt.headless', project.hasProperty('headful') ? 'false' : 'true'
  }

  // Optional, once the plugin has a ReadmeShots test of its own:
  tasks.register('screenshots', Test) {
      description = 'Renders the README screenshots into docs/images'
      group = 'documentation'
      testClassesDirs = sourceSets.test.output.classesDirs
      classpath = sourceSets.test.runtimeClasspath
      filter { includeTestsMatching '*.ReadmeShots' }
      systemProperty 'java.awt.headless', 'true'
      systemProperty 'screenshots.dir', file('docs/images').absolutePath
      outputs.upToDateWhen { false }
  }

Then write a fixture that builds the plugin's panel and a few tests; see
docs/testing-ui.md in group-ironman-plugin.
GRADLE
