# API reference

This document describes the WebSocket message protocol between the CLI, server, and bridge page. It is intended for developers who want to implement alternative clients or extend the bridge.

## WebSocket endpoints

| Endpoint | Purpose |
|----------|---------|
| `http://127.0.0.1:8765` | HTTP server; serves `bridge.html`. |
| `ws://127.0.0.1:8766` | WebSocket relay. Both the bridge page and the CLI connect here. |

The server does not interpret message payloads; it only forwards messages between the single “bridge” client and the single “cli” client.

## Client registration

Each client must send exactly one registration message as the first message after connecting.

**Bridge (browser):**

```json
{ "role": "bridge" }
```

**CLI (proot):**

```json
{ "role": "cli" }
```

**Server response (to either):**

```json
{ "type": "registered", "role": "bridge" }
```

or

```json
{ "type": "registered", "role": "cli" }
```

After that, all other messages are relayed to the other client. The server does not add or change message types.

## Message types (CLI → Bridge)

These are sent by the CLI and relayed to the bridge. The bridge page handles them and may send back responses.

### connect

Ask the bridge to connect to the Monocle via Web Bluetooth (or report status if already connected).

**Sent by CLI:**

```json
{ "type": "connect" }
```

**Bridge response (relayed back to CLI):**

```json
{ "type": "connected", "ok": true }
```

or

```json
{ "type": "connected", "ok": false }
```

### repl

Send code to the Monocle REPL and get the result.

**Sent by CLI:**

```json
{ "type": "repl", "code": "1+1" }
```

`code` is a string (one or more lines of MicroPython).

**Bridge response (relayed back to CLI):**

```json
{ "type": "repl_response", "data": "2" }
```

`data` is the REPL output (e.g. the result of the expression or print output). On error or no connection, the bridge may send an error string in `data` (e.g. `"ERROR: Not connected to Monocle"`).

## BLE (Web Bluetooth) reference

The bridge page uses the **Nordic UART Service (NUS)** to talk to the Monocle, matching Brilliant’s AR Studio / official tooling.

| Item | UUID |
|------|------|
| Service (REPL) | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| RX (write from host to device) | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` |
| TX (notify from device to host) | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` |

- **Connect:** `navigator.bluetooth.requestDevice({ filters: [{ services: [REPL_SERVICE] }], optionalServices: [REPL_SERVICE] })`, then connect to GATT, get primary service, get RX and TX characteristics.
- **Send REPL input:** Write the code string (as UTF-8) to the RX characteristic.
- **Receive REPL output:** Subscribe to notifications on the TX characteristic; decode incoming chunks as UTF-8 and buffer until a line (e.g. `\r\n`) is received; that line is the REPL response.

All messages on the WebSocket are JSON objects; the server forwards them as-is (as text frames).
