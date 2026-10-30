"""Unit tests for monocle-cli.py."""
import asyncio
import importlib.util
import json
import sys
from io import StringIO
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

PROJECT_ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location(
    "monocle_cli", PROJECT_ROOT / "monocle-cli.py"
)
monocle_cli = importlib.util.module_from_spec(spec)
sys.modules["monocle_cli"] = monocle_cli
spec.loader.exec_module(monocle_cli)


@pytest.mark.asyncio
async def test_cli_connect_success():
    """CLI connect command prints success when bridge responds ok."""
    mock_ws = AsyncMock()
    mock_ws.recv = AsyncMock(return_value=json.dumps({"type": "registered", "role": "cli"}))
    mock_ws.send = AsyncMock()

    cm = MagicMock()
    cm.__aenter__ = AsyncMock(return_value=mock_ws)
    cm.__aexit__ = AsyncMock(return_value=None)

    with patch.object(monocle_cli, "websockets") as mock_ws_mod:
        mock_ws_mod.connect = MagicMock(return_value=cm)

        with patch("sys.argv", ["monocle-cli", "connect"]):
            with patch("sys.stdout", new_callable=StringIO) as out:
                with patch.object(monocle_cli, "pending") as mock_pending:
                    mock_pending.get = AsyncMock(
                        return_value={"type": "connected", "ok": True}
                    )
                    await monocle_cli.cli()
                assert "Connected to Monocle" in out.getvalue()


@pytest.mark.asyncio
async def test_cli_connect_failure():
    """CLI connect command prints failure when bridge responds not ok."""
    mock_ws = AsyncMock()
    mock_ws.recv = AsyncMock(return_value=json.dumps({"type": "registered", "role": "cli"}))
    mock_ws.send = AsyncMock()

    cm = MagicMock()
    cm.__aenter__ = AsyncMock(return_value=mock_ws)
    cm.__aexit__ = AsyncMock(return_value=None)

    with patch.object(monocle_cli, "websockets") as mock_ws_mod:
        mock_ws_mod.connect = MagicMock(return_value=cm)

        with patch("sys.argv", ["monocle-cli", "connect"]):
            with patch("sys.stdout", new_callable=StringIO) as out:
                with patch.object(monocle_cli, "pending") as mock_pending:
                    mock_pending.get = AsyncMock(
                        return_value={"type": "connected", "ok": False}
                    )
                    await monocle_cli.cli()
                assert "Connection failed" in out.getvalue()


@pytest.mark.asyncio
async def test_cli_repl_prints_response():
    """CLI repl command prints response data."""
    mock_ws = AsyncMock()
    mock_ws.recv = AsyncMock(return_value=json.dumps({"type": "registered", "role": "cli"}))
    mock_ws.send = AsyncMock()

    cm = MagicMock()
    cm.__aenter__ = AsyncMock(return_value=mock_ws)
    cm.__aexit__ = AsyncMock(return_value=None)

    with patch.object(monocle_cli, "websockets") as mock_ws_mod:
        mock_ws_mod.connect = MagicMock(return_value=cm)

        with patch("sys.argv", ["monocle-cli", "repl", "1+1"]):
            with patch("sys.stdout", new_callable=StringIO) as out:
                with patch.object(monocle_cli, "pending") as mock_pending:
                    mock_pending.get = AsyncMock(
                        return_value={"type": "repl_response", "data": "2"}
                    )
                    await monocle_cli.cli()
                assert "2" in out.getvalue()


@pytest.mark.asyncio
async def test_cli_repl_timeout():
    """CLI repl prints (timeout) on timeout."""
    mock_ws = AsyncMock()
    mock_ws.recv = AsyncMock(return_value=json.dumps({"type": "registered", "role": "cli"}))
    mock_ws.send = AsyncMock()

    cm = MagicMock()
    cm.__aenter__ = AsyncMock(return_value=mock_ws)
    cm.__aexit__ = AsyncMock(return_value=None)

    with patch.object(monocle_cli, "websockets") as mock_ws_mod:
        mock_ws_mod.connect = MagicMock(return_value=cm)

        with patch("sys.argv", ["monocle-cli", "repl", "sleep(100)"]):
            with patch("sys.stdout", new_callable=StringIO) as out:
                with patch.object(monocle_cli, "pending") as mock_pending:
                    mock_pending.get = AsyncMock(
                        side_effect=asyncio.TimeoutError()
                    )
                    await monocle_cli.cli()
                assert "(timeout)" in out.getvalue()


def test_main_connection_refused():
    """main() exits 1 on ConnectionRefusedError."""
    with patch("monocle_cli.asyncio.run", side_effect=ConnectionRefusedError()):
        with pytest.raises(SystemExit) as exc:
            monocle_cli.main()
        assert exc.value.code == 1


def test_main_invalid_status():
    """main() exits 1 on InvalidStatusCode."""
    import websockets.exceptions

    with patch(
        "monocle_cli.asyncio.run",
        side_effect=websockets.exceptions.InvalidStatusCode(404, "Not Found"),
    ):
        with pytest.raises(SystemExit) as exc:
            monocle_cli.main()
        assert exc.value.code == 1
