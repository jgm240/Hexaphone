#!/usr/bin/env bash
# Makes bootanimation itself check /data/hexaphone_bootscreen/bootanimation.zip
# (written by the Bootscreen app, packages/hexaphone/Bootscreen) before
# falling back to /product, /oem, then /system's own bootanimation.zip.
#
# The original design had a root-run script overwrite
# /system/media/bootanimation.zip directly -- that doesn't compile: AOSP's
# own sepolicy has an unconditional neverallow on any domain writing to
# /system ("Nobody should be doing writes to /system & /vendor... would
# violate important Android security guarantees and invalidate dm-verity
# signatures", system/sepolicy/public/domain.te), discovered on the first
# real build of vendor/hexaphone/sepolicy once it was actually wired into
# the build (see scripts/patch-device-tree.sh). Checking an extra /data
# path instead needs no /system write at all: bootanimation (the existing
# `bootanim` domain, already privileged) just gets a small read-only
# sepolicy grant for the new path (see vendor/hexaphone/sepolicy/hexaphone.te)
# and Bootscreen only ever writes to its own already-permitted data
# directory.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

F="$HEXAPHONE_SRC/frameworks/base/cmds/bootanimation/BootAnimation.cpp"
MARKER="DATA_BOOTANIMATION_FILE"

[ -f "$F" ] || { echo "no BootAnimation.cpp yet, skipping"; exit 0; }

if grep -qF "$MARKER" "$F"; then
  echo "BootAnimation.cpp already patched."
  exit 0
fi

python3 - "$F" << 'EOF'
import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

old_const = 'static const char OEM_BOOTANIMATION_FILE[] = "/oem/media/bootanimation.zip";'
new_const = (
    'static const char OEM_BOOTANIMATION_FILE[] = "/oem/media/bootanimation.zip";\n'
    '// Hexaphone: Bootscreen app (packages/hexaphone/Bootscreen) writes a\n'
    '// hash-verified preset here; checked ahead of everything else so a\n'
    '// picked preset always wins. See scripts/patch-bootanimation.sh.\n'
    'static const char DATA_BOOTANIMATION_FILE[] = "/data/hexaphone_bootscreen/bootanimation.zip";'
)
if old_const not in content:
    print("error: OEM_BOOTANIMATION_FILE const anchor not found -- file may have changed",
          file=sys.stderr)
    sys.exit(1)
content = content.replace(old_const, new_const, 1)

old_array = ('    static const char* bootFiles[] =\n'
             '        {PRODUCT_BOOTANIMATION_FILE, OEM_BOOTANIMATION_FILE, SYSTEM_BOOTANIMATION_FILE};')
new_array = ('    static const char* bootFiles[] =\n'
             '        {DATA_BOOTANIMATION_FILE, PRODUCT_BOOTANIMATION_FILE, OEM_BOOTANIMATION_FILE,\n'
             '         SYSTEM_BOOTANIMATION_FILE};')
if old_array not in content:
    print("error: bootFiles array anchor not found -- file may have changed", file=sys.stderr)
    sys.exit(1)
content = content.replace(old_array, new_array, 1)

# bootFiles and shutdownFiles were the same length (and so the same
# array type) before this patch, which is why the original ternary
# inside a range-for compiled: same-type operands need no conversion, so
# the array type survives into the loop. Adding a 4th bootFiles entry
# breaks that -- a ternary between differently-sized arrays decays both
# to bare const char** (no bounds), which a range-for can't iterate
# ("invalid range expression", hit on the first real compile of this
# patch). Split into two loops instead of trying to keep the array types
# unifiable.
old_loop = ('    for (const char* f : (!mShuttingDown ? bootFiles : shutdownFiles)) {\n'
            '        if (access(f, R_OK) == 0) {\n'
            '            mZipFileName = f;\n'
            '            return NO_ERROR;\n'
            '        }\n'
            '    }')
new_loop = (
    '    if (!mShuttingDown) {\n'
    '        for (const char* f : bootFiles) {\n'
    '            if (access(f, R_OK) == 0) {\n'
    '                mZipFileName = f;\n'
    '                return NO_ERROR;\n'
    '            }\n'
    '        }\n'
    '    } else {\n'
    '        for (const char* f : shutdownFiles) {\n'
    '            if (access(f, R_OK) == 0) {\n'
    '                mZipFileName = f;\n'
    '                return NO_ERROR;\n'
    '            }\n'
    '        }\n'
    '    }'
)
if old_loop not in content:
    print("error: boot/shutdown range-for anchor not found -- file may have changed",
          file=sys.stderr)
    sys.exit(1)
content = content.replace(old_loop, new_loop, 1)

with open(path, "w") as f:
    f.write(content)
EOF

echo "BootAnimation.cpp patched."
