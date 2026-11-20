#!/usr/bin/env bash
# Tests for tools/unix/run-gim-utilities.sh and for the pins it shares with
# tools/windows/Run-GimUtilities.cmd and tools/bundle/SHA256SUMS.
#   bash scripts/test_unix_launcher.sh
set -uo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
launcher="$repo/tools/unix/run-gim-utilities.sh"
cmd="$repo/tools/windows/Run-GimUtilities.cmd"
sums="$repo/tools/bundle/SHA256SUMS"
failures=0

check() { # name, command...
  local name="$1"; shift
  local out
  if out="$("$@" 2>&1)"; then echo "PASS $name"; else failures=$((failures + 1)); echo "FAIL $name : $out"; fi
}
expect_fail() { ! "$@" >/dev/null 2>&1; }

tmp="$(mktemp -d "${TMPDIR:-/tmp}/gu-unix.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT
export GU_HOME="$tmp/home" GU_SIDELOAD_DIR="$tmp/side"
# shellcheck source=/dev/null
source "$launcher"
set +e
abc='ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
printf abc > "$tmp/payload"

check 'matching hash passes' assert_sha256 "$tmp/payload" "$abc"
check 'hash comparison ignores case' assert_sha256 "$tmp/payload" "$(printf '%s' "$abc" | tr a-f A-F)"
check 'one changed byte is refused' expect_fail assert_sha256 "$tmp/payload" "${abc%?}0"
check 'missing file is refused' expect_fail assert_sha256 "$tmp/none" "$abc"
check 'malformed pin is refused' expect_fail assert_sha256 "$tmp/payload" 'abc123'

new_download() { printf '%s' "$1" > "$tmp/dl.part"; printf '%s' "$tmp/dl.part"; }
installed_ok() {
  local r; r="$(install_sideloaded_jar "$(new_download abc)" "$tmp/side" x.jar "$abc")" || return 1
  [[ "$r" == installed && "$(cat "$tmp/side/x.jar")" == abc && ! -e "$tmp/side/x.jar.bak" ]]
}
replaced_keeps_bak() {
  printf old > "$tmp/side/x.jar"
  local r; r="$(install_sideloaded_jar "$(new_download abc)" "$tmp/side" x.jar "$abc")" || return 1
  [[ "$r" == replaced && "$(cat "$tmp/side/x.jar.bak")" == old && "$(cat "$tmp/side/x.jar")" == abc ]]
}
mismatch_touches_nothing() {
  printf old > "$tmp/side/x.jar"; rm -f "$tmp/side/x.jar.bak"
  install_sideloaded_jar "$(new_download abd)" "$tmp/side" x.jar "$abc" && return 1
  [[ "$(cat "$tmp/side/x.jar")" == old && ! -e "$tmp/side/x.jar.bak" ]]
}
check 'a matching jar is installed into a new folder' installed_ok
check 'an earlier jar is kept as .bak' replaced_keeps_bak
check 'a mismatching jar is refused and nothing is touched' mismatch_touches_nothing

dry_run_lists_three() {
  local out; out="$(GU_DRY_RUN=1 bash "$launcher")" || return 1
  grep -q "gim-test-9" <<<"$out" && grep -q "Combat & Skilling Vitals  $CV_COMMIT" <<<"$out" \
    && grep -q "Quest Route             $QR_COMMIT" <<<"$out"
}
check 'a dry run lists the three components with commits' dry_run_lists_three

pin_of() { grep -E "^$1=" "$launcher" | head -1 | sed -E "s/^[A-Z0-9_]+='([^']*)'.*/\1/"; }
cmd_pin() { grep -E "^\\\$$1 += " "$cmd" | head -1 | sed -E "s/.*'([^']*)'.*/\1/"; }
sums_pin() { grep -E "  $1\$" "$sums" | cut -d' ' -f1; }

pins_match_sums() {
  [[ "$(pin_of CV_SHA256)" == "$(sums_pin combat-vitals.jar)" ]] || { echo 'CV pin differs from SHA256SUMS'; return 1; }
  [[ "$(pin_of QR_SHA256)" == "$(sums_pin quest-route.jar)" ]] || { echo 'QR pin differs from SHA256SUMS'; return 1; }
}
pins_match_cmd() {
  [[ "$(pin_of CV_SHA256)" == "$(cmd_pin CvJarSha256)" ]] || { echo 'CV pin differs from the .cmd'; return 1; }
  [[ "$(pin_of QR_SHA256)" == "$(cmd_pin QrJarSha256)" ]] || { echo 'QR pin differs from the .cmd'; return 1; }
}
real_pins() {
  local n h
  for n in CV_SHA256 QR_SHA256; do
    h="$(pin_of "$n")"
    [[ "$h" =~ ^[0-9a-f]{64}$ ]] || { echo "$n is not 64 lower-case hex"; return 1; }
    [[ ! "$h" =~ ^0{64}$ ]] || { echo "$n is all zeros: attach the jar to the release and pin its real SHA-256"; return 1; }
  done
}
sums_file_checks_out() { grep -Ec '^[0-9a-f]{64}  [a-z-]+\.jar$' "$sums" | grep -qx 2; }
check 'the launcher pins match tools/bundle/SHA256SUMS' pins_match_sums
check 'the launcher pins match the Windows .cmd' pins_match_cmd
check 'SHA256SUMS lists both jars in sha256sum format' sums_file_checks_out
check 'no jar pin is all zeros' real_pins

syntax_ok() { bash -n "$launcher"; }
check 'the launcher parses as bash' syntax_ok

if (( failures > 0 )); then echo "$failures failed"; exit 1; fi
echo 'all passed'
