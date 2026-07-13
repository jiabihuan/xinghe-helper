package com.xinghe.helper.fragments;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;
import com.xinghe.helper.activity.CastPlayerActivity;
import com.xinghe.helper.cast.CastService;
import com.xinghe.helper.cast.CastState;

public class CastFragment extends Fragment {

    private static final String TAG = "CastFragment";

    private TextView statusText;
    private TextView wifiNameText;
    private TextView wifiNameHighlight;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean playerActivityRunning = false;

    private final CastState.StateListener castListener = new CastState.StateListener() {
        @Override
        public void onPlay(String url, String mimeType) {
            if (getActivity() == null || !isAdded() || playerActivityRunning) return;
            handler.post(() -> {
                if (getActivity() == null || !isAdded() || playerActivityRunning) return;
                playerActivityRunning = true;
                Intent intent = new Intent(getActivity(), CastPlayerActivity.class);
                startActivity(intent);
            });
        }

        @Override
        public void onPause() {
        }

        @Override
        public void onStop() {
            handler.post(CastFragment.this::updateStatus);
        }

        @Override
        public void onSeek(long position) {}

        @Override
        public void onVolume(int volume) {}

        @Override
        public void onMute(boolean mute) {}
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cast, container, false);

        statusText = view.findViewById(R.id.statusText);
        wifiNameText = view.findViewById(R.id.wifiNameText);
        wifiNameHighlight = view.findViewById(R.id.wifiNameHighlight);

        handler.postDelayed(this::startCastService, 300);
        handler.post(this::updateWifiInfo);

        updateStatus();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        playerActivityRunning = false;
        CastState.getInstance().addListener(castListener);
        updateStatus();
        updateWifiInfo();
    }

    @Override
    public void onPause() {
        super.onPause();
        CastState.getInstance().removeListener(castListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        CastState.getInstance().removeListener(castListener);
    }

    private void startCastService() {
        if (getActivity() == null || !isAdded()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getActivity(),
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        try {
            Intent intent = new Intent(getActivity(), CastService.class);
            ContextCompat.startForegroundService(getActivity(), intent);
        } catch (Exception e) {
            if (getActivity() != null && isAdded()) {
                Toast.makeText(getActivity(), "投屏服务启动失败，请重试", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateWifiInfo() {
        if (getActivity() == null || !isAdded()) return;
        String wifiName = getCurrentWifiName();
        if (!TextUtils.isEmpty(wifiName) && !"<unknown ssid>".equals(wifiName)) {
            wifiNameText.setText(wifiName);
            wifiNameHighlight.setText(wifiName);
        } else {
            wifiNameText.setText("WiFi未连接");
            wifiNameHighlight.setText("同一WiFi");
        }
    }

    private String getCurrentWifiName() {
        if (getActivity() == null) return "";
        try {
            WifiManager wifiManager = (WifiManager) getActivity().getApplicationContext()
                    .getSystemService(android.content.Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    String ssid = wifiInfo.getSSID();
                    if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid = ssid.substring(1, ssid.length() - 1);
                    }
                    return ssid;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void updateStatus() {
        if (statusText == null) return;
        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty()) {
            statusText.setText("投屏中...");
        } else {
            statusText.setText("等待投屏中...");
        }
    }
}
