LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_PACKAGE_NAME := AppInstaller
# Platform-signed (not privileged, no sharedUserId -- this app has no need
# for hidden/system APIs) purely so REQUEST_INSTALL_PACKAGES
# (protectionLevel signature|appop) auto-grants on signature match alone.
# Without that, the user would have to flip "allow installs from this
# source" in Settings once before the first install; with it, they go
# straight to the normal per-app PackageInstaller confirmation dialog,
# same as any real app store would show.
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
