package org.hexaphone.installer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One row in the app catalog, parsed from store/manifest.json in
 * jgm240/hexaphone-apps rather than hardcoded here -- adding an app or
 * updating a pin used to mean a full ROM rebuild to ship a new
 * CatalogEntry.ALL, just to change a list. Same pattern as Bootscreen's
 * own preset manifest (BootscreenPreset in the Bootscreen app).
 */
final class CatalogEntry {
    static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/jgm240/hexaphone-apps/main/store/manifest.json";

    final String displayName;
    final String description;
    final String packageName;
    final String sha256;
    final long sizeBytes;
    final boolean available;
    private final String downloadUrl;

    private CatalogEntry(String displayName, String description, String packageName,
            String sha256, long sizeBytes, String downloadUrl, boolean available) {
        this.displayName = displayName;
        this.description = description;
        this.packageName = packageName;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.downloadUrl = downloadUrl;
        this.available = available;
    }

    String downloadUrl() {
        return downloadUrl;
    }

    static List<CatalogEntry> parseManifest(String json) throws JSONException {
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        JSONObject root = new JSONObject(json);
        JSONArray array = root.getJSONArray("apps");
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            boolean available = entry.optBoolean("available", true);
            entries.add(new CatalogEntry(
                    entry.getString("displayName"),
                    entry.getString("description"),
                    entry.optString("packageName", null),
                    entry.optString("sha256", null),
                    entry.optLong("sizeBytes", 0),
                    entry.optString("downloadUrl", null),
                    available));
        }
        return entries;
    }
}
