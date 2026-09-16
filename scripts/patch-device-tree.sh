#!/usr/bin/env bash
# device/samsung/manta needs three small changes, all documented (some for
# a while) but easy to lose track of since they're spread across separate
# files. Idempotent per-fix, safe to call before every build in case the
# device tree gets re-synced from scratch.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

DEVICE_DIR="$HEXAPHONE_SRC/device/samsung/manta"
[ -d "$DEVICE_DIR" ] || { echo "no device/samsung/manta yet, skipping"; exit 0; }

# 1. Kernel Soong bridge -- BoardConfig.mk predates vendor/lineage's
# kernel-header-generation Soong module (vendor/lineage/build/soong/
# Android.bp "generated_kernel_includes"), which expects KERNEL_MAKE_FLAGS
# etc. to be exported to Soong via these two fragments. Without them, `m`
# fails at build.ninja generation with "unknown variable
# '$(KERNEL_MAKE_FLAGS)'" -- hit this on the first real build attempt.
BOARD_CONFIG="$DEVICE_DIR/BoardConfig.mk"
KERNEL_MARKER="include vendor/lineage/config/BoardConfigKernel.mk"
if [ -f "$BOARD_CONFIG" ] && grep -qF "$KERNEL_MARKER" "$BOARD_CONFIG"; then
  echo "BoardConfig.mk: kernel Soong bridge already patched."
else
  cat >> "$BOARD_CONFIG" << 'EOF'

# Hexaphone: this device tree's BoardConfig.mk predates vendor/lineage's
# kernel-header-generation Soong module (vendor/lineage/build/soong/Android.bp
# "generated_kernel_includes"), which expects KERNEL_MAKE_FLAGS etc. to be
# exported to Soong via these two fragments. Without them, `m` fails at
# build.ninja generation with "unknown variable '$(KERNEL_MAKE_FLAGS)'".
# See scripts/patch-device-tree.sh.
include vendor/lineage/config/BoardConfigKernel.mk
include vendor/lineage/config/BoardConfigSoong.mk
EOF
  echo "BoardConfig.mk: kernel Soong bridge patched."
fi

# 2. Register vendor/hexaphone/sepolicy -- without this, hexaphone.te and
# property_contexts (the hexaphone_zram/hexaphone_selinux_permissive domains,
# the persist.sys.hexaphone.* property type) never get compiled into the
# device's sepolicy at all, silently. This was documented as a pending
# patch back when Performance was first written (patches/device/README.md)
# but never actually got applied once the device tree was synced --
# meaning Performance's ZRAM/swap feature has likely never worked on a
# real boot. Caught while wiring up the Bootscreen app's own new sepolicy
# domain and cross-checking why it'd need the same registration.
SEPOLICY_MARKER="vendor/hexaphone/sepolicy"
if [ -f "$BOARD_CONFIG" ] && grep -qF "$SEPOLICY_MARKER" "$BOARD_CONFIG"; then
  echo "BoardConfig.mk: hexaphone sepolicy dir already registered."
else
  cat >> "$BOARD_CONFIG" << 'EOF'

# Hexaphone: without this, vendor/hexaphone/sepolicy/{hexaphone.te,
# property_contexts} never get compiled into the device's sepolicy.
# hexaphone.te is a draft (see the comment at its top) needing a real
# build+boot+logcat iteration pass, not just merging in blind.
BOARD_SEPOLICY_DIRS += vendor/hexaphone/sepolicy
EOF
  echo "BoardConfig.mk: hexaphone sepolicy dir registered."
fi

# 3. Import hexaphone.rc -- vendor/hexaphone/rootdir/etc/init/hexaphone.rc
# gets copied to system/etc/init/hexaphone.rc at build time (see
# vendor/hexaphone/hexaphone.mk), but init doesn't auto-import every .rc
# file in that directory on this tree, so without an explicit import the
# hexaphone_zram and hexaphone_selinux_permissive services are simply never
# defined. Same "documented but never actually applied" story as #2 above.
INIT_RC="$DEVICE_DIR/init.manta.rc"
IMPORT_MARKER="import /system/etc/init/hexaphone.rc"
if [ -f "$INIT_RC" ] && grep -qF "$IMPORT_MARKER" "$INIT_RC"; then
  echo "init.manta.rc: hexaphone.rc already imported."
else
  # Prepend rather than append -- import lines conventionally sit at the
  # top of this file, and it's harmless either way since import order
  # only matters between rc files that reference the same section names.
  printf '%s\n%s\n' "$IMPORT_MARKER" "$(cat "$INIT_RC")" > "$INIT_RC"
  echo "init.manta.rc: hexaphone.rc import added."
fi
