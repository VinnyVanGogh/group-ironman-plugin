#!/usr/bin/env bash
#
# Installs (or removes) a launchd agent that polls the Plugin Hub for this
# plugin and posts a macOS notification when it lands.
#
#   scripts/install-watcher.sh [--interval SECONDS] [--dir REPO] [--uninstall]
#
# --dir watches a repo these scripts do not live inside, which is the case for
# a contribution to somebody else's plugin: that clone is upstream's and should
# stay free of our tooling. The script then runs from here while the config and
# log live in that repo, and the launchd label is keyed on its slug, so several
# watchers run side by side without colliding.
#
# launchd rather than cron, deliberately: cron on macOS misses fires while the
# machine is asleep and runs outside the GUI session, where a notification has
# nowhere to appear. launchd catches up a missed StartInterval on wake and runs
# in the user's session, which is the whole point of wanting a popup.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT=""
INTERVAL=1800
UNINSTALL=0

while [ $# -gt 0 ]; do
	case "$1" in
		--interval)  INTERVAL="$2"; shift 2 ;;
		--dir)       ROOT="$(cd "$2" && pwd)"; shift 2 ;;
		--uninstall) UNINSTALL=1; shift ;;
		*) printf 'unknown option: %s\n' "$1" >&2; exit 1 ;;
	esac
done

# Default to the repo the scripts were copied into, which is what the scaffold
# produces. Resolved after arg parsing, because --dir changes the answer.
[ -n "$ROOT" ] || ROOT="$(git -C "$HERE" rev-parse --show-toplevel 2>/dev/null || dirname "$HERE")"
SLUG="$(python3 -c "import json;print(json.load(open('$ROOT/.hubwatch.json'))['slug'])" 2>/dev/null || basename "$ROOT")"

LABEL="net.runelite.hubwatch.${SLUG}"
PLIST="$HOME/Library/LaunchAgents/${LABEL}.plist"

if [ "$UNINSTALL" -eq 1 ]; then
	launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
	rm -f "$PLIST"
	printf 'removed %s\n' "$LABEL"
	exit 0
fi

[ -f "$ROOT/.hubwatch.json" ] || {
	printf 'no .hubwatch.json — run: %s/hub-watch.py --init\n' "$HERE" >&2
	exit 1
}

if ! command -v terminal-notifier >/dev/null 2>&1; then
	cat <<'NOTE'

terminal-notifier is not installed. The watcher will fall back to osascript,
which works but produces a notification you cannot click — so the message has
to carry everything, and it will not open the PR for you.

  brew install terminal-notifier

NOTE
	# Only ask when there is somebody there to answer. Run from a script, a
	# hook or an agent, stdin is not a terminal and `read` fails immediately,
	# which under `set -e` aborts the install before the plist is written --
	# an interactive nicety silently becoming an installation failure.
	if [ -t 0 ]; then
		printf 'Install it now? [y/N] '
		read -r reply || reply=""
		case "$reply" in
			[yY]*) brew install terminal-notifier ;;
			*)     printf 'continuing with osascript\n' ;;
		esac
	else
		printf 'not a terminal, continuing with osascript\n'
	fi
fi

mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" <<PLISTEOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>${LABEL}</string>

	<key>ProgramArguments</key>
	<array>
		<string>/usr/bin/python3</string>
		<string>${HERE}/hub-watch.py</string>
	</array>

	<key>WorkingDirectory</key>
	<string>${ROOT}</string>

	<!-- gh lives in a Homebrew prefix that launchd does not inherit. -->
	<key>EnvironmentVariables</key>
	<dict>
		<key>PATH</key>
		<string>/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
	</dict>

	<key>StartInterval</key>
	<integer>${INTERVAL}</integer>

	<!-- Fires once at load so a misconfiguration surfaces now, not in
	     half an hour when you have stopped watching for it. -->
	<key>RunAtLoad</key>
	<true/>

	<key>StandardOutPath</key>
	<string>${ROOT}/.hubwatch.log</string>
	<key>StandardErrorPath</key>
	<string>${ROOT}/.hubwatch.log</string>
</dict>
</plist>
PLISTEOF

launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"

cat <<DONE

Installed ${LABEL}
  watching  ${ROOT}
  polls every ${INTERVAL}s
  log: ${ROOT}/.hubwatch.log

Prove the alert path before trusting its silence:

  ${HERE}/hub-watch.py --test-notify

A watcher that cannot notify looks exactly like a watcher with nothing to
report, and you find out which one you have at the worst possible moment.

  status:     launchctl list | grep ${LABEL}
  stop:       ${0##*/} --uninstall ${ROOT:+--dir ${ROOT}}
  check now:  cd ${ROOT} && ${HERE}/hub-watch.py --peek

hub-watch.py reads .hubwatch.json from the current directory's repo root, so
run it from ${ROOT}. launchd does that for you via WorkingDirectory.
DONE
