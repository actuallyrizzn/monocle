#!/usr/bin/env python3
"""DM Mark a Monocle JPEG on Telegram (@OttoVernalBot).

Loads ~/.ssh/otto-telegram-bot.pass. Never prints the token.
  python3 tools/otto_lens_tg_snap.py /tmp/monocle-snap.jpg
  python3 tools/otto_lens_tg_snap.py /tmp/monocle-snap.jpg --caption "From the glass"
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

PASS = Path.home() / ".ssh" / "otto-telegram-bot.pass"


def _load_pass(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        out[k.strip()] = v.strip().strip("'").strip('"')
    return out


def send_photo(path: Path, caption: str = "") -> dict:
    env = _load_pass(PASS)
    token = env.get("OTTO_TELEGRAM_BOT_TOKEN") or env.get("TELEGRAM_BOT_TOKEN")
    chat = env.get("OTTO_TELEGRAM_MARK_CHAT_ID")
    if not token or not chat:
        raise SystemExit("missing OTTO_TELEGRAM_BOT_TOKEN or OTTO_TELEGRAM_MARK_CHAT_ID")
    data = path.read_bytes()
    boundary = "----ottoMonocleSnap"
    parts = []

    def field(name: str, value: str) -> None:
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        parts.append(value.encode() + b"\r\n")

    field("chat_id", chat)
    if caption:
        field("caption", caption[:1024])
    parts.append(f"--{boundary}\r\n".encode())
    parts.append(
        f'Content-Disposition: form-data; name="photo"; filename="{path.name}"\r\n'.encode()
    )
    parts.append(b"Content-Type: image/jpeg\r\n\r\n")
    parts.append(data)
    parts.append(b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    body = b"".join(parts)
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendPhoto",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("jpeg")
    p.add_argument("--caption", default="From the Monocle.")
    args = p.parse_args()
    path = Path(args.jpeg)
    if not path.is_file():
        print(json.dumps({"ok": False, "error": "missing_file"}))
        return 2
    try:
        out = send_photo(path, args.caption)
    except urllib.error.HTTPError as exc:
        print(json.dumps({"ok": False, "http": exc.code}))
        return 1
    except urllib.error.URLError as exc:
        print(json.dumps({"ok": False, "error": str(exc.reason)}))
        return 1
    ok = bool(out.get("ok"))
    print(json.dumps({"ok": ok, "message_id": (out.get("result") or {}).get("message_id")}))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
