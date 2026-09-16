package org.hexaphone.installer;

/**
 * Tiny in-process pub-sub bridging InstallReceiver (which fires whenever the
 * app happens to be running, possibly backgrounded) back to MainActivity.
 * Only one listener at a time -- MainActivity is the only subscriber this
 * app has. If MainActivity isn't around when a result lands, it just
 * re-syncs each row against PackageManager in onResume instead.
 */
final class InstallResultBus {
    interface Listener {
        void onInstallResult(String packageName, String displayName, boolean success,
                String message);
    }

    private static volatile Listener listener;

    private InstallResultBus() {}

    static void setListener(Listener l) {
        listener = l;
    }

    static void notifyResult(String packageName, String displayName, boolean success,
            String message) {
        Listener l = listener;
        if (l != null) {
            l.onInstallResult(packageName, displayName, success, message);
        }
    }
}
