package org.hexaphone.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Lists every jgm240/Hexaphone GitHub release and lets the user download
 * (and hash-verify) any of them -- installing or downgrading to a
 * specific version. Actually flashing still means rebooting to recovery,
 * same as every other Hexaphone build; this only gets a verified zip
 * onto the device.
 */
public class MainActivity extends Activity implements DownloadReleaseTask.Listener {

    // Tracks "downloaded this release, expecting to reboot into it soon"
    // across app restarts (a manual TWRP flash always restarts the app).
    // Build.VERSION.INCREMENTAL changes with every real Hexaphone build,
    // so comparing it against what was recorded at download time is how
    // this tells "actually rebooted into the new build" apart from "just
    // reopened the app before flashing yet" -- all public APIs, no
    // SystemProperties needed.
    private static final String PREFS_NAME = "updater";
    private static final String KEY_PENDING_TAG = "pending_tag";
    private static final String KEY_PENDING_BODY = "pending_body";
    private static final String KEY_PENDING_INCREMENTAL = "pending_incremental";

    private LinearLayout listContainer;
    private TextView emptyState;
    private final Map<String, RowViews> rowsByTag = new HashMap<String, RowViews>();
    private DownloadReleaseTask runningTask;
    private RowViews currentTaskRow;

    private View selfUpdateBanner;
    private View selfUpdateDivider;
    private TextView selfUpdateTitle;
    private TextView selfUpdateStatus;
    private Button selfUpdateAction;
    private SelfUpdateInfo pendingSelfUpdate;
    private SelfUpdateInstallTask selfUpdateTask;

    private static class RowViews {
        ReleaseInfo release;
        TextView status;
        Button action;
        // Set once DownloadReleaseTask finishes; toggles the action
        // button from "Download" to "Flash Now" for this row.
        File downloadedFile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listContainer = (LinearLayout) findViewById(R.id.list_container);
        emptyState = (TextView) findViewById(R.id.empty_state);

