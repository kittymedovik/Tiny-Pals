package club.tinywins.app;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

/** Methods the web app calls as window.TinyNative.*  Everything here is untested on a real device. */
public class NativeBridge {
    private final Activity act;
    NativeBridge(Activity a) { act = a; }

    @JavascriptInterface public boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) act.getSystemService(Context.APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), act.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
    @JavascriptInterface public void openUsageSettings() {
        act.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    @JavascriptInterface public String getUsageToday() {
        try {
            UsageStatsManager um = (UsageStatsManager) act.getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            long start = c.getTimeInMillis(), end = System.currentTimeMillis();
            Map<String, UsageStats> m = um.queryAndAggregateUsageStats(start, end);
            PackageManager pm = act.getPackageManager();
            ArrayList<Object[]> rows = new ArrayList<>();
            long total = 0;
            for (UsageStats u : m.values()) {
                long t = u.getTotalTimeInForeground();
                if (t < 60000 || u.getPackageName().equals(act.getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(u.getPackageName()) == null) continue; // skip system pieces
                String name = u.getPackageName();
                try { name = pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString(); } catch (Exception e) {}
                rows.add(new Object[]{name, t});
                total += t;
            }
            Collections.sort(rows, new Comparator<Object[]>() {
                public int compare(Object[] a, Object[] b) { return Long.compare((Long) b[1], (Long) a[1]); }
            });
            JSONArray apps = new JSONArray();
            for (int i = 0; i < rows.size() && i < 5; i++) {
                apps.put(new JSONObject().put("name", rows.get(i)[0]).put("minutes", Math.round((Long) rows.get(i)[1] / 60000.0)));
            }
            return new JSONObject().put("minutes", Math.round(total / 60000.0)).put("apps", apps).toString();
        } catch (Exception e) { return "null"; }
    }

    @JavascriptInterface public boolean hasDndAccess() {
        return ((NotificationManager) act.getSystemService(Context.NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted();
    }
    @JavascriptInterface public void openDndSettings() {
        act.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    /** on=true: alarms only (silences notifications and calls). on=false: restore what the user had. */
    @JavascriptInterface public void setDnd(boolean on) {
        NotificationManager nm = (NotificationManager) act.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) return;
        SharedPreferences p = act.getSharedPreferences("tw", Context.MODE_PRIVATE);
        if (on) {
            if (!p.contains("prevFilter")) p.edit().putInt("prevFilter", nm.getCurrentInterruptionFilter()).apply();
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
        } else if (p.contains("prevFilter")) {
            int prev = p.getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL);
            nm.setInterruptionFilter(prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN ? NotificationManager.INTERRUPTION_FILTER_ALL : prev);
            p.edit().remove("prevFilter").apply();
        }
    }

    @JavascriptInterface public boolean hasNotifPermission() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    @JavascriptInterface public void requestNotif() {
        if (Build.VERSION.SDK_INT >= 33) act.runOnUiThread(new Runnable() { public void run() {
            act.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }});
    }
    /** json: [{id,t:"HH:MM",label}]  Saved and scheduled as daily repeating reminders. */
    @JavascriptInterface public void setReminders(String json) {
        act.getSharedPreferences("tw", Context.MODE_PRIVATE).edit().putString("rems", json).apply();
        ReminderReceiver.scheduleAll(act);
    }
}
