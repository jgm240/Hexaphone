# Renders the curated bootanimation.zip variants the Bootscreen app
# (packages/hexaphone/Bootscreen) offers -- same rendering code as
# gen_bootanim.py (the ROM's own baked-in boot animation), just swept
# across a curated color x speed matrix instead of the single brand-amber
# default. Output goes to bootscreen-presets/ (gitignored -- these get
# pushed to the jgm240/hexaphone-apps repo as release assets, not
# committed here) along with a manifest.json the Bootscreen app and its
# catalog entry in AppInstaller-adjacent tooling can both read.
#
# Run from anywhere: python3 gen_bootscreen_presets.py
import hashlib, json, os, shutil

from gen_bootanim import generate

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(SCRIPT_DIR, "bootscreen-presets")

# (id, display name, accent, accent_light, accent_dark)
COLORS = [
    ("amber", "Amber", "#F2B705", "#FFD54A", "#E0980A"),
    ("cyan", "Cyan", "#06B6D4", "#67E8F9", "#0891B2"),
    ("magenta", "Magenta", "#D946EF", "#F0ABFC", "#A21CAF"),
    ("emerald", "Emerald", "#10B981", "#6EE7B7", "#047857"),
]

# (id, display name, spin_multiplier) -- must be an integer so the spin
# stays an exact multiple of 60deg (see gen_bootanim.generate's docstring
# comment on why that's what keeps the loop seamless).
SPEEDS = [
    ("calm", "Calm", 1),
    ("brisk", "Brisk", 3),
]


def main():
    shutil.rmtree(OUT_DIR, ignore_errors=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    work_dir = os.path.join(SCRIPT_DIR, "bootscreen-presets-work")

    presets = []
    for color_id, color_name, accent, accent_light, accent_dark in COLORS:
        for speed_id, speed_name, multiplier in SPEEDS:
            preset_id = f"{color_id}-{speed_id}"
            zip_path = os.path.join(OUT_DIR, f"{preset_id}.zip")
            print(f"rendering {preset_id}...")
            generate(
                accent=accent, accent_light=accent_light, accent_dark=accent_dark,
                spin_multiplier=multiplier, out_zip=zip_path, work_dir=work_dir,
                quiet=True,
            )
            sha256 = hashlib.sha256(open(zip_path, "rb").read()).hexdigest()
            size = os.path.getsize(zip_path)
            presets.append({
                "id": preset_id,
                "displayName": f"{color_name} — {speed_name}",
                "color": color_id,
                "speed": speed_id,
                "file": f"{preset_id}.zip",
                "sha256": sha256,
                "sizeBytes": size,
            })
            print(f"  {size} bytes, sha256 {sha256}")

    shutil.rmtree(work_dir, ignore_errors=True)

    manifest = {"presets": presets}
    manifest_path = os.path.join(OUT_DIR, "manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")

    print(f"\nwrote {len(presets)} presets + manifest.json to {OUT_DIR}")


if __name__ == "__main__":
    main()
