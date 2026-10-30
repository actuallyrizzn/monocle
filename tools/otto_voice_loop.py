#!/usr/bin/env python3
"""Poll Monocle Bridge listen-loop WAVs and put replies on the lens.

No speaker. Glasses mic → VAD/WAV on the APK → this script transcribes (AssemblyAI
if a key is reachable) → POST /v1/display.

  python3 tools/otto_voice_loop.py
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = os.environ.get("MONOCLE_HTTP", "http://127.0.0.1:8767")
OUT = Path(os.environ.get("MONOCLE_WAV_DIR", "/tmp/monocle-utterances"))
SSH_PASS = Path.home() / ".ssh" / "newdev2.pass"
REMOTE_KEY = "/root/doordash-native-marketing-research-temp/.assemblyai_key"


def http_json(method: str, path: str, payload: dict | None = None, timeout: int = 60) -> dict:
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(
        f"{BASE}/{path.lstrip('/')}",
        data=data,
        method=method,
        headers={"Content-Type": "application/json"} if data else {},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
        if not raw:
            return {}
        return json.loads(raw.decode())


def display(text: str) -> None:
    http_json("POST", "v1/display", {"text": text[:80]})


def load_assemblyai_key() -> str:
    k = os.environ.get("ASSEMBLYAI_API_KEY", "").strip()
    if k:
        return k
    if not SSH_PASS.is_file():
        return ""
    try:
        out = subprocess.check_output(
            ["sshpass", "-f", str(SSH_PASS), "ssh", "NewDev", f"cat {REMOTE_KEY}"],
            timeout=20,
        )
        return out.decode().strip()
    except Exception:
        return ""


def transcribe_wav(path: Path, key: str) -> str:
    if not key:
        return ""
    req = urllib.request.Request(
        "https://api.assemblyai.com/v2/upload",
        data=path.read_bytes(),
        method="POST",
        headers={"authorization": key},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        upload = json.loads(resp.read().decode())
    req = urllib.request.Request(
        "https://api.assemblyai.com/v2/transcript",
        data=json.dumps({"audio_url": upload["upload_url"]}).encode(),
        method="POST",
        headers={"authorization": key, "content-type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        tid = json.loads(resp.read().decode())["id"]
    for _ in range(40):
        time.sleep(1.5)
        st_req = urllib.request.Request(
            f"https://api.assemblyai.com/v2/transcript/{tid}",
            headers={"authorization": key},
        )
        with urllib.request.urlopen(st_req, timeout=30) as resp:
            st = json.loads(resp.read().decode())
        if st.get("status") == "completed":
            return (st.get("text") or "").strip()
        if st.get("status") == "error":
            return ""
    return ""


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    health = http_json("GET", "health")
    print("health", json.dumps(health, indent=2)[:800])
    http_json("POST", "v1/listen/start", {"seconds": 2.0})
    display("LISTEN ON")
    key = load_assemblyai_key()
    last_at = 0
    print("polling /v1/listen/utterance  (Ctrl-C to stop)")
    while True:
        try:
            u = http_json("GET", "v1/listen/utterance", timeout=10)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                time.sleep(1.2)
                continue
            raise
        at = int(u.get("at_ms") or 0)
        if at == last_at:
            time.sleep(0.8)
            continue
        last_at = at
        wav_path = OUT / f"utt-{at}.wav"
        wav_b64 = u.get("wav_b64") or ""
        if wav_b64:
            import base64

            wav_path.write_bytes(base64.b64decode(wav_b64))
        speaking = bool(u.get("speaking"))
        energy = u.get("energy")
        print(f"utt at={at} speaking={speaking} energy={energy} bytes={u.get('bytes')} file={wav_path}")
        if not speaking:
            continue
        text = transcribe_wav(wav_path, key) if wav_path.is_file() else ""
        if text:
            print("stt:", text)
            display(text)
        else:
            display("HEARD YOU")
            print("no transcript (key missing or empty) — lens shows HEARD YOU")
        time.sleep(0.4)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        try:
            http_json("POST", "v1/listen/stop", {})
            display("LISTEN OFF")
        except Exception:
            pass
        sys.exit(0)
