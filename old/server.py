#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
# monocle-bridge - Bridge for Brilliant Monocle (proot CLI → Web Bluetooth)
# Copyright (C) 2025 actuallyrizzn
"""
Monocle Bridge Server - relays between CLI (proot) and Web Bluetooth (Chrome).
Run in proot. Then open http://127.0.0.1:8765 in Chrome on the same Android device.
"""
import asyncio
import json
import sys
from pathlib import Path

try:
    import websockets
except ImportError:
    print("Install: pip install websockets")
    sys.exit(1)

PORT = 8765
BRIDGE_DIR = Path(__file__).resolve().parent
bridge_ws = None
cli_ws = None


async def relay(websocket, path=None):
    global bridge_ws, cli_ws
    role = None

    try:
        async for message in websocket:
            data = json.loads(message) if isinstance(message, str) else message

            if data.get("role") == "bridge":
                role = "bridge"
                bridge_ws = websocket
                await websocket.send(json.dumps({"type": "registered", "role": "bridge"}))
                continue
            elif data.get("role") == "cli":
                role = "cli"
                cli_ws = websocket
                await websocket.send(json.dumps({"type": "registered", "role": "cli"}))
                continue

            # Relay: forward to the other client
            other = cli_ws if websocket == bridge_ws else bridge_ws
            if other and getattr(other, "open", True):
                await other.send(message)
    except Exception:
        pass
    finally:
        if role == "bridge":
            bridge_ws = None
        elif role == "cli":
            cli_ws = None


async def http_handler(reader, writer):
    data = await reader.read(2048)
    req = data.decode().split("\r\n", 1)[0]
    path = req.split()[1] if " " in req else "/"
    if path == "/" or path == "/bridge.html":
        html = (BRIDGE_DIR / "bridge.html").read_text()
        writer.write(b"HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n")
        writer.write(html.encode())
    else:
        writer.write(b"HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n")
    await writer.drain()
    writer.close()


async def main():
    http_server = await asyncio.start_server(http_handler, "0.0.0.0", PORT)
    ws_server = await websockets.serve(relay, "0.0.0.0", PORT + 1, ping_interval=20, ping_timeout=10)
    print(f"Monocle bridge: http://127.0.0.1:{PORT}  ws://127.0.0.1:{PORT+1}", flush=True)
    print("Open the URL in Chrome on this device, then run: monocle-cli connect", flush=True)
    await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
