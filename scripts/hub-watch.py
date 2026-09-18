#!/usr/bin/env python3
"""Watch one plugin's journey onto the RuneLite Plugin Hub, and say so when it lands.

Five states, because merged is not live:

    submitted -> checks -> labelled -> merged -> LIVE on the hub

The last hop is a separate fact with a separate source. A merged PR still has
to build on the Hub's own infrastructure before anyone can install it, and the
only thing that proves it did is the plugin appearing in the Hub's own index:

    https://api.runelite.net/runelite-<client version>/pluginhub

which is a JSON map of plugin slug to install count. Being in it means the
plugin is installable right now, and the number is how many people have it.

It also watches a PR on any other repo, which is the same job minus the Hub
half: a contribution to somebody else's plugin has the review to wait on but
no Hub listing to reach. Set "repo" and "watch_hub": false in .hubwatch.json
and the Hub checks are skipped. Waiting on a maintainer's reply is exactly the
case where a poll beats checking by hand, because the reply may take weeks and
checking costs attention every time.

Usage:
    hub-watch.py --init            write .hubwatch.json from this repo + gh
    hub-watch.py                   poll; notify and print only on a change
    hub-watch.py --peek            print the picture, change nothing
    hub-watch.py --test-notify     prove the notification path works

Reads .hubwatch.json in the repo root; writes .hubwatch.state.json beside it
(gitignored). First real run is silent: it records a baseline so a backlog of
existing bot comments does not fire as if it were new.

.hubwatch.json keys:
    slug          plugin slug, used for the Hub index lookup
    display_name  what the notification calls it
    pr            PR number to watch, or null
    author        your login; your own comments are not news
    repo          owner/name the PR lives on (default runelite/plugin-hub)
    watch_hub     false to skip the Hub index entirely (default true)
"""

import json
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"],
                      capture_output=True, text=True).stdout.strip() or os.getcwd()
CONFIG_PATH = os.path.join(ROOT, ".hubwatch.json")
STATE_PATH = os.path.join(ROOT, ".hubwatch.state.json")

HUB_REPO = "runelite/plugin-hub"
DEFAULT_REPO = HUB_REPO
VERSION_URL = f"https://raw.githubusercontent.com/{HUB_REPO}/master/runelite.version"
INDEX_URL = "https://api.runelite.net/runelite-{version}/pluginhub"

# stale.yml on plugin-hub: only-pr-labels 'waiting for author',
# days-before-pr-stale 7, days-before-pr-close 0. So this label is a deadline,
# not a notification, and it is the one thing here worth waking up for.
DEADLINE_LABEL = "waiting for author"

PEEK = "--peek" in sys.argv
INIT = "--init" in sys.argv
TEST_NOTIFY = "--test-notify" in sys.argv


# ----------------------------------------------------------------- plumbing

def run(args, timeout=90):
    try:
        out = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        return out.stdout if out.returncode == 0 else None
    except Exception:
        return None


def get_json(url, timeout=20):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            return json.loads(response.read().decode())
    except (urllib.error.URLError, ValueError, OSError):
        return None


def get_text(url, timeout=20):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            return response.read().decode().strip()
    except (urllib.error.URLError, OSError):
        return None


def notify(title, message, url=None):
    """macOS notification. terminal-notifier if present, osascript otherwise.

    terminal-notifier is preferred because its notification is clickable and
    can open the PR. osascript needs no install but the popup does nothing
    when clicked, so the message has to carry everything worth knowing.
    """
    if shutil.which("terminal-notifier"):
        args = ["terminal-notifier", "-title", title, "-message", message,
                "-sound", "Glass", "-group", "runelite-hub-watch"]
        if url:
            args += ["-open", url]
        subprocess.run(args, capture_output=True)
        return

    # AppleScript string literals: backslashes first, then quotes.
    def esc(s):
        return s.replace("\\", "\\\\").replace('"', '\\"')

    script = (f'display notification "{esc(message)}" '
              f'with title "{esc(title)}" sound name "Glass"')
    subprocess.run(["osascript", "-e", script], capture_output=True)


