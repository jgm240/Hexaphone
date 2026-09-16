package org.hexaphone.bootscreen;

import android.os.AsyncTask;
import android.util.Log;

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
 * Downloads a preset zip, verifies it against the manifest's pinned
 * sha256, and writes it to a fixed filename under
 * /data/hexaphone_bootscreen/ -- bootanimation itself checks that exact
 * path directly at next boot (see scripts/patch-bootanimation.sh), so
 * there's no root "apply" step: bootanimation.zip here just always is
 * whatever was picked last.
 *
 * The directory is created at boot by hexaphone.rc, owned system:system,
 * world-readable (mode 0755) -- this app runs as
 * sharedUserId=android.uid.system so it can write there directly (DAC
 * still requires that even though nothing here calls a hidden API any
 * more), and bootanimation (user/group graphics) needs to read it back.
 */
class ApplyPresetTask extends AsyncTask<Void, String, String> {
    private static final String TAG = "HexaphoneBootscreen";
    private static final int BUFFER_SIZE = 64 * 1024;
    static final String DEST_DIR = "/data/hexaphone_bootscreen";
    static final String DEST_FILE = DEST_DIR + "/bootanimation.zip";

    interface Listener {
        void onStatus(String status);
        void onError(String error);
        void onApplied();
    }

    private final BootscreenPreset preset;
    private final Listener listener;

    ApplyPresetTask(BootscreenPreset preset, Listener listener) {
        this.preset = preset;
        this.listener = listener;
    }

    @Override
    protected void onPreExecute() {
        listener.onStatus("Downloading…");
    }

    @Override
    protected String doInBackground(Void... params) {
        File destDir = new File(DEST_DIR);
        File tmp = new File(destDir, preset.id + ".zip.tmp");
        File finalFile = new File(DEST_FILE);

        try {
            download(preset.downloadUrl(), tmp);
        } catch (IOException e) {
            Log.w(TAG, "download failed", e);
            tmp.delete();
            return "Download failed. Check your connection and retry.";
        }

        publishProgress("Verifying…");
        String actualSha256;
        try {
            actualSha256 = sha256(tmp);
        } catch (IOException | NoSuchAlgorithmException e) {
            tmp.delete();
            return "Couldn't verify the download. Retry.";
        }
        if (!actualSha256.equalsIgnoreCase(preset.sha256)) {
            Log.w(TAG, "sha256 mismatch for " + preset.id + ": expected " + preset.sha256
                    + ", got " + actualSha256);
            tmp.delete();
            return "Download didn't match its expected checksum. Retry.";
        }

        finalFile.delete();
        if (!tmp.renameTo(finalFile)) {
            tmp.delete();
            return "Couldn't finish writing the preset. Retry.";
        }
        // Readable by bootanimation (user/group graphics, not system).
        finalFile.setReadable(true, false);

        return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        listener.onStatus(values[0]);
    }

    @Override
    protected void onPostExecute(String error) {
        if (error != null) {
            listener.onError(error);
        } else {
            listener.onApplied();
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

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
