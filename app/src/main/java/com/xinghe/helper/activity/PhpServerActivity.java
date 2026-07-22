package com.xinghe.helper.activity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.R;
import com.xinghe.helper.util.PhpLocalServer;

import java.io.IOException;

public class PhpServerActivity extends AppCompatActivity {

    private TextView urlText;
    private TextView statusText;
    private TextView rootText;
    private Button restartButton;
    private PhpLocalServer phpServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_php_server);

        urlText = findViewById(R.id.phpUrlText);
        statusText = findViewById(R.id.phpStatusText);
        rootText = findViewById(R.id.phpRootText);
        restartButton = findViewById(R.id.phpRestartButton);

        restartButton.setOnClickListener(v -> restartServer());
        startServer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (phpServer != null) {
            phpServer.stop();
            phpServer = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void restartServer() {
        if (phpServer != null) {
            phpServer.stop();
            phpServer = null;
        }
        startServer();
    }

    private void startServer() {
        phpServer = new PhpLocalServer(this);
        rootText.setText("PHP 文件目录：" + phpServer.getDocumentRoot().getAbsolutePath());
        statusText.setText("正在启动服务...");
        try {
            phpServer.startServer();
            urlText.setText(phpServer.getServerUrl());
            statusText.setText("服务运行中\n" + phpServer.getInterpreterStatus());
            Toast.makeText(this, "PHP服务已启动", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            urlText.setText("启动失败");
            statusText.setText("服务启动失败：" + e.getMessage());
            Toast.makeText(this, "PHP服务启动失败", Toast.LENGTH_SHORT).show();
        }
    }
}
