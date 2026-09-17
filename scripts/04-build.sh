#!/usr/bin/env bash
# Re-sync the overlay trees (cheap, in case they changed) then build.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

"$PROJECT_ROOT/scripts/fetch-prebuilt-apks.sh"
"$PROJECT_ROOT/scripts/patch-device-tree.sh"
"$PROJECT_ROOT/scripts/patch-frameworks-base.sh"
"$PROJECT_ROOT/scripts/patch-mms-service.sh"
"$PROJECT_ROOT/scripts/patch-telecom.sh"
"$PROJECT_ROOT/scripts/patch-imagewallpaper.sh"
"$PROJECT_ROOT/scripts/patch-bootanimation.sh"
"$PROJECT_ROOT/scripts/patch-trebuchet-drawer-bg.sh"
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
# Started at -j4, confirmed -j7 is safe (memory has stayed well within
# budget even in heavy phases); leaves 1 of the container's 8 cores free
# rather than maxing out.
JOBS="${BUILD_JOBS:-7}"

# dex2oatd crashes (SIGABRT, native, inside art::ClassLinker::InitWithoutImage
# during boot-image generation — a core ART bootstrap crash, not a normal
# verifier rejection) trying to precompile the boot image on this tree.
# Root cause not yet isolated — it's crashing before per-class verification
# even starts, so it needs real bisection across the boot classpath dex
# files to locate, not a quick patch like the WiFi/MMS API mismatches.
# WITH_DEXPREOPT=false skips AOT boot-image compilation entirely (device
# falls back to on-device dex2oat / interpretation at first boot — slower
# first boot, no other functional loss) so we can get a working, flashable
# build now rather than block everything on deep ART internals. Revisit:
# flip back to true and re-investigate once the ROM is otherwise verified
# working.
DEXPREOPT="${HEXAPHONE_DEXPREOPT:-false}"

docker_run "
  source build/envsetup.sh &&
  lunch hexaphone_manta-userdebug &&
  WITH_DEXPREOPT=$DEXPREOPT m -j$JOBS bacon
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
