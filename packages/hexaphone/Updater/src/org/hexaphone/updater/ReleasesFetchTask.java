package org.hexaphone.updater;

import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Downloads and parses the GitHub releases list for jgm240/Hexaphone. */
class ReleasesFetchTask extends AsyncTask<Void, Void, ReleasesFetchTask.Result> {

    static class Result {
        final List<ReleaseInfo> releases;
        final String error;

        private Result(List<ReleaseInfo> releases, String error) {
            this.releases = releases;
            this.error = error;
        }

        static Result ok(List<ReleaseInfo> releases) {
            return new Result(releases, null);
        }

        static Result failed(String error) {
            return new Result(null, error);
        }
    }

    interface Listener {
        void onReleasesResult(Result result);
    }

    private final Listener listener;

    ReleasesFetchTask(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected Result doInBackground(Void... params) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(ReleaseInfo.RELEASES_API_URL).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return Result.failed("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
                String json = buffer.toString("UTF-8");
                return Result.ok(ReleaseInfo.parseReleases(json));
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return Result.failed(e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        listener.onReleasesResult(result);
    }
}
