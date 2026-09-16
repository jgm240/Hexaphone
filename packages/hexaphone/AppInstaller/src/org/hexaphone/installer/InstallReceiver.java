package org.hexaphone.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/**
 * Receives the async result of a PackageInstaller.Session.commit() started
 * by InstallTask. Declared not-exported in the manifest, so only this app
 * (and the system, which addresses it by the PendingIntent it was handed,
 * not by matching the intent-filter) can trigger it.
 */
public class InstallReceiver extends BroadcastReceiver {
    private static final String TAG = "HexaphoneInstaller";

    static final String ACTION_INSTALL_STATUS = "org.hexaphone.installer.INSTALL_STATUS";
    static final String EXTRA_DISPLAY_NAME = "org.hexaphone.installer.extra.DISPLAY_NAME";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        String displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // Not a priv-app allowlisted for silent INSTALL_PACKAGES, so
                // the system needs the user to confirm -- surface that UI.
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "install succeeded: " + packageName);
                InstallResultBus.notifyResult(packageName, displayName, true, null);
                break;
            default:
                Log.w(TAG, "install failed for " + displayName + " (" + packageName + "): "
                        + message);
                InstallResultBus.notifyResult(packageName, displayName, false, message);
                break;
        }
    }
}
