#!/usr/bin/env python3
"""Estimate the plugin's size the way the Plugin Hub review bot counts it.

The bot skips comments, resources, test sources and the README, so this reads
only src/main/java, strips comments, collapses whitespace outside string
literals, and takes chars/4. That raw figure runs low against the bot, so it is
scaled by CALIBRATION, measured once: the bot reported 204k tokens on
runelite/plugin-hub#20149 for main at baff100, where the raw estimate was
RAW_AT_BOT_COUNT.

Usage:
  python3 tools/tokens.py              # working tree, delta vs origin/main
  python3 tools/tokens.py --base REF   # delta vs another git ref
  python3 tools/tokens.py --top 15     # also list the biggest files
"""
import argparse
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = "src/main/java"
BOT_COUNT = 204_000
RAW_AT_BOT_COUNT = 178_162
CALIBRATION = BOT_COUNT / RAW_AT_BOT_COUNT


def strip(src):
    """Drop comments and collapse whitespace runs outside literals."""
    out = []
    i, n = 0, len(src)
    pending_space = False
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
            pending_space = True
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            pending_space = True
            continue
        if c.isspace():
            pending_space = True
            i += 1
            continue
        if pending_space and out:
            out.append(" ")
        pending_space = False
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(src[i:j])
            i = j
            continue
        if c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1])
            i = j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def worktree_files():
    files = {}
    for dirpath, _, names in os.walk(os.path.join(ROOT, SRC)):
        for name in names:
            if name.endswith(".java"):
                path = os.path.join(dirpath, name)
                with open(path, encoding="utf-8") as f:
                    files[os.path.relpath(path, ROOT)] = f.read()
    return files


def ref_files(ref):
    names = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", ref, SRC],
        cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    files = {}
    for name in names:
        if name.endswith(".java"):
            files[name] = subprocess.run(
                ["git", "show", f"{ref}:{name}"],
                cwd=ROOT, capture_output=True, text=True, check=True).stdout
    return files


def raw_tokens(files):
    return {name: len(strip(src)) / 4 for name, src in files.items()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="origin/main")
    parser.add_argument("--top", type=int, default=0)
    parser.add_argument("--max", type=int, default=0,
                        help="exit 1 when the bot estimate is above this")
    args = parser.parse_args()

    now = raw_tokens(worktree_files())
    total = sum(now.values())
    print(f"raw chars/4:     {total:,.0f}")
    print(f"bot estimate:    {total * CALIBRATION:,.0f}  (limit 200,000, target < 190,000)")
    try:
        base = sum(raw_tokens(ref_files(args.base)).values())
        delta = (total - base) * CALIBRATION
        print(f"vs {args.base}: {base * CALIBRATION:,.0f} -> delta {delta:+,.0f}")
    except subprocess.CalledProcessError:
        print(f"(no base ref {args.base})", file=sys.stderr)
    if args.top:
        print()
        for name, tok in sorted(now.items(), key=lambda kv: -kv[1])[:args.top]:
            print(f"{tok * CALIBRATION:9,.0f}  {os.path.basename(name)}")
    if args.max and total * CALIBRATION > args.max:
        print(f"FAIL: bot estimate {total * CALIBRATION:,.0f} is over {args.max:,}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
