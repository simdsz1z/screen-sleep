package local.g50.screensleep;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        if (context.getSharedPreferences("sleep", Context.MODE_PRIVATE).getInt("minutes", -1) == 0)
            context.startForegroundService(new Intent(context, AwakeService.class));
    }
}
