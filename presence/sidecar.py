#!/usr/bin/env python3
"""Fallback phone-side loop: heartbeat Moya, pull queued text, paint the glass.

APK 0.5.0+ owns this loop. Do not run this sidecar at the same time as the APK
presence thread — they would race on pull/ack. Keep this for when the APK is
down. Fail-closed: if Moya is unreachable the flag goes stale on its own.
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.join(os.path.dirname(HERE), "tools")
sys.path.insert(0, TOOLS)
from lens_show import post_display  # noqa: E402

LENS = os.environ.get("MONOCLE_LENS_URL", "http://127.0.0.1:8767").rstrip("/")
PRESENCE = os.environ.get("MONOCLE_PRESENCE_URL", "http://127.0.0.1:8890").rstrip("/")
KEY = (
    os.environ.get("MONOCLE_PRESENCE_API_KEY")
    or os.environ.get("MONOCLE_API_KEY")
    or ""
).strip()


def http(method: str, url: str, payload=None, timeout: int = 8):
    data = None if payload is None else json.dumps(payload).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if KEY:
        headers["Authorization"] = f"Bearer {KEY}"
        headers["X-API-Key"] = KEY
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def local_ble() -> dict:
    try:
        return http("GET", LENS + "/v1/status", timeout=4)
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "ble": False, "error": str(exc)}


def once() -> dict:
    lens = local_ble()
    ble = bool(lens.get("ble"))
    hb = None
    try:
        hb = http(
            "POST",
            PRESENCE + "/v1/heartbeat",
            {"ble": ble, "device": lens.get("device") or "monocle"},
        )
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "stage": "heartbeat", "error": str(exc), "ble": ble}
    shown = []
    if ble and hb.get("on"):
        pulled = http("GET", PRESENCE + "/v1/pull")
        ids = []
        for msg in pulled.get("messages") or []:
            try:
                out = post_display(
                    str(msg.get("text") or ""),
                    sender=str(msg.get("sender") or ""),
                    subject=str(msg.get("subject") or ""),
                    url=LENS,
                )
            except Exception as exc:  # noqa: BLE001
                return {"ok": False, "stage": "display", "error": str(exc), "id": msg.get("id")}
            if out.get("ok"):
                ids.append(msg["id"])
                shown.append(msg["id"])
        if ids:
            http("POST", PRESENCE + "/v1/ack", {"ids": ids})
    return {"ok": True, "ble": ble, "on": hb.get("on"), "shown": shown}


def main() -> int:
    if not KEY:
        print("Set MONOCLE_PRESENCE_API_KEY", file=sys.stderr)
        return 2
    interval = float(os.environ.get("MONOCLE_SIDECAR_INTERVAL", "3"))
    if "--once" in sys.argv:
        print(json.dumps(once()))
        return 0
    while True:
        try:
            print(json.dumps(once()), flush=True)
        except Exception as exc:  # noqa: BLE001
            print(json.dumps({"ok": False, "error": str(exc)}), flush=True)
        time.sleep(interval)


if __name__ == "__main__":
    raise SystemExit(main())