# --------------------------------------------------------------------- init

def do_init():
    slug = os.path.basename(ROOT)
    for suffix in ("-plugin", "-plugin-hub"):
        if slug.endswith(suffix):
            slug = slug[: -len(suffix)]

    props = os.path.join(ROOT, "runelite-plugin.properties")
    display = slug
    if os.path.exists(props):
        for line in open(props):
            if line.startswith("displayName="):
                display = line.split("=", 1)[1].strip()

    # The PR is on plugin-hub, not on this repo, so find it by author.
    me = (run(["gh", "api", "user", "--jq", ".login"]) or "").strip()
    pr = None
    if me:
        listing = run(["gh", "pr", "list", "--repo", HUB_REPO, "--author", me,
                       "--state", "all", "--limit", "20",
                       "--json", "number,title,headRefName,createdAt"])
        if listing:
            try:
                for candidate in json.loads(listing):
                    haystack = (candidate["title"] + candidate["headRefName"]).lower()
                    if slug in haystack:
                        pr = candidate["number"]
                        break
            except Exception:
                pass

    config = {"slug": slug, "display_name": display, "pr": pr, "author": me,
              "repo": DEFAULT_REPO, "watch_hub": True}
    with open(CONFIG_PATH, "w") as handle:
        json.dump(config, handle, indent=2)
        handle.write("\n")

    print(f"wrote {CONFIG_PATH}")
    print(json.dumps(config, indent=2))
    if pr is None:
        print("\nNo Plugin Hub PR found yet. Set \"pr\" by hand once it is open,")
        print("or re-run --init after opening it. The live-on-hub check works")
        print("without a PR number.")


# ------------------------------------------------------------------ gather

def load_config():
    if not os.path.exists(CONFIG_PATH):
        sys.exit(f"no {CONFIG_PATH} — run: hub-watch.py --init")
    return json.load(open(CONFIG_PATH))


def hub_status(slug):
    """(is_live, installs, client_version). Unknown is None, never False."""
    version = get_text(VERSION_URL)
    if not version:
        return None, None, None
    index = get_json(INDEX_URL.format(version=version))
    if index is None:
        return None, None, version
    return slug in index, index.get(slug), version


def pr_status(pr, author, repo=DEFAULT_REPO):
    if not pr:
        return None
    raw = run(["gh", "pr", "view", str(pr), "--repo", repo, "--json",
               "state,isDraft,mergedAt,reviewDecision,comments,reviews,"
               "statusCheckRollup,labels,assignees,reviewRequests,url,createdAt"])
    if not raw:
        return None
    try:
        d = json.loads(raw)
    except ValueError:
        return None

    # Your own comments are not news. Bot comments are kept: they carry the
    # check results and the reviewer routing.
    def others(items):
        return [i for i in (items or [])
                if (i.get("author") or {}).get("login") != author]

    return {
        "url": d.get("url"),
        "created": d.get("createdAt"),
        "state": d.get("state"),
        "merged": bool(d.get("mergedAt")),
        "merged_at": d.get("mergedAt"),
        "decision": d.get("reviewDecision"),
        "n_comments": len(others(d.get("comments"))),
        "n_reviews": len(others(d.get("reviews"))),
        "checks": {c.get("name") or c.get("context"):
                   (c.get("conclusion") or c.get("state"))
                   for c in (d.get("statusCheckRollup") or [])},
        "labels": sorted(l["name"] for l in (d.get("labels") or [])),
        "reviewers": sorted(r["login"] for r in (d.get("reviewRequests") or [])),
        "assignees": sorted(a["login"] for a in (d.get("assignees") or [])),
    }


