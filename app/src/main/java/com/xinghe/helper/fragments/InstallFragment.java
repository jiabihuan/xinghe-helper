package com.xinghe.helper.fragments;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class InstallFragment extends Fragment {

    private static final String TAG = "InstallFragment";
    private static final String SERVER_URL = "http://172.245.61.121:3000";

    private TextView[] codeDigits = new TextView[4];
    private TextView tipText;
    private StringBuilder currentCode = new StringBuilder();

    private long downloadId = -1;
    private DownloadManager downloadManager;

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                installDownloadedApk();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_install, container, false);

        codeDigits[0] = view.findViewById(R.id.code_1);
        codeDigits[1] = view.findViewById(R.id.code_2);
        codeDigits[2] = view.findViewById(R.id.code_3);
        codeDigits[3] = view.findViewById(R.id.code_4);
        tipText = view.findViewById(R.id.tip_text);

        setupKeyboard(view);
        updateCodeDisplay();

        downloadManager = (DownloadManager) requireActivity().getSystemService(Context.DOWNLOAD_SERVICE);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().registerReceiver(downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(downloadReceiver);
    }

    private void setupKeyboard(View view) {
        int[] keyIds = {
                R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
                R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9,
                R.id.key_del, R.id.key_ok
        };

        View.OnClickListener keyListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tag = (String) v.getTag();
                if ("del".equals(tag)) {
                    if (currentCode.length() > 0) {
                        currentCode.deleteCharAt(currentCode.length() - 1);
                        updateCodeDisplay();
                    }
                } else if ("ok".equals(tag)) {
                    verifyCode();
                } else {
                    if (currentCode.length() < 4) {
                        currentCode.append(tag);
                        updateCodeDisplay();
                        if (currentCode.length() == 4) {
                            verifyCode();
                        }
                    }
                }
            }
        };

        for (int id : keyIds) {
            View key = view.findViewById(id);
            if (key != null) {
                key.setOnClickListener(keyListener);
            }
        }
    }

    private void updateCodeDisplay() {
        for (int i = 0; i < 4; i++) {
            if (i < currentCode.length()) {
                codeDigits[i].setText(String.valueOf(currentCode.charAt(i)));
                codeDigits[i].setBackgroundResource(R.drawable.bg_code_digit_active);
            } else {
                codeDigits[i].setText("");
                codeDigits[i].setBackgroundResource(R.drawable.bg_code_digit);
            }
        }
        tipText.setText(R.string.hint_code);
        tipText.setTextColor(getResources().getColor(R.color.text_secondary));
    }

    private void verifyCode() {
        if (currentCode.length() != 4) {
            tipText.setText(R.string.error_empty);
            tipText.setTextColor(getResources().getColor(R.color.error));
            return;
        }

        final String code = currentCode.toString();
        tipText.setText(R.string.loading_verify);
        tipText.setTextColor(getResources().getColor(R.color.text_secondary));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(SERVER_URL + "/api/code/" + code);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

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

                        JSONObject json = new JSONObject(response.toString());
                        boolean success = json.optBoolean("success", false);

                        if (success) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null) {
                                String downloadUrl = data.optString("downloadUrl", "");
                                String fileName = data.optString("fileName", "app.apk");

                                if (!downloadUrl.startsWith("http")) {
                                    downloadUrl = SERVER_URL + downloadUrl;
                                }

                                final String finalUrl = downloadUrl;
                                final String finalFileName = fileName;
                                requireActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        startDownload(finalUrl, finalFileName);
                                    }
                                });
                            }
                        } else {
                            requireActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tipText.setText(R.string.error_code);
                                    tipText.setTextColor(getResources().getColor(R.color.error));
                                    currentCode.setLength(0);
                                    updateCodeDisplay();
                                }
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tipText.setText(R.string.error_code);
                                tipText.setTextColor(getResources().getColor(R.color.error));
                                currentCode.setLength(0);
                                updateCodeDisplay();
                            }
                        });
                    }
                    conn.disconnect();
                } catch (final Exception e) {
                    Log.e(TAG, "Verify code error", e);
                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tipText.setText(getString(R.string.error_network) + ": " + e.getMessage());
                            tipText.setTextColor(getResources().getColor(R.color.error));
                        }
                    });
                }
            }
        }).start();
    }

    private void startDownload(String url, String fileName) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("正在下载: " + fileName);
            request.setDescription("星河助手下载中...");
            request.setDestinationInExternalFilesDir(requireContext(),
                    Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            downloadId = downloadManager.enqueue(request);
            tipText.setText(R.string.title_download);
            tipText.setTextColor(getResources().getColor(R.color.accent));
            Toast.makeText(requireContext(), "开始下载: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
            tipText.setText("下载失败: " + e.getMessage());
            tipText.setTextColor(getResources().getColor(R.color.error));
        }
    }

    private void installDownloadedApk() {
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            android.database.Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(columnIndex);
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String uriString = cursor.getString(
                            cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    cursor.close();

                    tipText.setText(R.string.title_downloaded);
                    tipText.setTextColor(getResources().getColor(R.color.success));

                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    Uri apkUri;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        File file = new File(Uri.parse(uriString).getPath());
                        apkUri = FileProvider.getUriForFile(requireContext(),
                                requireContext().getPackageName() + ".fileprovider", file);
                        installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        apkUri = Uri.parse(uriString);
                        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }

                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(installIntent);

                    currentCode.setLength(0);
                    updateCodeDisplay();
                } else {
                    tipText.setText("下载失败");
                    tipText.setTextColor(getResources().getColor(R.color.error));
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Install error", e);
            tipText.setText("安装启动失败: " + e.getMessage());
            tipText.setTextColor(getResources().getColor(R.color.error));
        }
    }
}
