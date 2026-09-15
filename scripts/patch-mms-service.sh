#!/usr/bin/env bash
# packages/services/Mms/src/com/android/mms/service/MmsService.java (stock,
# unforked) implements IMms.Stub with 4 methods taking an extra
# `int callingUser` first/second parameter — a multi-user API refactor
# that frameworks/base/telephony/.../IMms.aidl (part of followmsi's
# forked frameworks/base) predates, same category of version-skew bug as
# StaticIpConfiguration (see patch-frameworks-base.sh). Unlike that one,
# PRODUCT_PACKAGES_REMOVE += MmsService (tried first) does NOT actually
# exist as a mechanism in this build system — grepped the whole build/
# tree, it's not processed anywhere, so debloating it out isn't an
# option here. Fixed at the source instead: dropped the callingUser
# parameter from the 4 affected Stub methods to match the AIDL interface,
# deriving it locally via UserHandle.myUserId() instead. This is a
# single-user tablet (no multi-user profiles), so that's always correct.
#
# Idempotent, safe to call before every build in case
# packages/services/Mms gets re-synced from scratch.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

F="$HEXAPHONE_SRC/packages/services/Mms/src/com/android/mms/service/MmsService.java"
MARKER="Hexaphone: this IMms.aidl"

[ -f "$F" ] || { echo "no MmsService.java yet, skipping"; exit 0; }

if grep -qF "$MARKER" "$F"; then
  echo "MmsService.java already patched."
  exit 0
fi

python3 - "$F" << 'EOF'
import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

replacements = [
    (
        '''        public void sendMessage(int subId, int callingUser, String callingPkg, Uri contentUri,
                String locationUrl, Bundle configOverrides, PendingIntent sentIntent)
                        throws RemoteException {
            LogUtil.d("sendMessage");
            enforceSystemUid();''',
        '''        public void sendMessage(int subId, String callingPkg, Uri contentUri,
                String locationUrl, Bundle configOverrides, PendingIntent sentIntent)
                        throws RemoteException {
            LogUtil.d("sendMessage");
            enforceSystemUid();
            // Hexaphone: this IMms.aidl (part of followmsi's forked
            // frameworks/base) predates the callingUser multi-user param
            // this method used to take upstream. Single-user tablet, no
            // multi-user profiles — the current user is always correct.
            final int callingUser = UserHandle.myUserId();''',
    ),
    (
        '''        public void downloadMessage(int subId, int callingUser, String callingPkg, String locationUrl,
                Uri contentUri, Bundle configOverrides,
                PendingIntent downloadedIntent) throws RemoteException {
            LogUtil.d("downloadMessage: " + MmsHttpClient.redactUrlForNonVerbose(locationUrl));
            enforceSystemUid();''',
        '''        public void downloadMessage(int subId, String callingPkg, String locationUrl,
                Uri contentUri, Bundle configOverrides,
                PendingIntent downloadedIntent) throws RemoteException {
            LogUtil.d("downloadMessage: " + MmsHttpClient.redactUrlForNonVerbose(locationUrl));
            enforceSystemUid();
            // Hexaphone: see sendMessage() above for why callingUser is
            // derived here instead of taken as a parameter.
            final int callingUser = UserHandle.myUserId();''',
    ),
    (
        '''        public Uri importMultimediaMessage(int callingUser, String callingPkg,
                Uri contentUri, String messageId, long timestampSecs, boolean seen, boolean read) {
            LogUtil.d("importMultimediaMessage");
            enforceSystemUid();
            return importMms(contentUri, messageId, timestampSecs, seen,
                read, callingUser, callingPkg);
        }''',
        '''        public Uri importMultimediaMessage(String callingPkg,
                Uri contentUri, String messageId, long timestampSecs, boolean seen, boolean read) {
            LogUtil.d("importMultimediaMessage");
            enforceSystemUid();
            // Hexaphone: see sendMessage() above for why callingUser is
            // derived here instead of taken as a parameter.
            return importMms(contentUri, messageId, timestampSecs, seen,
                read, UserHandle.myUserId(), callingPkg);
        }''',
    ),
    (
        '''        public Uri addMultimediaMessageDraft(int callingUser,
                String callingPkg, Uri contentUri) throws RemoteException {
            LogUtil.d("addMultimediaMessageDraft");
            enforceSystemUid();
            return addMmsDraft(contentUri, callingUser, callingPkg);
        }''',
        '''        public Uri addMultimediaMessageDraft(String callingPkg, Uri contentUri)
                throws RemoteException {
            LogUtil.d("addMultimediaMessageDraft");
            enforceSystemUid();
            // Hexaphone: see sendMessage() above for why callingUser is
            // derived here instead of taken as a parameter.
            return addMmsDraft(contentUri, UserHandle.myUserId(), callingPkg);
        }''',
    ),
]

for old, new in replacements:
    if old not in content:
        print(f"error: expected anchor text not found for one of the 4 methods, file may have changed", file=sys.stderr)
        sys.exit(1)
    content = content.replace(old, new, 1)

with open(path, "w") as f:
    f.write(content)
EOF

echo "MmsService.java patched."
