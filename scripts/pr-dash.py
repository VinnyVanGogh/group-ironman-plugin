#!/usr/bin/env python3
"""A terminal dashboard for this repo's RuneLite Plugin Hub submission.

Read-only. It never writes .hubwatch.state.json, so checking here can never
swallow a change the background watcher has not notified you about yet.

    python3 scripts/pr-dash.py          # reads .hubwatch.json
    python3 scripts/pr-dash.py 16050    # override the PR number

Keys: r refresh now, q quit.

Needs textual and rich:  pip install textual
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

from rich.markup import escape
from rich.text import Text
from textual import work
from textual.app import App, ComposeResult
from textual.containers import Horizontal
from textual.widgets import Footer, Header, RichLog, Static

REPO = "runelite/plugin-hub"
VERSION_URL = f"https://raw.githubusercontent.com/{REPO}/master/runelite.version"
INDEX_URL = "https://api.runelite.net/runelite-{version}/pluginhub"

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"],
                      capture_output=True, text=True).stdout.strip() or os.getcwd()
try:
    CONFIG = json.load(open(os.path.join(ROOT, ".hubwatch.json")))
except (OSError, ValueError):
    sys.exit("no .hubwatch.json — run: scripts/hub-watch.py --init")

SLUG = CONFIG["slug"]
NAME = CONFIG.get("display_name", SLUG)
ME = CONFIG.get("author", "")
PR = sys.argv[1] if len(sys.argv) > 1 else str(CONFIG.get("pr") or "")

REFRESH_SECONDS = 30 * 60
DEADLINE_LABEL = "waiting for author"
STALE_DAYS = 7  # .github/workflows/stale.yml: days-before-pr-close is 0

VIOLET = "#8B3AF0"
MAGENTA = "#D633D0"
PINK = "#FF2D7D"
GREEN = "#3ED598"
DIM = "#8B8B9A"


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


def hub_status():
    """Is the plugin installable right now, and by how many people.

    Merged is not live: a merged PR still has to build on the Hub's own
    infrastructure. The Hub's index is the only thing that proves it did.
    """
    version = get_text(VERSION_URL)
    if not version:
        return {"live": None, "installs": None, "version": None}
    index = get_json(INDEX_URL.format(version=version))
    if index is None:
        return {"live": None, "installs": None, "version": version}
    return {"live": SLUG in index, "installs": index.get(SLUG), "version": version}


def gh(args, timeout=90):
    """Run a gh command, returning parsed JSON or None. Never raises."""
    try:
        out = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        if out.returncode != 0:
            return None
        return json.loads(out.stdout)
    except Exception:
        return None


def parse_ts(value):
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def humanise(delta):
    """A timedelta as '2d 4h' or '31m'."""
    total = int(delta.total_seconds())
    if total < 0:
        total = 0
    days, rem = divmod(total, 86400)
    hours, rem = divmod(rem, 3600)
    minutes = rem // 60
    if days:
        return f"{days}d {hours}h"
    if hours:
        return f"{hours}h {minutes}m"
    return f"{minutes}m"


def fetch():
    """Everything the dashboard shows, in one blocking call. Returns a dict."""
    hub = hub_status()
    if not PR:
        # The live check stands on its own: a plugin can be watched before a
        # PR number is known, and after the PR stops being the interesting part.
        return {"error": None, "pr": False, "hub": hub, "title": NAME,
                "state": "no PR set", "labels": [], "merged": None,
                "created": None, "updated": None, "draft": False,
                "decision": "-", "reviewers": [], "assignees": [],
                "checks": [], "comments": [], "reviews": [],
                "deadline": None, "queue": None, "url": ""}

    data = gh(["gh", "pr", "view", PR, "--repo", REPO, "--json",
               "number,title,state,isDraft,createdAt,updatedAt,mergedAt,"
               "reviewDecision,labels,assignees,reviewRequests,comments,reviews,"
               "statusCheckRollup,url"])
    if data is None:
        return {"error": "gh call failed — network, auth, or rate limit", "hub": hub}

    labels = [l["name"] for l in (data.get("labels") or [])]
    result = {
        "error": None,
        "pr": True,
        "hub": hub,
        "number": data.get("number"),
        "title": data.get("title"),
        "state": data.get("state"),
        "draft": data.get("isDraft"),
        "url": data.get("url"),
        "created": parse_ts(data.get("createdAt")),
        "updated": parse_ts(data.get("updatedAt")),
        "merged": parse_ts(data.get("mergedAt")),
        "decision": data.get("reviewDecision") or "none yet",
        "labels": labels,
        "reviewers": [r["login"] for r in (data.get("reviewRequests") or [])],
        "assignees": [a["login"] for a in (data.get("assignees") or [])],
        "checks": [(c.get("name") or c.get("context"),
                    c.get("conclusion") or c.get("state"))
                   for c in (data.get("statusCheckRollup") or [])],
        "comments": [(parse_ts(c["createdAt"]), c["author"]["login"], c.get("body") or "")
                     for c in (data.get("comments") or [])],
        "reviews": [(parse_ts(r.get("submittedAt")), r["author"]["login"],
                     r.get("state"), r.get("body") or "")
                    for r in (data.get("reviews") or [])],
        "deadline": None,
        "queue": None,
    }

    # Only pay for the timeline when a clock is actually running.
    if DEADLINE_LABEL in labels:
        events = gh(["gh", "api", f"repos/{REPO}/issues/{PR}/timeline",
                     "--paginate"]) or []
        labelled = [parse_ts(e.get("created_at")) for e in events
                    if e.get("event") == "labeled"
                    and (e.get("label") or {}).get("name") == DEADLINE_LABEL]
        if labelled:
            result["deadline"] = max(labelled) + timedelta(days=STALE_DAYS)

    # Queue context: how much moved while this sat there.
    merged = gh(["gh", "pr", "list", "--repo", REPO, "--state", "merged",
                 "--limit", "100", "--json", "number,title,mergedAt"])
    if merged is not None and result["created"]:
        since = [p for p in merged
                 if parse_ts(p["mergedAt"]) and parse_ts(p["mergedAt"]) > result["created"]]
        overtook = [p for p in since
                    if p["number"] > (result["number"] or 0)
                    and p["title"].lower().startswith(("add", "create", "new plugin"))]
        result["queue"] = {"merged_since": len(since), "overtook": len(overtook)}

    return result


class PRDash(App):
    """Live status for one Plugin Hub submission."""

    TITLE = f"{NAME} · Plugin Hub" + (f" PR #{PR}" if PR else "")
    BINDINGS = [("r", "refresh", "Refresh now"), ("q", "quit", "Quit")]

    CSS = f"""
    Screen {{ background: #12101a; }}
    #banner {{
        height: auto; padding: 1 2; margin: 0 0 1 0;
        background: #1c1730; color: {VIOLET}; border: round {VIOLET};
    }}
    #banner.alert {{ color: {PINK}; border: heavy {PINK}; background: #2a1020; }}
    #cols {{ height: 1fr; }}
    #status {{
        width: 2fr; padding: 1 2; margin: 0 1 0 0;
        background: #1a1626; border: round {MAGENTA};
    }}
    #queue {{
        width: 1fr; padding: 1 2;
        background: #1a1626; border: round {DIM};
    }}
    #activity {{
        height: 14; margin: 1 0 0 0; padding: 0 1;
        background: #16121f; border: round {DIM};
    }}
    """

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)
        yield Static(id="banner")
        with Horizontal(id="cols"):
            yield Static(id="status")
            yield Static(id="queue")
        yield RichLog(id="activity", wrap=True, markup=True, highlight=False)
        yield Footer()

    def on_mount(self) -> None:
        self.seconds_left = 0
        self.last = None          # most recent payload, for live re-rendering
        self.fetching = False
        self.query_one("#banner", Static).update(
            Text("loading…", style=VIOLET))
        self.set_interval(1, self.tick)
        self.refresh_data()

    def tick(self) -> None:
        # Ages and deadlines are recomputed every second so the panel is alive
        # between fetches, rather than frozen at whatever the last poll said.
        if self.last:
            self.query_one("#banner", Static).update(
                Text(self.banner_text(self.last), style="bold"))

        if self.seconds_left > 0:
            self.seconds_left -= 1
        elif not self.fetching:
            # The countdown reaching zero is what triggers the refetch. Reset it
            # before starting so this branch cannot fire again every tick while
            # the worker is in flight.
            self.seconds_left = REFRESH_SECONDS
            self.refresh_data()

        mins, secs = divmod(max(self.seconds_left, 0), 60)
        state = "refreshing…" if self.fetching else f"next refresh in {mins:02d}:{secs:02d}"
        self.sub_title = f"{state}  ·  r to refresh now"

    def action_refresh(self) -> None:
        self.refresh_data()

    @work(exclusive=True, thread=True)
    def refresh_data(self) -> None:
        self.fetching = True
        try:
            data = fetch()
        finally:
            self.fetching = False
        self.call_from_thread(self.render_data, data)

    def banner_text(self, d) -> str:
        """The headline, recomputed against the clock rather than cached."""
        now = datetime.now(timezone.utc)
        hub = d.get("hub") or {}
        if hub.get("live"):
            installs = hub.get("installs")
            count = f"{installs} installs" if installs is not None else "installable"
            return f"★ LIVE ON THE PLUGIN HUB  ·  {NAME}  ·  {count}"
        if d["merged"]:
            return f"✓ MERGED  ·  not in the Hub index yet  ·  {d['title']}"
        if not d.get("pr"):
            return f"● {NAME}  ·  no PR yet  ·  not on the Hub"
        if d["state"] != "OPEN":
            return f"✗ {d['state']}  ·  {d['title']}"
        if DEADLINE_LABEL in d["labels"]:
            left = humanise(d["deadline"] - now) if d["deadline"] else "unknown time"
            return (f"⚠  '{DEADLINE_LABEL}' IS SET  ·  auto-closes in {left}"
                    f"  ·  push a new commit hash")
        age = humanise(now - d["created"]) if d["created"] else "?"
        return f"● OPEN  ·  waiting {age}  ·  {d['title']}"

    def render_data(self, d) -> None:
        self.seconds_left = REFRESH_SECONDS
        banner = self.query_one("#banner", Static)
        status = self.query_one("#status", Static)
        queue = self.query_one("#queue", Static)
        log = self.query_one("#activity", RichLog)
        stamp = datetime.now().strftime("%H:%M:%S")

        if d.get("error"):
            banner.update(Text(f"⚠  {d['error']}", style=PINK))
            log.write(f"[{DIM}]{stamp}  fetch failed, keeping last known state[/]")
            return

        now = datetime.now(timezone.utc)
        self.last = d
        alert = DEADLINE_LABEL in (d.get("labels") or [])
        banner.set_class(alert, "alert")
        banner.update(Text(self.banner_text(d), style="bold"))

        def row(label, value, colour=MAGENTA):
            return f"[{DIM}]{label:<12}[/] [{colour}]{escape(str(value))}[/]"

        labels = ", ".join(d["labels"]) or "none"
        hub = d.get("hub") or {}
        if hub.get("live"):
            hub_line = row("hub", f"LIVE — {hub.get('installs')} installs", GREEN)
        elif hub.get("live") is None:
            hub_line = row("hub", "index unreachable", DIM)
        else:
            hub_line = row("hub", "not published yet", DIM)

        lines = [
            hub_line,
            row("state", d["state"] + (" (draft)" if d["draft"] else "")),
            row("decision", d["decision"]),
            row("labels", labels, PINK if alert else MAGENTA),
            row("reviewers", ", ".join(d["reviewers"]) or "none assigned",
                MAGENTA if d["reviewers"] else DIM),
            row("assignees", ", ".join(d["assignees"]) or "none",
                MAGENTA if d["assignees"] else DIM),
            row("opened", d["created"].strftime("%Y-%m-%d %H:%M UTC") if d["created"] else "?"),
            row("last event", (humanise(now - d["updated"]) + " ago") if d["updated"] else "?"),
            "",
            f"[{DIM}]checks[/]",
        ]
        for name, verdict in d["checks"]:
            tone = {"SUCCESS": VIOLET, "SKIPPED": DIM}.get(verdict, PINK)
            lines.append(f"  [{tone}]{escape(str(verdict)):<9}[/] [{DIM}]{escape(name)}[/]")
        lines.append("")
        lines.append(f"[{DIM}]{escape(d['url'] or '')}[/]")
        status.update("\n".join(lines))

        q = d.get("queue")
        qlines = [f"[{DIM}]queue context[/]", ""]
        if q:
            qlines += [
                f"[{VIOLET}]{q['merged_since']}[/] [{DIM}]PRs merged since[/]",
                f"[{DIM}]yours was opened[/]",
                "",
                f"[{MAGENTA}]{q['overtook']}[/] [{DIM}]newer new-plugin[/]",
                f"[{DIM}]submissions merged[/]",
                f"[{DIM}]ahead of yours[/]",
                "",
                f"[{DIM}]Being overtaken by[/]",
                f"[{DIM}]simpler plugins is[/]",
                f"[{DIM}]the normal shape.[/]",
                f"[{DIM}]The signal to watch[/]",
                f"[{DIM}]is a label or a[/]",
                f"[{DIM}]human comment.[/]",
            ]
        else:
            qlines.append(f"[{DIM}]unavailable[/]")
        queue.update("\n".join(qlines))

        log.clear()
        events = [(ts, who, "comment", body) for ts, who, body in d["comments"]]
        events += [(ts, who, f"review {state}", body)
                   for ts, who, state, body in d["reviews"]]
        events = [e for e in events if e[0]]
        for ts, who, kind, body in sorted(events, key=lambda e: e[0]):
            tone = DIM if who == ME else MAGENTA
            first = " ".join(body.split())[:160] or "(no body)"
            log.write(f"[{DIM}]{ts.strftime('%m-%d %H:%M')}[/] "
                      f"[{tone}]{escape(who)}[/] [{DIM}]{kind}[/]")
            log.write(f"    [{DIM}]{escape(first)}[/]")
        log.write(f"[{DIM}]— refreshed {stamp} —[/]")


if __name__ == "__main__":
    PRDash().run()
