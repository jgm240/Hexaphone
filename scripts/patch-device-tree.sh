#!/usr/bin/env bash
# device/samsung/manta/BoardConfig.mk predates vendor/lineage's
# kernel-header-generation Soong module
# (vendor/lineage/build/soong/Android.bp "generated_kernel_includes"),
# which expects KERNEL_MAKE_FLAGS etc. to be exported to Soong via
# vendor/lineage/config/BoardConfigKernel.mk + BoardConfigSoong.mk. Without
# them, `m` fails at build.ninja generation with
# "unknown variable '$(KERNEL_MAKE_FLAGS)'" — hit this on the first real
# build attempt. Idempotent, safe to call before every build in case the
# device tree gets re-synced from scratch.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

F="$HEXAPHONE_SRC/device/samsung/manta/BoardConfig.mk"
MARKER="include vendor/lineage/config/BoardConfigKernel.mk"

[ -f "$F" ] || { echo "no BoardConfig.mk yet, skipping"; exit 0; }

if grep -qF "$MARKER" "$F"; then
  echo "BoardConfig.mk already patched."
  exit 0
fi

cat >> "$F" << 'EOF'

# Hexaphone: this device tree's BoardConfig.mk predates vendor/lineage's
# kernel-header-generation Soong module (vendor/lineage/build/soong/Android.bp
# "generated_kernel_includes"), which expects KERNEL_MAKE_FLAGS etc. to be
# exported to Soong via these two fragments. Without them, `m` fails at
# build.ninja generation with "unknown variable '$(KERNEL_MAKE_FLAGS)'".
# See scripts/patch-device-tree.sh.
include vendor/lineage/config/BoardConfigKernel.mk
include vendor/lineage/config/BoardConfigSoong.mk
EOF

echo "BoardConfig.mk patched."
