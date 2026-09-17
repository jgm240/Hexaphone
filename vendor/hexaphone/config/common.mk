# Hexaphone brand identity, shared across all Hexaphone products.
#
# NOTE: exact CM_/LINEAGE_ variable names below are best-effort from the
# cm-14.1-era LineageOS build system (14.1 was the CyanogenMod -> LineageOS
# transition release). Verify these against vendor/lineage/config/common.mk
# once the source tree is synced (scripts/03-sync.sh) and adjust if the
# actual variable names differ.

# Bump these to match the git tag (alphavX.Y.0) each time a release is
# tagged -- ro.hexaphone.version is the only on-device record of which
# release is actually installed. Was stuck at 1.0 through the alphav1.1.0
# release (nothing wired this to the tag automatically); caught while
# building Hexaphone Updater (packages/hexaphone/Updater). Updater itself
# still doesn't read this back -- its self-update/What's New logic
# compares against jgm240/hexaphone-apps' manifest.json and
# Build.VERSION.INCREMENTAL instead, both public APIs, and that's kept
# even though Updater is a priv-app as of this release so it could now
# read hidden SystemProperties -- no reason to trade a working
# public-API mechanism for a hidden one.
HEXAPHONE_BUILDTYPE := UNOFFICIAL
HEXAPHONE_VERSION_MAJOR := 1
HEXAPHONE_VERSION_MINOR := 2

PRODUCT_PROPERTY_OVERRIDES += \
    ro.hexaphone.version=$(HEXAPHONE_VERSION_MAJOR).$(HEXAPHONE_VERSION_MINOR) \
    ro.hexaphone.releasetype=$(HEXAPHONE_BUILDTYPE)
