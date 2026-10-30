#!/usr/bin/env python3
"""Put a card on the Monocle: subject, from, scrolling body, battery.

Prefers APK /v1/display (0.4.0+ chrome). Falls back to /v1/repl with the same
layout if the installed APK ignores extra JSON fields.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request

from lens_layout import card_code

LENS_URL = "http://127.0.0.1:8767"


def post_json(path: str, payload: dict, url: str = LENS_URL, timeout: int = 20) -> dict:
    req = urllib.request.Request(
        url.rstrip("/") + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def post_repl(code: str, url: str = LENS_URL, timeout: int = 20) -> dict:
    return post_json("/v1/repl", {"code": code}, url=url, timeout=timeout)


def post_display(
    text: str,
    sender: str = "",
    subject: str = "",
    url: str = LENS_URL,
    timeout: int = 20,
) -> dict:
    try:
        return post_json(
            "/v1/display",
            {"text": text, "from": sender, "sender": sender, "subject": subject},
            url=url,
            timeout=timeout,
        )
    except urllib.error.HTTPError:
        return post_repl(card_code(text, sender=sender, subject=subject), url=url, timeout=timeout)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("text", nargs="?", help="sentences for the lens")
    parser.add_argument("--sender", default="")
    parser.add_argument("--subject", default="")
    parser.add_argument("--url", default=LENS_URL)
    args = parser.parse_args()
    text = args.text or sys.stdin.read()
    text = (text or "").strip()
    if not text:
        print("missing text", file=sys.stderr)
        return 2
    try:
        out = post_display(text, sender=args.sender, subject=args.subject, url=args.url)
    except urllib.error.URLError as exc:
        print(json.dumps({"ok": False, "error": str(exc.reason)}))
        return 1
    print(json.dumps(out))
    return 0 if out.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
