package local.g50.screensleep;

import android.app.*;
import android.content.*;
import android.os.*;

public class AwakeService extends Service {
    private PowerManager.WakeLock lock;
    public int onStartCommand(Intent intent, int flags, int id) {
        if (getSharedPreferences("sleep", MODE_PRIVATE).getInt("minutes", 0) != 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("awake", "Screen stays on", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "awake")
            .setSmallIcon(getApplicationInfo().icon)
            .setContentTitle("Screen sleep: Never")
            .setContentText("Tap to change. Power button still turns the screen off.")
            .setContentIntent(open).setOngoing(true).setShowWhen(false).build();
        startForeground(1, notification);
        if (lock == null) {
            PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
            lock = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "ScreenSleep:Never");
            lock.setReferenceCounted(false);
        }
        if (!lock.isHeld()) lock.acquire();
        return START_STICKY;
    }
    public void onDestroy() {
        if (lock != null && lock.isHeld()) lock.release();
        stopForeground(true);
        super.onDestroy();
    }
    public IBinder onBind(Intent intent) { return null; }
}
