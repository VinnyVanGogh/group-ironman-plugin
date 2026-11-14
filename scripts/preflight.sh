#!/usr/bin/env bash
#
# Pre-submission audit for a RuneLite Plugin Hub plugin.
#
#   scripts/preflight.sh [--build]
#
# Checks the things that get a plugin rejected mechanically, so a reviewer's
# time goes on the parts that need judgement. It cannot tell you whether a
# feature is allowed — that is the runelite-compliance skill's job, and it
# needs a human reading the rules against your design.
#
# Exit status: 0 when nothing failed, 1 when something did.

set -uo pipefail

cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; DIM=$'\033[2m'; OFF=$'\033[0m'
FAILED=0
WARNED=0

fail() { printf '%s FAIL%s  %s\n' "$RED" "$OFF" "$1"; FAILED=$((FAILED + 1)); }
warn() { printf '%s WARN%s  %s\n' "$YELLOW" "$OFF" "$1"; WARNED=$((WARNED + 1)); }
pass() { printf '%s  ok %s  %s\n' "$GREEN" "$OFF" "$1"; }
note() { printf '%s       %s%s\n' "$DIM" "$1" "$OFF"; }

SRC="src/main/java"
[ -d "$SRC" ] || { fail "no $SRC — run this from the plugin repo root"; exit 1; }

printf '\nPlugin Hub preflight — %s\n\n' "$(basename "$PWD")"

# ---------------------------------------------- forbidden language features

printf 'Forbidden language features\n'

# Matches in code only. A javadoc line saying "never use reflection" was
# failing the reflection check, which trained you to ignore the output — the
# worst possible outcome for a checker.
strip_comments() { grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*)'; }
hits() { grep -rn --include='*.java' -E "$1" "$SRC" 2>/dev/null | strip_comments; }
gradle_hits() { grep -nE "$1" build.gradle 2>/dev/null | grep -vE '^[0-9]+:[[:space:]]*//'; }

# Gson and Guice perform reflection inside their own libraries, which is fine.
# What is forbidden is reflection this plugin performs.
if out=$(hits '\bClass\.forName\b|getDeclaredMethod|getDeclaredField|\.getMethod\(|java\.lang\.reflect'); [ -n "$out" ]; then
	fail "reflection — an automatic rejection"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no reflection"
fi

if out=$(hits '\bProcessBuilder\b|Runtime\.getRuntime\(\)\.exec'); [ -n "$out" ]; then
	fail "external process execution"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no external processes"
fi

# The scenario harness (TESTING.md) is test-only; main code must never reach it.
if out=$(hits 'com\.vinnyvangogh\.scenarios'); [ -n "$out" ]; then
	fail "src/main uses the test-only scenario harness"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "scenario harness stays in src/test"
fi

if out=$(hits '\bsun\.misc\.Unsafe\b|\borg\.lwjgl\b|System\.loadLibrary|\bnative\b +[a-zA-Z]'); [ -n "$out" ]; then
	fail "native / unsafe memory access"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no JNI, JNA or Unsafe"
fi

if out=$(hits 'implements +Serializable|ObjectOutputStream|ObjectInputStream'); [ -n "$out" ]; then
	fail "Java serialization"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no Java serialization"
fi

if out=$(hits '\bURLClassLoader\b|defineClass|\bClassLoader\b'); [ -n "$out" ]; then
	fail "dynamic class loading"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no dynamic class loading"
fi

# ------------------------------------------------------------ api and http

printf '\nAPI usage\n'

if out=$(hits 'HttpURLConnection|java\.net\.http\.HttpClient|org\.apache\.http'); [ -n "$out" ]; then
	fail "HTTP client other than OkHttp"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no forbidden HTTP client"
fi

if out=$(hits 'new +GsonBuilder\(\)|new +Gson\(\)'); [ -n "$out" ]; then
	fail "Gson constructed rather than injected — use @Inject Gson, then .newBuilder()"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "Gson injected, not constructed"
fi

if out=$(hits '\.execute\(\)'); [ -n "$out" ]; then
	warn "okhttp execute() — blocking. Prefer enqueue(); never call this on the client thread"
	printf '%s\n' "$out" | sed 's/^/         /'
