#!/usr/bin/env python3
"""Register Athena's dedicated Monocle SMCP and attach status/send only.

Run on moya (223). Does not bounce Athena's other MCP servers.
  LETTA_BASE_URL=http://127.0.0.1:8284 python3 attach.py
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

ATHENA_AGENT_ID = os.environ.get(
    "ATHENA_AGENT_ID", "agent-5712c4be-eb77-4d96-ac38-d4a8ba3c4eaf"
)
MCP_SERVER_NAME = os.environ.get("ATHENA_MONOCLE_MCP_NAME", "athena-monocle-smcp")
STDIO_COMMAND = os.environ.get(
    "ATHENA_MONOCLE_STDIO",
    "/home/rizzn/sanctum/agents/athena/smcp/run-smcp-stdio-for-letta.sh",
)


def _load_dotenv(path: str) -> None:
    if not os.path.isfile(path):
        return
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, val = line.split("=", 1)
            key = key.strip()
            val = val.strip().strip("'").strip('"')
            os.environ.setdefault(key, val)


def _base_url() -> str:
    return (
        os.environ.get("LETTA_BASE_URL")
        or os.environ.get("LETTA_SERVER_URL")
        or os.environ.get("AGENT_ENDPOINT")
        or "http://127.0.0.1:8284"
    ).rstrip("/")


def _api_key() -> str:
    for k in ("LETTA_API_KEY", "LETTA_SERVER_PASSWORD", "AGENT_API_KEY"):
        v = os.environ.get(k, "").strip()
        if v:
            return v
    return ""


def req(method: str, path: str, data: dict | None = None):
    url = f"{_base_url()}{path}"
    headers = {
        "Authorization": f"Bearer {_api_key()}",
        "Content-Type": "application/json",
        "Accept": "application/json",
        "User-Agent": "attach-athena-monocle/1.0",
    }
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(r, timeout=180) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def main() -> int:
    _load_dotenv(os.path.expanduser("~/sanctum/env.letta"))
    _load_dotenv(os.path.expanduser("~/sanctum/agents/athena/broca/.env"))
    if not _api_key():
        print("Need LETTA_API_KEY / AGENT_API_KEY", file=sys.stderr)
        return 1

    mcp_list = req("GET", "/v1/mcp-servers/")
    if not isinstance(mcp_list, list):
        mcp_list = mcp_list.get("data", []) or []
    server = next(
        (s for s in mcp_list if (s.get("server_name") or "") == MCP_SERVER_NAME),
        None,
    )
    if server:
        print(f"Using existing MCP server {MCP_SERVER_NAME} id={server.get('id')}")
    else:
        payload = {
            "server_name": MCP_SERVER_NAME,
            "config": {
                "mcp_server_type": "stdio",
                "command": STDIO_COMMAND,
                "args": [],
                "env": None,
            },
        }
        server = req("POST", "/v1/mcp-servers/", data=payload)
        print(f"Created MCP server {MCP_SERVER_NAME} id={server.get('id')}")

    sid = server.get("id")
    if not sid:
        print("No MCP server id", file=sys.stderr)
        return 1

    try:
        ref = req("PATCH", f"/v1/mcp-servers/{sid}/refresh", data={})
        print(f"MCP refresh: {ref if isinstance(ref, (dict, list)) else ref!r}")
    except urllib.error.HTTPError as exc:
        print(f"MCP refresh failed: {exc.code} {exc.read().decode()}", file=sys.stderr)

    tools_out = req("GET", f"/v1/mcp-servers/{sid}/tools")
    tools = tools_out if isinstance(tools_out, list) else tools_out.get("data", []) or []
    wanted = []
    for t in tools:
        if not isinstance(t, dict) or not t.get("id"):
            continue
        name = (t.get("name") or "").lower()
        if name.startswith("monocle"):
            wanted.append(t)
            print(f" tool {t.get('name')} id={t.get('id')}")
    if not wanted:
        print("No monocle_* tools on that MCP server.", file=sys.stderr)
        return 1

    agent = req("GET", f"/v1/agents/{ATHENA_AGENT_ID}")
    cur = agent.get("tools") or []
    cur_ids = {(t.get("id") if isinstance(t, dict) else None) for t in cur}
    attached = 0
    for t in wanted:
        tid = t["id"]
        if tid in cur_ids:
            print(f" already attached {t.get('name')}")
            continue
        req("PATCH", f"/v1/agents/{ATHENA_AGENT_ID}/tools/attach/{tid}")
        print(f" attached {t.get('name')}")
        attached += 1
    print(f"Done. newly_attached={attached} agent={ATHENA_AGENT_ID}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
