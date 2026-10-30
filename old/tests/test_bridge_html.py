"""Tests for bridge.html content and structure."""
from pathlib import Path

import pytest

BRIDGE_HTML = Path(__file__).resolve().parent.parent / "bridge.html"


def test_bridge_html_exists():
    """bridge.html exists."""
    assert BRIDGE_HTML.exists()


def test_bridge_html_contains_monocle_ble_uuids():
    """bridge.html contains Monocle BLE service UUIDs."""
    content = BRIDGE_HTML.read_text()
    assert "6e400001-b5a3-f393-e0a9-e50e24dcca9e" in content
    assert "6e400002-b5a3-f393-e0a9-e50e24dcca9e" in content
    assert "6e400003-b5a3-f393-e0a9-e50e24dcca9e" in content


def test_bridge_html_contains_websocket_logic():
    """bridge.html contains WebSocket connection logic."""
    content = BRIDGE_HTML.read_text()
    assert "WebSocket" in content
    assert "role" in content
    assert "bridge" in content


def test_bridge_html_contains_web_bluetooth():
    """bridge.html uses Web Bluetooth API."""
    content = BRIDGE_HTML.read_text()
    assert "navigator.bluetooth" in content
    assert "requestDevice" in content
