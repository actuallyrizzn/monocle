"""Integration tests - real server with WebSocket clients."""
import asyncio
import json

import pytest
import websockets

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import server


@pytest.mark.asyncio
async def test_server_relay_bridge_to_cli():
    """Full flow: bridge connects, cli connects, bridge sends, cli receives."""
    # Start HTTP and WS servers on random ports to avoid conflicts
    port_http = 18765
    port_ws = 18766

    http_server = await asyncio.start_server(
        server.http_handler, "127.0.0.1", port_http
    )
    ws_server = await websockets.serve(
        server.relay, "127.0.0.1", port_ws,
        ping_interval=20, ping_timeout=10
    )

    try:
        # Connect bridge client
        bridge = await websockets.connect(f"ws://127.0.0.1:{port_ws}")
        await bridge.send(json.dumps({"role": "bridge"}))
        reg = json.loads(await bridge.recv())
        assert reg["type"] == "registered"
        assert reg["role"] == "bridge"

        # Connect CLI client
        cli = await websockets.connect(f"ws://127.0.0.1:{port_ws}")
        await cli.send(json.dumps({"role": "cli"}))
        reg = json.loads(await cli.recv())
        assert reg["type"] == "registered"
        assert reg["role"] == "cli"

        # Bridge sends connect response (simulating BLE connect)
        await bridge.send(json.dumps({"type": "connected", "ok": True}))
        await asyncio.sleep(0.1)  # Allow relay to process
        resp = json.loads(await asyncio.wait_for(cli.recv(), timeout=3))
        assert resp["type"] == "connected"
        assert resp["ok"] is True

        # CLI sends repl, bridge would respond - we simulate bridge response
        await cli.send(json.dumps({"type": "repl", "code": "1+1"}))
        # Bridge (simulated) would get it and send repl_response
        msg = json.loads(await asyncio.wait_for(bridge.recv(), timeout=2))
        assert msg["type"] == "repl"
        assert msg["code"] == "1+1"

        await bridge.send(json.dumps({"type": "repl_response", "data": "2"}))
        resp = json.loads(await asyncio.wait_for(cli.recv(), timeout=2))
        assert resp["type"] == "repl_response"
        assert resp["data"] == "2"

        await bridge.close()
        await cli.close()
    finally:
        ws_server.close()
        await ws_server.wait_closed()
        http_server.close()
        await http_server.wait_closed()


@pytest.mark.asyncio
async def test_http_serves_bridge_html():
    """HTTP server serves bridge.html with expected content."""
    port = 18767
    srv = await asyncio.start_server(server.http_handler, "127.0.0.1", port)

    try:
        reader, writer = await asyncio.open_connection("127.0.0.1", port)
        writer.write(b"GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")
        await writer.drain()

        data = await reader.read(4096)
        writer.close()
        await writer.wait_closed()

        assert b"HTTP/1.1 200 OK" in data
        assert b"Monocle Bridge" in data
        assert b"6e400001-b5a3-f393-e0a9-e50e24dcca9e" in data
    finally:
        srv.close()
        await srv.wait_closed()
