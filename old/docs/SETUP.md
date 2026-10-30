# Setup

## Requirements

- **Environment:** proot (e.g. Ubuntu under Termux on Android), or any Linux where `127.0.0.1` is shared with the device running Chrome.
- **Browser:** Chrome on the **same** Android device (Web Bluetooth is required; it is not available in proot).
- **Hardware:** Brilliant Monocle powered on and in BLE range.

## One-time installation

### 1. Clone or download the repo

```bash
git clone https://github.com/actuallyrizzn/monocle-bridge.git
cd monocle-bridge
```

### 2. Python dependencies

The server and CLI use the `websockets` library.

**Option A — system package (Debian/Ubuntu in proot):**

```bash
apt update
apt install python3-websockets
```

**Option B — pip:**

```bash
pip install websockets
```

### 3. No build step

There is no build step. Use the repo as-is: `server.py`, `bridge.html`, and `monocle-cli.py` run directly.

## Verifying setup

1. Start the server in proot:
   ```bash
   python3 server.py
   ```
   You should see:
   ```
   Monocle bridge: http://127.0.0.1:8765  ws://127.0.0.1:8766
   Open the URL in Chrome on this device, then run: monocle-cli connect
   ```

2. On the **same Android device**, open Chrome and go to: **http://127.0.0.1:8765**

3. The bridge page should show “Bridge ready. Click ‘Connect to Monocle’ when ready.”

4. Click “Connect to Monocle” and select your Monocle in the Bluetooth picker.

5. In proot (in another terminal or after backgrounding the server):
   ```bash
   python3 monocle-cli.py repl "1+1"
   ```
   You should see `2` (or the result of the expression).

If any step fails, see [Troubleshooting](TROUBLESHOOTING.md).
