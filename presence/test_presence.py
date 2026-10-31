#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import threading
import unittest
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from store import PresenceStore  # noqa: E402
import server as presence_server  # noqa: E402


class FakeClock:
    def __init__(self, t: float = 1000.0) -> None:
        self.t = t

    def __call__(self) -> float:
        return self.t


class StoreTests(unittest.TestCase):
    def test_off_until_heartbeat(self):
        s = PresenceStore(stale_after=45, clock=FakeClock())
        self.assertFalse(s.status()["on"])

    def test_on_when_ble_fresh(self):
        clock = FakeClock()
        s = PresenceStore(stale_after=45, clock=clock)
        st = s.heartbeat(True, device="monocle", battery=40)
        self.assertTrue(st["on"])
        self.assertTrue(st["ble"])

    def test_off_when_ble_false(self):
        s = PresenceStore(stale_after=45, clock=FakeClock())
        s.heartbeat(False)
        self.assertFalse(s.status()["on"])

    def test_stale_is_off(self):
        clock = FakeClock(10)
        s = PresenceStore(stale_after=45, clock=clock)
        s.heartbeat(True)
        clock.t = 60
        self.assertFalse(s.status()["on"])

    def test_send_fails_when_off(self):
        s = PresenceStore(clock=FakeClock())
        out = s.send("hello")
        self.assertFalse(out["ok"])
        self.assertEqual(out["error"], "monocle_off")

    def test_send_queues_when_on(self):
        s = PresenceStore(clock=FakeClock())
        s.heartbeat(True)
        out = s.send("Order up", sender="rico")
        self.assertTrue(out["ok"])
        pulled = s.pull()
        self.assertEqual(len(pulled["messages"]), 1)
        self.assertEqual(pulled["messages"][0]["text"], "Order up")
        ack = s.ack([pulled["messages"][0]["id"]])
        self.assertEqual(ack["removed"], 1)
        self.assertEqual(s.pull()["messages"], [])

    def test_listen_flag_is_on_the_queued_card(self):
        s = PresenceStore(clock=FakeClock())
        s.heartbeat(True)
        out = s.send("Say the word", sender="athena", subject="Ask", listen=True)
        self.assertTrue(out["ok"])
        msg = s.pull()["messages"][0]
        self.assertTrue(msg["listen"])
        self.assertEqual(msg["text"], "Say the word")
        quiet = s.send("Just a status")
        self.assertTrue(quiet["ok"])
        self.assertFalse(s.pull()["messages"][1]["listen"])

    def test_empty_text(self):
        s = PresenceStore(clock=FakeClock())
        s.heartbeat(True)
        self.assertEqual(s.send("  ")["error"], "missing_text")


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.clock = FakeClock()
        self.store = PresenceStore(stale_after=45, clock=self.clock)
        self.key = "test-key"
        self.httpd = presence_server.make_server(self.store, self.key, host="127.0.0.1", port=0)
        self.port = self.httpd.server_address[1]
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()

    def call(self, method, path, payload=None, key=None):
        data = None if payload is None else json.dumps(payload).encode()
        headers = {"Content-Type": "application/json"}
        token = self.key if key is None else key
        if token:
            headers["Authorization"] = f"Bearer {token}"
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.port}{path}",
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status, json.loads(resp.read().decode())
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read().decode())

    def test_unauthorized(self):
        code, body = self.call("GET", "/v1/status", key="nope")
        self.assertEqual(code, 401)
        self.assertFalse(body["ok"])

    def test_heartbeat_send_pull(self):
        code, body = self.call("POST", "/v1/heartbeat", {"ble": True, "device": "monocle"})
        self.assertEqual(code, 200)
        self.assertTrue(body["on"])
        code, body = self.call("POST", "/v1/send", {"text": "Fryer ping", "sender": "rico"})
        self.assertEqual(code, 200)
        self.assertTrue(body["queued"])
        code, body = self.call("GET", "/v1/pull")
        self.assertEqual(body["messages"][0]["text"], "Fryer ping")
        mid = body["messages"][0]["id"]
        code, body = self.call("POST", "/v1/ack", {"ids": [mid]})
        self.assertEqual(body["removed"], 1)

    def test_send_off_is_409(self):
        code, body = self.call("POST", "/v1/send", {"text": "nope"})
        self.assertEqual(code, 409)
        self.assertEqual(body["error"], "monocle_off")


if __name__ == "__main__":
    unittest.main()
