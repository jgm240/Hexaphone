package org.hexaphone.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/**
 * Receives the async result of the self-update PackageInstaller.Session
 * started by SelfUpdateInstallTask. On real success Android kills and
 * restarts this process with the new version before this even has a
 * chance to matter much -- this mainly exists to surface the system's
 * confirmation UI (STATUS_PENDING_USER_ACTION) and to log failures.
 */
public class SelfUpdateInstallReceiver extends BroadcastReceiver {
    private static final String TAG = "HexaphoneUpdater";

    static final String ACTION_INSTALL_STATUS = "org.hexaphone.updater.SELF_UPDATE_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "self-update succeeded");
                break;
            default:
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.w(TAG, "self-update failed: " + message);
                break;
        }
    }
}
