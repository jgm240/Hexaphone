package org.hexaphone.updater;

import android.content.Context;
import android.os.PowerManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Writes /cache/recovery/command and reboots straight into recovery
 * (TWRP), the same convention fastboot/adb's own --update_package flow
 * uses. Only reachable from a real priv-app install (see Android.mk) --
 * on an unprivileged install (e.g. someone reinstalled this from
 * Hexaphone Store's catalog rather than getting it baked into
 * system.img) this throws, and the caller should fall back to telling
 * the user to flash by hand from recovery instead.
 */
class RecoveryFlasher {
    private RecoveryFlasher() {}

    static void flash(Context context, File zipFile) throws IOException {
        File commandFile = new File("/cache/recovery/command");
        FileWriter writer = new FileWriter(commandFile);
        try {
            writer.write("--update_package=" + toRecoveryPath(zipFile) + "\n");
        } finally {
            writer.close();
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.reboot("recovery");
    }

    /**
     * Recovery has no FUSE/sdcardfs runtime layer, so the app-visible
     * /storage/emulated/<n>/... path this file actually lives under
     * (from getExternalFilesDir()) isn't valid there -- substitute the
     * raw backing path recovery can actually read. This is the standard
     * Android storage convention: /data/media/<n>/... is what
     * /storage/emulated/<n>/... is bind-mounted from, for any user id.
     */
    private static String toRecoveryPath(File file) {
        return file.getAbsolutePath().replaceFirst("^/storage/emulated/", "/data/media/");
    }
}
