package org.hexaphone.installer;

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
 * Downloads a catalog entry's APK to the app cache dir, verifies it against
 * the pinned sha256 (same verification scheme scripts/fetch-prebuilt-apks.sh
 * uses at build time), then hands it to the system's PackageInstaller. This
 * task's own result only covers the download+verify+handoff step -- actual
 * install success/failure arrives later via InstallReceiver, since
 * PackageInstaller.Session.commit() is itself asynchronous.
 */
class InstallTask extends AsyncTask<Void, String, String> {
    private static final String TAG = "HexaphoneInstaller";
    private static final int BUFFER_SIZE = 64 * 1024;

    interface Listener {
        void onStatus(String status);
        void onError(String error);
        void onHandedOff();
    }

    private final Context appContext;
    private final CatalogEntry entry;
    private final Listener listener;

    InstallTask(Context context, CatalogEntry entry, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.entry = entry;
        this.listener = listener;
    }

    @Override
    protected void onPreExecute() {
        listener.onStatus(appContext.getString(R.string.status_downloading));
    }

    @Override
    protected String doInBackground(Void... params) {
        File dest = new File(appContext.getCacheDir(), entry.packageName + ".apk");
        try {
            download(entry.downloadUrl(), dest);
        } catch (IOException e) {
            Log.w(TAG, "download failed", e);
            dest.delete();
            return appContext.getString(R.string.error_download_failed);
        }

        publishProgress(appContext.getString(R.string.status_verifying));
        String actualSha256;
        try {
            actualSha256 = sha256(dest);
        } catch (IOException | NoSuchAlgorithmException e) {
            dest.delete();
            return appContext.getString(R.string.error_verify_failed);
        }
        if (!actualSha256.equalsIgnoreCase(entry.sha256)) {
            Log.w(TAG, "sha256 mismatch for " + entry.packageName + ": expected "
                    + entry.sha256 + ", got " + actualSha256);
            dest.delete();
            return appContext.getString(R.string.error_hash_mismatch);
        }

        publishProgress(appContext.getString(R.string.status_installing));
        try {
            install(dest);
        } catch (IOException e) {
            Log.w(TAG, "install handoff failed", e);
            return appContext.getString(R.string.error_install_failed);
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
        params.setAppPackageName(entry.packageName);
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

            Intent statusIntent = new Intent(appContext, InstallReceiver.class);
            statusIntent.setAction(InstallReceiver.ACTION_INSTALL_STATUS);
            statusIntent.putExtra(InstallReceiver.EXTRA_DISPLAY_NAME, entry.displayName);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext, sessionId,
                    statusIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pendingIntent.getIntentSender());
        } finally {
            session.close();
        }
    }
}
