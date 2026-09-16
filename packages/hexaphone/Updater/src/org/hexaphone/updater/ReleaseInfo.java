package org.hexaphone.updater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One release from the GitHub API (jgm240/Hexaphone/releases). Only
 * releases that have both a .zip and a matching .zip.md5sum asset are
 * usable -- everything else (e.g. a release missing an asset for
 * whatever reason) is filtered out rather than shown as a broken entry.
 */
final class ReleaseInfo {
    static final String RELEASES_API_URL =
            "https://api.github.com/repos/jgm240/Hexaphone/releases";

    final String tagName;
    final String name;
    final String publishedAt;
    final String zipName;
    final String zipUrl;
    final String md5Url;

    private ReleaseInfo(String tagName, String name, String publishedAt, String zipName,
            String zipUrl, String md5Url) {
        this.tagName = tagName;
        this.name = name;
        this.publishedAt = publishedAt;
        this.zipName = zipName;
        this.zipUrl = zipUrl;
        this.md5Url = md5Url;
    }

    static List<ReleaseInfo> parseReleases(String json) throws JSONException {
        List<ReleaseInfo> releases = new ArrayList<ReleaseInfo>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            String zipName = null, zipUrl = null, md5Url = null;
            JSONArray assets = entry.getJSONArray("assets");
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.getJSONObject(j);
                String name = asset.getString("name");
                String url = asset.getString("browser_download_url");
                if (name.endsWith(".zip.md5sum")) {
                    md5Url = url;
                } else if (name.endsWith(".zip")) {
                    zipName = name;
                    zipUrl = url;
                }
            }
            if (zipName == null || zipUrl == null || md5Url == null) {
                continue;
            }
            releases.add(new ReleaseInfo(
                    entry.getString("tag_name"),
                    entry.optString("name", entry.getString("tag_name")),
                    entry.optString("published_at", ""),
                    zipName, zipUrl, md5Url));
        }
        return releases;
    }
}
