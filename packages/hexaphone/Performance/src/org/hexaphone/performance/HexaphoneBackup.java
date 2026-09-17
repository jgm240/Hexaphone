package org.hexaphone.performance;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What gets captured/restored. Deliberately limited to state this app
 * can actually reach: Android sandboxes each app's private data by UID,
 * so a value here is either something read via public PackageManager
 * APIs (works for any package) or another app's SharedPreferences read
 * via createPackageContext (only works for apps sharing this UID --
 * Bootscreen and LiveWallpaper, both android.uid.system for exactly this
 * reason). There's no mechanism for a real factory reset to preserve
 * anything automatically; this is a manual export the user moves
 * off-device (USB/adb) before wiping, and a manual import after.
 */
final class HexaphoneBackup {
    String bootscreenPresetId;
    String wallpaperColorId;
    boolean liveWallpaperActive;
    final List<String> installedPackages = new ArrayList<String>();

    JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("bootscreen_preset_id", bootscreenPresetId);
        root.put("wallpaper_color_id", wallpaperColorId);
        root.put("live_wallpaper_active", liveWallpaperActive);
        root.put("installed_packages", new JSONArray(installedPackages));
        return root;
    }

    static HexaphoneBackup fromJson(JSONObject root) throws JSONException {
        HexaphoneBackup backup = new HexaphoneBackup();
        backup.bootscreenPresetId = root.isNull("bootscreen_preset_id") ? null
                : root.getString("bootscreen_preset_id");
        backup.wallpaperColorId = root.isNull("wallpaper_color_id") ? null
                : root.getString("wallpaper_color_id");
        backup.liveWallpaperActive = root.optBoolean("live_wallpaper_active", false);
        JSONArray packages = root.optJSONArray("installed_packages");
        if (packages != null) {
            for (int i = 0; i < packages.length(); i++) {
                backup.installedPackages.add(packages.getString(i));
            }
        }
        return backup;
    }
}
