#!/usr/bin/env python3
"""Regenerate (or check) GIM Utilities' bundled quest item data from Quest Path.

The Shopping tab's presets need each quest's item lines, prerequisites,
requirements and rewards, plus the shops and monster locations those items
name. Quest Path already has all of it in its bundled quests.json; this copies
the subset the presets reach into
src/main/resources/com/vinnyvangogh/groupironman/quest-items.json, so GIM
Utilities never depends on Quest Path at runtime.

Which quests: every quest named in shopping-presets.json (each preset's
"quests" and every "choose.from" option) and all their prerequisites,
followed to the end.

Usage (from the repo root):
    python3 scripts/gen-quest-items.py --source PATH/TO/questpath/quests.json --commit SHA
    python3 scripts/gen-quest-items.py --source ... --commit SHA --check   # exit 1 if stale

The source is Quest Path's src/main/resources/com/vinnyvangogh/questpath/quests.json
at the commit given; the commit is recorded in the output's "source" field.
"""

import argparse
import json
import sys
from pathlib import Path

RES = Path("src/main/resources/com/vinnyvangogh/groupironman")
PRESETS = RES / "shopping-presets.json"
OUT = RES / "quest-items.json"
SOURCE_PATH = "src/main/resources/com/vinnyvangogh/questpath/quests.json"

ITEM_FIELDS = ("name", "item_id", "quantity", "optional", "obtainable", "unresolved",
               "sources", "shop_names", "drop_source_names")


def roots(presets):
    out = []
    for p in presets["presets"]:
        out.extend(p.get("quests", []))
        out.extend((p.get("choose") or {}).get("from", []))
    return out


def closure(quests_by_name, quests_by_id, names):
    seen, order, stack = set(), [], []
    for name in names:
        if name not in quests_by_name:
            sys.exit("preset quest not in the source: %r" % name)
        stack.append(quests_by_name[name])
    while stack:
        q = stack.pop()
        if q["id"] in seen:
            continue
        seen.add(q["id"])
        order.append(q)
        for pre in q.get("prerequisites") or []:
            if pre not in quests_by_id:
                sys.exit("%s names prerequisite %s, which the source lacks" % (q["name"], pre))
            stack.append(quests_by_id[pre])
    return sorted(order, key=lambda q: q["id"])


def compact(obj):
    """Drops false, zero, empty and null fields: the reader treats a missing field the same."""
    return {k: v for k, v in obj.items() if v not in (None, False, 0, "", [], {})}


def item(i):
    out = {k: i.get(k) for k in ITEM_FIELDS}
    out["grouped"] = bool(i.get("group_name")) and bool(i.get("alternatives"))
    return compact(out)


def quest(q):
    return compact({
        "id": q["id"],
        "name": q["name"],
        "members": q.get("members", False),
        "prerequisites": q.get("prerequisites") or [],
        "prerequisites_started_only": q.get("prerequisites_started_only") or [],
        "requirements": [compact(r) for r in q.get("requirements") or []],
        "rewards": [compact(r) for r in q.get("rewards") or []],
        "items": [item(i) for i in q.get("items") or []],
    })


def shop(s):
    return compact({k: s.get(k) for k in ("name", "x", "y", "plane", "town", "reachability")})


def quest_names(src):
    """Every quest's name by id: from the source's quests, plus a "quest_names" map when the source is
    itself a subset carrying one. Shops can be behind any quest, not only the ones carried."""
    names = {int(k): v for k, v in (src.get("quest_names") or {}).items()}
    names.update({q["id"]: q["name"] for q in src["quests"]})
    return {str(k): names[k] for k in sorted(names)}


def generate(src, presets, commit):
    by_name = {q["name"]: q for q in src["quests"]}
    by_id = {q["id"]: q for q in src["quests"]}
    quests = [quest(q) for q in closure(by_name, by_id, roots(presets))]
    shop_names = {n for q in quests for i in q.get("items", []) for n in i.get("shop_names", [])}
    droppers = {n for q in quests for i in q.get("items", []) for n in i.get("drop_source_names", [])}
    shops = sorted((shop(s) for s in src.get("shops") or [] if s.get("name") in shop_names),
                   key=lambda s: s["name"])
    drops = sorted({(d.get("source_name"), d.get("region") or "") for d in src.get("drop_locations") or []
                    if d.get("source_name") in droppers})
    return {
        "source": {
            "repo": "VinnyVanGogh/questpath-plugin",
            "commit": commit,
            "path": SOURCE_PATH,
            "graph_version": str(src.get("version")),
        },
        "quests": quests,
        "quest_names": quest_names(src),
        "shops": shops,
        "drop_locations": [compact({"source_name": n, "region": r}) for n, r in drops],
    }


def render(doc):
    """One quest, shop or drop location per line: small, and a regeneration diffs line by line."""
    def one(v):
        return json.dumps(v, ensure_ascii=False, separators=(",", ":"))
    lines = ["{"]
    keys = list(doc)
    for i, key in enumerate(keys):
        end = "," if i < len(keys) - 1 else ""
        value = doc[key]
        if isinstance(value, list):
            lines.append(' "%s": [' % key)
            lines.extend("  " + one(e) + ("," if j < len(value) - 1 else "") for j, e in enumerate(value))
            lines.append(" ]" + end)
        else:
            lines.append(' "%s": %s%s' % (key, one(value), end))
    lines.append("}")
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", required=True, help="Quest Path's quests.json")
    ap.add_argument("--commit", required=True, help="the Quest Path commit the source is from")
    ap.add_argument("--check", action="store_true", help="exit 1 when the bundled file differs")
    ap.add_argument("--out", type=Path, default=OUT, help="where to write (default: the bundled resource)")
    args = ap.parse_args()
    with open(args.source, encoding="utf-8") as f:
        src = json.load(f)
    with open(PRESETS, encoding="utf-8") as f:
        presets = json.load(f)
    doc = generate(src, presets, args.commit)
    text = render(doc)
    if args.check:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if current != text:
            print("%s is stale: rerun without --check" % OUT)
            return 1
        print("%s is up to date" % OUT)
        return 0
    args.out.write_text(text, encoding="utf-8")
    print("wrote %s: %d quests, %d shops" % (args.out, len(doc["quests"]), len(doc["shops"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
