package org.hexaphone.updater;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;

/**
 * Fetches SelfUpdateInfo and compares it against the installed
 * versionCode. Silent by design: if this fails (offline, GitHub
 * unreachable) there's simply no update banner -- the release list
 * below still works standalone, so a failed self-update check shouldn't
 * block or scare anyone.
 */
class SelfUpdateCheckTask extends AsyncTask<Void, Void, SelfUpdateInfo> {

    interface Listener {
        /** Called with null if no update is available (or the check failed). */
        void onSelfUpdateResult(SelfUpdateInfo info);
    }

    private final Context appContext;
    private final Listener listener;

    SelfUpdateCheckTask(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    @Override
    protected SelfUpdateInfo doInBackground(Void... params) {
        try {
            int installedVersionCode = appContext.getPackageManager()
                    .getPackageInfo(appContext.getPackageName(), 0).versionCode;
            SelfUpdateInfo info = SelfUpdateInfo.fetch();
            return info.versionCode > installedVersionCode ? info : null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(SelfUpdateInfo info) {
        listener.onSelfUpdateResult(info);
    }
}
