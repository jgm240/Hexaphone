#!/usr/bin/env bash
# Same category of followmsi-fork version-skew as the WiFi/MMS fixes
# (patch-frameworks-base.sh, patch-mms-service.sh), just larger in scope.
# Tried removing Telecom via the debloat list first (manta has no
# cellular hardware, so it looked like dead weight) but filter-out
# doesn't actually drop it — something else in the tree still pulls it
# in as a build dependency regardless of PRODUCT_PACKAGES, never isolated
# exactly what. Patched properly instead. Found the full scope by
# directly re-running the real javac invocation with the actual
# java-source-list + srcjar-list (see git history of this file / session
# notes) rather than trial-and-error through full ninja rebuilds — much
# faster to iterate on.
#
# frameworks/base/telecomm/java/android/telecom/StatusHints.java was
# missing 3 things the stock (unforked) packages/services/Telecomm
# expects, all from the same upstream multi-user security fix:
#   - validateAccountIconUserBoundary(Icon, UserHandle) static method
#   - getUserIdFromAuthority(String, int) static method — parses a user
#     ID out of a multi-user-aware content:// URI authority upstream;
#     always returns the fallback here, since there's no multi-user
#     authority encoding to parse and no other user's ID it could
#     legitimately resolve to on this device
#   - setIcon(Icon) instance method — the icon field had to stop being
#     final to support this
# All three added as behavior-preserving passthroughs (no multi-user
# profiles on this device, so the security boundary they'd enforce can
# never actually be crossed).
#
# packages/services/Telecomm/.../TelecomServiceImpl.java: 6
# ITelecomService.Stub methods returned ParceledListSlice<T> (a later API
# wrapping large Binder-IPC lists more efficiently) where the AIDL
# interface (part of the forked frameworks/base) still expects plain
# List<T>. Changed the 6 method signatures back to List<T> and unwrapped
# their return statements — plus one internal caller
# (getAllPhoneAccountsCount) that still called .getList() on the result
# of one of those 6, which broke once its return type became a plain
# List (missed on the first pass; caught by re-running the real javac
# invocation directly).
#
# frameworks/base/api/system-current.txt: adding methods to .java source
# alone isn't enough — StatusHints is a tracked @SystemApi class, and
# this build's header-jar/stub generation only includes methods
# explicitly listed in the API signature file, not just anything found
# in source. Also: that header jar isn't rebuilt just because
# system-current.txt changed (not tracked as a rule input by this
# build's dependency tracking) — after editing it, delete
# out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes-header.jar
# by hand once to force regeneration; this script can't do that part for
# you since HEXAPHONE_SRC's out/ isn't its concern.
# Deliberately NOT added to api/current.txt (the public, non-system
# surface) — these are security-sensitive internal validation helpers,
# correctly system-only, matching real upstream Android.
#
# Idempotent, safe to call before every build in case frameworks/base or
# packages/services/Telecomm get re-synced from scratch.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

STATUS_HINTS="$HEXAPHONE_SRC/frameworks/base/telecomm/java/android/telecom/StatusHints.java"
TELECOM_IMPL="$HEXAPHONE_SRC/packages/services/Telecomm/src/com/android/server/telecom/TelecomServiceImpl.java"
SYSTEM_API="$HEXAPHONE_SRC/frameworks/base/api/system-current.txt"

if [ ! -f "$STATUS_HINTS" ] || [ ! -f "$TELECOM_IMPL" ] || [ ! -f "$SYSTEM_API" ]; then
  echo "frameworks/base or packages/services/Telecomm not synced yet, skipping"
  exit 0
fi

if grep -qF "public void setIcon(Icon icon)" "$STATUS_HINTS" \
   && grep -qF "getUserIdFromAuthority" "$STATUS_HINTS" \
   && grep -qF "public List<PhoneAccountHandle> getAllPhoneAccountHandles()" "$TELECOM_IMPL" \
   && grep -qF "return getAllPhoneAccounts().size();" "$TELECOM_IMPL" \
   && grep -qF "setIcon" "$SYSTEM_API" \
   && grep -qF "getUserIdFromAuthority" "$SYSTEM_API"; then
  echo "Telecom already patched."
  exit 0
fi

python3 - "$STATUS_HINTS" "$TELECOM_IMPL" "$SYSTEM_API" << 'EOF'
import sys

status_hints_path, telecom_impl_path, system_api_path = sys.argv[1], sys.argv[2], sys.argv[3]

# --- StatusHints.java ---
with open(status_hints_path) as f:
    content = f.read()

