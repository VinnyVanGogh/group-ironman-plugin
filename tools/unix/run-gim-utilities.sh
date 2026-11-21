#!/usr/bin/env bash
# run-gim-utilities.sh - macOS / Linux twin of tools/windows/Run-GimUtilities.cmd.
# Starts a RuneLite test client with GIM Utilities loaded, and sideloads the
# current Combat & Skilling Vitals and Quest Route jars next to it.
#
#   tools/unix/run-gim-utilities.sh            build and start the client
#   GU_BUILD_ONLY=1 tools/unix/run-gim-utilities.sh   build only (CI)
#   GU_DRY_RUN=1 tools/unix/run-gim-utilities.sh      list the components and exit
#
# Nothing here needs sudo or changes shell or system settings. Downloads go
# under $GU_HOME (default ~/.gim-utilities). Java 11 to 21 with javac must
# already be installed (the Windows launcher downloads one; this one does not).
set -euo pipefail

# ---- Pins. Change these together with tools/windows/Run-GimUtilities.cmd and
# tools/bundle/SHA256SUMS; tools/unix/tests/test_launcher.sh checks they agree. ----
SOURCE_REF='gim-test-9'
SOURCE_URL="https://github.com/VinnyVanGogh/group-ironman-plugin/archive/refs/tags/${SOURCE_REF}.tar.gz"
RELEASE_URL="https://github.com/VinnyVanGogh/group-ironman-plugin/releases/download/${SOURCE_REF}"
# Plain `./gradlew jar` of VinnyVanGogh/combat-vitals-plugin at this commit.
CV_COMMIT='084f6e5'
CV_JAR='combat-vitals.jar'
CV_SHA256='6156ad6ca1dd134cd78b2169a4c726997bf42feeb32f8d1ef0a6ec6856408081'
# Plain `./gradlew jar` of VinnyVanGogh/questpath-plugin at this commit,
# build/libs/questpath.jar renamed. All zeros means "not pinned yet".
QR_COMMIT='2fe0674'
QR_JAR='quest-route.jar'
QR_SHA256='0000000000000000000000000000000000000000000000000000000000000000'

GU_HOME="${GU_HOME:-$HOME/.gim-utilities}"
SIDELOAD_DIR="${GU_SIDELOAD_DIR:-$HOME/.runelite/sideloaded-plugins}"

say() { printf '%s\n' "$*"; }

# Prints the SHA-256 of a file, lower case. macOS has shasum, most Linux has sha256sum.
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

