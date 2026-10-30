# Monocle display (firmware facts)

Researched 2026-10-29 against live firmware `v23.248.0754` and
[monocle-micropython display.py](https://github.com/brilliantlabsAR/monocle-micropython).

**This pair is irreplaceable.** Brilliant EOL'd Monocle. Application-only
DFU, never bootloader/SoftDevice, no flash on a low or empty cell, no DFU
retry storms. Recovery is the GitHub **v23.248.0754** zip onto `DfuTarg`.

## Text

| Constant | Value |
|---|---|
| `display.WIDTH` / `HEIGHT` | 640 × 400 |
| `FONT_WIDTH` | 24 (23 + `SPACE_WIDTH` 1) |
| `FONT_HEIGHT` | 48 |
| Max characters per line | `640 // 24` = **26** |

`display.Text` **does not wrap**. A long string is clipped in `clip_x()`, which is
what looked like “scrolling off the side.” Two text boxes that share any pixel
raise `TextOverlapError`. The hit test is inclusive, so line pitch of 48
overlaps; use **50** (Noa’s `graphics.py` on this device).

Do **not** `display.Fill()` a notification — that paints over the real world.

## Chrome (on a card, then dark)

Every paint is a **card**, not a dump of body text. The glasses run a resident
**operator** (`op.py`): wrap, chrome, battery, camera, mic, sensors, LEDs, touch.
The phone sends 4-byte tags (`crd:` / `clr:` / `cam:` / `mic:` / `sns:` …) on
the raw data service.

| Slot | Where | Color |
|---|---|---|
| Subject | top left, line 0, max 21 chars | cyan |
| Battery | top right, line 0, `NN%` | yellow |
| From | top left, line 1, max 26 chars | yellow |
| Body | lines 2–7 (y=100), wraps, first six rows | white |

Subject is clipped to 21 columns so it cannot overlap a 4-character battery
(`100%`). Line pitch stays 50. APK **0.5.5** `POST /v1/display` accepts
`text`, `subject` / `title`, `from` / `sender`. Missing fields keep the last
card. On BLE connect the bridge paints a connected card (subject Monocle,
from Otto) so the factory launch screen does not sit at full nits. Panel
brightness on a card is **0** of 0–4 (750 cd/m²). After 20s with no new card
the waveguide blanks (`display.clear()` + brightness 0) even when `op.py` is
not armed. That blank is **not** panel off: `clear()` paints a black space,
and brightness 0 is still 750 nits with the 10V boost up. Independent tests
on a blanked screen with BLE still die in about an hour (70 mAh cell).
Product idle (locked 30 Oct): **Bluetooth stays**; FPGA / 10V / camera analog
**off** between cards. Seconds of wake are fine. Do not `force_sleep` (kills
BLE). Do not `fpga.run(False)` (rails come back). Firmware patch in
`projects/monocle-micropython` (tag `v23.248.0754` + `device.hibernate`)
copies Brilliant’s `power_all_rails(false)` and leaves SoftDevice up.
APK **0.5.13** calls that when present, still blanks on stock v23.248, and
flashes the signed application zip with `POST /v1/firmware`. Detail: Tasks
[Doc #1617](https://tasks.decisionsciencecorp.com/admin/doc.php?id=1617).
Max (4) drains the cell fast.

## Images

There is **no `Sprite` class** on this firmware (issue #236 never shipped).

Camera `capture()` / `read()` send **JPEG over BLE to the phone**. They do not
draw on the OLED. On-glass pictures are vector: `Rectangle`, `Line`, `Polygon`,
`Polyline`. Photos stay a phone-side artifact until we do FPGA work on purpose.

## Official Noa — photo “preview”

Mark remembered seeing a picture on-screen after shooting with the cam.
Official Noa **does** preview the JPEG — **on the phone**, not on the waveguide.

### What the glasses show

Long-press → `photo.py` `camera.capture()`, then BLE `ist:` / `idt:` chunks.
On the OLED, `graphics.py` only paints yellow **text**: `Sending photo [=====]`
at `BOTTOM_CENTER`. No bitmap, no thumbnail, no camera overlay.

Stock FPGA (`monocle-fpga` `streamlogic/pipeline.json`, id `v23.230.0808`):

- Display path: `FBBackground` → `FBGraphics` → `FBText` → `FBOut` → OLED
- Camera path: separate `OVJpeg` FIFO (what `camera.read()` drains)
- StreamLogic `Preview` node is `"testOnly": true` — designer simulator, not the lens

Firmware `camera.output(x, y, mode)` and `camera.zoom()` still return
`NotImplemented`.

### What the phone shows

**Android** (`BaseActivity.kt`): on `ien:` decode JPEG → `updatechatList(..., bitmap)`
→ chat bubble; tap opens `FullScreenPopup` (Glide / Bitmap, save to Pictures/Noa).

**iOS** (`Controller.swift`): assemble `_imageData` from `ist:`/`idt:`,
`printToChat(..., picture: UIImage, as: .user)` in the chat thread, then
Stability img2img. The **reply** picture also lands in chat.

iOS had an unfinished “put the result back on Monocle” function
`sendImageToMonocleInChunks`: pack 640×400 **RGB343**, BLE `bst:` / `bdt:` / `ben:`.
It is **commented out** with `//TODO: this does not seem to work yet`. Noa’s
on-device Python (`main.py` / `graphics.py` / `states.py`) never handles those
commands.

So the recollection matches the **phone chat thumbnail / full-screen photo**,
plus the on-glass progress string — *and* a real live-camera HUD that Brilliant
shipped, then deleted. See the fork/forum pass below.

## Who actually put the camera on the waveguide

Someone did. Brilliant Labs. Then they ripped it out to make JPEG capture fit.

### It shipped (Feb–Jul 2023)

Issue [#85](https://github.com/brilliantlabsAR/monocle-micropython/issues/85)
(Raj, 2023-02-03): required APIs were `overlay`, `capture`, `zoom`, `replay`.
`overlay` and `zoom` were checked off. Commit `45e954b` (2023-02-15) is literally
**“camera overlay working.”**

Factory FPGA **`v230117`** (the image devices shipped with): tap → live video on
the OLED (`fpga_disp_live` / `FPGA_DISP_CAM`). Double-press → replay the SRAM
buffer. Zoom. Hackaday tech doc 1.6 and the C driver (`fpga_set_display(1)`)
describe that exact product.

Python that worked on that bitstream:

```python
camera.overlay(True)          # fpga.write(0x1005); fpga.write(0x3005)
camera.zoom(1)                # or 16
camera.record(True/False)
camera.replay()               # fpga.write(0x3007) — freeze-frame replay, 4s
```

Community used it: [RauziDssh gist](https://gist.github.com/RauziDssh/4fbb3fec99871099dd3a2adfb885ab2b)
(2023-04-14) is overlay + zoom on the touch pads. Firmware **v23.181** still had
the full overlay/record/replay Python.

### They swapped the FPGA for JPEG (Apr–Aug 2023)

| FPGA | Date | What happened |
|---|---|---|
| `v230117` | 2023-01 | Factory. Live cam on OLED. **Incompatible** with MicroPython ≥ v23.112 |
| `v23.110.0855` | 2023-04 | First StreamLogic build. “Camera may not work properly.” |
| `v23.154.0806` | 2023-06 | Mic in. Display buffer 16k→8k. Camera still iffy |
| `v23.179.1006` | 2023-06 | Mic rates. Firmware adds `capture()`/`read()` with **no FPGA support yet** |
| `v23.230.0808` | 2023-08 | **JPEG enabled.** This is what’s on the glass now |

Python was rewritten to match the new bitstream (`2aa3b2a`, 2023-07-11). Overlay
became a no-op / later `output()`/`zoom()` → `NotImplemented`. Current
`pipeline.json` is vector graphics + a separate `OVJpeg` FIFO. Camera never
reaches the OLED.

Release note on factory rollback: *“You should only roll back to this version if
you want really old features.”*

### Forks (walked GitHub, 2026-10-29)

| Repo | Forks walked | Anything real? |
|---|---|---|
| `monocle-micropython` | 28 | **One:** [sathibault/streamlogic-monocle-micropython](https://github.com/sathibault/streamlogic-monocle-micropython) — still has `overlay(enable)` talking to StreamLogic `__camera.command(5/4)`. Rest are stale clones (Apr 2024 GitHub-network copies) |
| `monocle-fpga` | 10 | Clones. No overlay revival |
| `noa-for-ios` | 22 | Bundle-ID / tutorial forks. Nobody uncommented `sendImageToMonocleInChunks` |
| `noa-for-android` | 10 | Josh-XT translate-toggle. Same `ist:`/`idt:` → phone Bitmap |

Sprites exist on the **`dev`** branch (`modules/sprite.py`, FPGA `0x4504` /
`0x4502` / `0x4503`) via PR [#254](https://github.com/brilliantlabsAR/monocle-micropython/pull/254)
merged **to `dev` 2023-10-20**. Never tagged. Shipping `main` / v23.248 has no
`Sprite`. Issue [#236](https://github.com/brilliantlabsAR/monocle-micropython/issues/236)
is still open.

### Community that looked like “pictures on screen”

- [milesprovus/Monocle-QR-Reader](https://github.com/milesprovus/Monocle-QR-Reader) — JPEG over BLE, OpenCV **on the laptop**, text result back on glass. No OLED bitmap.
- [jdc-cunningham/bl-monocle-video-stream](https://github.com/jdc-cunningham/bl-monocle-video-stream) — a **phone camera pointed at the Monocle**, not the Monocle camera on the OLED.
- [velhud/prismos](https://github.com/velhud/prismos) — host-side Gallery OS; capture path recorded as failed 2026-05-30.
- StreamLogic **`monocle-minimal`**: OVCam → FrameBuf → FBOut → OLED. Documented live HUD. Needs a **custom bitstream** (Gowin + `sxlogic`), not the stock JPEG FPGA. sathibault’s firmware fork is the matching Python.

Brilliant Discord exists (`discord.gg/JS44fGjNSH`); no public searchable archive
beyond GitHub/Hackaday. Reddit AMA is 2023 product talk, not overlay code.

### What this means for us

Poking `0x1005` / `0x3005` on **this** FPGA is the old register map. Don’t.

Three real paths:

1. **Stay on current FPGA** — JPEG to phone, mosaic rectangles on glass (what we have).
2. **Custom StreamLogic bitstream** — live camera HUD again, compose JPEG *and* FBOut if we want both. Flashes over WebREPL; loses stock Noa graphics until we rebuild the pipeline.
3. **Factory FPGA rollback** (`v230117`) — overlay works, current firmware does not. Last-resort museum piece.

Frame (the next glasses) has `GRAPHICS_DRAW_SPRITE`. That is a different product.

## What talks to the glass today

APK **0.5.13** shows the version on the phone app, the notification, and
the connected lens card. It reconnects from the last Monocle address
(background-safe) and paints/blanks without requiring `op.py`.
`POST /v1/operator` writes `op.py` in small pastes with one interrupt.
A photo does not install `op.py`. The old one-shot REPL dump reboots this firmware.
Heartbeat/pull stays on the phone. Camera
and mic offload are stock Noa tags (`ist:`/`idt:`/`ien:`, `ast:`/`dat:`/`aen:`)
with `main.py`’s `OSError` busy-retry on `bluetooth.send`. Otto serves
`MONOCLE-BRIDGE.apk` on the single-file link (not Downloads).
