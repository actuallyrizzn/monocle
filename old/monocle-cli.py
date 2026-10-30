#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
# monocle-bridge - Bridge for Brilliant Monocle (proot CLI → Web Bluetooth)
# Copyright (C) 2025 actuallyrizzn
"""
Monocle CLI - connect to Monocle via the bridge (Chrome + Web Bluetooth).
Requires: bridge server running, bridge.html open in Chrome on same device.
"""
import asyncio
import json
import sys

try:
    import websockets
except ImportError:
    print("Install: pip install websockets")
    sys.exit(1)

WS_URL = "ws://127.0.0.1:8766"
pending = asyncio.Queue()


async def cli():
    async with websockets.connect(WS_URL, ping_interval=20, ping_timeout=10) as ws:
        await ws.send(json.dumps({"role": "cli"}))
        reg = json.loads(await ws.recv())
        if reg.get("type") != "registered":
            print("Unexpected:", reg)
            return

        async def recv_loop():
            async for msg in ws:
                data = json.loads(msg)
                await pending.put(data)

        recv_task = asyncio.create_task(recv_loop())

        if len(sys.argv) < 2 or sys.argv[1] == "connect":
            await ws.send(json.dumps({"type": "connect"}))
            resp = await asyncio.wait_for(pending.get(), timeout=15)
            if resp.get("ok"):
                print("Connected to Monocle")
            else:
                print("Connection failed. Ensure bridge page is open and you selected the Monocle.")
            recv_task.cancel()
            return

        if sys.argv[1] == "repl" and len(sys.argv) > 2:
            code = " ".join(sys.argv[2:])
        elif sys.argv[1] == "repl":
            code = sys.stdin.read()
        else:
            code = " ".join(sys.argv[1:])

        await ws.send(json.dumps({"type": "repl", "code": code}))
        try:
            resp = await asyncio.wait_for(pending.get(), timeout=10)
            if resp.get("type") == "repl_response":
                print(resp.get("data", ""))
        except asyncio.TimeoutError:
            print("(timeout)")
        recv_task.cancel()


def main():
    try:
        asyncio.run(cli())
    except websockets.exceptions.InvalidStatusCode as e:
        print("Cannot connect to bridge. Is the server running? Open http://127.0.0.1:8765 in Chrome.")
        sys.exit(1)
    except ConnectionRefusedError:
        print("Bridge not running. Start with: python3 server.py")
        sys.exit(1)


if __name__ == "__main__":
    main()
