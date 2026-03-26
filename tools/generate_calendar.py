#!/usr/bin/env python3
"""Generate 128x128 monitoring station calendar map art.

Lore: apocalypse on June 20 2042 (summer solstice). Someone at the
monitoring station X'd off each day from June 20 through September 19,
then stopped.
"""

from PIL import Image, ImageDraw

# ---------- tiny 3x5 bitmap font -----------------------------------------
GLYPHS = {
    '0': ["###", "#.#", "#.#", "#.#", "###"],
    '1': [".#.", "##.", ".#.", ".#.", "###"],
    '2': ["###", "..#", "###", "#..", "###"],
    '3': ["###", "..#", "###", "..#", "###"],
    '4': ["#.#", "#.#", "###", "..#", "..#"],
    '5': ["###", "#..", "###", "..#", "###"],
    '6': ["###", "#..", "###", "#.#", "###"],
    '7': ["###", "..#", ".#.", ".#.", ".#."],
    '8': ["###", "#.#", "###", "#.#", "###"],
    '9': ["###", "#.#", "###", "..#", "###"],
    'A': [".#.", "#.#", "###", "#.#", "#.#"],
    'B': ["##.", "#.#", "##.", "#.#", "##."],
    'C': [".##", "#..", "#..", "#..", ".##"],
    'D': ["##.", "#.#", "#.#", "#.#", "##."],
    'E': ["###", "#..", "##.", "#..", "###"],
    'F': ["###", "#..", "##.", "#..", "#.."],
    'G': [".##", "#..", "#.#", "#.#", ".##"],
    'H': ["#.#", "#.#", "###", "#.#", "#.#"],
    'I': ["###", ".#.", ".#.", ".#.", "###"],
    'J': ["###", "..#", "..#", "#.#", ".#."],
    'K': ["#.#", "#.#", "##.", "#.#", "#.#"],
    'L': ["#..", "#..", "#..", "#..", "###"],
    'M': ["#.#", "###", "#.#", "#.#", "#.#"],
    'N': ["#.#", "###", "###", "#.#", "#.#"],
    'O': [".#.", "#.#", "#.#", "#.#", ".#."],
    'P': ["##.", "#.#", "##.", "#..", "#.."],
    'R': ["##.", "#.#", "##.", "#.#", "#.#"],
    'S': [".##", "#..", ".#.", "..#", "##."],
    'T': ["###", ".#.", ".#.", ".#.", ".#."],
    'U': ["#.#", "#.#", "#.#", "#.#", ".#."],
    'V': ["#.#", "#.#", "#.#", ".#.", ".#."],
    'W': ["#.#", "#.#", "#.#", "###", "#.#"],
    'X': ["#.#", "#.#", ".#.", "#.#", "#.#"],
    'Y': ["#.#", "#.#", ".#.", ".#.", ".#."],
    'Z': ["###", "..#", ".#.", "#..", "###"],
    ' ': ["...", "...", "...", "...", "..."],
    '-': ["...", "...", "###", "...", "..."],
    '/': ["..#", "..#", ".#.", "#..", "#.."],
}


def draw_text(draw, x, y, text, color):
    """Draw text using the 3x5 bitmap font with 1px spacing."""
    cx = x
    for ch in text.upper():
        glyph = GLYPHS.get(ch)
        if glyph is None:
            cx += 4
            continue
        for row_i, row in enumerate(glyph):
            for col_i, px in enumerate(row):
                if px == '#':
                    draw.point((cx + col_i, y + row_i), fill=color)
        cx += len(glyph[0]) + 1


def text_width(text):
    """Calculate pixel width of text in 3x5 font."""
    w = 0
    for ch in text.upper():
        glyph = GLYPHS.get(ch)
        if glyph is None:
            w += 4
        else:
            w += len(glyph[0]) + 1
    return max(0, w - 1)  # remove trailing spacing


