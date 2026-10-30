#!/usr/bin/env python3
"""Keep trying BLE connect + paint chrome until the lens answers."""
import json
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8767"
CHROME = """
import device,display
S='Live check'
W='Otto'
b=str(device.battery_level())+'%'
print('BAT', b)
o=[display.Text(S,0,0,0x00FFFF),display.Text(b,640-len(b)*24,0,0xFFFF00),display.Text(W,0,50,0xFFFFFF),display.Text('Back in range.',0,100,0xFFFFFF)]
display.brightness(0)
display.show(o)
print('SHOWN')
""".strip()


def get(path, timeout=8):
    with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
        return json.loads(r.read().decode())


def post(path, payload=None, timeout=28):
    data = json.dumps(payload or {}).encode()
    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())


def main():
    for i in range(1, 48):
        try:
            h = get("/health", 6)
        except Exception as e:
            print(f"tick {i} health FAIL {e}", flush=True)
            time.sleep(5)
            continue
        ble = bool(h.get("ble"))
        print(f"tick {i} ble={ble} status={h.get('status')} version={h.get('version')}", flush=True)
        if not ble:
            code, body = post("/v1/connect", {}, 25)
            print(
                f"tick {i} connect {code} ok={body.get('ok')} status={body.get('status')}",
                flush=True,
            )
            ble = bool(body.get("ok"))
        if ble:
            code, body = post("/v1/repl", {"code": CHROME}, 18)
            print(f"tick {i} chrome {code} {body}", flush=True)
            if body.get("ok"):
                print("REPL_OK", flush=True)
                return 0
        time.sleep(6)
    print("STILL_DOWN", flush=True)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
