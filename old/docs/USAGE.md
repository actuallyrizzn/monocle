# Usage

## Starting the bridge

1. **In proot**, from the project directory:
   ```bash
   cd /path/to/monocle-bridge
   python3 server.py
   ```
   Leave this running. It serves the bridge page on port 8765 and the WebSocket relay on 8766.

2. **On the same Android device**, open Chrome and go to:
   ```
   http://127.0.0.1:8765
   ```
   The bridge page loads; it connects to the WebSocket server automatically.

3. On the bridge page, click **“Connect to Monocle”** and choose your Monocle in the Bluetooth dialog. Do this once per session (or after the Monocle disconnects).

## CLI commands

Run these from proot (in a separate terminal or after backgrounding the server). The bridge page must be open in Chrome and connected to the Monocle for REPL commands to work.

### connect

Registers the CLI with the relay and asks the bridge to connect to the Monocle via Web Bluetooth. You can also connect by clicking “Connect to Monocle” in the browser; this command is useful to trigger that flow from the CLI and see success/failure.

```bash
python3 monocle-cli.py connect
```

Expected output: `Connected to Monocle` or an error message.

### repl — run code on the Monocle

Send MicroPython code to the Monocle REPL and print the result.

**Single expression (command line):**

```bash
python3 monocle-cli.py repl "1+1"
python3 monocle-cli.py repl "print('hello')"
```

**Multi-line or from stdin:**

```bash
python3 monocle-cli.py repl << 'EOF'
import display
display.text("Hi from CLI")
EOF
```

Or pipe a file:

```bash
echo "import display; display.text('hi')" | python3 monocle-cli.py repl
```

**Timeout:** If the Monocle does not respond within about 10 seconds, the CLI prints `(timeout)` and exits.

## Running monocle-cli from anywhere

To run `monocle-cli.py` without typing the path:

- Add the project directory to `PATH`, or
- Create a wrapper script in a directory on `PATH`, e.g.:
  ```bash
  #!/bin/sh
  exec python3 /path/to/monocle-bridge/monocle-cli.py "$@"
  ```

## Typical workflow

1. Start `python3 server.py` in proot.
2. Open http://127.0.0.1:8765 in Chrome and click “Connect to Monocle”.
3. Use `python3 monocle-cli.py repl "..."` to run code on the Monocle.
4. Keep the bridge tab open; closing it disconnects the relay. Restart the server if you need to reload the bridge page.
