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

/**
 * Foreground service that owns the server lifecycle.
 *
 * The PHP server and the optional frp tunnel are children of this service's
 * process, not of the UI activity. That is what makes "run in the background"
 * actually work: when the user presses home and the activity is destroyed, the
 * service (and its notification) keep the app process alive, so the server and
 * the tunnel keep running. The activity only sends intents:
 *   ACTION_START - bring the server (and tunnel, if enabled) up
 *   ACTION_STOP  - ask the server to shut down gracefully
 *   ACTION_KILL  - force-kill everything right now
 *
 * When the server process really exits, ServerUtils calls {@link #onServerExited()}
 * which tears the service (and frp) down.
 */
public class ServerService extends Service {
    private static final String CHANNEL_ID = "bluelight_server";
    private static ServerService sInstance;

    public static final String ACTION_START = "com.haniokasai.app.pmmp_srv.action.START";
    public static final String ACTION_STOP = "com.haniokasai.app.pmmp_srv.action.STOP";
    public static final String ACTION_KILL = "com.haniokasai.app.pmmp_srv.action.KILL";

    private boolean mStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopGraceful();
        } else if (ACTION_KILL.equals(action)) {
            killNow();
        } else {
            // No action (or ACTION_START) -> bring the server up.
            startServer();
        }
        // START_NOT_STICKY: if the system kills us, do NOT auto-restart the
        // server. A restart loop on a crashed server is worse than a dead server.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Belt and suspenders: if the system tears the service down, make sure
        // the child processes do not get orphaned.
        ServerUtils.killServer();
        FrpManager.stop();
        sInstance = null;
        super.onDestroy();
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

    private void startServer() {
        if (mStarted) {
            ConsoleActivity.log("[Server] Already running; ignoring duplicate start request.");
            return;
        }
        mStarted = true;
        ensureChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        builder.setContentTitle(("PocketMine") + " " + getString(R.string.message_running));
        builder.setContentText(getString(R.string.message_tap_open));
        builder.setOngoing(true);
        builder.setSmallIcon(R.drawable.ic_launcher);
        builder.setContentIntent(PendingIntent.getActivity(this, 0,
                new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        startForeground(1337, builder.build());
        ConsoleActivity.log("[Server] Service started in foreground; launching server process.");

        // Launch on a worker thread: php preparation + startup can be slow and
        // onStartCommand must not block.
        new Thread(this::launchAll).start();
    }

    /** Starts the frp tunnel (if enabled) and then the PHP server. */
    private void launchAll() {
        if (AppSettings.frpEnabled(this)) {
            if (FrpManager.isFrpcInstalled(this)) {
                FrpManager.start(this);
            } else {
                ConsoleActivity.log("[frp] Tunnel is enabled but frpc is not installed. "
                        + "Open the frp settings (menu > frp Tunnel) to install it; "
                        + "the server will run without the tunnel for now.");
            }
        }
        ServerUtils.runServer();
    }

    /** Ask the server to shut down cleanly, then force-kill if it lingers. */
    private void stopGraceful() {
        if (!mStarted) {
            stopSelf();
            return;
        }
        ConsoleActivity.log("[Server] Stop requested; sending 'stop' to the server…");
        ServerUtils.writeCommand("stop");
        // Give the server up to ~4s to exit on its own before we force-kill.
        new Thread(() -> {
            try {
                for (int i = 0; i < 40; i++) {
                    if (!ServerUtils.isRunning()) break;
                    Thread.sleep(100);
                }
            } catch (InterruptedException ignored) {
            }
            if (ServerUtils.isRunning()) {
                ConsoleActivity.log("[Server] Did not exit in time; force-killing.");
                ServerUtils.killServer();
            }
            FrpManager.stop();
            // If the server already exited, the monitor would have torn us down;
            // if not, we just force-killed it above and must stop ourselves.
            if (!ServerUtils.isRunning() && sInstance != null) {
                sInstance.stopForeground(true);
                sInstance.stopSelf();
            }
        }).start();
    }

    private void killNow() {
        mStarted = false;
        ConsoleActivity.log("[Server] Force-killing server and tunnel.");
        ServerUtils.killServer();
        FrpManager.stop();
        stopForeground(true);
        stopSelf();
    }

    /**
     * Called by ServerUtils when the server process actually exits (clean stop
     * or crash). Tears down the notification, the tunnel, and the service.
     */
    public static void onServerExited() {
        if (sInstance != null) {
            sInstance.mStarted = false;
        }
        MainActivity.notifyServerStopped();
        // The tunnel target is gone, so bring frpc down too.
        FrpManager.stop();
        if (sInstance != null) {
            sInstance.stopForeground(true);
            sInstance.stopSelf();
        }
    }

    /** Activity convenience: stop everything now (used by the Kill menu). */
    public static void requestKill() {
        if (sInstance != null) {
            sInstance.killNow();
        }
    }
}
