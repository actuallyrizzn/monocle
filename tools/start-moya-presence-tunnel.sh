#!/bin/bash
# Idempotent: keep a local 127.0.0.1:8890 SSH forward to moya's presence listener.
# Phone APK heartbeats this port. Same screen+cron shape as Sanctum Broca starters.
#
# Cron on this Termux/proot host:
#   * * * * * SCREENDIR=/root/.screen /root/projects/monocle-android/tools/start-moya-presence-tunnel.sh >>/root/.local/log/monocle-moya-tunnel.log 2>&1

set -euo pipefail

SESSION_NAME="monocle-moya"
export SCREENDIR="${SCREENDIR:-$HOME/.screen}"
mkdir -p "$SCREENDIR" "$HOME/.local/log"
chmod 700 "$SCREENDIR"

port_open() {
  python3 - <<'PY'
import socket
s = socket.socket()
s.settimeout(1)
try:
    s.connect(("127.0.0.1", 8890))
    print("open")
except Exception:
    print("closed")
finally:
    s.close()
PY
}

screen_line=$(screen -list 2>/dev/null | grep -F "$SESSION_NAME" || true)
if echo "$screen_line" | grep -q "Dead"; then
  screen -S "$SESSION_NAME" -X quit 2>/dev/null || true
  screen_line=""
fi

if [ -n "$screen_line" ]; then
  if [ "$(port_open)" = "open" ]; then
    exit 0
  fi
  screen -S "$SESSION_NAME" -X quit 2>/dev/null || true
fi

if [ "$(port_open)" = "open" ]; then
  # Something else already forwarded the port.
  exit 0
fi

PASS="${HOME}/.ssh/athena-moya.pass"
CFG="${HOME}/.ssh/config"
if [[ ! -f "$PASS" || ! -f "$CFG" ]]; then
  echo "missing ssh pass or config" >&2
  exit 2
fi

SSHPASS_BIN="$(command -v sshpass)"
SSH_BIN="$(command -v ssh)"
CMD="exec '$SSHPASS_BIN' -f '$PASS' '$SSH_BIN' -F '$CFG' -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o StrictHostKeyChecking=accept-new -L 127.0.0.1:8890:127.0.0.1:8890 moya"
screen -dmS "$SESSION_NAME" bash -c "$CMD"

for _ in 1 2 3 4 5 6 7 8 9 10; do
  if [ "$(port_open)" = "open" ]; then
    exit 0
  fi
  sleep 1
done
echo "screen started but 8890 not listening yet" >&2
exit 1
