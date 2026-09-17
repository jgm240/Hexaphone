#!/usr/bin/env bash
# Adds a faint hex-grid texture over Trebuchet's all-apps drawer scrim
# (ScrimView.java) -- same "outline hexagons at ~5% opacity" motif
# already used in the boot animation's background
# (vendor/hexaphone/art/gen_bootanim.py's hex_grid()), just drawn
# procedurally in Java here instead of pre-rendered.
#
# Deliberately NOT a tiled bitmap: getting a hex tiling pattern to repeat
# with pixel-perfect seams needs either careful math verified visually
# (which this session can't do without a real device) or trial and
# error against a live screen. Drawing the actual hexagon Path objects
# every time the view size changes has no seam to get wrong -- it's the
# same hex-grid math already used successfully elsewhere this session,
# just walked across the view bounds instead of a fixed canvas size.
#
# Only draws when the scrim itself is showing some color (mCurrentFlatColor
# != 0), so the texture fades in/out with the same drawer-open transition
# as the scrim color already does, rather than needing separate alpha
# logic.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

F="$HEXAPHONE_SRC/packages/apps/Trebuchet/src/com/android/launcher3/views/ScrimView.java"
MARKER="mHexGridPaint"

[ -f "$F" ] || { echo "no ScrimView.java yet, skipping"; exit 0; }

if grep -qF "$MARKER" "$F"; then
  echo "ScrimView.java already patched."
  exit 0
fi

python3 - "$F" << 'EOF'
import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

old_import = "import android.graphics.drawable.Drawable;"
new_import = (
    "import android.graphics.drawable.Drawable;\n"
    "import android.graphics.Paint;\n"
    "import android.graphics.Path;"
)
if old_import not in content:
    print("error: import anchor not found -- file may have changed", file=sys.stderr)
    sys.exit(1)
content = content.replace(old_import, new_import, 1)

old_ctor_end = (
    "        mAM = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);\n"
    "        setFocusable(false);\n"
    "    }"
)
new_ctor_end = (
    "        mAM = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);\n"
    "        setFocusable(false);\n"
    "\n"
    "        // Hexaphone: faint hex-grid texture over the scrim, see\n"
    "        // scripts/patch-trebuchet-drawer-bg.sh.\n"
    "        mHexGridPaint.setStyle(Paint.Style.STROKE);\n"
    "        mHexGridPaint.setStrokeWidth(1.5f);\n"
    "        mHexGridPaint.setColor(0x14FFFFFF);\n"
    "        mHexGridPaint.setAntiAlias(true);\n"
    "    }\n"
    "\n"
    "    private final Paint mHexGridPaint = new Paint();\n"
    "    private final Path mHexGridPath = new Path();\n"
    "    private int mHexGridWidth = -1;\n"
    "    private int mHexGridHeight = -1;\n"
    "\n"
    "    private void buildHexGridPath(int w, int h) {\n"
    "        mHexGridWidth = w;\n"
    "        mHexGridHeight = h;\n"
    "        mHexGridPath.rewind();\n"
    "        float size = 36f;\n"
    "        float hexH = (float) (Math.sqrt(3) * size);\n"
    "        int cols = (int) (w / (1.5f * size)) + 3;\n"
    "        int rows = (int) (h / hexH) + 3;\n"
    "        for (int row = -1; row < rows; row++) {\n"
    "            for (int col = -1; col < cols; col++) {\n"
    "                float cx = col * (1.5f * size);\n"
    "                float cy = row * hexH + ((col % 2 != 0) ? hexH / 2f : 0f);\n"
    "                addHexagon(mHexGridPath, cx, cy, size);\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "\n"
    "    private static void addHexagon(Path path, float cx, float cy, float r) {\n"
    "        for (int i = 0; i < 6; i++) {\n"
    "            double angle = Math.toRadians(60 * i);\n"
    "            float x = (float) (cx + r * Math.cos(angle));\n"
    "            float y = (float) (cy + r * Math.sin(angle));\n"
    "            if (i == 0) {\n"
    "                path.moveTo(x, y);\n"
    "            } else {\n"
    "                path.lineTo(x, y);\n"
    "            }\n"
    "        }\n"
    "        path.close();\n"
    "    }"
)
if old_ctor_end not in content:
    print("error: constructor-end anchor not found -- file may have changed", file=sys.stderr)
    sys.exit(1)
content = content.replace(old_ctor_end, new_ctor_end, 1)

old_ondraw = (
    "    protected void onDraw(Canvas canvas) {\n"
    "        if (mCurrentFlatColor != 0) {\n"
    "            canvas.drawColor(mCurrentFlatColor);\n"
    "        }\n"
    "        drawDragHandle(canvas);\n"
    "    }"
)
new_ondraw = (
    "    protected void onDraw(Canvas canvas) {\n"
    "        if (mCurrentFlatColor != 0) {\n"
    "            canvas.drawColor(mCurrentFlatColor);\n"
    "            if (mHexGridWidth != getWidth() || mHexGridHeight != getHeight()) {\n"
    "                buildHexGridPath(getWidth(), getHeight());\n"
    "            }\n"
    "            canvas.drawPath(mHexGridPath, mHexGridPaint);\n"
    "        }\n"
    "        drawDragHandle(canvas);\n"
    "    }"
)
if old_ondraw not in content:
    print("error: onDraw anchor not found -- file may have changed", file=sys.stderr)
    sys.exit(1)
content = content.replace(old_ondraw, new_ondraw, 1)

with open(path, "w") as f:
    f.write(content)
EOF

echo "ScrimView.java patched."
