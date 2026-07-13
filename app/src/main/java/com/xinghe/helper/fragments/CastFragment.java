package com.xinghe.helper.fragments;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
    private ImageView castIcon;
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
            // 播放器Activity处理
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
        castIcon = view.findViewById(R.id.castIcon);

        handler.postDelayed(this::startCastService, 300);

        updateStatus();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        playerActivityRunning = false;
        CastState.getInstance().addListener(castListener);
        updateStatus();
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

    private void updateStatus() {
        if (statusText == null) return;
        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty()) {
            statusText.setText("投屏中");
            statusText.setTextColor(0xFF4CAF50);
            castIcon.setAlpha(1.0f);
        } else {
            statusText.setText("等待投屏");
            statusText.setTextColor(0xFFFFFFFF);
            castIcon.setAlpha(0.6f);
        }
    }
}
