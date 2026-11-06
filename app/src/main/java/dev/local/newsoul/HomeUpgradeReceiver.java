package dev.local.newsoul;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Apply the saved opt-in choice on upgrade; absent choice remains hidden. */
public final class HomeUpgradeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
            HomeApplication.initializeLauncher(context);
    }
}
