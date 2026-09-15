#!/usr/bin/env bash
# Fetches the third-party APKs Hexaphone bundles, pinning each to a known
# sha256 so they don't need to live in git but stay reproducible and
# tamper-checked. Every entry's hash+size was cross-checked against
# F-Droid's own signed index-v1.jar at the time it was added, not just the
# raw APK file — catches a compromised/mismatched raw-download endpoint
# specifically. Re-run any time to verify what's on disk, or after bumping
# a version code here.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# name | package | version_code | sha256 | size_bytes
APPS="
DuckDuckGo|com.duckduckgo.mobile.android|52921000|126f79deacb7a7b087e3d085d971fb04e958afc1918a5838831f8f00f42694b8|176062923
FDroid|org.fdroid.fdroid|1023052|985f5181d48bb6bafd54083a048b391271e0ab28385881cc41294fb01a222762|12426276
AuroraStore|com.aurora.store|76|fd9c75d90d0f4a7c132b9b4a5a2cf1992a45e03b8d8ff988b7dcfbc0db2c4d11|9360088
"

fetch_one() {
  local name="$1" package="$2" version_code="$3" sha256="$4" size="$5"
  local dest="$PROJECT_ROOT/vendor/hexaphone/prebuilt/apps/$name/$name.apk"
  local url="https://f-droid.org/repo/${package}_${version_code}.apk"

  if [ -f "$dest" ]; then
    local actual
    actual=$(shasum -a 256 "$dest" | cut -d' ' -f1)
    if [ "$actual" = "$sha256" ]; then
      echo "$name.apk already present and verified."
      return 0
    fi
    echo "$name.apk exists but hash doesn't match, re-fetching..."
  fi

  mkdir -p "$(dirname "$dest")"
  curl -fSL -o "$dest" "$url"

  local actual_size actual_sha
  actual_size=$(stat -f %z "$dest" 2>/dev/null || stat -c %s "$dest")
  actual_sha=$(shasum -a 256 "$dest" | cut -d' ' -f1)

  if [ "$actual_size" != "$size" ] || [ "$actual_sha" != "$sha256" ]; then
    echo "error: downloaded $name.apk doesn't match expected size/hash" >&2
    echo "  expected: $size bytes, sha256 $sha256" >&2
    echo "  got:      $actual_size bytes, sha256 $actual_sha" >&2
    rm -f "$dest"
    return 1
  fi

  echo "$name.apk fetched and verified (v${version_code})."
}

while IFS='|' read -r name package version_code sha256 size; do
  [ -z "$name" ] && continue
  fetch_one "$name" "$package" "$version_code" "$sha256" "$size"
done <<< "$APPS"
