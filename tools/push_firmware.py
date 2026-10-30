#!/usr/bin/env python3
"""Stage a signed application zip on the running bridge, then optionally flash.

Does not bump the APK. Does not touch the nRF bootloader.
  python3 tools/push_firmware.py /path/to/monocle-hibernate.zip
  python3 tools/push_firmware.py /path/to/zip --flash
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request

LENS_URL = "http://127.0.0.1:8767"


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("zip")
    p.add_argument("--flash", action="store_true")
    p.add_argument("--url", default=LENS_URL)
    args = p.parse_args()
    with open(args.zip, "rb") as fh:
        data = fh.read()
    if len(data) < 1024 or data[:4] != b"PK\x03\x04":
        print(json.dumps({"ok": False, "error": "not a zip"}))
        return 2
    req = urllib.request.Request(
        args.url.rstrip("/") + "/v1/firmware/zip",
        data=data,
        headers={"Content-Type": "application/zip", "Content-Length": str(len(data))},
        method="PUT",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            staged = json.loads(resp.read().decode("utf-8"))
    except urllib.error.URLError as exc:
        print(json.dumps({"ok": False, "error": str(exc.reason)}))
        return 1
    print(json.dumps(staged))
    if not args.flash:
        return 0 if staged.get("ok") else 1
    flash = urllib.request.Request(
        args.url.rstrip("/") + "/v1/firmware",
        data=b"",
        method="POST",
    )
    with urllib.request.urlopen(flash, timeout=200) as resp:
        out = json.loads(resp.read().decode("utf-8"))
    print(json.dumps(out))
    return 0 if out.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
