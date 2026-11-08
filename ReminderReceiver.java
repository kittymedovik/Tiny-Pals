package club.tinywins.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CH = "reminders";

    @Override public void onReceive(Context c, Intent i) {
        String label = i.getStringExtra("label");
        int id = i.getIntExtra("id", 0);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CH, "Reminders", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, CH) : new android.app.Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Tiny Wins Club").setContentText(label == null ? "Time for a tiny win" : label).setContentIntent(pi).setAutoCancel(true);
        try { nm.notify(id, b.build()); } catch (SecurityException e) {}
        scheduleAll(c); // plan tomorrow's
    }

    static void scheduleAll(Context c) {
        SharedPreferences p = c.getSharedPreferences("tw", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray old = new JSONArray(p.getString("scheduled", "[]"));
            for (int k = 0; k < old.length(); k++) am.cancel(pending(c, old.getInt(k), null));
            JSONArray arr = new JSONArray(p.getString("rems", "[]")), ids = new JSONArray();
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                String[] hm = o.getString("t").split(":");
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0])); cal.set(Calendar.MINUTE, Integer.parseInt(hm[1])); cal.set(Calendar.SECOND, 0);
                if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
                int rid = Math.abs(o.getString("id").hashCode() % 100000) + 1;
                ids.put(rid);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pending(c, rid, o.optString("label")));
            }
            p.edit().putString("scheduled", ids.toString()).apply();
        } catch (Exception e) {}
    }

    static PendingIntent pending(Context c, int id, String label) {
        Intent i = new Intent(c, ReminderReceiver.class).putExtra("id", id).putExtra("label", label);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
