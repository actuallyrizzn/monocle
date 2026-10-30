"""Pytest fixtures for monocle-bridge tests."""
import sys
from pathlib import Path

import pytest

# Add project root to path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


@pytest.fixture(autouse=True)
def reset_server_globals():
    """Reset server globals before each test to avoid cross-test pollution."""
    import server as server_mod
    server_mod.bridge_ws = None
    server_mod.cli_ws = None
    yield
    server_mod.bridge_ws = None
    server_mod.cli_ws = None


@pytest.fixture
def bridge_dir():
    """Path to monocle-bridge project root."""
    return PROJECT_ROOT
