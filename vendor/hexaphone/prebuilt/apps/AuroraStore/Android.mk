LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := AuroraStore
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := APPS
LOCAL_SRC_FILES := AuroraStore.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

# Kept as a normal (non-privileged) system app, presigned with Aurora's own
# key. Not made a priv-app: silent/no-prompt installs would need it
# platform-signed and priv-app-whitelisted, which isn't possible without
# rebuilding the APK ourselves (we're bundling F-Droid's presigned build,
# not building from source) and would break its own self-update path
# anyway. Users get the normal one-time "install unknown apps" prompt for
# it, same as installing any other app store equivalent.
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_DEX_PREOPT := false

include $(BUILD_PREBUILT)
