# Debloat list — packages pulled in by full_manta.mk that we don't want in
# Hexaphone by default. Starting point only; review against the actual
# PRODUCT_PACKAGES set once synced (device/samsung/manta/full_manta.mk and
# whatever it inherits) since package names must match exactly or the build
# just silently keeps them.

PRODUCT_PACKAGES_REMOVE += \
    LineageParts \
    Eleven \
    Jelly

# Overlay (colors/fonts/icons) applied on top of frameworks/base — see
# vendor/hexaphone/overlay/. Kept as a separate device overlay path so it's
# easy to diff against upstream.
DEVICE_PACKAGE_OVERLAYS += vendor/hexaphone/overlay

# Boot animation — hexagon mark materializing then breathing, at
# 1280x800 (TARGET_BOOTANIMATION_HALF_RES is set in the device tree's
# lineage_manta.mk, so half of manta's real 2560x1600 panel).
PRODUCT_COPY_FILES += \
    vendor/hexaphone/bootanimation/bootanimation.zip:system/media/bootanimation.zip

# Default wallpaper — same hexagon mark, full 2560x1600. Overrides the
# framework's default_wallpaper drawable via the overlay path above rather
# than a PRODUCT_COPY_FILES system/media path, since the exact copy-file
# convention for that has drifted across AOSP versions and the resource
# overlay approach is stable back to well before this era.
# (vendor/hexaphone/overlay/frameworks/base/core/res/res/drawable-nodpi/default_wallpaper.png)

# Performance app (ZRAM / swap file control) — see
# packages/hexaphone/Performance. Needs matching sepolicy + an init.rc
# import wired into the device tree; see patches/device/README.md for the
# two-line patch that still needs to be applied once device/samsung/manta
# is synced.
PRODUCT_PACKAGES += \
    Performance

PRODUCT_COPY_FILES += \
    vendor/hexaphone/rootdir/etc/init/hexaphone.rc:system/etc/init/hexaphone.rc \
    vendor/hexaphone/bin/hexaphone-zram.sh:system/bin/hexaphone-zram.sh

# Bundled third-party apps, all F-Droid builds kept presigned (see each
# Android.mk under vendor/hexaphone/prebuilt/apps/ for why). None of the
# .apk files are in git — scripts/fetch-prebuilt-apks.sh fetches and
# hash-pins all of them, wired into scripts/04-build.sh.
#
# - DuckDuckGo: default browser now that stock Jelly is removed above.
# - FDroid + AuroraStore: app stores. There's no Play Store/GApps on this
#   ROM, so these are how a user actually installs anything post-setup —
#   F-Droid for FOSS apps, Aurora Store as a GApps-free Play Store client.
PRODUCT_PACKAGES += \
    DuckDuckGo \
    FDroid \
    AuroraStore
