# Regenerates bootanimation.zip. Needs rsvg-convert (brew install librsvg).
# Run from anywhere:
#   python3 gen_bootanim.py
#   cp bootanimation.zip ../bootanimation/bootanimation.zip
import math, os, subprocess, shutil, zipfile

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

W, H = 1280, 800   # TARGET_BOOTANIMATION_HALF_RES := true -> half of 2560x1600
FPS = 30
CX, CY = W * 0.5, H * 0.46
R_MAX = 200
R_INNER = 124
R_CENTER = 28

OUT = os.path.join(SCRIPT_DIR, "bootanim")
shutil.rmtree(OUT, ignore_errors=True)
os.makedirs(f"{OUT}/part0", exist_ok=True)
os.makedirs(f"{OUT}/part1", exist_ok=True)


def hex_points(cx, cy, r, rotation_deg=0):
    pts = []
    for i in range(6):
        angle = math.radians(60 * i + rotation_deg)
        pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in pts)


def hex_grid(rows, cols, size, x0, y0):
    out = []
    h = math.sqrt(3) * size
    for row in range(rows):
        for col in range(cols):
            cx = x0 + col * (1.5 * size)
            cy = y0 + row * h + (h / 2 if col % 2 else 0)
            out.append(f'<polygon points="{hex_points(cx, cy, size)}" />')
    return "\n".join(out)


GRID = hex_grid(16, 16, 55, -60, -60)

DEFS = '''
<defs>
  <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
    <stop offset="0%" stop-color="#0A0E16"/>
    <stop offset="55%" stop-color="#121A26"/>
    <stop offset="100%" stop-color="#1B2536"/>
  </linearGradient>
  <radialGradient id="glow" cx="50%" cy="50%" r="50%">
    <stop offset="0%" stop-color="#F2B705" stop-opacity="GLOWOP"/>
    <stop offset="45%" stop-color="#F2B705" stop-opacity="GLOWOP2"/>
    <stop offset="100%" stop-color="#F2B705" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="markStroke" x1="0%" y1="0%" x2="100%" y2="100%">
    <stop offset="0%" stop-color="#FFD54A"/>
    <stop offset="100%" stop-color="#E0980A"/>
  </linearGradient>
</defs>
'''


def ease_out_cubic(t):
    return 1 - (1 - t) ** 3


def frame_svg(outer_r, outer_rot, outer_op, inner_op, center_scale, center_op,
              lines_op, dots_op, glow_op):
    defs = DEFS.replace("GLOWOP", f"{0.22*glow_op:.3f}").replace("GLOWOP2", f"{0.08*glow_op:.3f}")
    outer_pts = hex_points(CX, CY, outer_r, outer_rot)
    inner_pts = hex_points(CX, CY, R_INNER * (outer_r / R_MAX), outer_rot + 30)
    center_r = R_CENTER * center_scale
    center_pts = hex_points(CX, CY, center_r)
    dots = "".join(
        f'<circle cx="{CX + outer_r*math.cos(math.radians(60*i+outer_rot)):.2f}" '
        f'cy="{CY + outer_r*math.sin(math.radians(60*i+outer_rot)):.2f}" r="4.5" '
        f'fill="#FFD54A" opacity="{dots_op:.3f}"/>'
        for i in range(6)
    )
    lines = "".join(
        f'<line x1="{CX + (center_r+6)*math.cos(math.radians(60*i+outer_rot)):.2f}" '
        f'y1="{CY + (center_r+6)*math.sin(math.radians(60*i+outer_rot)):.2f}" '
        f'x2="{CX + (outer_r-5)*math.cos(math.radians(60*i+outer_rot)):.2f}" '
        f'y2="{CY + (outer_r-5)*math.sin(math.radians(60*i+outer_rot)):.2f}"/>'
        for i in range(6)
    )
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
  {defs}
  <rect width="{W}" height="{H}" fill="url(#bg)"/>
  <g stroke="#8FA3BE" stroke-width="0.7" fill="none" opacity="0.05">{GRID}</g>
  <circle cx="{CX}" cy="{CY}" r="380" fill="url(#glow)"/>
  <polygon points="{outer_pts}" fill="none" stroke="url(#markStroke)" stroke-width="3.5" opacity="{outer_op:.3f}"/>
  <polygon points="{inner_pts}" fill="none" stroke="#F2B705" stroke-width="1.8" opacity="{inner_op:.3f}"/>
  <g stroke="#F2B705" stroke-width="1" opacity="{lines_op:.3f}">{lines}</g>
  {dots}
  <polygon points="{center_pts}" fill="#F2B705" opacity="{center_op:.3f}"/>
