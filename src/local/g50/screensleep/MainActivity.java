package local.g50.screensleep;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status;
    private RadioGroup choices;
    private Button permission;
    private boolean updating;
    private final int[] minutes = {0, 1, 5, 10, 30};
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(35, 45, 55));
        view.setPadding(0, dp(8), 0, dp(8)); return view;
    }
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(20), dp(28), dp(24));
        scroll.addView(content); setContentView(scroll);
        content.addView(text("Screen sleep", 28));
        content.addView(text("Choose when the screen turns off automatically.", 17));
        status = text("", 16); status.setTextColor(Color.rgb(21, 101, 192)); content.addView(status);
        permission = new Button(this); permission.setText("Allow system settings changes"); content.addView(permission);
        permission.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                catch (ActivityNotFoundException error) { status.setText("This firmware hides the permission page. See the project's ADB setup instructions."); }
            }
        });
        choices = new RadioGroup(this);
        for (int i = 0; i < minutes.length; i++) {
            RadioButton option = new RadioButton(this); option.setId(100 + i);
            option.setText(minutes[i] == 0 ? "Never (stay awake)" : minutes[i] + (minutes[i] == 1 ? " minute" : " minutes"));
            option.setTextSize(19); option.setMinHeight(dp(52)); choices.addView(option);
        }
        content.addView(choices);
        choices.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int id) {
                if (!updating && id >= 100 && id < 100 + minutes.length) apply(minutes[id - 100]);
            }
        });
        content.addView(text("Never works on battery and while charging. You can still turn the screen off with the power button.", 15));
        content.addView(text("Apps with their own keep-awake setting can override shorter timeouts.", 14));
        Button home = new Button(this); home.setText("Add to Home screen"); content.addView(home);
        home.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pin(); } });
    }
    public void onResume() {
        super.onResume();
        boolean allowed = Settings.System.canWrite(this);
        permission.setVisibility(allowed ? View.GONE : View.VISIBLE);
        for (int i = 0; i < choices.getChildCount(); i++) choices.getChildAt(i).setEnabled(allowed);
        if (!allowed) {
            status.setText("Allow this app to change system settings, then return here.");
            return;
        }
        int selected = getSharedPreferences("sleep", MODE_PRIVATE).getInt("minutes", 0);
        apply(selected);
    }
    private void apply(int selected) {
        try {
            if (!Settings.System.canWrite(this)) { status.setText("System settings permission is needed."); return; }
            if (!Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, selected == 0 ? Integer.MAX_VALUE : selected * 60000))
                throw new IllegalStateException("Android rejected the timeout");
            getSharedPreferences("sleep", MODE_PRIVATE).edit().putInt("minutes", selected).commit();
            Intent service = new Intent(this, AwakeService.class);
            if (selected == 0) startForegroundService(service); else stopService(service);
            updating = true;
            for (int i = 0; i < minutes.length; i++) if (minutes[i] == selected) choices.check(100 + i);
            updating = false;
            status.setText(selected == 0 ? "Active: Never sleep automatically" : "Active: " + selected + (selected == 1 ? " minute" : " minutes"));
        } catch (Exception error) { updating = false; status.setText("Could not change sleep: " + error.getMessage()); }
    }
    private void pin() {
        ShortcutManager shortcuts = getSystemService(ShortcutManager.class);
        if (!shortcuts.isRequestPinShortcutSupported()) {
            Toast.makeText(this, "Find Screen sleep in the Android app list.", Toast.LENGTH_LONG).show(); return;
        }
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "screen-sleep")
            .setShortLabel("Screen sleep").setIcon(Icon.createWithResource(this, getApplicationInfo().icon))
            .setIntent(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)).build();
        shortcuts.requestPinShortcut(shortcut, null);
    }
}
