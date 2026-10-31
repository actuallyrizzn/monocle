# Monocle

Android bridge for a Brilliant Labs **Monocle**: foreground service, loopback HTTP, cards and stills on the waveguide.

Repo: [actuallyrizzn/monocle](https://github.com/actuallyrizzn/monocle)

Replies go **on the lens**. There is no speaker on the glasses and this app does not play TTS.

The February 2026 Python/HTML Web Bluetooth CLI lives in [`old/`](old/).

## Install

```bash
git clone https://github.com/actuallyrizzn/monocle.git
cd monocle
bash tools/assemble-debug-on-termux.sh
```

Install the debug APK from `app/build/outputs/apk/debug/MONOCLE-BRIDGE.apk`. Do **not** copy it to Downloads. Force-stop **Noa** before Start. Grant Bluetooth (and mic if you want the phone-STT fallback). Leave the notification running.

Current app: **0.5.32**. Loopback HTTP: `http://127.0.0.1:8767` (not 8765).

Presence (optional) is **not** compiled in. The phone **only dials out** — Broca web-chat / Otto HTTP bridge shape. `POST /v1/heartbeat` is “I’m here”; queued cards come back on that response. Agents enqueue with `POST /v1/send`. Nothing connects inbound to the phone. Set the relay URL and API key on the app screen, or `POST /v1/presence/config` with `{url, key}`. Empty config means the heartbeat stays off.

Idle uses `device.hibernate` (Bluetooth stays; FPGA / 10V / camera analog off). Application-only DFU (`POST /v1/firmware`). Do not flash below **40%**. Recovery zip is Brilliant’s GitHub **v23.248.0754** release.

On BLE up: connect splash, then blank after 20s. Camera snaps re-init the sensor after FPGA-off idle, then hibernate again. Do not shoot the camera on a dying cell.

## Hooks

| Call | What it drives |
|---|---|
| `GET /health` | BLE, raw-data service, listen, route list |
| `POST /v1/connect` | Scan / reconnect |
| `POST /v1/display` | Card on the waveguide: `{text, subject, from}` |
| `POST /v1/display/clear` | Blank the lens |
| `POST /v1/display/brightness` | `{level:0-4}` |
| `GET /v1/sensors` | Battery, charging, version, touch, FPGA |
| `POST /v1/led` | `{color:GREEN\|RED, on:true}` |
| `GET /v1/touch` | Touch pads right now |
| `POST /v1/mic` | `{seconds}` glasses mic for that long, capped by the app record limit |
| `POST /v1/ask` | Question card, then a fixed-length take + progress bar. Mic off after. |
| `GET`/`POST /v1/record-limit` | App cap in seconds (1–8, default 4). Also a field on the app screen. |
| `POST /v1/camera` | JPEG snapshot (operator `cam:` when armed, else REPL) |
| `POST /v1/operator` | Write `op.py` in small pastes and ARM |
| `POST /v1/firmware/zip` | Stage an application DFU zip |
| `POST /v1/firmware` | Flash staged zip (or bundled) |
| `POST /v1/listen/start` | Loop: record, energy-VAD, keep last WAV |
| `GET /v1/listen/utterance.wav` | Last clip |
| `POST /v1/stt/phone` | Android recognizer on the **phone** mic. Optional. |
| `POST /v1/repl` | Raw MicroPython |
| `GET /v1/presence` | Presence flag as the phone sees it. No secrets. |
| `POST /v1/presence/config` | `{url, key}` saved on the phone. |

## License

AGPLv3 for code (`LICENSE`). The archived CLI in `old/` is the same.
