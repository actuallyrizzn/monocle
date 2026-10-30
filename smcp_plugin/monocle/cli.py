#!/usr/bin/env python3
"""SMCP plugin: monocle — status and send to Mark's waveguide when the flag is on.

Talks to the Moya presence HTTP API (loopback). Agents do not dial the phone.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any, Dict, Optional

PLUGIN_VERSION = "0.1.0"
DEFAULT_BASE = "http://127.0.0.1:8890"


def _base_url() -> str:
    return (
        os.environ.get("MONOCLE_PRESENCE_URL")
        or os.environ.get("MONOCLE_PRESENCE_BASE_URL")
        or DEFAULT_BASE
    ).rstrip("/")


def _api_key() -> str:
    return (
        os.environ.get("MONOCLE_PRESENCE_API_KEY")
        or os.environ.get("MONOCLE_API_KEY")
        or ""
    ).strip()


def get_plugin_description() -> Dict[str, Any]:
    return {
        "plugin": {
            "name": "monocle",
            "version": PLUGIN_VERSION,
            "description": (
                "Mark's Brilliant Monocle. status = is he wearing it (Moya flag). "
                "send = put a sentence on the waveguide. Fails cleanly if the flag is off."
            ),
        },
        "commands": [
            {
                "name": "status",
                "description": "Is the Monocle on? Last heartbeat, BLE, queue length.",
                "parameters": [],
            },
            {
                "name": "send",
                "description": (
                    "Queue text for the waveguide. Scrolls on-device if longer than one screen. "
                    "Returns monocle_off if the flag is off — not a fake success."
                ),
                "parameters": [
                    {
                        "name": "text",
                        "type": "string",
                        "description": "Full sentences for the lens.",
                        "required": True,
                        "default": None,
                    },
                    {
                        "name": "sender",
                        "type": "string",
                        "description": "Who is sending (otto, athena, rico, koda, …).",
                        "required": False,
                        "default": "agent",
                    },
                    {
                        "name": "subject",
                        "type": "string",
                        "description": "Title-bar subject: what this chat or note is about.",
                        "required": False,
                        "default": "",
                    },
                ],
            },
        ],
    }


def _request(method: str, path: str, payload: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    key = _api_key()
    if not key:
        return {
            "status": "error",
            "error": "Set MONOCLE_PRESENCE_API_KEY",
            "error_type": "misconfigured",
        }
    url = _base_url() + path
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Authorization": f"Bearer {key}",
            "X-API-Key": key,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            body = {"ok": False, "error": raw or str(exc)}
        body.setdefault("http_status", exc.code)
    except urllib.error.URLError as exc:
        return {
            "status": "error",
            "error": f"presence unreachable: {exc.reason}",
            "error_type": "connection_error",
            "on": False,
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "status": "error",
            "error": str(exc),
            "error_type": "unknown_error",
        }
    if body.get("ok") is False:
        out = {"status": "error"}
        out.update(body)
        out.setdefault("error_type", body.get("error") or "error")
        return out
    out = {"status": "success"}
    out.update(body)
    return out


def cmd_status(_: Dict[str, Any]) -> Dict[str, Any]:
    return _request("GET", "/v1/status")


def cmd_send(args: Dict[str, Any]) -> Dict[str, Any]:
    text = (args.get("text") or "").strip()
    if not text:
        return {"status": "error", "error": "missing_text", "error_type": "validation_error"}
    return _request(
        "POST",
        "/v1/send",
        {
            "text": text,
            "sender": (args.get("sender") or "agent").strip() or "agent",
            "subject": (args.get("subject") or "").strip(),
        },
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Monocle SMCP plugin")
    parser.add_argument("--describe", action="store_true")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("status")
    send = sub.add_parser("send")
    send.add_argument("--text", required=True)
    send.add_argument("--sender", default="agent")
    send.add_argument("--subject", default="")
    args = parser.parse_args()
    if args.describe:
        print(json.dumps(get_plugin_description(), indent=2))
        return 0
    if args.command == "status":
        result = cmd_status(vars(args))
    elif args.command == "send":
        result = cmd_send(vars(args))
    else:
        parser.print_help()
        return 1
    print(json.dumps(result))
    return 0 if result.get("status") == "success" else 1


if __name__ == "__main__":
    raise SystemExit(main())
