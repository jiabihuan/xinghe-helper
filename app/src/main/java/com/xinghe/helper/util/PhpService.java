package com.xinghe.helper.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import androidx.core.app.NotificationCompat;

import com.xinghe.helper.R;

public class PhpService extends Service {

    private static final String TAG = "PhpService";
    private static final String CHANNEL_ID = "php_service_channel";
    public static final int NOTIFICATION_ID = 2001;
    public static final String ACTION_START = "com.xinghe.helper.action.PHP_STARTED";
    public static final String ACTION_STOP = "com.xinghe.helper.action.PHP_STOPPED";
    public static final String EXTRA_URL = "php_server_url";
    public static final String EXTRA_STATUS = "php_server_status";

    private static volatile PhpService instance = null;
    private PhpLocalServer phpServer;
    private volatile String serverUrl = "";
    private volatile String interpreterStatus = "";
    private static volatile OnPhpServiceListener listener = null;

    public interface OnPhpServiceListener {
        void onStarted(String url, String status);
        void onStopped();
        void onError(String error);
    }

    public static void setListener(OnPhpServiceListener l) {
        listener = l;
    }

    public static boolean isRunning() {
        return instance != null && instance.phpServer != null;
    }

    public static String getServerUrl() {
        return instance != null ? instance.serverUrl : "";
    }

    public static String getInterpreterStatus() {
        return instance != null ? instance.interpreterStatus : "";
    }

    public static File getDocumentRoot() {
        if (instance != null && instance.phpServer != null) {
            return instance.phpServer.getDocumentRoot();
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        startPhpServer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (phpServer != null) {
            phpServer.stop();
            phpServer = null;
        }
        OnPhpServiceListener l = listener;
        if (l != null) l.onStopped();
        instance = null;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startPhpServer() {
        new Thread(() -> {
            try {
                PhpLocalServer server = new PhpLocalServer(this);
                server.startServer();
                phpServer = server;
                serverUrl = server.getServerUrl();
                interpreterStatus = server.getInterpreterStatus();
                updateNotification(serverUrl, "PHP 服务运行中");
                OnPhpServiceListener l = listener;
                if (l != null) l.onStarted(serverUrl, interpreterStatus);
            } catch (Throwable e) {
                Log.e(TAG, "PHP服务启动失败", e);
                String msg = "启动失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                updateNotification("", msg);
                OnPhpServiceListener l = listener;
                if (l != null) l.onError(msg);
            }
        }, "xinghe-php-service").start();
    }

    private void startForegroundCompat() {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("星河 PHP 服务")
                .setContentText("正在启动...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }
    }

    private void updateNotification(String url, String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        String text = url.isEmpty() ? status : url + " - " + status;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("星河 PHP 服务")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
        nm.notify(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "PHP服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
