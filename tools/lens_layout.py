"""Layout for the Brilliant Monocle waveguide.

Firmware (v23.248, github.com/brilliantlabsAR/monocle-micropython display.py):

- OLED is 640x400.
- Glyphs are FONT_WIDTH=24, FONT_HEIGHT=48. SPACE_WIDTH is baked into FONT_WIDTH.
- display.Text does not wrap. Overflow is clipped (clip_x) or raises TextOverlapError
  if two text boxes share pixels. Adjacent lines at y and y+48 count as overlap
  because the hit test is inclusive. Line pitch must be at least 49; Noa used 50.
- There is no Sprite class. Camera JPEG is BLE-out only — it never hits the OLED.
  Pictures on the glass are vector objects (Rectangle, Line, Polygon, Fill).
- Fill() paints the whole world black. Do not use it for notifications.

Chrome (always on): subject + from at top left, battery top right. Body scrolls
under that title bar. Matches APK LensText / MonocleCommands.showText.
"""

from __future__ import annotations

WIDTH = 640
HEIGHT = 400
FONT_WIDTH = 24
FONT_HEIGHT = 48
LINE_H = 50
COLS = WIDTH // FONT_WIDTH  # 26
BAT_COLS = 4
SUBJ_COLS = COLS - BAT_COLS - 1  # 21
CHROME_ROWS = 2
BODY_Y = CHROME_ROWS * LINE_H  # 100
BODY_ROWS = 6
ROWS = CHROME_ROWS + BODY_ROWS
ORIGIN_X = 0
ORIGIN_Y = 0
MAX_LINES = 48
CYAN = 0x29D0D0
WHITE = 0xFFFFFF
YELLOW = 0xFFEE33
DEFAULT_SUBJECT = "Monocle"
DEFAULT_FROM = "—"


def wrap(text: str, cols: int = COLS, max_lines: int = MAX_LINES) -> list[str]:
    words = [w for w in text.replace("\r", " ").replace("\n", " ").split() if w]
    if not words:
        return [" "]
    lines: list[str] = []
    cur = ""
    for raw in words:
        word = raw
        while len(word) > cols:
            if cur:
                lines.append(cur)
                cur = ""
            lines.append(word[:cols])
            word = word[cols:]
            if len(lines) >= max_lines:
                return lines[:max_lines]
        trial = word if not cur else f"{cur} {word}"
        if len(trial) <= cols:
            cur = trial
        else:
            lines.append(cur)
            cur = word
        if len(lines) >= max_lines:
            return lines[:max_lines]
    if cur:
        lines.append(cur)
    return lines[:max_lines] or [" "]


def clip(text: str, cols: int) -> str:
    t = " ".join((text or "").replace("\r", " ").replace("\n", " ").split())
    if not t:
        return " "
    return t[:cols]


def escape_py(text: str) -> str:
    return (
        text.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", "")
    )


def card_code(text: str, sender: str = "", subject: str = "") -> str:
    """Title bar (subject, from, battery) + scrolling body. No Fill."""
    subj = clip(subject.strip() or DEFAULT_SUBJECT, SUBJ_COLS)
    who = clip(sender.strip() or DEFAULT_FROM, COLS)
    body = wrap(text)
    lit = ",".join(f"'{escape_py(line)}'" for line in body)
    return "\n".join(
        [
            "import device,display,time",
            f"S='{escape_py(subj)}'",
            f"W='{escape_py(who)}'",
            f"B=[{lit}]",
            f"H={LINE_H}",
            f"Y={BODY_Y}",
            f"N={BODY_ROWS}",
            "def chrome():",
            " b=str(device.battery_level())+'%'",
            f" if len(b)>{BAT_COLS}:",
            f"  b=b[:{BAT_COLS}]",
            " bx=640-len(b)*24",
            f" return [display.Text(S,0,0,{hex(CYAN)}),display.Text(b,bx,0,{hex(YELLOW)}),display.Text(W,0,{LINE_H},{hex(YELLOW)})]",
            "def fr(s):",
            " o=chrome()",
            " i=0",
            " while i<N:",
            "  if s+i>=len(B):",
            "   break",
            f"  o.append(display.Text(B[s+i],0,Y+i*H,{hex(WHITE)}))",
            "  i+=1",
            " display.show(o)",
            "display.brightness(2)",
            "if len(B)<=N:",
            " fr(0)",
            "else:",
            " w=len(B)-N+1",
            " s=0",
            " while s<w:",
            "  fr(s)",
            "  time.sleep(1.8)",
            "  s+=1",
        ]
    )
