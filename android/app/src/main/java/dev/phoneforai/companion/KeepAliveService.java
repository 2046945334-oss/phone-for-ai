package dev.phoneforai.companion;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
/** Foreground service: keeps the process unfrozen so the heartbeat poll keeps running. */
public final class KeepAliveService extends Service {
    private static final String CHANNEL = "pfa_keepalive";
    private static final int NOTIFICATION_ID = 42;
    public static void start(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }
    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startInForeground();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        BridgeClient.start(this);
        return START_STICKY;
    }
    private void startInForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION_ID, notification);
    }
    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return builder
                .setContentTitle(getString(R.string.keepalive_title))
                .setContentText(getString(R.string.keepalive_text))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, getString(R.string.keepalive_channel), NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
