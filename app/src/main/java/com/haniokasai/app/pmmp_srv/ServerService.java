package com.haniokasai.app.pmmp_srv;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ServerService extends Service {
    private static final String CHANNEL_ID = "bluelight_server";
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        run();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "Server", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("PocketMine server status");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void run() {
        if (!isRunning) {
            isRunning = true;
            ensureChannel();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
            builder.setContentTitle(("PocketMine") + " " + MainActivity.instance.getString(R.string.message_running));
            builder.setContentText(MainActivity.instance.getString(R.string.message_tap_open));
            builder.setOngoing(true);
            builder.setSmallIcon(R.drawable.ic_launcher);
            builder.setContentIntent(PendingIntent.getActivity(this, 0,
                    new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            startForeground(1337, builder.build());
        }
    }

    private void stop() {
        if (isRunning) {
            isRunning = false;
            stopForeground(true);
        }
    }
}