</svg>'''


def render(svg_str, path):
    tmp_svg = path + ".svg"
    with open(tmp_svg, "w") as f:
        f.write(svg_str)
    subprocess.run(["rsvg-convert", "-w", str(W), "-h", str(H), "-o", path, tmp_svg], check=True)
    os.remove(tmp_svg)


# ---- part0: intro, hexagon materializes (0.9s) ----
N_INTRO = 27
for i in range(N_INTRO):
    t = i / (N_INTRO - 1)
    e = ease_out_cubic(t)
    outer_r = 40 + (R_MAX - 40) * e
    outer_rot = -25 * (1 - e)
    outer_op = min(1.0, e * 1.3)
    delay = max(0.0, (t - 0.35) / 0.65)
    d = ease_out_cubic(min(1.0, delay))
    svg = frame_svg(
        outer_r=outer_r, outer_rot=outer_rot, outer_op=outer_op,
        inner_op=0.55 * d, center_scale=0.6 + 0.4 * d, center_op=0.95 * d,
        lines_op=0.35 * d, dots_op=0.85 * d, glow_op=e,
    )
    render(svg, f"{OUT}/part0/{i:05d}.png")

# hold last intro frame briefly so the loop hand-off doesn't feel abrupt
for j in range(6):
    shutil.copy(f"{OUT}/part0/{N_INTRO-1:05d}.png", f"{OUT}/part0/{N_INTRO+j:05d}.png")

# ---- part1: seamless breathing loop ----
N_LOOP = 40
for i in range(N_LOOP):
    t = i / N_LOOP
    pulse = math.sin(2 * math.pi * t)  # -1..1, seamless
    glow_op = 0.85 + 0.15 * pulse
    center_scale = 1.0 + 0.06 * pulse
    outer_op = 0.92 + 0.08 * pulse
    svg = frame_svg(
        outer_r=R_MAX, outer_rot=0, outer_op=outer_op,
        inner_op=0.55, center_scale=center_scale, center_op=0.95,
        lines_op=0.35, dots_op=0.85, glow_op=glow_op,
    )
    render(svg, f"{OUT}/part1/{i:05d}.png")

with open(f"{OUT}/desc.txt", "w") as f:
    f.write(f"{W} {H} {FPS}\n")
    f.write(f"p 1 0 part0\n")
    f.write(f"p 0 0 part1\n")

print("frames rendered:", len(os.listdir(f"{OUT}/part0")), len(os.listdir(f"{OUT}/part1")))

# ---- assemble bootanimation.zip (stored, not deflated, per AOSP convention) ----
zip_path = os.path.join(SCRIPT_DIR, "bootanimation.zip")
if os.path.exists(zip_path):
    os.remove(zip_path)

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_STORED) as z:
    z.write(f"{OUT}/desc.txt", "desc.txt")
    for part in ["part0", "part1"]:
        for fname in sorted(os.listdir(f"{OUT}/{part}")):
            z.write(f"{OUT}/{part}/{fname}", f"{part}/{fname}")

print("wrote", zip_path, os.path.getsize(zip_path), "bytes")

print("frames rendered:", len(os.listdir(f"{OUT}/part0")), len(os.listdir(f"{OUT}/part1")))
