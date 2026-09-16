LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
# Not "Updater" -- collides with packages/apps/Updater, LineageOS's own
# (defunct, hasn't received updates since 2016) updater module, which is
# still part of this tree even though we don't include it in
# PRODUCT_PACKAGES.
LOCAL_PACKAGE_NAME := HexaphoneUpdater
# Deliberately unprivileged and default-signed: unlike Bootscreen and
# Hexaphone Store, this app has no need to write anywhere but its own
# external files dir, so it needs no shared UID, no platform signature,
# and no hidden APIs.
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
