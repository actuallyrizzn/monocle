# Architecture

## Overview

Monocle Bridge lets you control a [Brilliant Monocle](https://docs.brilliant.xyz/monocle/monocle/) from a proot (or other Linux) CLI by bridging to the only supported interface: **Web Bluetooth** in a browser. The bridge runs a small server in proot and an HTML page in Chrome; the CLI talks to the server, and the page talks to the Monocle over BLE.

## Components

```
┌─────────────────────────────────────────────────────────────────────────┐
│  proot (e.g. Ubuntu in Termux on Android)                                │
│  ┌─────────────────┐     WebSocket (127.0.0.1:8766)                      │
│  │  monocle-cli     │ ◄──────────────────────────────┐                   │
│  │  (Python)        │                                │                   │
│  └────────┬─────────┘                                │                   │
│           │                                          │                   │
│           │  TCP                                     │  relay            │
│           ▼                                          │                   │
│  ┌─────────────────┐     HTTP (127.0.0.1:8765)       │                   │
│  │  server.py       │ ◄───────────────────────────────┼───────────────────┼──► Chrome
│  │  - HTTP server   │     serves bridge.html          │  WebSocket        │    (Android)
│  │  - WS relay      │                                │                   │
│  └─────────────────┘                                 │                   │
└──────────────────────────────────────────────────────┼───────────────────┘
                                                       │
                        ┌──────────────────────────────┘
                        │
                        ▼
              ┌─────────────────┐     Web Bluetooth (BLE)
              │  bridge.html    │ ◄──────────────────────────────►  Monocle
              │  (in Chrome)    │     Nordic UART Service            (device)
              └─────────────────┘
```

### 1. server.py (proot)

- **HTTP (port 8765):** Serves `bridge.html` so Chrome can load it from `http://127.0.0.1:8765`.
- **WebSocket (port 8766):** Relay between two clients:
  - **Bridge client:** The loaded `bridge.html` page (one tab).
  - **CLI client:** `monocle-cli.py` (or any client speaking the same protocol).

Messages from one client are forwarded to the other; the server does not interpret payloads.

### 2. bridge.html (Chrome)

- Connects to the WebSocket server at `ws://127.0.0.1:8766` (or host+1 if loaded from another port).
- Registers as the **bridge** client.
- Uses the **Web Bluetooth** API to:
  - Discover and connect to the Monocle (Nordic UART Service).
  - Send REPL input to the device and receive REPL output.
- Translates high-level commands from the CLI (e.g. `connect`, `repl`) into BLE operations and sends responses back over the WebSocket.

### 3. monocle-cli.py (proot)

- Connects to the WebSocket server as the **CLI** client.
- Sends JSON commands (`connect`, `repl` with code) and prints responses.
- Invoked from the shell: `monocle-cli connect`, `monocle-cli repl "1+1"`, etc.

### 4. Monocle (hardware)

- Runs MicroPython; exposes a REPL over BLE via the **Nordic UART Service (NUS)**.
- UUIDs used by the bridge match those used by Brilliant’s official tooling (e.g. AR Studio).

## Data flow

1. **CLI → Monocle (REPL):**  
   CLI sends `{"type":"repl","code":"1+1"}` over WebSocket → server relays to bridge page → bridge writes to NUS RX characteristic → Monocle executes and sends result on NUS TX → bridge reads TX, sends `{"type":"repl_response","data":"2"}` → server relays to CLI → CLI prints `2`.

2. **Connect:**  
   CLI sends `{"type":"connect"}` → bridge runs `navigator.bluetooth.requestDevice` and connects to the Monocle’s GATT service → bridge sends `{"type":"connected","ok":true|false}` → CLI prints success or failure.

## Network assumptions

- The **same Android device** runs:
  - proot (where `server.py` and `monocle-cli` run),
  - Chrome (where `bridge.html` is loaded).
- proot shares the host’s network namespace, so `127.0.0.1` in proot is the Android device’s loopback.
- Chrome can therefore reach `http://127.0.0.1:8765` and `ws://127.0.0.1:8766` served from proot.

## File layout

```
monocle-bridge/
├── server.py        # HTTP + WebSocket relay
├── bridge.html      # Web Bluetooth bridge (served by server)
├── monocle-cli.py   # CLI client
├── tests/           # Test suite
├── docs/            # This documentation
├── LICENSE          # AGPLv3 (code)
└── LICENSE.markdown # CC BY-SA 4.0 (non-code)
```