        selfUpdateBanner = findViewById(R.id.self_update_banner);
        selfUpdateDivider = findViewById(R.id.self_update_divider);
        selfUpdateTitle = (TextView) findViewById(R.id.self_update_title);
        selfUpdateStatus = (TextView) findViewById(R.id.self_update_status);
        selfUpdateAction = (Button) findViewById(R.id.self_update_action);
        selfUpdateAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSelfUpdate();
            }
        });

        checkWhatsNew();
        checkSelfUpdate();
        loadReleases();
    }

    private void checkWhatsNew() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String pendingTag = prefs.getString(KEY_PENDING_TAG, null);
        String pendingIncremental = prefs.getString(KEY_PENDING_INCREMENTAL, null);
        if (pendingTag == null || pendingIncremental == null) {
            return;
        }
        if (pendingIncremental.equals(Build.VERSION.INCREMENTAL)) {
            // Downloaded but not flashed yet (or flashed a dirty update
            // that didn't actually change the running build) -- wait
            // for INCREMENTAL to actually change before claiming this.
            return;
        }
        String body = prefs.getString(KEY_PENDING_BODY, "");
        prefs.edit().remove(KEY_PENDING_TAG).remove(KEY_PENDING_BODY)
                .remove(KEY_PENDING_INCREMENTAL).apply();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.whats_new_title, pendingTag))
                .setMessage(body.isEmpty() ? getString(R.string.whats_new_empty) : body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void checkSelfUpdate() {
        new SelfUpdateCheckTask(this, new SelfUpdateCheckTask.Listener() {
            @Override
            public void onSelfUpdateResult(SelfUpdateInfo info) {
                if (info == null) {
                    return;
                }
                pendingSelfUpdate = info;
                selfUpdateTitle.setText(getString(R.string.self_update_title, info.versionName));
                selfUpdateBanner.setVisibility(View.VISIBLE);
                selfUpdateDivider.setVisibility(View.VISIBLE);
            }
        }).execute();
    }

    private void startSelfUpdate() {
        if (selfUpdateTask != null || pendingSelfUpdate == null) {
            return;
        }
        selfUpdateAction.setEnabled(false);
        selfUpdateTask = new SelfUpdateInstallTask(this, pendingSelfUpdate,
                new SelfUpdateInstallTask.Listener() {
                    @Override
                    public void onStatus(final String status) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                selfUpdateStatus.setVisibility(View.VISIBLE);
                                selfUpdateStatus.setText(status);
                            }
                        });
                    }

                    @Override
                    public void onError(final String error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                selfUpdateStatus.setVisibility(View.VISIBLE);
                                selfUpdateStatus.setText(error);
                                selfUpdateAction.setEnabled(true);
                                selfUpdateTask = null;
                            }
                        });
                    }

                    @Override
                    public void onHandedOff() {
                        // Real success/failure lands via
                        // SelfUpdateInstallReceiver, and a successful
                        // self-update kills+restarts this process on its
                        // own -- just note that the handoff worked.
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                selfUpdateStatus.setVisibility(View.VISIBLE);
                                selfUpdateStatus.setText(R.string.status_installing_self_update);
                            }
                        });
                    }
                });
        selfUpdateTask.execute();
    }

    private void loadReleases() {
        emptyState.setText(R.string.loading);
        emptyState.setVisibility(View.VISIBLE);
        new ReleasesFetchTask(new ReleasesFetchTask.Listener() {
            @Override
            public void onReleasesResult(ReleasesFetchTask.Result result) {
                if (result.error != null) {
                    emptyState.setText(getString(R.string.releases_error, result.error));
                    emptyState.setVisibility(View.VISIBLE);
                    return;
                }
                showReleases(result.releases);
            }
        }).execute();
    }

    private void showReleases(List<ReleaseInfo> releases) {
        emptyState.setVisibility(View.GONE);
        listContainer.removeAllViews();
        rowsByTag.clear();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReleaseInfo release : releases) {
            View row = inflater.inflate(R.layout.row_release, listContainer, false);
            TextView name = (TextView) row.findViewById(R.id.row_name);
            TextView date = (TextView) row.findViewById(R.id.row_date);
            final TextView status = (TextView) row.findViewById(R.id.row_status);
            final Button action = (Button) row.findViewById(R.id.row_action);

            name.setText(release.name);
            date.setText(formatDate(release.publishedAt));

            final RowViews rv = new RowViews();
            rv.release = release;
            rv.status = status;
            rv.action = action;
            rowsByTag.put(release.tagName, rv);

            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (rv.downloadedFile != null) {
                        confirmFlash(rv);
                    } else {
                        startDownload(rv);
                    }
                }
            });

            listContainer.addView(row);
        }
    }

    private void startDownload(final RowViews rv) {
        if (runningTask != null) {
            return;
        }
        for (RowViews other : rowsByTag.values()) {
            other.action.setEnabled(false);
        }
        currentTaskRow = rv;
        runningTask = new DownloadReleaseTask(this, rv.release, this);
        runningTask.execute();
    }

    @Override
    public void onStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (currentTaskRow != null) {
                    currentTaskRow.status.setVisibility(View.VISIBLE);
                    currentTaskRow.status.setText(status);
                }
            }
        });
    }

    @Override
    public void onError(final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (currentTaskRow != null) {
                    currentTaskRow.status.setVisibility(View.VISIBLE);
                    currentTaskRow.status.setText(error);
                    currentTaskRow.action.setText(R.string.action_retry);
                }
                finishTask();
            }
        });
    }

    @Override
    public void onDownloaded(final File file) {
        if (currentTaskRow != null) {
            currentTaskRow.downloadedFile = file;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_PENDING_TAG, currentTaskRow.release.tagName)
                    .putString(KEY_PENDING_BODY, currentTaskRow.release.body)
                    .putString(KEY_PENDING_INCREMENTAL, Build.VERSION.INCREMENTAL)
                    .apply();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (currentTaskRow != null) {
                    currentTaskRow.status.setVisibility(View.VISIBLE);
                    currentTaskRow.status.setText(getString(R.string.status_downloaded,
                            file.getName()));
                    currentTaskRow.action.setText(R.string.action_flash);
                }
                finishTask();
            }
        });
    }

    private void confirmFlash(final RowViews rv) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.flash_confirm_title, rv.release.tagName))
                .setMessage(R.string.flash_confirm_message)
                .setPositiveButton(R.string.action_flash, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doFlash(rv);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doFlash(RowViews rv) {
        try {
            // On success the device reboots into recovery almost
            // immediately; there's nothing further to do here.
            RecoveryFlasher.flash(this, rv.downloadedFile);
        } catch (IOException | SecurityException e) {
            // Most likely cause: this install isn't the real priv-app
            // (e.g. reinstalled from Hexaphone Store's catalog rather
            // than the one baked into system.img), so either SELinux or
            // the /cache/recovery DAC permissions refused the write, or
            // REBOOT wasn't actually granted (SecurityException).
            rv.status.setVisibility(View.VISIBLE);
            rv.status.setText(R.string.status_flash_failed);
        }
    }

    private void finishTask() {
        runningTask = null;
        currentTaskRow = null;
        for (RowViews rv : rowsByTag.values()) {
            rv.action.setEnabled(true);
        }
    }

    private static String formatDate(String iso8601) {
        if (iso8601 == null || iso8601.isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                    Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(iso8601);
            return DateFormat.getDateInstance(DateFormat.MEDIUM).format(date);
        } catch (ParseException e) {
            return iso8601;
        }
    }
}