replacements = [
    (
        '''    private final CharSequence mLabel;
    private final Icon mIcon;
    private final Bundle mExtras;''',
        '''    private final CharSequence mLabel;
    // Hexaphone: not final — see setIcon() below.
    private Icon mIcon;
    private final Bundle mExtras;''',
    ),
    (
        '''    public Icon getIcon() {
        return mIcon;
    }''',
        '''    public Icon getIcon() {
        return mIcon;
    }

    /**
     * Hexaphone: caller-side code (packages/services/Telecomm, stock,
     * unforked) mutates the icon in place via
     * getStatusHints().setIcon(...) after validating it — this fork's
     * StatusHints predates that and only had the getter.
     */
    public void setIcon(Icon icon) {
        mIcon = icon;
    }

    /**
     * Hexaphone: this fork predates an upstream security fix that
     * stripped cross-user-profile references out of an Icon (e.g. a
     * content:// URI pointing at another profile's data) before it's
     * displayed. packages/services/Telecomm (stock, unforked) calls this
     * expecting that validation to happen. Safe no-op passthrough here:
     * this device has no multi-user profiles, so there's no cross-user
     * boundary for an Icon to violate in the first place.
     */
    public static Icon validateAccountIconUserBoundary(Icon icon, android.os.UserHandle userHandle) {
        return icon;
    }

    /**
     * Hexaphone: same security fix as validateAccountIconUserBoundary()
     * above — parses a user ID out of a multi-user-aware content:// URI
     * authority, upstream. Always returns fallbackUserId here: no
     * multi-user profiles on this device, so there's no authority
     * encoding to actually parse, and no other user's ID it could
     * legitimately resolve to anyway.
     */
    public static int getUserIdFromAuthority(String authority, int fallbackUserId) {
        return fallbackUserId;
    }''',
    ),
]

for old, new in replacements:
    if old not in content:
        print("error: StatusHints.java anchor text not found, file may have changed", file=sys.stderr)
        sys.exit(1)
    content = content.replace(old, new, 1)

with open(status_hints_path, "w") as f:
    f.write(content)

# --- TelecomServiceImpl.java ---
with open(telecom_impl_path) as f:
    content = f.read()

replacements = [
    (
        '''        public ParceledListSlice<PhoneAccountHandle> getCallCapablePhoneAccounts(
                boolean includeDisabledAccounts, String callingPackage) {
            try {
                Log.startSession("TSI.gCCPA");
                if (!canReadPhoneState(callingPackage, "getDefaultOutgoingPhoneAccount")) {
                    return ParceledListSlice.emptyList();
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(
                                mPhoneAccountRegistrar.getCallCapablePhoneAccounts(null,
                                includeDisabledAccounts, callingUserHandle));''',
        '''        public List<PhoneAccountHandle> getCallCapablePhoneAccounts(
                boolean includeDisabledAccounts, String callingPackage) {
            try {
                Log.startSession("TSI.gCCPA");
                if (!canReadPhoneState(callingPackage, "getDefaultOutgoingPhoneAccount")) {
                    return Collections.emptyList();
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar.getCallCapablePhoneAccounts(null,
                                includeDisabledAccounts, callingUserHandle);''',
    ),
    (
        '''        public ParceledListSlice<PhoneAccountHandle> getSelfManagedPhoneAccounts(
                String callingPackage) {
            try {
                Log.startSession("TSI.gSMPA");
                if (!canReadPhoneState(callingPackage, "Requires READ_PHONE_STATE permission.")) {
                    throw new SecurityException("Requires READ_PHONE_STATE permission.");
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getSelfManagedPhoneAccounts(callingUserHandle));''',
        '''        public List<PhoneAccountHandle> getSelfManagedPhoneAccounts(
                String callingPackage) {
            try {
                Log.startSession("TSI.gSMPA");
                if (!canReadPhoneState(callingPackage, "Requires READ_PHONE_STATE permission.")) {
                    throw new SecurityException("Requires READ_PHONE_STATE permission.");
                }
                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar
                                .getSelfManagedPhoneAccounts(callingUserHandle);''',
    ),
    (
        '''        public ParceledListSlice<PhoneAccountHandle> getPhoneAccountsSupportingScheme(
                String uriScheme, String callingPackage) {
             try {
                Log.startSession("TSI.gPASS");
                try {
                    enforceModifyPermission(
                            "getPhoneAccountsSupportingScheme requires MODIFY_PHONE_STATE");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "getPhoneAccountsSupportingScheme: " + callingPackage);
                    return ParceledListSlice.emptyList();
                }

                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getCallCapablePhoneAccounts(uriScheme, false,
                                callingUserHandle));''',
        '''        public List<PhoneAccountHandle> getPhoneAccountsSupportingScheme(
                String uriScheme, String callingPackage) {
             try {
                Log.startSession("TSI.gPASS");
                try {
                    enforceModifyPermission(
                            "getPhoneAccountsSupportingScheme requires MODIFY_PHONE_STATE");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "getPhoneAccountsSupportingScheme: " + callingPackage);
                    return Collections.emptyList();
                }

                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar
                                .getCallCapablePhoneAccounts(uriScheme, false,
                                callingUserHandle);''',
    ),
    (
        '''        public ParceledListSlice<PhoneAccountHandle> getPhoneAccountsForPackage(
                String packageName) {''',
        '''        public List<PhoneAccountHandle> getPhoneAccountsForPackage(
                String packageName) {''',
    ),
    (
        '''                    Log.startSession("TSI.gPAFP");
                    return new ParceledListSlice<>(mPhoneAccountRegistrar
                            .getAllPhoneAccountHandlesForPackage(callingUserHandle, packageName));''',
        '''                    Log.startSession("TSI.gPAFP");
                    return mPhoneAccountRegistrar
                            .getAllPhoneAccountHandlesForPackage(callingUserHandle, packageName);''',
    ),
    (
        '''        public ParceledListSlice<PhoneAccount> getAllPhoneAccounts() {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.gAPA");
                    try {
                        enforceModifyPermission(
                                "getAllPhoneAccounts requires MODIFY_PHONE_STATE permission.");
                    } catch (SecurityException e) {
                        EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                                "getAllPhoneAccounts");
                        throw e;
                    }

                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getAllPhoneAccounts(callingUserHandle));''',
        '''        public List<PhoneAccount> getAllPhoneAccounts() {
            synchronized (mLock) {
                try {
                    Log.startSession("TSI.gAPA");
                    try {
                        enforceModifyPermission(
                                "getAllPhoneAccounts requires MODIFY_PHONE_STATE permission.");
                    } catch (SecurityException e) {
                        EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                                "getAllPhoneAccounts");
                        throw e;
                    }

                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar
                                .getAllPhoneAccounts(callingUserHandle);''',
    ),
    (
        '''        public ParceledListSlice<PhoneAccountHandle> getAllPhoneAccountHandles() {
            try {
                Log.startSession("TSI.gAPAH");
                try {
                    enforceModifyPermission(
                            "getAllPhoneAccountHandles requires MODIFY_PHONE_STATE permission.");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "getAllPhoneAccountHandles");
                    throw e;
                }

                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return new ParceledListSlice<>(mPhoneAccountRegistrar
                                .getAllPhoneAccountHandles(callingUserHandle));''',
        '''        public List<PhoneAccountHandle> getAllPhoneAccountHandles() {
            try {
                Log.startSession("TSI.gAPAH");
                try {
                    enforceModifyPermission(
                            "getAllPhoneAccountHandles requires MODIFY_PHONE_STATE permission.");
                } catch (SecurityException e) {
                    EventLog.writeEvent(0x534e4554, "62347125", Binder.getCallingUid(),
                            "getAllPhoneAccountHandles");
                    throw e;
                }

                synchronized (mLock) {
                    final UserHandle callingUserHandle = Binder.getCallingUserHandle();
                    long token = Binder.clearCallingIdentity();
                    try {
                        return mPhoneAccountRegistrar
                                .getAllPhoneAccountHandles(callingUserHandle);''',
    ),
    (
        '''                synchronized (mLock) {
                    try {
                        // This list is pre-filtered for the calling user.
                        return getAllPhoneAccounts().getList().size();''',
        '''                synchronized (mLock) {
                    try {
                        // This list is pre-filtered for the calling user.
                        // Hexaphone: getAllPhoneAccounts() returns List<T>
                        // directly now, not ParceledListSlice<T> — see
                        // that method's own fix comment.
                        return getAllPhoneAccounts().size();''',
    ),
]

