#!/usr/bin/env bash
# external/chromium-webview/prebuilt/{arch}/webview.apk are each their own
# git-lfs-backed repo. The git-lfs binary's own TLS client fails hard against
# review.lineageos.org under this Docker/Rosetta setup ("tls: bad record
# MAC", consistent, not transient) even though plain curl to the same host
# works fine — looks like a Go-TLS-under-emulation issue, not a server or
# network problem. So we skip git-lfs entirely: hit the LFS batch API with
# curl to get a signed download URL, then curl the object directly.
#
# manta is 32-bit ARM only, so we only need the "arm" arch — no point
# fetching arm64/x86/x86_64 blobs a 2012 tablet will never load.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

APK="$HEXAPHONE_SRC/external/chromium-webview/prebuilt/arm/webview.apk"
[ -f "$APK" ] || { echo "no webview.apk at $APK, skipping (chromium-webview not synced?)"; exit 0; }

if ! grep -q "^version https://git-lfs" "$APK" 2>/dev/null; then
  echo "webview.apk is already real content, nothing to do."
  exit 0
fi

oid=$(grep "^oid sha256:" "$APK" | cut -d: -f2)
size=$(grep "^size " "$APK" | awk '{print $2}')

echo "fetching webview.apk (arm, ${size} bytes, oid ${oid:0:12}...)"

docker run --rm -i --platform linux/amd64 -v "$HEXAPHONE_SRC:/src" hexaphone-builder bash -lc "
  set -e
  resp=\$(curl -sS -X POST \
    -H 'Accept: application/vnd.git-lfs+json' \
    -H 'Content-Type: application/vnd.git-lfs+json' \
    -d '{\"operation\":\"download\",\"transfers\":[\"basic\"],\"objects\":[{\"oid\":\"$oid\",\"size\":$size}]}' \
    https://review.lineageos.org/LineageOS/android_external_chromium-webview_prebuilt_arm.git/info/lfs/objects/batch)
  url=\$(echo \"\$resp\" | python3 -c 'import json,sys; print(json.load(sys.stdin)[\"objects\"][0][\"actions\"][\"download\"][\"href\"])')
  curl -sS -L -o /src/external/chromium-webview/prebuilt/arm/webview.apk \"\$url\"
"

actual_sha=$(shasum -a 256 "$APK" | cut -d' ' -f1)
if [ "$actual_sha" != "$oid" ]; then
  echo "error: downloaded webview.apk sha256 ($actual_sha) doesn't match expected ($oid)" >&2
  exit 1
fi
echo "webview.apk fetched and verified."
