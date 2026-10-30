#!/usr/bin/env python3
import json
import unittest
from unittest.mock import patch

import otto_lens_update as u


class SkipTests(unittest.TestCase):
    def test_bridge_down_is_skip(self):
        with patch.object(u, "status", return_value=None):
            with patch("sys.argv", ["otto_lens_update.py", "Turn closed."]):
                code = u.main()
        self.assertEqual(code, 0)

    def test_ble_false_is_skip(self):
        with patch.object(u, "status", return_value={"ok": True, "ble": False, "status": "idle"}):
            with patch("sys.argv", ["otto_lens_update.py", "--kind", "task-start", "Starting the operator."]):
                code = u.main()
        self.assertEqual(code, 0)

    def test_connected_paints(self):
        painted = {}

        def fake_display(text, sender="", subject="", url="", timeout=20):
            painted["text"] = text
            painted["subject"] = subject
            painted["sender"] = sender
            return {"ok": True, "text": text}

        with patch.object(u, "status", return_value={"ok": True, "ble": True}):
            with patch.object(u, "post_display", side_effect=fake_display):
                with patch("sys.argv", ["otto_lens_update.py", "--kind", "turn-done", "Turn closed."]):
                    code = u.main()
        self.assertEqual(code, 0)
        self.assertEqual(painted["text"], "Turn closed.")
        self.assertEqual(painted["subject"], "Turn")
        self.assertEqual(painted["sender"], "Otto")


if __name__ == "__main__":
    unittest.main()
