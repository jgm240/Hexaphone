#!/usr/bin/env bash
# Bug: picking any wallpaper other than the default goes blank, and stays
# blank across reboots until factory reset.
#
# Root cause, traced through frameworks/base/core/java/android/app/
# WallpaperManager.java: SystemUI's ImageWallpaper (this file) loads the
# wallpaper via mWallpaperManager.getBitmap(true /* hardware */) at three
# call sites. That "hardware" flag only affects the *custom* wallpaper
# path -- WallpaperManager$Globals.getCurrentWallpaperLocked() sets
# BitmapFactory.Options.inPreferredConfig = Bitmap.Config.HARDWARE, which
# requires the gralloc/HWC stack to back a GPU-resident bitmap. manta's
# HWC is already known-broken here (see README "Hardware composer partly
# broken" under inherited issues) so this decode silently fails.
# getDefaultWallpaper() -- the code path used only when no custom
# wallpaper file exists -- never sets that option, always decoding a
# plain software bitmap, which is exactly why the *default* wallpaper
# still renders fine and nothing else does.
#
# "Stays blank until factory reset": on failure, ImageWallpaper.loadWallpaper()
# calls mWallpaperManager.clear() and retries getBitmap(true) once more --
# same broken hardware decode, fails again, gives up and draws nothing.
# The underlying HWC problem never changes, so every later redraw
# (rotation, unlock, reboot) repeats the same failure. clear() doesn't
# delete the already-selected wallpaper file, so nothing recovers until
# factory reset wipes it and the default (software-decoded) path takes
# over again.
#
# Fix: don't request a hardware-backed bitmap for the wallpaper on this
# device -- decode it the same way the default wallpaper already does
# successfully. Costs a bit more Java-heap memory during wallpaper load
# (a real tradeoff on a 2GB device) but a working wallpaper beats a
# memory-optimized blank one. Scoped to SystemUI's ImageWallpaper only,
# not the shared WallpaperManager API surface every app uses.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

F="$HEXAPHONE_SRC/frameworks/base/packages/SystemUI/src/com/android/systemui/ImageWallpaper.java"
MARKER="hexaphone: manta's HWC can't back hardware bitmaps"

[ -f "$F" ] || { echo "no ImageWallpaper.java yet, skipping"; exit 0; }

if grep -qF "$MARKER" "$F"; then
  echo "ImageWallpaper.java already patched."
  exit 0
fi

python3 - "$F" << 'EOF'
import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

class_anchor = "public class ImageWallpaper extends WallpaperService {"
comment = (
    "/*\n"
    " * hexaphone: manta's HWC can't back hardware bitmaps -- every\n"
    " * hardware-bitmap getBitmap() call below is forced to a software\n"
    " * decode instead. See scripts/patch-imagewallpaper.sh for the full\n"
    " * root-cause writeup (blank wallpaper on anything but the default,\n"
    " * persisting until factory reset).\n"
    " */\n"
)

if class_anchor not in content:
    print("error: class declaration anchor not found -- file may have changed",
          file=sys.stderr)
    sys.exit(1)
content = content.replace(class_anchor, comment + class_anchor, 1)

old = "getBitmap(true /* hardware */)"
new = "getBitmap(false /* hardware */)"
count = content.count(old)
if count != 3:
    print(f"error: expected 3 occurrences of {old!r}, found {count} "
          "-- file may have changed", file=sys.stderr)
    sys.exit(1)
content = content.replace(old, new, 3)

with open(path, "w") as f:
    f.write(content)
EOF

echo "ImageWallpaper.java patched."
