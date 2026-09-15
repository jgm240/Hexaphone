#!/usr/bin/env bash
# Re-sync the overlay trees (cheap, in case they changed) then build.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

"$PROJECT_ROOT/scripts/fetch-prebuilt-apks.sh"
"$PROJECT_ROOT/scripts/patch-device-tree.sh"
sync_overlay_trees

# `breakfast hexaphone_manta` (no dash) would wrongly assume it's a stock
# device codename and try `lunch lineage_hexaphone_manta-userdebug` (see
# the actual function in vendor/lineage/build/envsetup.sh — it only treats
# the argument as a direct lunch target if it contains a "-"). Skip
# breakfast entirely and lunch our product directly; vendor/hexaphone's
# vendorsetup.sh (which registers it) is already auto-sourced by the
# generic vendor/*/vendorsetup.sh scan in build/envsetup.sh.
#
# Also skip `mka` (`m -j "$@"`, i.e. an unqualified -j — dangerous with
# Docker only getting 8GB RAM here) in favor of an explicit job count.
# Started at -j4, confirmed -j6 is safe (plenty of memory headroom); leaves
# 2 of the container's 8 cores free rather than maxing out.
JOBS="${BUILD_JOBS:-6}"

docker_run "
  source build/envsetup.sh &&
  lunch hexaphone_manta-userdebug &&
  m -j$JOBS bacon
"

# bacon.mk names its output lineage-$(LINEAGE_VERSION).zip — copy (not
# rename in place) to our own name/version so the LineageOS-named original
# and its .md5sum are still there if anything expects that name.
OUT_DIR="$HEXAPHONE_SRC/out/target/product/manta"
VERSION="${HEXAPHONE_BUILD_VERSION:-1.0.0}"
DEST="$OUT_DIR/hexaphone-alphav${VERSION}.zip"

SRC_ZIP=$(ls -t "$OUT_DIR"/lineage-*.zip 2>/dev/null | head -1)
if [ -n "$SRC_ZIP" ]; then
  cp "$SRC_ZIP" "$DEST"
  # Content is byte-identical to the original (straight copy), so its
  # existing checksum is still valid — just relabel it rather than
  # recomputing (avoids depending on md5sum/md5 being on the host's PATH).
  if [ -f "$SRC_ZIP.md5sum" ]; then
    hash=$(awk '{print $1}' "$SRC_ZIP.md5sum")
    echo "$hash  $(basename "$DEST")" > "$DEST.md5sum"
  fi
  echo "Build finished: $DEST"
else
  echo "Build finished, but no lineage-*.zip found under $OUT_DIR — check the log above." >&2
fi
