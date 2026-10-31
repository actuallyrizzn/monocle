#!/bin/bash
# Letta stdio MCP for Athena's Monocle tools only. Does not replace her other MCP servers.
set -e
SMCP_HOME="/home/rizzn/sanctum/smcp"
ATHENA_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SMCP_HOME"
VENV_PY="/home/rizzn/sanctum/venv/bin/python"
[ -x "$VENV_PY" ] && PYTHON="$VENV_PY" || PYTHON=python3
[ -f "/home/rizzn/sanctum/env.letta" ] && set -a && . /home/rizzn/sanctum/env.letta && set +a
[ -f "$ATHENA_DIR/env.smcp" ] && set -a && . "$ATHENA_DIR/env.smcp" && set +a
exec "$PYTHON" smcp_stdio.py