for old, new in replacements:
    if old not in content:
        print("error: TelecomServiceImpl.java anchor text not found for one of the entries, file may have changed", file=sys.stderr)
        sys.exit(1)
    content = content.replace(old, new, 1)

with open(telecom_impl_path, "w") as f:
    f.write(content)

# --- frameworks/base/api/system-current.txt ---
with open(system_api_path) as f:
    content = f.read()

old = '''  public final class StatusHints implements android.os.Parcelable {
    ctor public deprecated StatusHints(android.content.ComponentName, java.lang.CharSequence, int, android.os.Bundle);
    method public deprecated android.graphics.drawable.Drawable getIcon(android.content.Context);
    method public deprecated int getIconResId();
    method public deprecated android.content.ComponentName getPackageName();
  }'''

new = '''  public final class StatusHints implements android.os.Parcelable {
    ctor public deprecated StatusHints(android.content.ComponentName, java.lang.CharSequence, int, android.os.Bundle);
    method public deprecated android.graphics.drawable.Drawable getIcon(android.content.Context);
    method public deprecated int getIconResId();
    method public deprecated android.content.ComponentName getPackageName();
    method public static int getUserIdFromAuthority(java.lang.String, int);
    method public void setIcon(android.graphics.drawable.Icon);
    method public static android.graphics.drawable.Icon validateAccountIconUserBoundary(android.graphics.drawable.Icon, android.os.UserHandle);
  }'''

if old not in content:
    print("error: system-current.txt StatusHints anchor text not found, file may have changed", file=sys.stderr)
    sys.exit(1)

with open(system_api_path, "w") as f:
    f.write(content.replace(old, new, 1))
EOF

echo "Telecom (StatusHints.java + TelecomServiceImpl.java + system-current.txt) patched."
echo "NOTE: if framework_intermediates/classes-header.jar already exists in your"
echo "out/ dir from a previous run, delete it now to force regeneration — this"
echo "build's dependency tracking doesn't treat system-current.txt as a rule input:"
echo "  rm -f \$HEXAPHONE_SRC/out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes-header.jar"
