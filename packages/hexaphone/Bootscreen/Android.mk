LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_PACKAGE_NAME := Bootscreen
# Platform-signed so it can join sharedUserId=android.uid.system even
# though it's sideloaded (via AppInstaller) rather than built into
# system.img -- signature match is what gates sharedUserId membership,
# not partition placement. Not LOCAL_PRIVILEGED_MODULE: that only means
# something for an app actually placed under /system/priv-app, which this
# one never is.
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
