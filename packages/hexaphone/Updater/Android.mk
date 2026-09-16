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
# Still deliberately unprivileged (no shared UID, no hidden APIs) --
# just platform-signed now, same as Hexaphone Store, so
# REQUEST_INSTALL_PACKAGES (protectionLevel signature|appop) auto-grants
# on signature match for self-updating without an extra "allow this
# source" prompt.
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
