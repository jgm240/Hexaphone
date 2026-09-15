# Hexaphone brand identity, shared across all Hexaphone products.
#
# NOTE: exact CM_/LINEAGE_ variable names below are best-effort from the
# cm-14.1-era LineageOS build system (14.1 was the CyanogenMod -> LineageOS
# transition release). Verify these against vendor/lineage/config/common.mk
# once the source tree is synced (scripts/03-sync.sh) and adjust if the
# actual variable names differ.

HEXAPHONE_BUILDTYPE := UNOFFICIAL
HEXAPHONE_VERSION_MAJOR := 1
HEXAPHONE_VERSION_MINOR := 0

PRODUCT_PROPERTY_OVERRIDES += \
    ro.hexaphone.version=$(HEXAPHONE_VERSION_MAJOR).$(HEXAPHONE_VERSION_MINOR) \
    ro.hexaphone.releasetype=$(HEXAPHONE_BUILDTYPE)
