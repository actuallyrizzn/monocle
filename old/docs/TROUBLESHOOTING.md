# Troubleshooting

## “Bridge not running” / “Cannot connect to bridge”

**Symptom:** Running `monocle-cli.py` prints “Bridge not running” or “Cannot connect to bridge. Is the server running?”

**Fix:**

1. Start the server in proot: `python3 server.py`. Leave it running.
2. Ensure nothing else is using ports 8765 or 8766.
3. If you use a firewall, allow localhost connections to 8765 and 8766.

## Bridge page: “Bridge server disconnected” or “Connecting to bridge server…” forever

**Symptom:** The bridge page never shows “Bridge ready” or it shows “Bridge server disconnected”.

**Fix:**

1. Confirm the server is running in proot (`python3 server.py`).
2. Open the page from the **same device** that runs proot: `http://127.0.0.1:8765`. Do not use another machine’s IP unless you’ve deliberately bound the server to that IP and understand the network setup.
3. If you use a different port (e.g. by changing `PORT` in `server.py`), the bridge page infers the WebSocket port as `location.port + 1`. Load the page using that same port (e.g. `http://127.0.0.1:8765`).

## “Connection failed” when running `monocle-cli connect`

**Symptom:** CLI prints “Connection failed. Ensure bridge page is open and you selected the Monocle.”

**Fix:**

1. Ensure the bridge page is open in Chrome and shows “Bridge ready”.
2. Click “Connect to Monocle” on the page and **select the Monocle** in the Bluetooth picker. If you cancel the picker or choose another device, `ok` will be false.
3. Ensure the Monocle is on and in range. Try turning it off and on, then connect again.

## REPL timeout or “ERROR: Not connected to Monocle”

**Symptom:** `monocle-cli repl "1+1"` prints “(timeout)” or the bridge sends back “ERROR: Not connected to Monocle”.

**Fix:**

1. Connect the Monocle from the bridge page first (click “Connect to Monocle” and select the device).
2. Keep the bridge tab open; closing it drops the BLE connection.
3. If the Monocle was disconnected (e.g. out of range, powered off), click “Connect to Monocle” again and retry.

## Chrome doesn’t show the Monocle in the Bluetooth list

**Symptom:** When you click “Connect to Monocle”, the picker doesn’t list the Monocle or shows “No devices found”.

**Fix:**

1. Confirm the Monocle is powered on and in pairing/connectable mode (see Brilliant docs).
2. Ensure Chrome has Bluetooth and “Nearby devices” (or similar) permission.
3. Try closing other apps that might be holding a BLE connection to the Monocle.
4. On some Android versions, you may need to pair the Monocle in system Settings → Bluetooth first; then try the bridge again.

## proot: 127.0.0.1 not reachable from Chrome

**Symptom:** On the Android device, Chrome cannot open `http://127.0.0.1:8765` (connection refused or no route).

**Fix:**

- proot usually shares the host network. If you run the server inside proot and Chrome on the same Android host, 127.0.0.1 should work. If you’re using a different setup (e.g. emulator, separate machine), use the correct IP and ensure the server is bound to `0.0.0.0` (it is by default) and that the firewall allows the connection.

## SSL / HTTPS

The server is HTTP only. Do not use it over the public internet; it is intended for local use (e.g. proot and Chrome on the same device). For HTTPS you would need to add a reverse proxy or TLS in front of the server (not covered here).

## “Install: pip install websockets”

**Symptom:** Running `server.py` or `monocle-cli.py` exits immediately with “Install: pip install websockets”.

**Fix:** Install the `websockets` library: `pip install websockets` or `apt install python3-websockets` (Debian/Ubuntu).

## Tests fail with import or coverage errors

**Symptom:** `pytest tests/` or `pytest tests/ --cov=.` fails on import or coverage.

**Fix:**

1. Install dev dependencies: `pip install pytest pytest-asyncio pytest-cov websockets`.
2. Run from the project root: `cd /path/to/monocle-bridge && pytest tests/ -v`.
3. If coverage fails under 80%, add or fix tests; see [Development](DEVELOPMENT.md).
