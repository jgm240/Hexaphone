# Hexaphone visual identity

Both the boot animation and default wallpaper are generated from these two
scripts rather than hand-drawn, so the design is easy to tweak and
reproduce exactly. Needs `rsvg-convert` (`brew install librsvg`) and
Pillow (`pip install pillow`).

```bash
python3 gen_wallpaper.py && rsvg-convert -w 2560 -h 1600 -o wallpaper.png wallpaper.svg
cp wallpaper.png ../wallpaper/default_wallpaper.png
cp wallpaper.png ../overlay/frameworks/base/core/res/res/drawable-nodpi/default_wallpaper.png

python3 gen_bootanim.py   # writes bootanimation.zip directly in this dir
cp bootanimation.zip ../bootanimation/bootanimation.zip
```

## The mark

A hexagon (for "Hexa-") with six short radiating lines to satellite dots at
each vertex (a signal/circuit motif, for "-phone"). Same mark used at every
scale: wallpaper, boot animation, and worth reusing for app icons or
launcher branding later rather than inventing a different shape each time.

## Palette

| | Hex | Use |
|---|---|---|
| Background gradient | `#0A0E16` → `#1B2536` | diagonal, near-black navy to dark slate |
| Accent | `#F2B705` (stroke) / `#FFD54A` (highlight) | the hex mark, glow, dots |
| Grid texture | `#8FA3BE` at 5% opacity | honeycomb tiling, background texture only |

## Boot animation structure

`gen_bootanim.py` renders at 1280x800 — half of manta's real 2560x1600
panel, matching `TARGET_BOOTANIMATION_HALF_RES := true` in the device
tree's `lineage_manta.mk` (this device is slow enough that full-res boot
animation frames would be a waste of decode time).

Two parts per the [AOSP bootanimation
format](https://source.android.com/docs/core/display/bootanimation):
`part0` plays once — the mark materializes (scale + rotation + fade-in,
staggered so the inner details settle after the outer hex). `part1` loops
forever until boot completes — a seamless sine-based "breathing" pulse on
the glow and center hex.

## Theme: dark mode + hexagon icons

Both pieces live in `vendor/hexaphone/overlay/frameworks/base/core/res/res/values/config.xml`.
Verified against the actual synced source (`/Volumes/HexaphoneSrc`), not
guessed — see the comments in that file for the exact line numbers checked.

**Dark by default** — `config_defaultNightMode` overridden to `2`
(`MODE_NIGHT_YES`; stock default is `1`/NO). Turned out Settings and
SystemUI in this tree already inherit `Theme.DeviceDefault` (not
`.Light`), so they're dark-themed regardless — this setting is what makes
anything that *does* respect day/night resource qualifiers (third-party
apps, the wallpaper picker) default to dark too, plus it enables
SystemUI's extra dark-mode notification-shade color adaptation
(`StatusBar.DARK_THEME_IN_NIGHT_MODE`). There's no separate user-facing
"dark theme" toggle to wire up — this tree doesn't have one, and Settings
already renders dark.

**Hexagon icons** — `config_icon_mask` overridden with a flat-top regular
hexagon (circumradius 50, same orientation as the mark above) in the
standard 100x100 adaptive-icon viewport. This is a framework-level
setting (`AdaptiveIconDrawable` reads it directly), so it reshapes any
app's icon system-wide — launcher, app drawer, share sheet, recents —
independent of which launcher is installed. `icon_mask_preview.png` in
this dir shows the math checks out: a real icon (Performance's, see
below) rendered flat vs. clipped by this exact mask.

Real limitation, not a bug: this only reshapes apps that ship a proper
`<adaptive-icon>` (foreground+background layers, API 26+). Legacy
square-icon apps are untouched — a resource overlay can't retroactively
add adaptive-icon support to an app that doesn't have it. Our own bundled
apps (DuckDuckGo, F-Droid, Aurora Store) are modern builds and should
already ship adaptive icons, so they pick this up for free. Performance
(`packages/hexaphone/Performance`) was rebuilt as a proper adaptive icon
specifically so it wouldn't be the one hexagon-themed ROM whose own app
icon stayed square.

**Deliberately not done, with reasons:**
- *Quick Settings tile hexagons* — checked the actual source
  (`frameworks/base/packages/SystemUI/res/drawable/qs_tile_background.xml`):
  it's a flat full-bleed color fill for the whole grid cell, not a
  circle/chip like some other Android versions. Hexagons don't tile
  edge-to-edge in a rectangular grid the way rounded rects do — forcing
  this would leave visible gaps between tiles and look broken, not
  themed. Skipped rather than shipping something that looks like a bug.
- *Status bar glyphs (wifi/battery/signal)* — these are small monochrome
  icons, not shapes with a fillable background; "hexagon-ifying" them
  doesn't really mean anything visually, and no ROM does this for a
  reason.
- *Notification large-icon / contact-photo circular clipping* — confirmed
  this is Java code (`RoundedBitmapDrawable`-style clipping in framework/
  launcher code), not a resource. Out of scope for an overlay-only theme
  pass; would need actual framework source changes.
