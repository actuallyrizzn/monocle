#!/usr/bin/env python3
"""Put a work ping on the Monocle only if the glasses are connected.

Otto on this phone: GET /v1/status, then POST /v1/display.
If the bridge is down or ble is false, skip (exit 0). Mark is not wearing it.
Do not queue through Moya for these pings — empty-queue heartbeats stay quiet.

Exit 1 only when the lens is connected and the paint fails.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request

from lens_show import LENS_URL, post_display

TIMEOUT = 8


def status(url: str) -> dict | None:
    req = urllib.request.Request(url.rstrip("/") + "/v1/status", method="GET")
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("text", nargs="?", help="full sentences for the lens")
    parser.add_argument("--kind", default="turn-done",
                        choices=("turn-done", "task-start", "task-done"))
    parser.add_argument("--sender", default="Otto")
    parser.add_argument("--subject", default="")
    parser.add_argument("--url", default=LENS_URL)
    args = parser.parse_args()
    text = (args.text or sys.stdin.read() or "").strip()
    if not text:
        print(json.dumps({"ok": False, "skipped": True, "reason": "missing_text"}))
        return 2
    subject = args.subject.strip() or {
        "turn-done": "Turn",
        "task-start": "Start",
        "task-done": "Done",
    }[args.kind]
    st = status(args.url)
    if not st:
        print(json.dumps({"ok": True, "skipped": True, "reason": "bridge_down"}))
        return 0
    if not st.get("ble"):
        print(json.dumps({
            "ok": True,
            "skipped": True,
            "reason": "not_connected",
            "status": st.get("status"),
        }))
        return 0
    try:
        out = post_display(text, sender=args.sender, subject=subject, url=args.url)
    except urllib.error.URLError as exc:
        print(json.dumps({"ok": False, "error": str(exc.reason)}))
        return 1
    print(json.dumps(out))
    return 0 if out.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
