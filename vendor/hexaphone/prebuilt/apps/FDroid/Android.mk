LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := FDroid
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := APPS
LOCAL_SRC_FILES := FDroid.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

# Keep F-Droid's own signature — normal (non-privileged) system app, and
# staying presigned means it can still self-update through its own repo.
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_DEX_PREOPT := false

include $(BUILD_PREBUILT)