fi

if out=$(hits 'Thread\.sleep'); [ -n "$out" ]; then
	fail "Thread.sleep"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no Thread.sleep"
fi

if out=$(hits 'awaitTermination'); [ -n "$out" ]; then
	fail "awaitTermination — shutDown() must never block; use shutdownNow()"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "shutDown does not block"
fi

if out=$(hits 'java\.awt\.Desktop'); [ -n "$out" ]; then
	fail "java.awt.Desktop — use LinkBrowser"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no java.awt.Desktop"
fi

if out=$(hits 'log\.info'); [ -n "$out" ]; then
	warn "log.info — fine once at startup, pollutes user logs per-event. Prefer log.debug"
	printf '%s\n' "$out" | sed 's/^/         /'
fi

# ------------------------------------------------------- menus and widgets

printf '\nRestricted behaviour (read these, do not just skim)\n'

if out=$(hits 'setMenuEntries|createMenuEntry|MenuEntryAdded|\.setAction\('); [ -n "$out" ]; then
	warn "menu or widget action changes — restricted, and several forms are outright banned"
	printf '%s\n' "$out" | sed 's/^/         /'
	note "banned: new entries that cause server actions; conditional removal by NPC/friend"
	note "banned: Construction and Blackjacking menu changes"
	note "check every hit against AGENTS.md before submitting"
fi

if out=$(hits 'setOriginalX|setOriginalY|setOriginalWidth|setOriginalHeight'); [ -n "$out" ]; then
	warn "widget geometry changes — moving or resizing click zones is restricted"
	printf '%s\n' "$out" | sed 's/^/         /'
	note "banned for: 3D components, combat options, inventory, equipment, spellbook, prayer"
fi

if out=$(hits 'new +Robot\(\)|dispatchEvent|KeyEvent\.|MouseEvent\.'); [ -n "$out" ]; then
	warn "input event construction — injecting input is banned"
	printf '%s\n' "$out" | sed 's/^/         /'
fi

# ------------------------------------------------------------- config rules

printf '\nConfig\n'

# The group is written either as a literal at the annotation or, more often,
# as a constant the annotation refers to. Try the literal, then the constant.
GROUP=$(grep -rh -A1 '@ConfigGroup' "$SRC" 2>/dev/null | grep -oE '"[a-z0-9][a-z0-9-]*"' | head -1 | tr -d '"')
if [ -z "$GROUP" ]; then
	GROUP=$(grep -rhE 'String +GROUP *=' "$SRC" 2>/dev/null | grep -oE '"[a-z0-9][a-z0-9-]*"' | head -1 | tr -d '"')
fi
if [ -z "$GROUP" ]; then
	warn "no @ConfigGroup found"
