package com.xinghe.helper.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.xinghe.helper.R;
import com.xinghe.helper.cast.CastService;
import com.xinghe.helper.cast.CastState;

public class CastEntryActivity extends AppCompatActivity {

    private TextView statusText;
    private TextView titleText;
    private TextView deviceNameHighlight;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean playerActivityRunning = false;

    private final CastState.StateListener castListener = new CastState.StateListener() {
        @Override
        public void onPlay(String url, String mimeType) {
            if (isFinishing() || playerActivityRunning) return;
            handler.post(() -> {
                if (isFinishing() || playerActivityRunning) return;
                playerActivityRunning = true;
                Intent intent = new Intent(CastEntryActivity.this, CastPlayerActivity.class);
                startActivity(intent);
            });
        }

        @Override
        public void onPause() {
        }

        @Override
        public void onStop() {
            handler.post(CastEntryActivity.this::updateStatus);
        }

        @Override
        public void onSeek(long position) {}

        @Override
        public void onVolume(int volume) {}

        @Override
        public void onMute(boolean mute) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cast_entry);

        statusText = findViewById(R.id.statusText);
        titleText = findViewById(R.id.titleText);
        deviceNameHighlight = findViewById(R.id.deviceNameHighlight);

        updateTitle();
        handler.postDelayed(this::startCastService, 300);
        updateStatus();

        CastState.getInstance().addListener(castListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerActivityRunning = false;
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        CastState.getInstance().removeListener(castListener);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateTitle() {
        String deviceName = android.os.Build.MODEL;
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "星河投屏";
        }
        titleText.setText("星河投屏 - " + deviceName);
        deviceNameHighlight.setText("星河投屏 - " + deviceName);
    }

    private void startCastService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        try {
            Intent intent = new Intent(this, CastService.class);
            ContextCompat.startForegroundService(this, intent);
        } catch (Exception e) {
            Toast.makeText(this, "投屏服务启动失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus() {
        CastState state = CastState.getInstance();
        if (state.getCurrentUrl() != null && !state.getCurrentUrl().isEmpty()) {
            statusText.setText("投屏中...");
        } else {
            statusText.setText("等待投屏中...");
        }
    }
}