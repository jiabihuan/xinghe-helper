package com.xinghe.helper.activity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.R;
import com.xinghe.helper.util.PhpLocalServer;

public class PhpServerActivity extends AppCompatActivity {

    private TextView urlText;
    private TextView statusText;
    private TextView rootText;
    private Button restartButton;
    private PhpLocalServer phpServer;
    private volatile boolean destroyed = false;
    private volatile boolean starting = false;

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
        destroyed = true;
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
        if (starting) {
            Toast.makeText(this, "PHP服务正在启动，请稍等", Toast.LENGTH_SHORT).show();
            return;
        }
        if (phpServer != null) {
            phpServer.stop();
            phpServer = null;
        }
        startServer();
    }

    private void startServer() {
        starting = true;
        restartButton.setEnabled(false);
        urlText.setText("正在准备...");
        statusText.setText("正在启动服务...");

        new Thread(() -> {
            try {
                PhpLocalServer server = new PhpLocalServer(PhpServerActivity.this);
                String rootPath = server.getDocumentRoot().getAbsolutePath();
                runOnUiThreadSafe(() -> {
                    rootText.setText("PHP 文件目录：" + rootPath);
                    statusText.setText("正在释放内置 PHP 环境，首次启动可能需要 1-3 分钟，请不要退出...");
                });

                server.startServer();
                phpServer = server;
                String url = server.getServerUrl();
                String status = server.getInterpreterStatus();
                runOnUiThreadSafe(() -> {
                    urlText.setText(url);
                    statusText.setText("服务运行中\n" + status);
                    Toast.makeText(this, "PHP服务已启动", Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable e) {
                String message = buildErrorMessage(e);
                runOnUiThreadSafe(() -> {
                    urlText.setText("启动失败");
                    statusText.setText(message);
                    Toast.makeText(this, "PHP服务启动失败", Toast.LENGTH_SHORT).show();
                });
            } finally {
                starting = false;
                runOnUiThreadSafe(() -> restartButton.setEnabled(true));
            }
        }, "xinghe-php-starter").start();
    }

    private void runOnUiThreadSafe(Runnable runnable) {
        if (destroyed) return;
        runOnUiThread(() -> {
            if (!destroyed) runnable.run();
        });
    }

    private String buildErrorMessage(Throwable e) {
        StringBuilder builder = new StringBuilder();
        builder.append("PHP服务启动失败：").append(e.getClass().getSimpleName());
        if (e.getMessage() != null) {
            builder.append("\n").append(e.getMessage());
        }
        builder.append("\n\n请把这段错误截图发给我，我可以继续定位。");

        StackTraceElement[] stack = e.getStackTrace();
        if (stack != null && stack.length > 0) {
            builder.append("\n\n").append(stack[0].toString());
            if (stack.length > 1) {
                builder.append("\n").append(stack[1].toString());
            }
        }
        return builder.toString();
    }
}
