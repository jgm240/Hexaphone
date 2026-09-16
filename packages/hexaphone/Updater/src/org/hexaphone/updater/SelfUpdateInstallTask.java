package org.hexaphone.updater;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
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
 * Downloads and hash-verifies the update described by a SelfUpdateInfo,
 * then hands it to PackageInstaller targeting this app's own package --
 * a self-update, same public API any app can use to replace itself. The
 * system shows the normal "Update this app?" dialog (or none at all,
 * since REQUEST_INSTALL_PACKAGES auto-grants on our platform signature
 * -- see Android.mk); on success Android kills and restarts this
 * process with the new version on its own.
 */
class SelfUpdateInstallTask extends AsyncTask<Void, String, String> {
    private static final String TAG = "HexaphoneUpdater";
    private static final int BUFFER_SIZE = 64 * 1024;

    interface Listener {
        void onStatus(String status);
        void onError(String error);
        void onHandedOff();
    }

    private final Context appContext;
    private final SelfUpdateInfo update;
    private final Listener listener;

    SelfUpdateInstallTask(Context context, SelfUpdateInfo update, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.update = update;
        this.listener = listener;
    }

    @Override
    protected void onPreExecute() {
        listener.onStatus("Downloading…");
    }

    @Override
    protected String doInBackground(Void... params) {
        File dest = new File(appContext.getCacheDir(), "self-update.apk");
        try {
            download(update.downloadUrl(), dest);
        } catch (IOException e) {
            Log.w(TAG, "download failed", e);
            dest.delete();
            return "Download failed. Check your connection and retry.";
        }

        publishProgress("Verifying…");
        String actualSha256;
        try {
            actualSha256 = sha256(dest);
        } catch (IOException | NoSuchAlgorithmException e) {
            dest.delete();
            return "Couldn't verify the download. Retry.";
        }
        if (!actualSha256.equalsIgnoreCase(update.sha256)) {
            Log.w(TAG, "sha256 mismatch: expected " + update.sha256 + ", got " + actualSha256);
            dest.delete();
            return "Download didn't match its expected checksum. Retry.";
        }

        publishProgress("Installing…");
        try {
            install(dest);
        } catch (IOException e) {
            Log.w(TAG, "install handoff failed", e);
            return "Couldn't start the install. Retry.";
        } finally {
            dest.delete();
        }
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
            listener.onHandedOff();
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

    private void install(File apkFile) throws IOException {
        PackageInstaller installer = appContext.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(appContext.getPackageName());
        params.setSize(apkFile.length());

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try {
            OutputStream out = session.openWrite("package", 0, apkFile.length());
            try {
                InputStream in = new FileInputStream(apkFile);
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                } finally {
                    in.close();
                }
                session.fsync(out);
            } finally {
                out.close();
            }

            Intent statusIntent = new Intent(appContext, SelfUpdateInstallReceiver.class);
            statusIntent.setAction(SelfUpdateInstallReceiver.ACTION_INSTALL_STATUS);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext, sessionId,
                    statusIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pendingIntent.getIntentSender());
        } finally {
            session.close();
        }
    }
}
