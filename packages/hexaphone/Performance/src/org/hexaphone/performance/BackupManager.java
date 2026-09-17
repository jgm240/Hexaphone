package org.hexaphone.performance;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Gathers/applies HexaphoneBackup. See that class for why the scope is
 * limited to what this app can actually reach across Android's per-app
 * sandbox.
 */
final class BackupManager {
    private static final String TAG = "HexaphoneSystemTweaks";

    private static final String BOOTSCREEN_PACKAGE = "org.hexaphone.bootscreen";
    private static final String BOOTSCREEN_PREFS = "bootscreen";
    private static final String BOOTSCREEN_KEY_PRESET = "current_preset_id";

    private static final String LIVE_WALLPAPER_PACKAGE = "org.hexaphone.livewallpaper";
    private static final String LIVE_WALLPAPER_PREFS = "live_wallpaper";
    private static final String LIVE_WALLPAPER_KEY_COLOR = "color_id";
    private static final String LIVE_WALLPAPER_SERVICE =
            "org.hexaphone.livewallpaper.HexSpinWallpaperService";

    // Every package Hexaphone Store can install -- see
    // packages/hexaphone/AppInstaller's CatalogEntry / the manifest it
    // fetches. Duplicated here rather than shared: these are separate
    // APKs, there's no library boundary to import a shared constant
    // across.
    private static final String[] STORE_PACKAGES = {
        "com.duckduckgo.mobile.android",
        "org.fdroid.fdroid",
        BOOTSCREEN_PACKAGE,
        "org.hexaphone.updater",
    };

    private BackupManager() {}

    static HexaphoneBackup gather(Context context) {
        HexaphoneBackup backup = new HexaphoneBackup();

        backup.bootscreenPresetId = readCrossAppString(context, BOOTSCREEN_PACKAGE,
                BOOTSCREEN_PREFS, BOOTSCREEN_KEY_PRESET);
        backup.wallpaperColorId = readCrossAppString(context, LIVE_WALLPAPER_PACKAGE,
                LIVE_WALLPAPER_PREFS, LIVE_WALLPAPER_KEY_COLOR);

        WallpaperInfo info = WallpaperManager.getInstance(context).getWallpaperInfo();
        backup.liveWallpaperActive = info != null
                && LIVE_WALLPAPER_PACKAGE.equals(info.getPackageName());

        PackageManager pm = context.getPackageManager();
        for (String pkg : STORE_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                backup.installedPackages.add(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                // Not installed -- fine, just not included.
            }
        }
        return backup;
    }

    /**
     * Writes back the two cross-app preferences and, if the backup says
     * the live wallpaper was active, launches the system's own
     * "set live wallpaper" confirmation (ACTION_CHANGE_LIVE_WALLPAPER is
     * public API; there's no way to silently flip the active wallpaper
     * without the user confirming in that system UI). Store-catalog
     * packages are reported back to the caller to show, not
     * reinstalled automatically -- this app has no install capability
     * of its own, and duplicating Hexaphone Store's download+verify
     * flow here isn't worth it for something the user can do in two taps.
     */
    static void restore(Context context, HexaphoneBackup backup) {
        if (backup.bootscreenPresetId != null) {
            writeCrossAppString(context, BOOTSCREEN_PACKAGE, BOOTSCREEN_PREFS,
                    BOOTSCREEN_KEY_PRESET, backup.bootscreenPresetId);
        }
        if (backup.wallpaperColorId != null) {
            writeCrossAppString(context, LIVE_WALLPAPER_PACKAGE, LIVE_WALLPAPER_PREFS,
                    LIVE_WALLPAPER_KEY_COLOR, backup.wallpaperColorId);
        }
        if (backup.liveWallpaperActive) {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new android.content.ComponentName(LIVE_WALLPAPER_PACKAGE,
                            LIVE_WALLPAPER_SERVICE));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private static String readCrossAppString(Context context, String pkg, String prefsName,
            String key) {
        try {
            Context other = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
            return other.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .getString(key, null);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static void writeCrossAppString(Context context, String pkg, String prefsName,
            String key, String value) {
        try {
            Context other = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
            other.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                    .putString(key, value).apply();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "couldn't restore " + key + " for " + pkg + " (not installed)");
        }
    }
}
