package com.safepulse.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * 시동이 켜져 있는 동안 판별 엔진을 살려 두는 포그라운드 서비스.
 * 화면이 다른 앱일 때 위기가 감지되면 긴급 알림(전체 화면 인텐트)으로 앱을 띄운다.
 * 음성 안내는 화면과 상관없이 나간다.
 */
public class SafePulseService extends Service {

    private static final String CH_STATUS = "status";
    private static final String CH_ALERT = "alert";
    private static final int ID_STATUS = 1;
    private static final int ID_ALERT = 2;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannels(this);
        Notification n = new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_monitoring))
                .setContentIntent(openAppIntent(this))
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(ID_STATUS, n);
            }
        } catch (Exception e) {
            Log.w("SafePulse", "startForeground failed; running while app is open", e);
        }
        SafePulseApp.controller(this).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static void showAlert(Context context, String title, String text) {
        try {
            ensureChannels(context);
            PendingIntent open = openAppIntent(context);
            Notification n = new Notification.Builder(context, CH_ALERT)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setContentIntent(open)
                    .setFullScreenIntent(open, true)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(ID_ALERT, n);
        } catch (Exception e) {
            Log.w("SafePulse", "alert notification failed", e);
        }
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent i = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void ensureChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CH_STATUS,
                context.getString(R.string.notif_channel_status), NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_ALERT,
                context.getString(R.string.notif_channel_alert), NotificationManager.IMPORTANCE_HIGH));
    }
}
