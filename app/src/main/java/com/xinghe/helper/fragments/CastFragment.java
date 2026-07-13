package com.xinghe.helper.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;
import com.xinghe.helper.activity.CastPlayerActivity;
import com.xinghe.helper.cast.CastService;
import com.xinghe.helper.cast.CastState;

public class CastFragment extends Fragment {

    private TextView statusText;
    private TextView ipText;
    private TextView tipText;
    private TextView openPlayerBtn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cast, container, false);

        statusText = view.findViewById(R.id.statusText);
        ipText = view.findViewById(R.id.ipText);
        tipText = view.findViewById(R.id.tipText);
        openPlayerBtn = view.findViewById(R.id.openPlayerBtn);

        startCastService();
        updateStatus();

        String ip = getLocalIp();
        if (ip != null) {
            ipText.setText("设备IP：" + ip);
        } else {
            ipText.setText("请连接WiFi网络");
        }

        openPlayerBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CastPlayerActivity.class);
            startActivity(intent);
        });

        openPlayerBtn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                openPlayerBtn.setBackgroundResource(R.drawable.bg_dialog_button_focus);
            } else {
                openPlayerBtn.setBackgroundResource(R.drawable.bg_dialog_button);
            }
        });

        return view;
    }

    private void startCastService() {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), CastService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getActivity().startForegroundService(intent);
        } else {
            getActivity().startService(intent);
        }
    }

    private void updateStatus() {
        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty()) {
            statusText.setText("投屏中");
            statusText.setTextColor(0xFF4CAF50);
            tipText.setText("正在播放投屏内容");
        } else {
            statusText.setText("等待投屏");
            statusText.setTextColor(0xFFFFFFFF);
            tipText.setText("请在手机视频APP中点击投屏按钮，选择「星河助手投屏」");
        }
    }

    private String getLocalIp() {
        if (getActivity() == null) return null;
        try {
            WifiManager wm = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                int ip = info.getIpAddress();
                if (ip != 0) {
                    return ((ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." +
                            ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
    }
}