def describe(config, pr, live, installs):
    name = config.get("display_name", config["slug"])
    repo = config.get("repo", DEFAULT_REPO)
    lines = []
    if pr:
        deadline = " ** waiting for author: 7-day auto-close clock **" \
            if DEADLINE_LABEL in pr["labels"] else ""
        lines.append(
            f"{repo}#{config['pr']}: {pr['state']}"
            + (" MERGED" if pr["merged"] else "")
            + f" | decision {pr['decision']}"
            + f" | labels {', '.join(pr['labels']) or 'none'}"
            + f" | reviewers {', '.join(pr['reviewers']) or 'none'}"
            + f" | {pr['n_comments']} comments, {pr['n_reviews']} reviews"
            + (" | checks " + ", ".join(f"{k}={v}" for k, v in pr["checks"].items())
               if pr["checks"] else "")
            + deadline)
    else:
        lines.append("PR: not set in .hubwatch.json")

    if not config.get("watch_hub", True):
        pass
    elif live is None:
        lines.append("Hub: could not reach the index")
    elif live:
        lines.append(f"Hub: {name} is LIVE — {installs} installs")
    else:
        lines.append(f"Hub: {config['slug']} is not in the index yet")
    return "\n".join(lines)


# -------------------------------------------------------------------- main

def main():
    if INIT:
        do_init()
        return

    config = load_config()
    slug = config["slug"]
    name = config.get("display_name", slug)

    if TEST_NOTIFY:
        # Prove the alert path works before trusting its silence. A watcher
        # that cannot notify is indistinguishable from a watcher with nothing
        # to report, and you find out which one you have at the worst moment.
        notify(f"{name}: test", "hub-watch notifications are working.",
               "https://github.com/runelite/plugin-hub")
        print("sent a test notification")
        return

    repo = config.get("repo", DEFAULT_REPO)
    if config.get("watch_hub", True):
        live, installs = hub_status(slug)[:2]
    else:
        live, installs = None, None
    pr = pr_status(config.get("pr"), config.get("author", ""), repo)

    if PEEK:
        print(describe(config, pr, live, installs))
        return

    now = {"live": live, "installs": installs}
    if pr:
        now.update({k: pr[k] for k in
                    ("state", "merged", "decision", "n_comments", "n_reviews",
                     "checks", "labels", "reviewers", "assignees")})

    # A failed fetch must never look like a change. Staying quiet and trying
    # again is always better than crying wolf about a flaky network.
    if live is None and pr is None:
        return

    before = None
    if os.path.exists(STATE_PATH):
        try:
            before = json.load(open(STATE_PATH))
        except ValueError:
            before = None

    with open(STATE_PATH, "w") as handle:
        json.dump(now, handle, indent=2)

    if before is None:
        # Baseline run: record and say nothing, so a backlog of existing bot
        # comments does not arrive as a burst of alerts.
        return
    if before == now:
        return

    # ------------------------------------------------ what actually changed

    url = pr["url"] if pr else f"https://github.com/{repo}"

    if now.get("live") and not before.get("live"):
        notify(f"{name} is LIVE on the Plugin Hub",
               f"Installable now. {installs} installs.",
               f"https://runelite.net/plugin-hub/{config.get('author', '')}")
    elif now.get("merged") and not before.get("merged"):
        notify(f"{name}: PR merged",
               "Merged into plugin-hub. Not installable until it appears in "
               "the Hub index — still watching.", url)
    elif DEADLINE_LABEL in now.get("labels", []) and \
            DEADLINE_LABEL not in before.get("labels", []):
        notify(f"{name}: changes requested",
               f"'{DEADLINE_LABEL}' was applied. The PR auto-closes in 7 days.", url)
    elif now.get("state") == "CLOSED" and before.get("state") != "CLOSED":
        notify(f"{name}: PR closed", "Closed without merging.", url)
    elif now.get("n_comments", 0) > before.get("n_comments", 0) or \
            now.get("n_reviews", 0) > before.get("n_reviews", 0):
        notify(f"{name}: new activity",
               f"{now.get('n_comments', 0)} comments, "
               f"{now.get('n_reviews', 0)} reviews on the PR.", url)
    elif now.get("installs") != before.get("installs") and before.get("live"):
        # Install counts move every day and are not worth a popup.
        pass
    else:
        notify(f"{name}: PR changed", describe(config, pr, live, installs), url)

    print(describe(config, pr, live, installs))


if __name__ == "__main__":
    main()
