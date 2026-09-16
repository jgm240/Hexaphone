package org.hexaphone.installer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists the catalog of apps this app can fetch+install (see CatalogEntry),
 * fetched fresh from store/manifest.json on jgm240/hexaphone-apps every
 * time this activity starts -- none of these apps ship in the base
 * system image (see vendor/hexaphone/hexaphone.mk for why DuckDuckGo and
 * F-Droid were pulled out of the build), and the catalog itself isn't
 * baked into this APK either, so adding or updating an entry doesn't
 * need a new Hexaphone build. Rows are indexed by display name (not
 * package name): InstallReceiver always has the display name (we pass it
 * in ourselves), but PackageInstaller only reliably fills in
 * EXTRA_PACKAGE_NAME on success, not on every failure path.
 */
public class MainActivity extends Activity implements InstallResultBus.Listener {

    private LinearLayout listContainer;
    private TextView emptyState;
    private final Map<String, RowViews> rowsByDisplayName = new HashMap<String, RowViews>();

    private static class RowViews {
        CatalogEntry entry;
        TextView status;
        Button action;
        InstallTask task;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listContainer = (LinearLayout) findViewById(R.id.list_container);
        emptyState = (TextView) findViewById(R.id.empty_state);

        loadCatalog();
    }

    private void loadCatalog() {
        emptyState.setText(R.string.loading);
        emptyState.setVisibility(View.VISIBLE);
        new CatalogFetchTask(new CatalogFetchTask.Listener() {
            @Override
            public void onCatalogResult(CatalogFetchTask.Result result) {
                if (result.error != null) {
                    emptyState.setText(getString(R.string.catalog_error, result.error));
                    emptyState.setVisibility(View.VISIBLE);
                    return;
                }
                showCatalog(result.entries);
            }
        }).execute();
    }

    private void showCatalog(List<CatalogEntry> entries) {
        emptyState.setVisibility(View.GONE);
        listContainer.removeAllViews();
        rowsByDisplayName.clear();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (CatalogEntry entry : entries) {
            View row = inflater.inflate(R.layout.row_app, listContainer, false);
            TextView name = (TextView) row.findViewById(R.id.row_name);
            TextView description = (TextView) row.findViewById(R.id.row_description);
            TextView status = (TextView) row.findViewById(R.id.row_status);
            Button action = (Button) row.findViewById(R.id.row_action);

            name.setText(entry.displayName);
            description.setText(entry.description);

            final RowViews rv = new RowViews();
            rv.entry = entry;
            rv.status = status;
            rv.action = action;
            rowsByDisplayName.put(entry.displayName, rv);

            if (!entry.available) {
                action.setText(R.string.action_coming_soon);
                action.setEnabled(false);
            } else {
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onActionClicked(rv);
                    }
                });
                refreshRowState(rv);
            }

            listContainer.addView(row);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        InstallResultBus.setListener(this);
        for (RowViews rv : rowsByDisplayName.values()) {
            if (rv.entry.available) {
                refreshRowState(rv);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        InstallResultBus.setListener(null);
    }

    private void onActionClicked(final RowViews rv) {
        if (isInstalled(rv.entry.packageName)) {
            launch(rv.entry.packageName);
            return;
        }
        rv.action.setEnabled(false);
        rv.task = new InstallTask(this, rv.entry, new InstallTask.Listener() {
            @Override
            public void onStatus(final String status) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rv.status.setVisibility(View.VISIBLE);
                        rv.status.setText(status);
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rv.status.setVisibility(View.VISIBLE);
                        rv.status.setText(error);
                        rv.action.setEnabled(true);
                        rv.action.setText(R.string.action_retry);
                    }
                });
            }

            @Override
            public void onHandedOff() {
                // Actual success/failure lands via onInstallResult() once
                // the system finishes the PackageInstaller session -- this
                // just means the APK was handed off successfully.
            }
        });
        rv.task.execute();
    }

    @Override
    public void onInstallResult(String packageName, final String displayName,
            final boolean success, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RowViews rv = rowsByDisplayName.get(displayName);
                if (rv == null) {
                    return;
                }
                if (success) {
                    refreshRowState(rv);
                } else {
                    rv.status.setVisibility(View.VISIBLE);
                    rv.status.setText(getString(R.string.error_install_failed,
                            message != null ? message : ""));
                    rv.action.setEnabled(true);
                    rv.action.setText(R.string.action_retry);
                }
            }
        });
    }

    private void refreshRowState(RowViews rv) {
        if (isInstalled(rv.entry.packageName)) {
            rv.action.setEnabled(true);
            rv.action.setText(R.string.action_open);
            rv.status.setVisibility(View.VISIBLE);
            rv.status.setText(R.string.status_installed);
        } else if (rv.task == null || rv.task.getStatus() != AsyncTask.Status.RUNNING) {
            rv.action.setEnabled(true);
            rv.action.setText(R.string.action_install);
        }
    }

    private boolean isInstalled(String packageName) {
        if (packageName == null) {
            return false;
        }
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void launch(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent);
        }
    }
}