elif [ ${#GROUP} -lt 6 ] || printf '%s' "$GROUP" | grep -qE '^(example|test|plugin|main|osrs)$'; then
	fail "config group \"$GROUP\" is too generic — AGENTS.md requires a specific name"
else
	pass "config group \"$GROUP\""
	note "frozen once published: renaming it silently resets every user's settings"
fi

# A third-party toggle has to be opt-in and carry the warning string verbatim.
WARNING='This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers'
if grep -rq --include='*.java' 'okhttp3\|HttpUrl' "$SRC" 2>/dev/null; then
	if grep -rqF --include='*.java' "$WARNING" "$SRC" 2>/dev/null; then
		pass "third-party warning string present"
	else
		fail "this plugin makes HTTP requests but no @ConfigItem carries the required warning"
		note "warning = \"$WARNING\""
	fi
	note "and the toggle must default to false — check that yourself, it cannot be grepped"
fi

# --------------------------------------------------------------- packaging

printf '\nPackaging\n'

# Main sources and build files only: the rule is about not shipping the
# template's identity, and a test may legitimately name com.example as a class
# it expects to be missing.
if out=$(grep -rn 'com\.example\|ExamplePlugin\|ExampleConfig' \
	"$SRC" build.gradle settings.gradle runelite-plugin.properties \
	--include='*.java' 2>/dev/null); then
	fail "template leftovers"
	printf '%s\n' "$out" | sed 's/^/         /'
else
	pass "no template leftovers"
fi

if [ -e "$SRC/../resources/META-INF/services/net.runelite.client.plugins.Plugin" ] \
	|| find . -path ./build -prune -o -name 'net.runelite.client.plugins.Plugin' -print 2>/dev/null | grep -q .; then
	fail "META-INF/services/net.runelite.client.plugins.Plugin must not be committed"
else
	pass "no META-INF services file"
fi

if git ls-files 2>/dev/null | grep -qE '\.class$|^build/|^out/|\.tmp/'; then
	fail "build artifacts are committed"
	git ls-files | grep -E '\.class$|^build/|^out/|\.tmp/' | sed 's/^/         /'
else
	pass "no committed build artifacts"
fi

if grep -q 'options.release.set(11)' build.gradle 2>/dev/null; then
	pass "targets Java 11"
else
	fail "build.gradle must target Java 11 — options.release.set(11)"
fi

if [ -n "$(gradle_hits '^[[:space:]]*(implementation|compileOnly|api)[[:space:]]+.*(com\.google\.code\.gson|com\.google\.inject|com\.squareup\.okhttp)')" ]; then
	fail "transitive runelite-client dependencies declared directly in build.gradle"
else
	pass "no transitive dependencies declared"
fi

if [ -n "$(gradle_hits 'latest\.release')" ]; then
	warn "runelite-client is a floating version — pin it so the submitted commit builds reproducibly"
else
	pass "runelite-client version pinned"
fi

if [ -f LICENSE ] && grep -qiE 'bsd|mit|apache' LICENSE; then
	pass "permissive licence"
else
	fail "LICENSE missing or not permissive (BSD-2 is the expected default)"
fi

for f in runelite-plugin.properties; do
	[ -f "$f" ] || { fail "$f missing"; continue; }
	for key in displayName author description tags plugins; do
		if ! grep -qE "^$key=.+" "$f"; then
			fail "$f: $key is empty"
		fi
	done
	grep -qE '^version=$' "$f" || warn "$f: version should be left empty; the Hub sets it"
	grep -qE '^build=standard$' "$f" || warn "$f: build=standard is the usual value"
done
[ -f runelite-plugin.properties ] && pass "manifest keys present"

# -------------------------------------------------------------------- icon

if [ -f icon.png ]; then
	kind="$(file -b icon.png)"
	case "$kind" in
		PNG*)
			bytes=$(wc -c < icon.png | tr -d ' ')
			dims="$(printf '%s' "$kind" | grep -oE '[0-9]+ x [0-9]+' | head -1)"
			if [ "$dims" = "1 x 1" ]; then
				fail "icon.png is the scaffold's 1x1 placeholder — replace it"
			else
				pass "icon.png is a real PNG (${dims:-?}, ${bytes} bytes)"
				note "Java holds it at width x height x 4 bytes in memory, whatever the file size"
			fi ;;
		*) fail "icon.png is not a PNG: $kind" ;;
	esac
else
	warn "no icon.png"
fi

# ------------------------------------------------------------------- build

if [ "${1:-}" = "--build" ]; then
	printf '\nBuild\n'
	if ./gradlew build --no-daemon -q >/tmp/preflight-build.log 2>&1; then
		pass "./gradlew build"
	else
		fail "./gradlew build — see /tmp/preflight-build.log"
		tail -20 /tmp/preflight-build.log | sed 's/^/         /'
	fi
else
	printf '\n%sBuild not run. Pass --build to include ./gradlew build.%s\n' "$DIM" "$OFF"
fi

# ------------------------------------------------------------------ result

printf '\n'
if [ "$FAILED" -gt 0 ]; then
	printf '%s%d failed%s, %d warnings. Fix the failures before opening the PR.\n\n' "$RED" "$FAILED" "$OFF" "$WARNED"
	exit 1
fi
printf '%sNothing failed%s, %d warnings.\n' "$GREEN" "$OFF" "$WARNED"
printf 'This checks mechanics only. Whether your features are allowed is a\n'
printf 'judgement call — run the runelite-compliance skill against BRIEF.md.\n\n'
