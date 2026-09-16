package org.hexaphone.performance;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Lets the user configure ZRAM (compressed RAM swap) and/or a storage-backed
 * swap file. This activity only ever writes persist.sys.hexaphone.* system
 * properties — it has no root access itself. A root init script
 * (vendor/hexaphone/bin/hexaphone-zram.sh, wired up via
 * vendor/hexaphone/rootdir/etc/init/hexaphone.rc) reads those properties at
 * boot and does the actual privileged work. See that .rc file for why
 * changes apply on next boot rather than live.
 */
public class MainActivity extends Activity {

    private static final String PROP_ZRAM_ENABLED = "persist.sys.hexaphone.zram.enabled";
    private static final String PROP_ZRAM_ALGO = "persist.sys.hexaphone.zram.algo";
    private static final String PROP_ZRAM_SIZE_MB = "persist.sys.hexaphone.zram.size_mb";
    private static final String PROP_SWAP_ENABLED = "persist.sys.hexaphone.swap.enabled";
    private static final String PROP_SWAP_SIZE_MB = "persist.sys.hexaphone.swap.size_mb";

    // Not persist.* -- a one-shot command ("do this now"), not state to
    // reapply at every boot. See vendor/hexaphone/rootdir/etc/init/hexaphone.rc.
    private static final String PROP_SELINUX_PERMISSIVE = "sys.hexaphone.selinux_permissive";

    // Total RAM is 2GB. ZRAM eats real RAM to hold compressed pages, so it
    // must stay well under that — cap at 1.5GB, default to something modest.
    private static final int ZRAM_MIN_MB = 64;
    private static final int ZRAM_MAX_MB = 1472; // ZRAM_MIN_MB + 1408 (seekbar max)
    private static final int ZRAM_DEFAULT_MB = 512;

    // Swap file lives on storage (16/32GB device), can be more generous.
    private static final int SWAP_MIN_MB = 128;
    private static final int SWAP_MAX_MB = 1920; // SWAP_MIN_MB + 1792 (seekbar max)
    private static final int SWAP_DEFAULT_MB = 512;

    private Switch switchZram;
    private Spinner spinnerZramAlgo;
    private SeekBar seekbarZramSize;
    private TextView labelZramSize;

    private Switch switchSwap;
    private SeekBar seekbarSwapSize;
    private TextView labelSwapSize;

    private TextView labelSelinuxPermissive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchZram = (Switch) findViewById(R.id.switch_zram);
        spinnerZramAlgo = (Spinner) findViewById(R.id.spinner_zram_algo);
        seekbarZramSize = (SeekBar) findViewById(R.id.seekbar_zram_size);
        labelZramSize = (TextView) findViewById(R.id.label_zram_size);

        switchSwap = (Switch) findViewById(R.id.switch_swap);
        seekbarSwapSize = (SeekBar) findViewById(R.id.seekbar_swap_size);
        labelSwapSize = (TextView) findViewById(R.id.label_swap_size);

        spinnerZramAlgo.setAdapter(ArrayAdapter.createFromResource(this,
                R.array.zram_algo_entries, android.R.layout.simple_spinner_dropdown_item));

        seekbarZramSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                updateZramSizeLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        seekbarSwapSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                updateSwapSizeLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        findViewById(R.id.button_apply).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply();
            }
        });

        labelSelinuxPermissive = (TextView) findViewById(R.id.label_selinux_permissive);
        findViewById(R.id.button_selinux_permissive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SystemProperties.set(PROP_SELINUX_PERMISSIVE, "1");
                labelSelinuxPermissive.setText(R.string.selinux_permissive_done);
            }
        });

        loadCurrentState();
    }

    private void loadCurrentState() {
        boolean zramEnabled = SystemProperties.getBoolean(PROP_ZRAM_ENABLED, false);
        String algo = SystemProperties.get(PROP_ZRAM_ALGO, "lz4");
        int zramSizeMb = SystemProperties.getInt(PROP_ZRAM_SIZE_MB, ZRAM_DEFAULT_MB);
        boolean swapEnabled = SystemProperties.getBoolean(PROP_SWAP_ENABLED, false);
        int swapSizeMb = SystemProperties.getInt(PROP_SWAP_SIZE_MB, SWAP_DEFAULT_MB);

        switchZram.setChecked(zramEnabled);
        spinnerZramAlgo.setSelection("lzo".equals(algo) ? 0 : 1);
        int zramProgress = clamp(zramSizeMb, ZRAM_MIN_MB, ZRAM_MAX_MB) - ZRAM_MIN_MB;
        seekbarZramSize.setProgress(zramProgress);
        updateZramSizeLabel(zramProgress);

        switchSwap.setChecked(swapEnabled);
        int swapProgress = clamp(swapSizeMb, SWAP_MIN_MB, SWAP_MAX_MB) - SWAP_MIN_MB;
        seekbarSwapSize.setProgress(swapProgress);
        updateSwapSizeLabel(swapProgress);
    }

    private void apply() {
        boolean zramEnabled = switchZram.isChecked();
        String algo = getResources().getStringArray(R.array.zram_algo_values)
                [spinnerZramAlgo.getSelectedItemPosition()];
        int zramSizeMb = ZRAM_MIN_MB + seekbarZramSize.getProgress();

        boolean swapEnabled = switchSwap.isChecked();
        int swapSizeMb = SWAP_MIN_MB + seekbarSwapSize.getProgress();

        SystemProperties.set(PROP_ZRAM_ENABLED, zramEnabled ? "1" : "0");
        SystemProperties.set(PROP_ZRAM_ALGO, algo);
        SystemProperties.set(PROP_ZRAM_SIZE_MB, String.valueOf(zramSizeMb));
        SystemProperties.set(PROP_SWAP_ENABLED, swapEnabled ? "1" : "0");
        SystemProperties.set(PROP_SWAP_SIZE_MB, String.valueOf(swapSizeMb));
    }

    private void updateZramSizeLabel(int progress) {
        labelZramSize.setText(getString(R.string.zram_size_label, ZRAM_MIN_MB + progress));
    }

    private void updateSwapSizeLabel(int progress) {
        labelSwapSize.setText(getString(R.string.swap_size_label, SWAP_MIN_MB + progress));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
