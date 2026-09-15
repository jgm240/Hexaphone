# Regenerates the default wallpaper. Needs rsvg-convert (brew install
# librsvg). Run from anywhere:
#   python3 gen_wallpaper.py
#   rsvg-convert -w 2560 -h 1600 -o wallpaper.png wallpaper.svg
#   cp wallpaper.png ../overlay/frameworks/base/core/res/res/drawable-nodpi/default_wallpaper.png
#   cp wallpaper.png ../wallpaper/default_wallpaper.png
import math
import os

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

W, H = 2560, 1600

def hex_points(cx, cy, r, rotation_deg=0):
    pts = []
    for i in range(6):
        angle = math.radians(60 * i + rotation_deg)
        pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in pts)

# Faint background hex-grid (flat-top hexagons), pointy layout
def hex_grid(rows, cols, size, x0, y0):
    out = []
    w = size * 2
    h = math.sqrt(3) * size
    for row in range(rows):
        for col in range(cols):
            cx = x0 + col * (1.5 * size)
            cy = y0 + row * h + (h / 2 if col % 2 else 0)
            out.append(f'<polygon points="{hex_points(cx, cy, size)}" />')
    return "\n".join(out)

grid_size = 70
grid = hex_grid(30, 30, grid_size, -100, -100)

cx, cy = W * 0.5, H * 0.46

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0A0E16"/>
      <stop offset="55%" stop-color="#121A26"/>
      <stop offset="100%" stop-color="#1B2536"/>
    </linearGradient>
    <radialGradient id="glow" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#F2B705" stop-opacity="0.22"/>
      <stop offset="45%" stop-color="#F2B705" stop-opacity="0.08"/>
      <stop offset="100%" stop-color="#F2B705" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="markStroke" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFD54A"/>
      <stop offset="100%" stop-color="#E0980A"/>
    </linearGradient>
    <radialGradient id="vignette" cx="50%" cy="50%" r="75%">
      <stop offset="60%" stop-color="#000000" stop-opacity="0"/>
      <stop offset="100%" stop-color="#000000" stop-opacity="0.35"/>
    </radialGradient>
  </defs>

  <rect width="{W}" height="{H}" fill="url(#bg)"/>

  <g stroke="#8FA3BE" stroke-width="1.1" fill="none" opacity="0.05">
    {grid}
  </g>

  <circle cx="{cx}" cy="{cy}" r="760" fill="url(#glow)"/>

  <!-- outer hex mark -->
  <polygon points="{hex_points(cx, cy, 420)}" fill="none" stroke="url(#markStroke)" stroke-width="6" opacity="0.9"/>
  <polygon points="{hex_points(cx, cy, 420)}" fill="none" stroke="#F2B705" stroke-width="18" opacity="0.06"/>

  <!-- inner rotated hex -->
  <polygon points="{hex_points(cx, cy, 260, 30)}" fill="none" stroke="#F2B705" stroke-width="3" opacity="0.55"/>

  <!-- six small satellite dots at outer vertices -->
  {"".join(f'<circle cx="{cx + 420*math.cos(math.radians(60*i)):.2f}" cy="{cy + 420*math.sin(math.radians(60*i)):.2f}" r="9" fill="#FFD54A" opacity="0.85"/>' for i in range(6))}

  <!-- center mark -->
  <polygon points="{hex_points(cx, cy, 58)}" fill="#F2B705" opacity="0.95"/>
  <polygon points="{hex_points(cx, cy, 58)}" fill="none" stroke="#0A0E16" stroke-width="3.5" opacity="0.4"/>

  <!-- thin connecting lines from center hex to outer vertices, tech/circuit feel -->
  <g stroke="#F2B705" stroke-width="1.6" opacity="0.35">
    {"".join(f'<line x1="{cx + 64*math.cos(math.radians(60*i)):.2f}" y1="{cy + 64*math.sin(math.radians(60*i)):.2f}" x2="{cx + 414*math.cos(math.radians(60*i)):.2f}" y2="{cy + 414*math.sin(math.radians(60*i)):.2f}"/>' for i in range(6))}
  </g>

  <rect width="{W}" height="{H}" fill="url(#vignette)"/>
</svg>
'''

with open(os.path.join(OUT_DIR, "wallpaper.svg"), "w") as f:
    f.write(svg)
print("wrote wallpaper.svg")
