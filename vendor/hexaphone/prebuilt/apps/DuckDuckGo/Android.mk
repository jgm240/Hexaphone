LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := DuckDuckGo
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := APPS
LOCAL_SRC_FILES := DuckDuckGo.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

# Keep F-Droid's own signature rather than re-signing with the platform
# key — this is a normal (non-privileged) system app, doesn't need
# platform signing, and staying presigned means it can still receive
# updates through F-Droid itself later without a signature mismatch.
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_DEX_PREOPT := false

include $(BUILD_PREBUILT)
