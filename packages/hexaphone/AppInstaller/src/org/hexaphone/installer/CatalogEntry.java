package org.hexaphone.installer;

/**
 * One row in the app catalog. sha256/sizeBytes for the F-Droid-hosted
 * entries are the same pins used by scripts/fetch-prebuilt-apks.sh for
 * the apps this ROM used to bundle directly — DuckDuckGo and F-Droid were
 * dropped from the system image for space (see vendor/hexaphone/hexaphone.mk),
 * so this app fetches and verifies them the same way, on demand, instead.
 * Bootscreen is a Hexaphone-built app hosted on jgm240/hexaphone-apps
 * rather than F-Droid, hence the explicit downloadUrl field instead of
 * always deriving one from an F-Droid package/version pair.
 */
final class CatalogEntry {
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

    static CatalogEntry fromFDroid(String displayName, String description, String packageName,
            long versionCode, String sha256, long sizeBytes) {
        String url = "https://f-droid.org/repo/" + packageName + "_" + versionCode + ".apk";
        return new CatalogEntry(displayName, description, packageName, sha256, sizeBytes, url,
                true);
    }

    static CatalogEntry fromUrl(String displayName, String description, String packageName,
            String sha256, long sizeBytes, String downloadUrl) {
        return new CatalogEntry(displayName, description, packageName, sha256, sizeBytes,
                downloadUrl, true);
    }

    static CatalogEntry comingSoon(String displayName, String description) {
        return new CatalogEntry(displayName, description, null, null, 0, null, false);
    }

    String downloadUrl() {
        return downloadUrl;
    }

    static final CatalogEntry[] ALL = {
        fromFDroid("DuckDuckGo Browser",
                "Privacy-focused browser. There's no preinstalled browser on Hexaphone.",
                "com.duckduckgo.mobile.android", 52921000L,
                "126f79deacb7a7b087e3d085d971fb04e958afc1918a5838831f8f00f42694b8",
                176062923L),
        fromFDroid("F-Droid",
                "Catalog of free & open-source Android apps.",
                "org.fdroid.fdroid", 1023052L,
                "985f5181d48bb6bafd54083a048b391271e0ab28385881cc41294fb01a222762",
                12426276L),
        fromUrl("Bootscreen",
                "Pick a boot animation from a curated set of colors and speeds.",
                "org.hexaphone.bootscreen",
                "c9b2d46657044b972ade2b20d128aa113a11ce1e07ec2de4c4f5da74bc45b7cb", 29477L,
                "https://raw.githubusercontent.com/jgm240/hexaphone-apps/main/bootscreen-app/Bootscreen.apk"),
        comingSoon("More Hexaphone apps", "Coming soon."),
    };
}
