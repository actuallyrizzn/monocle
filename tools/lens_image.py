#!/usr/bin/env python3
"""Put a picture on the Monocle as a rectangle mosaic.

This firmware has no Sprite class. camera.output() is NotImplemented, so the
JPEG never hits the OLED. The working path is: downsample, quantize, merge
same-color runs, draw display.Rectangle objects (max 128 colors).
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request

WIDTH = 640
HEIGHT = 400
LENS_URL = "http://127.0.0.1:8767"

# Named palette for the first test picture (smiley).
BG = 0x1C1C1C
FACE = 0xFFEE33
INK = 0x000000
WHITE = 0xFFFFFF
CYAN = 0x29D0D0
RED = 0xAD2323


def rle_rects(grid: list[list[int]], cell_w: int, cell_h: int) -> list[tuple[int, int, int, int, int]]:
    """Merge horizontal runs of the same color into rectangles."""
    rows = len(grid)
    cols = len(grid[0]) if rows else 0
    rects: list[tuple[int, int, int, int, int]] = []
    for y in range(rows):
        x = 0
        while x < cols:
            color = grid[y][x]
            run = 1
            while x + run < cols and grid[y][x + run] == color:
                run += 1
            x1 = x * cell_w
            y1 = y * cell_h
            x2 = min(WIDTH - 1, (x + run) * cell_w - 1)
            y2 = min(HEIGHT - 1, (y + 1) * cell_h - 1)
            rects.append((x1, y1, x2, y2, color))
            x += run
    return rects


def palette_and_indices(rects: list[tuple[int, int, int, int, int]]) -> tuple[list[int], list[tuple[int, int, int, int, int]]]:
    pal: list[int] = []
    out: list[tuple[int, int, int, int, int]] = []
    for x1, y1, x2, y2, color in rects:
        if color not in pal:
            pal.append(color)
        out.append((x1, y1, x2, y2, pal.index(color)))
    if len(pal) > 128:
        raise ValueError(f"too many colors: {len(pal)}")
    return pal, out


def mosaic_code(grid: list[list[int]], cell_w: int, cell_h: int) -> str:
    pal, rects = palette_and_indices(rle_rects(grid, cell_w, cell_h))
    pal_lit = ",".join(hex(c) for c in pal)
    # x1,y1,x2,y2,pal_index
    flat: list[int] = []
    for r in rects:
        flat.extend(r)
    data_lit = ",".join(str(n) for n in flat)
    return (
        "import display\n"
        f"C=[{pal_lit}]\n"
        f"D=[{data_lit}]\n"
        "o=[]\n"
        "i=0\n"
        "while i<len(D):\n"
        " o.append(display.Rectangle(D[i],D[i+1],D[i+2],D[i+3],C[D[i+4]]))\n"
        " i+=5\n"
        "display.brightness(4)\n"
        "display.show(o)"
    )


def smiley_grid(n: int = 16) -> list[list[int]]:
    """16x16-ish face. n should divide 640 and 400 reasonably."""
    grid = [[BG for _ in range(n)] for _ in range(n)]
    cx = cy = (n - 1) / 2
    r = n * 0.42
    for y in range(n):
        for x in range(n):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy <= r * r:
                grid[y][x] = FACE
    # eyes
    ey = max(1, n // 3)
    ex1, ex2 = n // 3, n - 1 - n // 3
    grid[ey][ex1] = INK
    grid[ey][ex2] = INK
    if n >= 12:
        grid[ey][ex1 + 1] = INK
        grid[ey][ex2 - 1] = INK
    # mouth
    my = n - n // 3
    for x in range(n // 3, n - n // 3):
        grid[my][x] = INK
        if n >= 12 and 0 < x - n // 3 < n - 2 * (n // 3) - 1:
            grid[my - 1][x] = FACE
    return grid


def bars_grid(cols: int = 8, rows: int = 1) -> list[list[int]]:
    colors = [0xAD2323, 0xFFEE33, 0x1D6914, 0x29D0D0, 0x2A4BD7, 0x8126C0, 0xFFFFFF, 0x383838]
    grid = []
    for _ in range(rows):
        row = []
        for x in range(cols):
            row.append(colors[x % len(colors)])
        grid.append(row)
    return grid


def post_repl(code: str, url: str = LENS_URL, timeout: int = 25) -> dict:
    req = urllib.request.Request(
        url.rstrip("/") + "/v1/repl",
        data=json.dumps({"code": code}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pattern", choices=("smiley", "bars"), default="smiley")
    parser.add_argument("--url", default=LENS_URL)
    args = parser.parse_args()
    if args.pattern == "bars":
        grid = bars_grid(8, 8)
        cell_w, cell_h = WIDTH // 8, HEIGHT // 8
    else:
        grid = smiley_grid(16)
        cell_w, cell_h = WIDTH // 16, HEIGHT // 16
    code = mosaic_code(grid, cell_w, cell_h)
    out = post_repl(code, url=args.url)
    nrects = len(rle_rects(grid, cell_w, cell_h))
    print(
        json.dumps(
            {
                "ok": out.get("ok"),
                "rects": nrects,
                "repl": out.get("repl"),
                "status": out.get("status"),
                "bytes": len(code),
            }
        )
    )
    return 0 if out.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
