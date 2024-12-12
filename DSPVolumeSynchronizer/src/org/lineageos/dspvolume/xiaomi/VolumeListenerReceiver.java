package org.lineageos.dspvolume.xiaomi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

import android.os.Bundle;

public class VolumeListenerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) {
            return;
        }

        if(intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", 0) == AudioManager.STREAM_MUSIC) {
            AudioManager audioManager = context.getSystemService(AudioManager.class);
            int current = intent.getIntExtra(
                            "android.media.EXTRA_VOLUME_STREAM_VALUE",
                            0
                          );
            audioManager.setParameters("volume_change=" + current + ";flags=8");
        }
    }
}
