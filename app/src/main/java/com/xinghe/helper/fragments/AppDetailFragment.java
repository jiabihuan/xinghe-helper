package com.xinghe.helper.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;
import com.xinghe.helper.coredata.CoreData;
import com.xinghe.helper.model.PasswordApp;
import com.xinghe.helper.util.ApkInstallUtil;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppDetailFragment extends Fragment {

    private String code;
    private PasswordApp app;

    private TextView btnBack;
    private TextView tvCode;
    private TextView tvAppIcon;
    private TextView tvAppName;
    private TextView tvPackageName;
    private TextView tvVersion;
    private TextView tvSize;
    private TextView tvDownloads;
    private TextView btnDownload;
    private TextView tvDownloadStatus;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public static AppDetailFragment newInstance(String code) {
        AppDetailFragment fragment = new AppDetailFragment();
        Bundle args = new Bundle();
        args.putString("code", code);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_detail, container, false);

        if (getArguments() != null) {
            code = getArguments().getString("code", "");
        }

        btnBack = view.findViewById(R.id.btnBack);
        tvCode = view.findViewById(R.id.tvCode);
        tvAppIcon = view.findViewById(R.id.tvAppIcon);
        tvAppName = view.findViewById(R.id.tvAppName);
        tvPackageName = view.findViewById(R.id.tvPackageName);
        tvVersion = view.findViewById(R.id.tvVersion);
        tvSize = view.findViewById(R.id.tvSize);
        tvDownloads = view.findViewById(R.id.tvDownloads);
        btnDownload = view.findViewById(R.id.btnDownload);
        tvDownloadStatus = view.findViewById(R.id.tvDownloadStatus);

        tvCode.setText("口令: " + code);

        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        btnDownload.setOnClickListener(v -> {
            if (app != null) {
                downloadAndInstall(app);
            }
        });

        btnDownload.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnDownload.setTextColor(getResources().getColor(R.color.white));
            } else {
                btnDownload.setTextColor(0xFF4CAF50);
            }
        });

        loadAppInfo();

        return view;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadAppInfo() {
        btnDownload.setEnabled(false);
        btnDownload.setText("加载中...");

        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = CoreData.HTTP_BASE_URL + "/api/codes/" + code;
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(response.toString());
                    JSONObject appJson = root.optJSONObject("app");
                    if (appJson != null) {
                        app = new PasswordApp(
                                appJson.optLong("id"),
                                appJson.optString("name"),
                                appJson.optString("package_name"),
                                appJson.optString("version_name"),
                                appJson.optLong("version_code"),
                                21,
                                appJson.optString("download_url"),
                                "",
                                appJson.optLong("apk_size"),
                                appJson.optString("icon_url"),
                                0,
                                appJson.optString("description"),
                                "",
                                0.0f
                        );

                        if (app.getDownloadUrl() != null && !app.getDownloadUrl().startsWith("http")) {
                            app.setDownloadUrl(CoreData.HTTP_BASE_URL + app.getDownloadUrl());
                        }

                        mainHandler.post(this::updateUI);
                    } else {
                        mainHandler.post(() -> {
                            Toast.makeText(getActivity(), "应用不存在", Toast.LENGTH_SHORT).show();
                            btnDownload.setEnabled(false);
                            btnDownload.setText("应用不存在");
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "服务器错误: " + responseCode, Toast.LENGTH_SHORT).show();
                        btnDownload.setEnabled(true);
                        btnDownload.setText("重试");
                    });
                }
            } catch (final Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(getActivity(), "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnDownload.setEnabled(true);
                    btnDownload.setText("重试");
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void updateUI() {
        if (app == null) return;

        tvAppName.setText(app.getName());
        tvPackageName.setText(app.getPackageName());
        tvVersion.setText("版本 " + app.getVersionName());
        tvSize.setText(formatSize(app.getSize()));
        tvDownloads.setText("0 次下载");

        btnDownload.setEnabled(true);
        btnDownload.setText("立即下载");
    }

    private void downloadAndInstall(final PasswordApp app) {
        final Context context = getContext();
        if (context == null || app == null) return;

        btnDownload.setEnabled(false);
        btnDownload.setText("下载中...");
        tvDownloadStatus.setVisibility(View.VISIBLE);
        tvDownloadStatus.setText("正在下载...");

        Toast.makeText(context, "开始下载: " + app.getName(), Toast.LENGTH_SHORT).show();

        executor.submit(() -> {
            try {
                String downloadUrl = app.getDownloadUrl();
                if (!downloadUrl.startsWith("http")) {
                    downloadUrl = CoreData.HTTP_BASE_URL + downloadUrl;
                }

                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                java.io.File outputDir = context.getExternalCacheDir();
                if (outputDir == null) {
                    outputDir = context.getCacheDir();
                }
                java.io.File apkFile = new java.io.File(outputDir, app.getName() + ".apk");

                java.io.InputStream inputStream = conn.getInputStream();
                java.io.FileOutputStream outputStream = new java.io.FileOutputStream(apkFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();
                conn.disconnect();

                if (apkFile.exists() && apkFile.length() > 0) {
                    mainHandler.post(() -> {
                        btnDownload.setText("下载完成");
                        tvDownloadStatus.setText("下载完成，准备安装");
                        tvDownloadStatus.setTextColor(getResources().getColor(R.color.success));
                        Toast.makeText(context, "下载完成，准备安装", Toast.LENGTH_SHORT).show();
                        ApkInstallUtil.installApk(context, apkFile);
                    });
                } else {
                    mainHandler.post(() -> {
                        btnDownload.setEnabled(true);
                        btnDownload.setText("重新下载");
                        tvDownloadStatus.setText("下载失败");
                        tvDownloadStatus.setTextColor(getResources().getColor(R.color.error));
                        Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show();
                    });
                }

            } catch (final Exception e) {
                mainHandler.post(() -> {
                    btnDownload.setEnabled(true);
                    btnDownload.setText("重新下载");
                    tvDownloadStatus.setText("下载错误");
                    tvDownloadStatus.setTextColor(getResources().getColor(R.color.error));
                    Toast.makeText(context, "下载错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String formatSize(long size) {
        if (size <= 0) return "未知大小";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}
