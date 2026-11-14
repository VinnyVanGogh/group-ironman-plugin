#!/usr/bin/env bash
#
# Copies the fake game events harness (scenario files, event kinds, the
# headless runner and the dev-only "Scenarios" sidebar) into another of our
# RuneLite plugins.
#
#   scripts/add-test-harness.sh <plugin repo> [--force]
#
# Everything lands under src/test: the Plugin Hub replaces build.gradle and
# compiles src/main only, so none of it can reach the Hub jar. A copy, not a
# shared library, on purpose: a library would be a new dependency the Hub has
# to verify, and test code gains nothing from being shared at runtime.
#
# Re-running it updates the copy. A copy edited in the target is refused
# unless --force, so local changes are not lost silently: port them back here
# instead, where every plugin gets them.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="src/test/java/com/vinnyvangogh/scenarios"
FIXTURES="src/test/resources/harness-fixtures"

usage() { echo "usage: $0 <plugin repo> [--force]" >&2; exit 2; }

[ $# -ge 1 ] || usage
TARGET="$(cd "$1" 2>/dev/null && pwd)" || { echo "no such directory: $1" >&2; exit 1; }
FORCE="${2:-}"
[ -z "$FORCE" ] || [ "$FORCE" = "--force" ] || usage

[ "$TARGET" != "$HERE" ] || { echo "that is the harness's own repo" >&2; exit 1; }
[ -d "$TARGET/src/main/java" ] && [ -f "$TARGET/build.gradle" ] \
	|| { echo "$TARGET is not a plugin repo (no src/main/java or build.gradle)" >&2; exit 1; }

# A target file that exists and differs is a local edit; refuse unless forced.
changed=()
for src in "$HERE/$PKG"/*.java "$HERE/$FIXTURES"/*.json; do
	rel="${src#"$HERE"/}"
	dst="$TARGET/$rel"
	if [ -f "$dst" ] && ! cmp -s "$src" "$dst"; then
		changed+=("$rel")
	fi
done
if [ ${#changed[@]} -gt 0 ] && [ "$FORCE" != "--force" ]; then
	echo "these differ in $TARGET (edited there, or an older copy):" >&2
	printf '  %s\n' "${changed[@]}" >&2
	echo "port wanted changes back to $HERE, then re-run with --force" >&2
	exit 1
fi

mkdir -p "$TARGET/$PKG" "$TARGET/$FIXTURES" "$TARGET/src/test/resources/scenarios"
cp "$HERE/$PKG"/*.java "$TARGET/$PKG/"
cp "$HERE/$FIXTURES"/*.json "$TARGET/$FIXTURES/"
[ -f "$TARGET/TESTING.md" ] || cp "$HERE/TESTING.md" "$TARGET/TESTING.md"

if grep -rqs "com.vinnyvangogh.scenarios" "$TARGET/src/main"; then
	echo "WARNING: src/main refers to the harness; it must stay test-only" >&2
fi

cat <<EOF
Harness copied into $TARGET ($(ls "$HERE/$PKG"/*.java | wc -l | tr -d ' ') files under $PKG).

Next, in that repo:
  1. Add <Plugin>EventKinds in the plugin's test package: EventKinds.core()
     plus the constants and kinds its scenarios need.
  2. In the *PluginTest launcher, before RuneLite.main:
       ScenariosPlugin.use(<Plugin>EventKinds.kinds());
       ExternalPluginManager.loadBuiltin(<Plugin>.class, ScenariosPlugin.class);
  3. Write scenarios in src/test/resources/scenarios/NN-words.json.
  4. For headless checks, a JUnit test that posts each scenario on an EventBus
     to the plugin's rig and checks Expectations (see GIM's GimScenariosTest).
  5. ./gradlew test, and ./gradlew run to use the Scenarios sidebar.
TESTING.md (copied if absent) explains each step.
EOF
