#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
CLI = os.path.join(HERE, "cli.py")


class DescribeTests(unittest.TestCase):
    def test_describe(self):
        out = subprocess.check_output([sys.executable, CLI, "--describe"], text=True)
        data = json.loads(out)
        names = [c["name"] for c in data["commands"]]
        self.assertEqual(data["plugin"]["name"], "monocle")
        self.assertEqual(names, ["status", "send"])


if __name__ == "__main__":
    unittest.main()
