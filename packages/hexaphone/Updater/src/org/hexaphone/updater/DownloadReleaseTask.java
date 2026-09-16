package org.hexaphone.updater;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Downloads a release zip to this app's external files dir (no storage
 * permission needed on API 19+, and reachable from TWRP's own file
 * browser since TWRP runs outside the OS's per-app sandbox) and verifies
 * it against the release's .md5sum asset -- md5, not sha256, because
 * that's the hash scheme scripts/04-build.sh already produces for every
 * Hexaphone zip.
 *
 * This only downloads and verifies; it deliberately does not attempt to
 * flash anything. Actually installing a build means rebooting into
 * recovery (TWRP) and flashing from there by hand, same as every other
 * Hexaphone zip -- automating that (an A/B update_engine style live
 * install) is real OTA-integration work this doesn't attempt.
 */
class DownloadReleaseTask extends AsyncTask<Void, String, String> {
    private static final String TAG = "HexaphoneUpdater";
    private static final int BUFFER_SIZE = 64 * 1024;

    interface Listener {
        void onStatus(String status);
        void onError(String error);
        void onDownloaded(File file);
    }

    private final Context appContext;
    private final ReleaseInfo release;
    private final Listener listener;

    DownloadReleaseTask(Context context, ReleaseInfo release, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.release = release;
        this.listener = listener;
    }

    @Override
    protected void onPreExecute() {
        listener.onStatus("Fetching checksum…");
    }

    @Override
    protected String doInBackground(Void... params) {
        String expectedMd5;
        try {
            expectedMd5 = fetchMd5(release.md5Url);
        } catch (IOException e) {
            Log.w(TAG, "md5sum fetch failed", e);
            return "Couldn't fetch the checksum. Check your connection and retry.";
        }
        if (expectedMd5 == null) {
            return "Checksum file was empty or malformed. Retry.";
        }

        File destDir = appContext.getExternalFilesDir(null);
        if (destDir == null) {
            return "External storage isn't available right now.";
        }
        File tmp = new File(destDir, release.zipName + ".tmp");
        File finalFile = new File(destDir, release.zipName);

        publishProgress("Downloading " + release.zipName + "…");
        try {
            download(release.zipUrl, tmp);
        } catch (IOException e) {
            Log.w(TAG, "download failed", e);
            tmp.delete();
            return "Download failed. Check your connection and retry.";
        }

        publishProgress("Verifying…");
        String actualMd5;
        try {
            actualMd5 = md5(tmp);
        } catch (IOException | NoSuchAlgorithmException e) {
            tmp.delete();
            return "Couldn't verify the download. Retry.";
        }
        if (!actualMd5.equalsIgnoreCase(expectedMd5)) {
            Log.w(TAG, "md5 mismatch for " + release.zipName + ": expected " + expectedMd5
                    + ", got " + actualMd5);
            tmp.delete();
            return "Download didn't match its expected checksum. Retry.";
        }

        finalFile.delete();
        if (!tmp.renameTo(finalFile)) {
            tmp.delete();
            return "Couldn't finish writing the file. Retry.";
        }

        downloadedFile = finalFile;
        return null;
    }

    private File downloadedFile;

    @Override
    protected void onProgressUpdate(String... values) {
        listener.onStatus(values[0]);
    }

    @Override
    protected void onPostExecute(String error) {
        if (error != null) {
            listener.onError(error);
        } else {
            listener.onDownloaded(downloadedFile);
        }
    }

    private String fetchMd5(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
                // Format: "<hash>  <filename>"
                String text = buffer.toString("UTF-8").trim();
                int spaceIdx = text.indexOf(' ');
                return spaceIdx > 0 ? text.substring(0, spaceIdx) : null;
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private void download(String urlStr, File dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            try {
                OutputStream out = new FileOutputStream(dest);
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (isCancelled()) {
                            throw new IOException("cancelled");
                        }
                        out.write(buffer, 0, n);
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String md5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