def draw_x(draw, cx, cy, size, color):
    """Draw an X mark centered at (cx, cy)."""
    hs = size // 2
    for i in range(-hs, hs + 1):
        draw.point((cx + i, cy + i), fill=color)
        draw.point((cx + i, cy - i), fill=color)


# ---------- calendar data ------------------------------------------------

# June 1 2042 = Sunday (day_of_week 0 = Sunday)
MONTHS = [
    # (name, year, days_in_month, first_day_of_week_0sun, x_start_day, x_end_day)
    ("JUN",  2042, 30, 0, 20, 30),   # X from 20th to 30th
    ("JUL",  2042, 31, 2, 1,  31),   # X all days (July 1 = Tuesday)
    ("AUG",  2042, 31, 5, 1,  31),   # X all days (Aug 1 = Friday)
    ("SEP",  2042, 30, 1, 1,  19),   # X from 1st to 19th (Sep 1 = Monday)
]

# Colors
BG           = (235, 225, 210)  # warm paper
GRID_LINE    = (180, 170, 155)  # subtle grid
TITLE_COLOR  = (50, 45, 40)     # dark brown/black
DAY_HDR      = (120, 110, 100)  # muted day headers
DAY_NUM      = (90, 82, 72)     # day numbers
X_COLOR      = (160, 50, 45)    # red pen X marks
BLANK_BEFORE = (210, 200, 185)  # grayed out (before apocalypse)

# Layout
IMG_SIZE = 128
MONTH_W = 58
MONTH_H = 56
COL_GAP = 12
ROW_GAP = 6
LEFT_MARGIN = (IMG_SIZE - MONTH_W * 2 - COL_GAP) // 2
TOP_MARGIN = (IMG_SIZE - MONTH_H * 2 - ROW_GAP) // 2

CELL_W = 8   # width per day column
CELL_H = 7   # height per day row
HEADER_H = 14  # month name + day-of-week headers

DAY_NAMES = "SMTWTFS"


def generate():
    img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), BG)
    draw = ImageDraw.Draw(img)

    for idx, (name, year, days, first_dow, x_start, x_end) in enumerate(MONTHS):
        col = idx % 2
        row = idx // 2
        ox = LEFT_MARGIN + col * (MONTH_W + COL_GAP)
        oy = TOP_MARGIN + row * (MONTH_H + ROW_GAP)

        # Month title centered
        title = f"{name} {year}"
        tw = text_width(title)
        draw_text(draw, ox + (MONTH_W - tw) // 2, oy, title, TITLE_COLOR)

        # Day-of-week headers
        hdr_y = oy + 7
        for d in range(7):
            dx = ox + d * CELL_W + 2
            draw_text(draw, dx, hdr_y, DAY_NAMES[d], DAY_HDR)

        # Grid
        grid_y = hdr_y + 7
        day = 1
        for week_row in range(6):
            for dow in range(7):
                if (week_row == 0 and dow < first_dow) or day > days:
                    continue

                cx = ox + dow * CELL_W
                cy = grid_y + week_row * CELL_H

                # Day number
                num_color = DAY_NUM
                if name == "JUN" and day < 20:
                    num_color = BLANK_BEFORE

                # Draw day number
                num_str = str(day)
                nx = cx + (CELL_W - text_width(num_str)) // 2
                draw_text(draw, nx, cy, num_str, num_color)

                # X mark for tracked days
                if x_start <= day <= x_end:
                    xcx = cx + CELL_W // 2
                    xcy = cy + 2
                    draw_x(draw, xcx, xcy, 4, X_COLOR)

                day += 1

        # Subtle bottom line under last used row
        weeks_used = (first_dow + days + 6) // 7
        line_y = grid_y + weeks_used * CELL_H
        for lx in range(ox, ox + 7 * CELL_W):
            draw.point((lx, line_y), fill=GRID_LINE)

    out_path = "../src/main/resources/data/frozendawn/map_art/monitoring_station_calendar.png"
    img.save(out_path)
    print(f"Saved to {out_path}")


if __name__ == "__main__":
    generate()
