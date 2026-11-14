#!/usr/bin/env python3
"""Generate regions-generated.txt: map region id -> place name.

The hand-kept regions.txt (Discord presence areas, Wilderness places) covers
named areas only, so members in open countryside, minigames and many
teleport spots showed "region 12345". This script fills those gaps from the
place lists RuneLite itself ships, each one a name plus a WorldPoint:

  runelite-client/src/main/java/net/runelite/client/plugins/
    worldmap/TeleportLocationData.java       teleport destinations
    worldmap/MinigameLocation.java           minigames
    worldmap/AgilityCourseLocation.java      agility courses
    worldmap/RunecraftingAltarLocation.java  runecrafting altars
    cluescrolls/clues/hotcold/HotColdLocation.java + HotColdArea.java
                                             broad areas ("Asgarnia")

RuneLite is BSD 2-Clause (see THIRD_PARTY_NOTICES.md). Only names and
coordinates are taken, no code. The commit is pinned so the output is
reproducible; bump RUNELITE_COMMIT and re-run to refresh.

Region id = (x >> 6) << 8 | (y >> 6). Ids regions.txt already names are left
out: the hand table wins, and the plugin loads it first anyway.

Usage:
  python3 tools/regions/gen_regions.py            # download the pinned sources
  python3 tools/regions/gen_regions.py --src DIR  # read <DIR>/<File>.java instead
Stdlib only. Run from the repository root.
"""

import argparse
import os
import re
import sys
import urllib.request

RUNELITE_COMMIT = "bb0fa42b3d9329ffe31115f1f20042c455def2e3"
BASE = ("https://raw.githubusercontent.com/runelite/runelite/" + RUNELITE_COMMIT
        + "/runelite-client/src/main/java/net/runelite/client/plugins/")

# Priority order: an id keeps the first name found, source by source.
NAMED_SOURCES = [
    "worldmap/MinigameLocation.java",
    "worldmap/AgilityCourseLocation.java",
    "worldmap/RunecraftingAltarLocation.java",
    "worldmap/TeleportLocationData.java",
]
HOTCOLD_LOCATIONS = "cluescrolls/clues/hotcold/HotColdLocation.java"
HOTCOLD_AREAS = "cluescrolls/clues/hotcold/HotColdArea.java"

RESOURCES = "src/main/resources/com/vinnyvangogh/groupironman/"
HAND_TABLE = RESOURCES + "regions.txt"
OUT = RESOURCES + "regions-generated.txt"

# One enum constant: NAME(... "Display" ... new WorldPoint(x, y, plane) ...
CONSTANT = re.compile(r'^\s*[A-Z][A-Z0-9_]*\((?P<args>.*)\)\s*[,;]', re.M)
STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
POINT = re.compile(r'new WorldPoint\(\s*(\d+)\s*,\s*(\d+)\s*,\s*\d+\s*\)')
HOTCOLD = re.compile(r'new WorldPoint\(\s*(\d+)\s*,\s*(\d+)\s*,\s*\d+\s*\)\s*,\s*([A-Z_]+)\s*,')
AREA = re.compile(r'^\s*([A-Z_]+)\("([^"]+)"\)', re.M)


def region_id(x, y):
    return (x >> 6) << 8 | (y >> 6)


def read(src_dir, path):
    if src_dir:
        with open(os.path.join(src_dir, os.path.basename(path)), encoding="utf-8") as f:
            return f.read()
    with urllib.request.urlopen(BASE + path, timeout=30) as r:
        return r.read().decode("utf-8")


def clean(name):
    """'Fremennik Slayer Dungeon (inside)' -> 'Fremennik Slayer Dungeon'; 'Chasm of Fire teleport' -> 'Chasm of Fire'."""
    name = re.sub(r"\s*\([^)]*\)$", "", name).strip()
    name = re.sub(r"\s+teleport$", "", name, flags=re.I).strip()
    return name


def named_points(text):
    """(name, x, y) for each enum constant: the last string before its WorldPoint is the place.

    Jewellery teleports read ("Slayer Ring", "Slayer Tower", point): the item comes first,
    the place last. A point written as arithmetic (2581 + 64 * 3) does not match and is skipped.
    """
    for m in CONSTANT.finditer(text):
        args = m.group("args")
        for p in POINT.finditer(args):
            names = [s for s in STRING.findall(args[:p.start()]) if not s.endswith(".png")]
            if names:
                yield clean(names[-1]), int(p.group(1)), int(p.group(2))


def hotcold_points(locations, areas):
    names = dict(AREA.findall(areas))
    for m in HOTCOLD.finditer(locations):
        area = names.get(m.group(3))
        if area:
            yield area, int(m.group(1)), int(m.group(2))


def hand_ids(path):
    ids = set()
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            for tok in line.rsplit("=", 1)[1].split(","):
                tok = tok.strip()
                if tok.isdigit():
                    ids.add(int(tok))
    return ids


def build(src_dir):
    known = hand_ids(HAND_TABLE)
    chosen = {}
    sources = [named_points(read(src_dir, s)) for s in NAMED_SOURCES]
    sources.append(hotcold_points(read(src_dir, HOTCOLD_LOCATIONS), read(src_dir, HOTCOLD_AREAS)))
    for points in sources:
        for name, x, y in points:
            rid = region_id(x, y)
            if name and rid not in known and rid not in chosen:
                chosen[rid] = name
    return chosen


def render(chosen):
    by_name = {}
    for rid, name in chosen.items():
        by_name.setdefault(name, []).append(rid)
    lines = [
        "# GENERATED by tools/regions/gen_regions.py - do not edit by hand; re-run it.",
        "# Region id -> place name for regions the hand table (regions.txt) does not name.",
        "# Source: RuneLite runelite-client @ " + RUNELITE_COMMIT + " (BSD-2-Clause,",
        "# see THIRD_PARTY_NOTICES.md): worldmap Minigame/AgilityCourse/RunecraftingAltar/",
        "# TeleportLocationData and cluescrolls hotcold HotColdLocation/HotColdArea.",
        "# Names and coordinates only; no code. regions.txt is loaded first and wins.",
    ]
    for name in sorted(by_name, key=str.lower):
        lines.append(name + "=" + ",".join(str(i) for i in sorted(by_name[name])))
    return "\n".join(lines) + "\n"


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--src", help="directory holding the RuneLite .java files (no download)")
    args = ap.parse_args(argv)
    chosen = build(args.src)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(render(chosen))
    print("wrote %s: %d regions" % (OUT, len(chosen)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
