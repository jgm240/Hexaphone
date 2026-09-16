# Debloat list — packages pulled in by full_manta.mk (or what it inherits)
# that we don't want in Hexaphone by default.
#
# NOTE: PRODUCT_PACKAGES_REMOVE is an AOSP mechanism that doesn't exist yet
# in this Pie-era tree (confirmed: zero references anywhere under build/) —
# LineageParts/Eleven/Jelly sat in a PRODUCT_PACKAGES_REMOVE list here for
# a while doing silently nothing before this was caught. The real
# mechanism this vintage needs is filtering PRODUCT_PACKAGES directly,
# which only works placed after the inherits that add these packages
# (see hexaphone_manta.mk's inherit order — this file is included last).
PRODUCT_PACKAGES := $(filter-out \
    LineageParts \
    Eleven \
    Jelly \
    WAPPushManager \
    MmsService \
    Telecom \
    TeleService \
    TelephonyProvider \
    ,$(PRODUCT_PACKAGES))

# Telephony/MMS/telecom apps removed here, not because they're broken —
# they compile fine (see patch-mms-service.sh, patch-telecom.sh for the
# real version-skew bugs that had to be fixed to get them compiling at
# all) — but because manta has no cellular modem or SIM slot, so they're
# ~22MB of pure dead weight. Needed the space: device/samsung/manta's
# BOARD_SYSTEMIMAGE_PARTITION_SIZE is a fixed 800MB (the real, physical
# partition boundary on this hardware — not something we can just bump
# up), and everything Hexaphone wanted to add pushed system.img to
# ~896MB. Combined with dropping DuckDuckGo/FDroid below, this gets
# comfortably back under budget instead of cutting it close.
# Filter-out was already confirmed working correctly for
# LineageParts/Eleven above — the actual system.img was still
# showing them because of stale installed files left over from before
# that fix, not because the mechanism was broken (installclean, or a
# fresh sync, clears that).

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

# System Tweaks (ZRAM / swap file control, SELinux permissive toggle) — see
# packages/hexaphone/Performance. Needs matching sepolicy + an init.rc
# import wired into the device tree; see patches/device/README.md for the
# two-line patch that still needs to be applied once device/samsung/manta
# is synced.
PRODUCT_PACKAGES += \
    Performance

# Hexaphone Store (packages/hexaphone/AppInstaller) -- fetches, hash-verifies,
# and installs DuckDuckGo/F-Droid on demand, since dropping them from the
# system image (see "Bundled third-party apps" below) means they're no
# longer preinstalled. Uses the same pinned name/package/version/sha256
# data as scripts/fetch-prebuilt-apks.sh, just at runtime instead of build
# time.
PRODUCT_PACKAGES += \
    AppInstaller

# Live wallpaper: the boot mark, spinning (packages/hexaphone/LiveWallpaper).
# Selectable from the system wallpaper picker's "Live Wallpapers" category;
# the actual *default* wallpaper stays the static image above (see
# vendor/hexaphone/wallpaper + the drawable-sw*dp-nodpi overlay variants),
# this is an additional option, not a default-wallpaper replacement.
PRODUCT_PACKAGES += \
    LiveWallpaper

# Bootscreen: NOT built into system.img. It's fetched on demand through
# AppInstaller (see CatalogEntry.java) from jgm240/hexaphone-apps, same as
# DuckDuckGo/F-Droid -- see packages/hexaphone/Bootscreen.

PRODUCT_COPY_FILES += \
    vendor/hexaphone/rootdir/etc/init/hexaphone.rc:system/etc/init/hexaphone.rc \
    vendor/hexaphone/bin/hexaphone-zram.sh:system/bin/hexaphone-zram.sh \
    vendor/hexaphone/bin/hexaphone-selinux-permissive.sh:system/bin/hexaphone-selinux-permissive.sh

# Bundled third-party apps, all F-Droid builds kept presigned (see each
# Android.mk under vendor/hexaphone/prebuilt/apps/ for why). None of the
# .apk files are in git — scripts/fetch-prebuilt-apks.sh fetches and
# hash-pins all of them, wired into scripts/04-build.sh.
#
# DuckDuckGo (167MB) and FDroid were both dropped here to fit the 800MB
# system partition (see "system partition size" note below) — Aurora
# Store alone is enough to get any app post-boot, DuckDuckGo included.
# stock Jelly is still removed above regardless (broken/unwanted either
# way), so there's currently no preinstalled browser at all; a user's
# first Aurora Store install should probably be one.
PRODUCT_PACKAGES += \
    AuroraStore
