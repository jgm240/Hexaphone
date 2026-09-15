LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
# Needs android.os.SystemProperties (hidden API, not in the public SDK),
# and runs as sharedUserId=android.uid.system — build against the full
# internal platform API surface rather than a public LOCAL_SDK_VERSION.
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_PACKAGE_NAME := Performance
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)
