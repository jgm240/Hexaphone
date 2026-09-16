package org.hexaphone.updater;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * updater-app/manifest.json on jgm240/hexaphone-apps -- describes
 * whatever HexaphoneUpdater.apk is currently published there. Bump
 * versionCode here (and in AndroidManifest.xml) every time the app
 * itself changes; SelfUpdateCheckTask compares this against the
 * installed versionCode to decide whether an update is offered.
 */
final class SelfUpdateInfo {
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/jgm240/hexaphone-apps/main/updater-app/manifest.json";
    private static final String BASE_URL =
            "https://raw.githubusercontent.com/jgm240/hexaphone-apps/main/updater-app/";

    final int versionCode;
    final String versionName;
    final String file;
    final String sha256;
    final long sizeBytes;

    private SelfUpdateInfo(int versionCode, String versionName, String file, String sha256,
            long sizeBytes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.file = file;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    String downloadUrl() {
        return BASE_URL + file;
    }

    static SelfUpdateInfo fetch() throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
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
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
                JSONObject root = new JSONObject(buffer.toString("UTF-8"));
                return new SelfUpdateInfo(
                        root.getInt("versionCode"),
                        root.getString("versionName"),
                        root.getString("file"),
                        root.getString("sha256"),
                        root.getLong("sizeBytes"));
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }
}