# Succeeds only when $1 is a file whose SHA-256 equals the 64-hex pin $2.
assert_sha256() {
  local path="$1" expected
  expected="$(printf '%s' "$2" | tr 'A-F' 'a-f')"
  [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { say "Pinned SHA-256 is not 64 hex characters: '$2'" >&2; return 1; }
  [[ -f "$path" ]] || { say "File to verify does not exist: $path" >&2; return 1; }
  local actual
  actual="$(sha256_of "$path")"
  if [[ "$actual" != "$expected" ]]; then
    say "Checksum mismatch for $path. Expected $expected, got $actual. The download is refused." >&2
    return 1
  fi
}

# Moves a verified download into the sideload folder. A different jar already
# there under the same name is kept as <name>.bak (RuneLite loads only *.jar).
# Prints 'installed' or 'replaced'. A mismatch leaves the folder alone.
install_sideloaded_jar() {
  local downloaded="$1" folder="$2" name="$3" expected="$4"
  assert_sha256 "$downloaded" "$expected" || return 1
  mkdir -p "$folder"
  local target="$folder/$name" result='installed'
  if [[ -f "$target" ]]; then
    cp -f "$target" "$target.bak"
    result='replaced'
  fi
  mv -f "$downloaded" "$target"
  printf '%s\n' "$result"
}

# Installs one sideloaded jar. Never fatal: without it GIM Utilities still starts.
install_sideload() {
  local label="$1" name="$2" sha="$3" commit="$4"
  local target="$SIDELOAD_DIR/$name"
  if [[ "$sha" =~ ^0{64}$ ]]; then
    say "      WARNING: $label is not pinned in this launcher yet, so it was skipped."
    say '      GIM Utilities still starts without it.'
    return 0
  fi
  if [[ -f "$target" && "$(sha256_of "$target")" == "$(printf '%s' "$sha" | tr 'A-F' 'a-f')" ]]; then
    say "      $label ($commit) is already in $SIDELOAD_DIR"
    return 0
  fi
  say "      Downloading $label ($commit) into $SIDELOAD_DIR..."
  mkdir -p "$GU_HOME/downloads"
  local part="$GU_HOME/downloads/$name.part" result
  rm -f "$part"
  if ! curl -fsSL -o "$part" "$RELEASE_URL/$name" \
    || ! result="$(install_sideloaded_jar "$part" "$SIDELOAD_DIR" "$name" "$sha" 2>&1)"; then
    rm -f "$part"
    say "      WARNING: $label was not installed (${result:-download failed})."
    say '      GIM Utilities still starts without it.'
    return 0
  fi
  if [[ "$result" == 'replaced' ]]; then
    say "      Your earlier $name is kept next to it as $name.bak."
  fi
  say "      $label installed. Only this test client loads it, not your normal RuneLite."
}

list_components() {
  say "GIM Utilities           $SOURCE_REF (this repo)"
  say "Combat & Skilling Vitals  $CV_COMMIT  $CV_JAR  sha256 $CV_SHA256"
  say "Quest Route             $QR_COMMIT  $QR_JAR  sha256 $QR_SHA256"
}

# Java major version of a JDK home, or 0 when it is not a usable JDK.
jdk_major() {
  local home="$1"
  [[ -n "$home" && -x "$home/bin/java" && -x "$home/bin/javac" ]] || { echo 0; return; }
  local out major minor
  out="$("$home/bin/java" -version 2>&1 || true)"
  if [[ "$out" =~ version\ \"([0-9]+)(\.([0-9]+))? ]]; then
    major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[3]:-}"
    if [[ "$major" == 1 && -n "$minor" ]]; then major="$minor"; fi
    echo "$major"
  else
    echo 0
  fi
}

# 11 is what the Plugin Hub builds with; 21 is the newest the Gradle 8.10
# wrapper and RuneLite have been run on.
find_jdk() {
  local candidates=() c m
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
  if [[ -x /usr/libexec/java_home ]]; then
    for v in 21 17 11; do
      c="$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)"
      [[ -n "$c" ]] && candidates+=("$c")
    done
  fi
  if command -v javac >/dev/null 2>&1; then
    c="$(command -v javac)"
    c="$(cd "$(dirname "$c")/.." 2>/dev/null && pwd -P || true)"
    [[ -n "$c" ]] && candidates+=("$c")
  fi
  for c in "${candidates[@]}"; do
    m="$(jdk_major "$c")"
    if (( m >= 11 && m <= 21 )); then printf '%s\n' "$c"; return 0; fi
  done
  return 1
}

is_checkout() {
  [[ -n "$1" && -x "$1/gradlew" && -d "$1/src/main/java/com/vinnyvangogh/groupironman" ]]
}

find_source() {
  local here checkout
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
  checkout="$(cd "$here/../.." && pwd -P)"
  if is_checkout "$checkout"; then
    say "      Using the source this launcher sits in: $checkout" >&2
    printf '%s\n' "$checkout"; return 0
  fi
  local root="$GU_HOME/src/$SOURCE_REF"
  if is_checkout "$root"; then
    say "      Using the downloaded source: $root" >&2
    printf '%s\n' "$root"; return 0
  fi
  say "      Downloading GIM Utilities source ($SOURCE_REF) from GitHub..." >&2
  mkdir -p "$GU_HOME/downloads" "$GU_HOME/src"
  local tarball="$GU_HOME/downloads/gim-utilities-$SOURCE_REF.tar.gz"
  curl -fsSL -o "$tarball" "$SOURCE_URL" \
    || { say "Could not download $SOURCE_URL. If it says 404, the repo is not public yet or the tag $SOURCE_REF does not exist." >&2; return 1; }
  rm -rf "$root.unpacking" "$root"
  mkdir -p "$root.unpacking"
  tar -xzf "$tarball" -C "$root.unpacking" --strip-components=1
  is_checkout "$root.unpacking" || { say "The downloaded source at $SOURCE_URL does not look like GIM Utilities." >&2; return 1; }
  mv "$root.unpacking" "$root"
  printf '%s\n' "$root"
}

main() {
  say 'GIM Utilities test client'
  say "Everything is kept in $GU_HOME"
  say ''
  if [[ "${GU_DRY_RUN:-}" == 1 ]]; then
    say 'Dry run (GU_DRY_RUN=1): nothing is downloaded or started. Components:'
    list_components
    return 0
  fi

  say '[1/3] Looking for Java 11 or newer...'
  local jdk
  jdk="$(find_jdk)" || { say 'ERROR: no JDK 11 to 21 (with javac) found. Install one (for example Eclipse Temurin 11) and run this again.' >&2; return 1; }
  say "      Found $jdk"

  say '[2/3] Getting the plugin source...'
  local src
  src="$(find_source)"
  install_sideload 'Combat & Skilling Vitals' "$CV_JAR" "$CV_SHA256" "$CV_COMMIT"
  install_sideload 'Quest Route' "$QR_JAR" "$QR_SHA256" "$QR_COMMIT"

  local task=run
  if [[ "${GU_BUILD_ONLY:-}" == 1 ]]; then
    task=build
    say '[3/3] Building GIM Utilities only (GU_BUILD_ONLY=1); RuneLite will not open.'
  else
    say '[3/3] Building and starting RuneLite with GIM Utilities. The first run downloads Gradle and RuneLite (a few minutes); later runs are quick.'
    say '      Close the RuneLite window when you are done.'
  fi
  export JAVA_HOME="$jdk"
  export GRADLE_USER_HOME="$GU_HOME/gradle"
  export PATH="$jdk/bin:$PATH"
  cd "$src"
  ./gradlew --console=plain "$task"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@" || { code=$?; say '' >&2; say 'GIM Utilities did not start. The reason is printed above.' >&2; exit "$code"; }
fi
