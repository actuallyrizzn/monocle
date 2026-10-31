"""Fail-closed Monocle presence + outbound text queue.

The flag is on only when a fresh heartbeat says BLE is actually connected.
Missing or stale heartbeats are off — never “probably still wearing it.”
"""

from __future__ import annotations

import threading
import time
from typing import Any, Callable, Dict, List, Optional


class PresenceStore:
    def __init__(
        self,
        stale_after: float = 45.0,
        clock: Optional[Callable[[], float]] = None,
        max_queue: int = 32,
    ) -> None:
        if stale_after <= 0:
            raise ValueError("stale_after must be positive")
        self.stale_after = float(stale_after)
        self._clock = clock or time.time
        self.max_queue = int(max_queue)
        self._lock = threading.Lock()
        self.last_heartbeat_at: Optional[float] = None
        self.ble = False
        self.device: Optional[str] = None
        self.battery: Optional[int] = None
        self._seq = 0
        self._queue: List[Dict[str, Any]] = []

    def _now(self) -> float:
        return float(self._clock())

    def status(self) -> Dict[str, Any]:
        with self._lock:
            return self._status_locked()

    def _status_locked(self) -> Dict[str, Any]:
        now = self._now()
        age = None if self.last_heartbeat_at is None else now - self.last_heartbeat_at
        fresh = age is not None and age < self.stale_after
        on = bool(self.ble) and fresh
        return {
            "ok": True,
            "on": on,
            "ble": bool(self.ble),
            "device": self.device,
            "battery": self.battery,
            "last_heartbeat_at": self.last_heartbeat_at,
            "heartbeat_age_s": None if age is None else round(age, 3),
            "stale_after_s": self.stale_after,
            "queue_len": len(self._queue),
        }

    def heartbeat(
        self,
        ble: bool,
        device: Optional[str] = None,
        battery: Optional[int] = None,
    ) -> Dict[str, Any]:
        with self._lock:
            self.last_heartbeat_at = self._now()
            self.ble = bool(ble)
            if device is not None:
                text = str(device).strip()
                self.device = text or None
            if battery is not None:
                try:
                    self.battery = int(battery)
                except (TypeError, ValueError):
                    self.battery = None
            return self._status_locked()

    def send(
        self,
        text: str,
        sender: str = "agent",
        subject: str = "",
        listen: bool = False,
    ) -> Dict[str, Any]:
        body = (text or "").strip()
        if not body:
            return {"ok": False, "error": "missing_text", "on": self.status()["on"]}
        with self._lock:
            st = self._status_locked()
            if not st["on"]:
                return {
                    "ok": False,
                    "error": "monocle_off",
                    "on": False,
                    "queued": False,
                }
            self._seq += 1
            item = {
                "id": self._seq,
                "text": body,
                "sender": (sender or "agent").strip() or "agent",
                "subject": (subject or "").strip(),
                "listen": bool(listen),
                "created_at": self._now(),
            }
            self._queue.append(item)
            overflow = len(self._queue) - self.max_queue
            if overflow > 0:
                del self._queue[:overflow]
            return {
                "ok": True,
                "queued": True,
                "on": True,
                "id": item["id"],
                "queue_len": len(self._queue),
            }

    def pull(self) -> Dict[str, Any]:
        with self._lock:
            return {
                "ok": True,
                "on": self._status_locked()["on"],
                "messages": [dict(m) for m in self._queue],
            }

    def ack(self, ids: List[int]) -> Dict[str, Any]:
        wanted = {int(i) for i in ids if str(i).strip() != ""}
        with self._lock:
            before = len(self._queue)
            self._queue = [m for m in self._queue if m["id"] not in wanted]
            return {
                "ok": True,
                "removed": before - len(self._queue),
                "queue_len": len(self._queue),
            }
