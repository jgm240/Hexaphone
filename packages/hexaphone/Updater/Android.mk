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
LOCAL_CERTIFICATE := platform
# Priv-app, baked into system.img (see hexaphone.mk) -- needed for
# one-tap flash: writing /cache/recovery/command only clears SELinux for
# the real priv_app domain (system/sepolicy/private/priv_app.te), which
# only applies to an app actually installed under /system/priv-app, not
# to a platform-signed-but-unprivileged app installed via PackageInstaller.
# REBOOT and ACCESS_CACHE_FILESYSTEM (both signature|privileged) also
# need the explicit privapp-permissions whitelist entry this pulls in --
# see vendor/hexaphone/etc/permissions/privapp-permissions-hexaphone-updater.xml.
# A future update still reaches this same install location via
# self-update (PackageInstaller replacing an already-privileged app keeps
# its privileged status), so this only needs doing once.
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
