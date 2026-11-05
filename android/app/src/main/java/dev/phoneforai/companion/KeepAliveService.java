package dev.phoneforai.companion;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/** Foreground service: keeps the process unfrozen and self-heals the accessibility grant. */
public final class KeepAliveService extends Service {
    private static final String CHANNEL = "pfa_keepalive";
    private static final int NOTIFICATION_ID = 42;
    private static final String COMPONENT =
            "dev.phoneforai.companion/dev.phoneforai.companion.PhoneAccessibilityService";
    private final ScheduledExecutorService guard = Executors.newSingleThreadScheduledExecutor();
    public static void start(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }
    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startInForeground();
        guard.scheduleWithFixedDelay(this::healAccessibility, 3, 15, TimeUnit.SECONDS);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        BridgeClient.start(this);
        return START_STICKY;
    }
    @Override public void onDestroy() {
        guard.shutdownNow();
        super.onDestroy();
    }
    /** If vivo tore down the accessibility service, rewrite the secure setting to bring it back. */
    private void healAccessibility() {
        try {
            if (PhoneAccessibilityService.isConnected()) return;
            ContentResolver cr = getContentResolver();
            String enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) enabled = "";
            boolean present = false;
            for (String part : enabled.split(":")) {
                if (COMPONENT.equalsIgnoreCase(part.trim())) { present = true; break; }
            }
            if (!present) {
                String next = TextUtils.isEmpty(enabled) ? COMPONENT : enabled + ":" + COMPONENT;
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Throwable ignored) {
            // No WRITE_SECURE_SETTINGS grant yet, or ROM blocked it; try again next tick.
        }
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
