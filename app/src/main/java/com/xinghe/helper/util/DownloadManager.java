package com.xinghe.helper.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.xinghe.helper.coredata.CoreData;
import com.xinghe.helper.model.PasswordApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {

    public interface DownloadListener {
        void onProgress(int index, int percent, long downloaded, long total);
        void onComplete(int index, File apkFile);
        void onError(int index, String error);
        void onAllComplete();
    }

    public static class DownloadTask {
        public PasswordApp app;
        public int percent;
        public long downloaded;
        public long total;
        public boolean completed;
        public boolean error;
        public String errorMsg;
        public File apkFile;

        public DownloadTask(PasswordApp app) {
            this.app = app;
            this.percent = 0;
            this.downloaded = 0;
            this.total = app.getSize();
            this.completed = false;
            this.error = false;
            this.errorMsg = "";
        }
    }

    private static DownloadManager instance;
    private final List<DownloadTask> tasks = new ArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private DownloadListener listener;

    private DownloadManager() {}

    public static synchronized DownloadManager getInstance() {
        if (instance == null) {
            instance = new DownloadManager();
        }
        return instance;
    }

    public void setListener(DownloadListener listener) {
        this.listener = listener;
    }

    public List<DownloadTask> getTasks() {
        return tasks;
    }

    public void addTask(PasswordApp app, Context context) {
        DownloadTask task = new DownloadTask(app);
        tasks.add(task);
        final int index = tasks.size() - 1;

        executor.submit(() -> {
            try {
                downloadFile(task, context, index);
            } catch (Exception e) {
                task.error = true;
                task.errorMsg = e.getMessage();
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onError(index, e.getMessage());
                    }
                    checkAllComplete();
                });
            }
        });
    }

    public void addTasks(List<PasswordApp> apps, Context context) {
        for (PasswordApp app : apps) {
            addTask(app, context);
        }
    }

    private void downloadFile(DownloadTask task, Context context, int index) throws Exception {
        PasswordApp app = task.app;
        String downloadUrl = app.getDownloadUrl();
        if (downloadUrl == null || !downloadUrl.startsWith("http")) {
            downloadUrl = CoreData.HTTP_BASE_URL + downloadUrl;
        }

        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("服务器错误: " + responseCode);
        }

        int contentLength = conn.getContentLength();
        if (contentLength > 0) {
            task.total = contentLength;
        }

        File outputDir = context.getExternalCacheDir();
        if (outputDir == null) {
            outputDir = context.getCacheDir();
        }
        File apkFile = new File(outputDir, app.getName() + ".apk");

        InputStream inputStream = conn.getInputStream();
        FileOutputStream outputStream = new FileOutputStream(apkFile);

        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalRead = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            totalRead += bytesRead;
            task.downloaded = totalRead;

            if (task.total > 0) {
                task.percent = (int) ((totalRead * 100) / task.total);
            }

            final int percent = task.percent;
            final long downloaded = totalRead;
            final long total = task.total;

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onProgress(index, percent, downloaded, total);
                }
            });
        }

        outputStream.close();
        inputStream.close();
        conn.disconnect();

        if (apkFile.exists() && apkFile.length() > 0) {
            task.completed = true;
            task.apkFile = apkFile;
            task.percent = 100;

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onComplete(index, apkFile);
                }
                ApkInstallUtil.installApk(context, apkFile);
                checkAllComplete();
            });
        } else {
            throw new Exception("下载失败");
        }
    }

    private void checkAllComplete() {
        boolean allDone = true;
        for (DownloadTask task : tasks) {
            if (!task.completed && !task.error) {
                allDone = false;
                break;
            }
        }
        if (allDone && listener != null) {
            mainHandler.post(() -> listener.onAllComplete());
        }
    }

    public void clearTasks() {
        tasks.clear();
    }
}
