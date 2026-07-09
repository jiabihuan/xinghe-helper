package com.xinghe.helper.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.xinghe.helper.R;
import com.xinghe.helper.util.ApkInstallUtil;
import com.xinghe.helper.util.QRCodeUtil;
import com.xinghe.helper.util.RemotePushServer;
import com.xinghe.helper.util.ToastUtil;

import java.io.File;

public class RemoteActivity extends AppCompatActivity implements RemotePushServer.OnPushListener {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private TextView tvPushUrl;
    private TextView tvPushStatus;
    private ImageView ivQrCode;
    private RemotePushServer pushServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);

        tvPushUrl = findViewById(R.id.tvPushUrl);
        tvPushStatus = findViewById(R.id.tvPushStatus);
        ivQrCode = findViewById(R.id.ivQrCode);

        pushServer = new RemotePushServer(this);
        pushServer.setListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            } else {
                startServer();
            }
        } else {
            startServer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startServer();
        }
    }

    private void startServer() {
        if (pushServer != null) {
            pushServer.start();
        }
    }

    @Override
    protected void onDestroy() {
        if (pushServer != null) {
            pushServer.stop();
            pushServer = null;
        }
        if (ivQrCode != null) {
            BitmapDrawable drawable = (BitmapDrawable) ivQrCode.getDrawable();
            if (drawable != null && drawable.getBitmap() != null) {
                drawable.getBitmap().recycle();
            }
        }
        super.onDestroy();
    }

    @Override
    public void onServerStarted(final String url) {
        if (tvPushUrl != null) {
            tvPushUrl.setText(url);
        }
        if (tvPushStatus != null) {
            tvPushStatus.setText("等待推送...");
        }
        if (ivQrCode != null) {
            Bitmap qrBitmap = QRCodeUtil.generateQRCodeWithBackground(url, 400, 400);
            if (qrBitmap != null) {
                ivQrCode.setImageBitmap(qrBitmap);
                ivQrCode.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onServerStopped() {
        if (tvPushStatus != null) {
            tvPushStatus.setText("服务已停止");
        }
        if (ivQrCode != null) {
            ivQrCode.setVisibility(View.INVISIBLE);
            BitmapDrawable drawable = (BitmapDrawable) ivQrCode.getDrawable();
            if (drawable != null && drawable.getBitmap() != null) {
                drawable.getBitmap().recycle();
            }
            ivQrCode.setImageBitmap(null);
        }
    }

    @Override
    public void onPushStarted() {
        if (tvPushStatus != null) {
            tvPushStatus.setText("正在接收文件...");
        }
    }

    @Override
    public void onPushCompleted(final File apkFile) {
        if (tvPushStatus != null) {
            tvPushStatus.setText("接收完成，准备安装");
        }
        ToastUtil.showShort(this, "远程推送成功，开始安装");
        ApkInstallUtil.installApk(this, apkFile);
    }

    @Override
    public void onPushFailed(final String error) {
        if (tvPushStatus != null) {
            tvPushStatus.setText("推送失败: " + error);
        }
    }
}