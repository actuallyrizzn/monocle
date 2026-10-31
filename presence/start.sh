#!/bin/bash
# Idempotent: start Monocle presence in screen only if that screen is live.
# Bind stays loopback-only (server.py).
# Cron example:
#   * * * * * /path/to/presence/start.sh >> ~/logs/monocle-presence-cron.log 2>&1

SESSION_NAME="monocle-presence"
DIR="${MONOCLE_PRESENCE_DIR:-$(cd "$(dirname "$0")" && pwd)}"
ENV_FILE="${MONOCLE_PRESENCE_ENV:-$HOME/.config/monocle-presence.env}"
LOG="${MONOCLE_PRESENCE_LOG:-$HOME/logs/monocle-presence.log}"

screen_line=$(screen -list 2>/dev/null | grep -F "$SESSION_NAME" || true)
if echo "$screen_line" | grep -q "Dead"; then
  screen -S "$SESSION_NAME" -X quit 2>/dev/null || true
  screen_line=""
fi
if [ -n "$screen_line" ]; then
  exit 0
fi

mkdir -p "$(dirname "$LOG")" "$DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing $ENV_FILE" >&2
  exit 2
fi
if [[ ! -f "$DIR/server.py" ]]; then
  echo "missing $DIR/server.py" >&2
  exit 2
fi

# A leftover nohup copy holds :8890 and is invisible to screen. Drop it once
# so this launcher owns the listener.
if pgrep -f "$DIR/server.py" >/dev/null 2>&1; then
  pkill -f "$DIR/server.py" || true
  sleep 1
fi

CMD="set -a && . '$ENV_FILE' && set +a && cd '$DIR' && exec python3 '$DIR/server.py' >>'$LOG' 2>&1"
screen -dmS "$SESSION_NAME" bash -c "$CMD"
