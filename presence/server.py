#!/usr/bin/env python3
"""Loopback HTTP for Monocle presence. Bind localhost only. Auth is Bearer / X-API-Key."""

from __future__ import annotations

import argparse
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, Optional, Tuple
from urllib.parse import urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from store import PresenceStore

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8890


def load_api_key() -> str:
    return (
        os.environ.get("MONOCLE_PRESENCE_API_KEY")
        or os.environ.get("MONOCLE_API_KEY")
        or ""
    ).strip()


def authorized(headers, expected: str) -> bool:
    if not expected:
        return False
    auth = (headers.get("Authorization") or "").strip()
    if auth.lower().startswith("bearer "):
        token = auth[7:].strip()
        if token and token == expected:
            return True
    key = (headers.get("X-API-Key") or "").strip()
    return bool(key) and key == expected


def parse_json_body(raw: bytes) -> Dict[str, Any]:
    if not raw:
        return {}
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise ValueError("invalid_json")
    if data is None:
        return {}
    if not isinstance(data, dict):
        raise ValueError("json_object_required")
    return data


class PresenceHandler(BaseHTTPRequestHandler):
    store: PresenceStore
    api_key: str

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("presence " + (fmt % args) + "\n")

    def _send(self, code: int, payload: Dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length > 0 else b""
        try:
            return parse_json_body(raw), None
        except ValueError as exc:
            return None, str(exc)

    def _guard(self) -> bool:
        if authorized(self.headers, self.api_key):
            return True
        self._send(401, {"ok": False, "error": "unauthorized"})
        return False

    def do_GET(self) -> None:  # noqa: N802
        if not self._guard():
            return
        path = urlparse(self.path).path.rstrip("/") or "/"
        if path in ("/", "/health", "/v1/health"):
            self._send(200, {"ok": True, "service": "monocle-presence"})
            return
        if path == "/v1/status":
            self._send(200, self.store.status())
            return
        if path == "/v1/pull":
            self._send(200, self.store.pull())
            return
        self._send(404, {"ok": False, "error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        if not self._guard():
            return
        path = urlparse(self.path).path.rstrip("/") or "/"
        body, err = self._read_json()
        if err:
            self._send(400, {"ok": False, "error": err})
            return
        assert body is not None
        if path == "/v1/heartbeat":
            ble = body.get("ble", False)
            if isinstance(ble, str):
                ble = ble.strip().lower() in ("1", "true", "yes", "on")
            self._send(
                200,
                self.store.heartbeat(
                    bool(ble),
                    device=body.get("device"),
                    battery=body.get("battery"),
                ),
            )
            return
        if path == "/v1/send":
            result = self.store.send(
                str(body.get("text") or ""),
                sender=str(body.get("sender") or "agent"),
                subject=str(body.get("subject") or ""),
            )
            code = 200 if result.get("ok") else (409 if result.get("error") == "monocle_off" else 400)
            self._send(code, result)
            return
        if path == "/v1/ack":
            ids = body.get("ids") or []
            if not isinstance(ids, list):
                self._send(400, {"ok": False, "error": "ids_must_be_list"})
                return
            self._send(200, self.store.ack(ids))
            return
        self._send(404, {"ok": False, "error": "not_found"})


def make_server(
    store: PresenceStore,
    api_key: str,
    host: str = DEFAULT_HOST,
    port: int = DEFAULT_PORT,
) -> ThreadingHTTPServer:
    if not api_key:
        raise ValueError("MONOCLE_PRESENCE_API_KEY is required")

    class BoundHandler(PresenceHandler):
        pass

    BoundHandler.store = store
    BoundHandler.api_key = api_key
    httpd = ThreadingHTTPServer((host, port), BoundHandler)
    return httpd


def main() -> int:
    parser = argparse.ArgumentParser(description="Monocle presence on localhost")
    parser.add_argument("--host", default=os.environ.get("MONOCLE_PRESENCE_HOST", DEFAULT_HOST))
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.environ.get("MONOCLE_PRESENCE_PORT", DEFAULT_PORT)),
    )
    parser.add_argument(
        "--stale-after",
        type=float,
        default=float(os.environ.get("MONOCLE_PRESENCE_STALE_AFTER", "45")),
    )
    args = parser.parse_args()
    key = load_api_key()
    if not key:
        print("Set MONOCLE_PRESENCE_API_KEY", file=sys.stderr)
        return 2
    store = PresenceStore(stale_after=args.stale_after)
    httpd = make_server(store, key, host=args.host, port=args.port)
    print(f"monocle-presence listening on {args.host}:{args.port}", file=sys.stderr)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
