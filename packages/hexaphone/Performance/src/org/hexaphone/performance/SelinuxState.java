package org.hexaphone.performance;

import android.os.SystemProperties;
import android.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Shared by MainActivity's button and SelinuxPermissiveTileService's QS
 * tile so both reflect and drive the same state through
 * vendor/hexaphone/rootdir/etc/init/hexaphone.rc's
 * hexaphone_selinux_permissive service.
 */
final class SelinuxState {
    private static final String TAG = "HexaphoneSystemTweaks";

    // Not persist.* -- a one-shot command ("apply this value now"), not
    // state to reapply at every boot. setenforce 0 itself already only
    // lasts until the next reboot regardless of whether anything ever
    // sets it back to enforcing first.
    static final String PROP_SELINUX_PERMISSIVE = "sys.hexaphone.selinux_permissive";

    private SelinuxState() {}

    /**
     * Reads /sys/fs/selinux/enforce directly -- readable without any
     * special permission since System Tweaks already runs as
     * sharedUserId=android.uid.system. Defaults to true (enforcing) if
     * the read fails, since that's the fail-safe assumption.
     */
    static boolean isEnforcing() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("/sys/fs/selinux/enforce")))
                    .trim();
            return !"0".equals(content);
        } catch (IOException e) {
            Log.w(TAG, "couldn't read /sys/fs/selinux/enforce", e);
            return true;
        }
    }

    static void setPermissive(boolean permissive) {
        SystemProperties.set(PROP_SELINUX_PERMISSIVE, permissive ? "1" : "0");
    }
}
