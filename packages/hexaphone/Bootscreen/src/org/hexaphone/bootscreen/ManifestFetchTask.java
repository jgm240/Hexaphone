package org.hexaphone.bootscreen;

import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Downloads and parses bootscreen/manifest.json. */
class ManifestFetchTask extends AsyncTask<Void, Void, ManifestFetchTask.Result> {

    static class Result {
        final List<BootscreenPreset> presets;
        final String error;

        private Result(List<BootscreenPreset> presets, String error) {
            this.presets = presets;
            this.error = error;
        }

        static Result ok(List<BootscreenPreset> presets) {
            return new Result(presets, null);
        }

        static Result failed(String error) {
            return new Result(null, error);
        }
    }

    interface Listener {
        void onManifestResult(Result result);
    }

    private final Listener listener;

    ManifestFetchTask(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected Result doInBackground(Void... params) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BootscreenPreset.BASE_URL + "manifest.json")
                    .openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
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
                return Result.ok(BootscreenPreset.parseManifest(json));
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
        listener.onManifestResult(result);
    }
}
