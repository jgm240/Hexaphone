package org.hexaphone.livewallpaper;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/**
 * Reachable via the system wallpaper picker's "Customize" affordance
 * (wired up through android:settingsActivity in res/xml/live_wallpaper.xml).
 * Just a color pick -- writes straight to SharedPreferences, which
 * HexSpinWallpaperService listens for live via
 * OnSharedPreferenceChangeListener, so a change while the wallpaper is
 * already visible takes effect immediately, no re-apply needed.
 */
public class ColorSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final SharedPreferences prefs = getSharedPreferences(WallpaperColors.PREFS_NAME,
                MODE_PRIVATE);
        String currentId = prefs.getString(WallpaperColors.KEY_COLOR_ID,
                WallpaperColors.DEFAULT_COLOR_ID);

        RadioGroup group = (RadioGroup) findViewById(R.id.color_group);
        for (final WallpaperColors color : WallpaperColors.ALL) {
            RadioButton button = new RadioButton(this);
            button.setText(color.displayName);
            button.setId(color.id.hashCode());
            button.setChecked(color.id.equals(currentId));
            group.addView(button);
        }

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                for (WallpaperColors color : WallpaperColors.ALL) {
                    if (color.id.hashCode() == checkedId) {
                        prefs.edit().putString(WallpaperColors.KEY_COLOR_ID, color.id).apply();
                        break;
                    }
                }
            }
        });
    }
}
