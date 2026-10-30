"""Unit and integration tests for server.py."""
import asyncio
import json
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
import websockets

# Import after path setup
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import server


@pytest.mark.asyncio
async def test_relay_registers_bridge():
    """Bridge client receives registered confirmation."""
    mock_ws = AsyncMock()
    mock_ws.open = True
    mock_ws.send = AsyncMock()

    async def mock_iter():
        yield json.dumps({"role": "bridge"})
        await asyncio.sleep(100)

    mock_ws.__aiter__ = lambda self: mock_iter()

    task = asyncio.create_task(server.relay(mock_ws, "/"))
    await asyncio.sleep(0.15)
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass

    mock_ws.send.assert_called_once()
    sent = json.loads(mock_ws.send.call_args[0][0])
    assert sent["type"] == "registered"
    assert sent["role"] == "bridge"


@pytest.mark.asyncio
async def test_relay_registers_cli():
    """CLI client receives registered confirmation."""
    mock_ws = AsyncMock()
    mock_ws.open = True
    mock_ws.send = AsyncMock()

    async def mock_iter():
        yield json.dumps({"role": "cli"})
        await asyncio.sleep(100)

    mock_ws.__aiter__ = lambda self: mock_iter()

    task = asyncio.create_task(server.relay(mock_ws, "/"))
    await asyncio.sleep(0.15)
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass

    mock_ws.send.assert_called_once()
    sent = json.loads(mock_ws.send.call_args[0][0])
    assert sent["type"] == "registered"
    assert sent["role"] == "cli"


@pytest.mark.asyncio
async def test_relay_forwards_to_other_client():
    """When bridge sends a message, it is forwarded to CLI."""
    # This is covered by integration test - unit test verifies the forward logic
    # by checking that a non-role message triggers send to the other client
    other_ws = AsyncMock()
    other_ws.open = True
    other_ws.send = AsyncMock()

    mock_ws = AsyncMock()
    mock_ws.open = True
    mock_ws.send = AsyncMock()

    server.bridge_ws = mock_ws
    server.cli_ws = other_ws

    async def mock_iter():
        yield json.dumps({"role": "bridge"})
        yield json.dumps({"type": "repl_response", "data": "42"})
        await asyncio.sleep(100)

    mock_ws.__aiter__ = lambda self: mock_iter()

    task = asyncio.create_task(server.relay(mock_ws, "/"))
    await asyncio.sleep(0.25)
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass

    # First call: registered. Second: forward to other (other_ws.send)
    assert other_ws.send.call_count >= 1
    forwarded = json.loads(other_ws.send.call_args_list[-1][0][0])
    assert forwarded["type"] == "repl_response"
    assert forwarded["data"] == "42"


@pytest.mark.asyncio
async def test_http_handler_serves_bridge_html():
    """HTTP handler returns bridge.html for / and /bridge.html."""
    reader = AsyncMock()
    writer = MagicMock()
    writer.write = MagicMock()
    writer.drain = AsyncMock()
    writer.close = MagicMock()

    for path in ["/", "/bridge.html"]:
        reader.read = AsyncMock(return_value=f"GET {path} HTTP/1.1\r\nHost: localhost\r\n\r\n".encode())
        writer.reset_mock()
        writer.write = MagicMock()
        writer.drain = AsyncMock()
        writer.close = MagicMock()

        await server.http_handler(reader, writer)

        assert b"HTTP/1.1 200 OK" in writer.write.call_args_list[0][0][0]
        assert b"Content-Type: text/html" in writer.write.call_args_list[0][0][0]
        assert b"Monocle Bridge" in writer.write.call_args_list[1][0][0]
        writer.close.assert_called_once()


@pytest.mark.asyncio
async def test_http_handler_404_for_unknown_path():
    """HTTP handler returns 404 for unknown paths."""
    reader = AsyncMock()
    reader.read = AsyncMock(return_value=b"GET /nonexistent HTTP/1.1\r\nHost: localhost\r\n\r\n")
    writer = MagicMock()
    writer.write = MagicMock()
    writer.drain = AsyncMock()
    writer.close = MagicMock()

    await server.http_handler(reader, writer)

    assert b"404 Not Found" in writer.write.call_args[0][0]
    writer.close.assert_called_once()


@pytest.mark.asyncio
async def test_http_handler_malformed_request():
    """HTTP handler handles malformed request without crashing."""
    reader = AsyncMock()
    reader.read = AsyncMock(return_value=b"garbage\r\n")
    writer = MagicMock()
    writer.write = MagicMock()
    writer.drain = AsyncMock()
    writer.close = MagicMock()

    await server.http_handler(reader, writer)

    # Should not raise; path extraction may get "garbage" or "/"
    writer.write.assert_called()
    writer.close.assert_called_once()


@pytest.mark.asyncio
async def test_relay_cleans_up_on_disconnect():
    """Relay clears bridge_ws when bridge disconnects."""
    mock_ws = AsyncMock()
    mock_ws.open = True
    mock_ws.send = AsyncMock()

    async def mock_iter():
        yield json.dumps({"role": "bridge"})
        raise websockets.exceptions.ConnectionClosed(1000, "test")

    mock_ws.__aiter__ = lambda self: mock_iter()

    await server.relay(mock_ws, "/")

    assert server.bridge_ws is None


@pytest.mark.asyncio
async def test_relay_handles_generic_exception():
    """Relay catches exceptions and cleans up."""
    mock_ws = AsyncMock()
    mock_ws.open = True
    mock_ws.send = AsyncMock()

    async def mock_iter():
        yield json.dumps({"role": "bridge"})
        raise ValueError("test error")

    mock_ws.__aiter__ = lambda self: mock_iter()

    await server.relay(mock_ws, "/")

    assert server.bridge_ws is None
