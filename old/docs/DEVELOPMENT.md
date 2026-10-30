# Development

## Test suite

Tests live in the `tests/` directory and use **pytest** with **pytest-cov** for coverage.

### Run all tests

```bash
cd /path/to/monocle-bridge
pytest tests/ -v
```

### Run with coverage (enforce 80% minimum)

```bash
pytest tests/ --cov=. --cov-fail-under=80
```

Configuration is in `pyproject.toml`: asyncio mode, coverage options, and omit patterns for `tests/*` and `*.html`.

### What is tested

- **server.py:** HTTP handler (/, /bridge.html, 404), WebSocket relay (registration of bridge and cli, forwarding, cleanup on disconnect), compatibility with different `websockets` connection object shapes (`getattr(other, "open", True)`).
- **monocle-cli.py:** Registration, `connect` and `repl` flows, timeout and exit behavior; module is loaded via `importlib.util` so it can be patched without installing.
- **bridge.html:** Presence of Nordic UART UUIDs, Web Bluetooth usage, WebSocket URL construction.
- **Integration:** Real WebSocket server and two clients (bridge and cli) exchanging messages through the relay.

## Code layout

- `server.py` — asyncio HTTP server + websockets server; single relay loop.
- `monocle-cli.py` — async CLI using `websockets.connect`; sends JSON and prints responses.
- `bridge.html` — single file: HTML, CSS, and JavaScript (WebSocket + Web Bluetooth).

No separate front-end build step; edit `bridge.html` and reload the page in Chrome.

## Dependencies for development

- Python 3.7+
- `websockets` (runtime)
- `pytest`, `pytest-asyncio`, `pytest-cov` (dev; see `pyproject.toml`)

Install dev deps (e.g.):

```bash
pip install -e ".[dev]"
```

If the project does not define a `[dev]` extra, install explicitly:

```bash
pip install pytest pytest-asyncio pytest-cov websockets
```

## Contributing

1. Fork the repo, create a branch, make changes.
2. Add or update tests as needed; keep coverage at or above 80%.
3. Run `pytest tests/ --cov=. --cov-fail-under=80` and fix any failures.
4. Submit a pull request. By contributing, you agree that your contributions are licensed under AGPL-3.0-or-later (code) and that non-code content may be under CC BY-SA 4.0 as stated in the project.

## Licenses

- **Code:** AGPL-3.0-or-later — see [LICENSE](../LICENSE).
- **Documentation and other non-code:** CC BY-SA 4.0 — see [LICENSE.markdown](../LICENSE.markdown).
