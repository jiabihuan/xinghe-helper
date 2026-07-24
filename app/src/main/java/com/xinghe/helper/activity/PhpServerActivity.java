package com.xinghe.helper.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.R;
import com.xinghe.helper.util.PhpService;

import java.io.File;

public class PhpServerActivity extends AppCompatActivity {

    private TextView urlText;
    private TextView statusText;
    private TextView rootText;
    private TextView toggleBtn;
    private TextView restartBtn;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private PhpService.OnPhpServiceListener serviceListener = new PhpService.OnPhpServiceListener() {
        @Override
        public void onStarted(String url, String status) {
            uiHandler.post(() -> {
                urlText.setText(url);
                statusText.setText("服务运行中\n" + status);
                toggleBtn.setText("停止服务");
                toggleBtn.setClickable(true);
                restartBtn.setVisibility(View.VISIBLE);
                updateRootDir();
            });
        }

        @Override
        public void onStopped() {
            uiHandler.post(() -> onServerStopped());
        }

        @Override
        public void onError(String error) {
            uiHandler.post(() -> {
                urlText.setText("启动失败");
                statusText.setText(error);
                toggleBtn.setText("启动服务");
                toggleBtn.setClickable(true);
                restartBtn.setVisibility(View.GONE);
                Toast.makeText(PhpServerActivity.this, "PHP服务启动失败", Toast.LENGTH_SHORT).show();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_php_server);

        urlText = findViewById(R.id.phpUrlText);
        statusText = findViewById(R.id.phpStatusText);
        rootText = findViewById(R.id.phpRootText);
        toggleBtn = findViewById(R.id.phpToggleBtn);
        restartBtn = findViewById(R.id.phpRestartBtn);

        toggleBtn.setOnClickListener(v -> toggleService());
        restartBtn.setOnClickListener(v -> restartService());

        PhpService.setListener(serviceListener);
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PhpService.setListener(serviceListener);
        refreshUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PhpService.setListener(null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleService() {
        if (PhpService.isRunning()) {
            stopPhpService();
        } else {
            startPhpService();
        }
    }

    private void startPhpService() {
        toggleBtn.setClickable(false);
        toggleBtn.setText("正在启动...");
        statusText.setText("正在启动服务...");

        Intent intent = new Intent(this, PhpService.class);
        intent.setAction(PhpService.ACTION_START);
        startForegroundService(intent);
    }

    private void stopPhpService() {
        Intent intent = new Intent(this, PhpService.class);
        intent.setAction(PhpService.ACTION_STOP);
        startService(intent);
    }

    private void restartService() {
        stopPhpService();
        toggleBtn.postDelayed(() -> startPhpService(), 800);
    }

    private void onServerStopped() {
        urlText.setText("未启动");
        statusText.setText("点击下方按钮启动服务");
        toggleBtn.setText("启动服务");
        toggleBtn.setClickable(true);
        restartBtn.setVisibility(View.GONE);
        rootText.setText("PHP 文件目录：未启动");
    }

    private void updateRootDir() {
        File dir = PhpService.getDocumentRoot();
        rootText.setText("PHP 文件目录：" + (dir != null ? dir.getAbsolutePath() : "未知"));
    }

    private void refreshUi() {
        if (PhpService.isRunning()) {
            String url = PhpService.getServerUrl();
            String status = PhpService.getInterpreterStatus();
            urlText.setText(url.isEmpty() ? "正在启动..." : url);
            statusText.setText(url.isEmpty() ? "正在启动服务..." : "服务运行中\n" + status);
            toggleBtn.setText("停止服务");
            restartBtn.setVisibility(View.VISIBLE);
            updateRootDir();
        } else {
            onServerStopped();
        }
    }
}
