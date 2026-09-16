package org.hexaphone.updater;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
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

    private LinearLayout listContainer;
    private TextView emptyState;
    private final Map<String, RowViews> rowsByTag = new HashMap<String, RowViews>();
    private DownloadReleaseTask runningTask;
    private RowViews currentTaskRow;

    private static class RowViews {
        ReleaseInfo release;
        TextView status;
        Button action;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listContainer = (LinearLayout) findViewById(R.id.list_container);
        emptyState = (TextView) findViewById(R.id.empty_state);

        loadReleases();
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
                    startDownload(rv);
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (currentTaskRow != null) {
                    currentTaskRow.status.setVisibility(View.VISIBLE);
                    currentTaskRow.status.setText(getString(R.string.status_downloaded,
                            file.getName()));
                }
                finishTask();
            }
        });
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
