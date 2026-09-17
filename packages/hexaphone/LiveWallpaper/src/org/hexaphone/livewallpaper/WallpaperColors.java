package org.hexaphone.livewallpaper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Same curated color palette as Bootscreen's presets
 * (vendor/hexaphone/art/gen_bootscreen_presets.py's COLORS list) --
 * keeping the two in sync means picking a color here and picking the
 * matching boot animation preset actually look like the same choice.
 */
final class WallpaperColors {
    static final String PREFS_NAME = "live_wallpaper";
    static final String KEY_COLOR_ID = "color_id";
    static final String DEFAULT_COLOR_ID = "amber";

    final String id;
    final String displayName;
    final int accent;
    final int accentLight;

    private WallpaperColors(String id, String displayName, int accent, int accentLight) {
        this.id = id;
        this.displayName = displayName;
        this.accent = accent;
        this.accentLight = accentLight;
    }

    static final WallpaperColors[] ALL = {
        new WallpaperColors("amber", "Amber", 0xFFF2B705, 0xFFFFD54A),
        new WallpaperColors("cyan", "Cyan", 0xFF06B6D4, 0xFF67E8F9),
        new WallpaperColors("magenta", "Magenta", 0xFFD946EF, 0xFFF0ABFC),
        new WallpaperColors("emerald", "Emerald", 0xFF10B981, 0xFF6EE7B7),
    };

    static WallpaperColors byId(String id) {
        for (WallpaperColors c : ALL) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return ALL[0];
    }

    static WallpaperColors current(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return byId(prefs.getString(KEY_COLOR_ID, DEFAULT_COLOR_ID));
    }
}
