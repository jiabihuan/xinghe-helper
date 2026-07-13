package com.xinghe.helper.cast;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.xinghe.helper.R;

public class CastService extends Service {

    private static final String TAG = "CastService";
    private static final String CHANNEL_ID = "cast_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private SSDPServer ssdpServer;
    private DLNAHttpServer httpServer;
    private static final int HTTP_PORT = 8192;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();

        CastState state = CastState.getInstance();
        ssdpServer = new SSDPServer(this, "星河助手投屏", HTTP_PORT);
        httpServer = new DLNAHttpServer(HTTP_PORT, ssdpServer, state);

        try {
            httpServer.start();
            ssdpServer.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start servers", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ssdpServer != null) {
            ssdpServer.stop();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundCompat() {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("星河助手投屏")
                .setContentText("投屏服务运行中")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
        try {
            // compileSdk 33: 直接用 startForeground，系统会读取 manifest 中的 foregroundServiceType
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "投屏服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
