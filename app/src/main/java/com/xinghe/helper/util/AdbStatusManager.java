package com.xinghe.helper.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdbStatusManager {

    public interface AdbCheckCallback {
        void onAdbAvailable();
        void onAdbUnavailable();
    }

    private static AdbStatusManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Boolean adbAvailable = null;
    private boolean checking = false;

    private AdbStatusManager() {}

    public static synchronized AdbStatusManager getInstance() {
        if (instance == null) {
            instance = new AdbStatusManager();
        }
        return instance;
    }

    public Boolean getAdbAvailable() {
        return adbAvailable;
    }

    public void checkAdbStatus(AdbCheckCallback callback) {
        if (adbAvailable != null) {
            if (callback != null) {
                if (adbAvailable) {
                    mainHandler.post(callback::onAdbAvailable);
                } else {
                    mainHandler.post(callback::onAdbUnavailable);
                }
            }
            return;
        }
        if (checking) return;
        checking = true;
        executor.submit(() -> {
            boolean available = AdbInstallUtil.isAdbAvailable();
            adbAvailable = available;
            checking = false;
            if (callback != null) {
                mainHandler.post(() -> {
                    if (available) {
                        callback.onAdbAvailable();
                    } else {
                        callback.onAdbUnavailable();
                    }
                });
            }
        });
    }

    public void refreshAdbStatus(AdbCheckCallback callback) {
        adbAvailable = null;
        checkAdbStatus(callback);
    }
}
