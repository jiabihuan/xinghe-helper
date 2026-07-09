package com.xinghe.helper.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;
import com.xinghe.helper.util.ApkInstallUtil;
import com.xinghe.helper.util.QRCodeUtil;
import com.xinghe.helper.util.RemotePushServer;
import com.xinghe.helper.util.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemoteFragment extends Fragment implements RemotePushServer.OnPushListener {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private TextView tvPushUrl;
    private ImageView ivQrCode;
    private RemotePushServer pushServer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_remote, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvPushUrl = view.findViewById(R.id.tvPushUrl);
        ivQrCode = view.findViewById(R.id.ivQrCode);

        if (getContext() == null) return;

        pushServer = new RemotePushServer(getContext());
        pushServer.setListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                ToastUtil.showShort(getContext(), "请开启\"所有文件访问权限\"以保存文件到根目录");
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivityForResult(intent, PERMISSION_REQUEST_CODE);
                } catch (Exception e) {
                    intent.setData(null);
                    startActivityForResult(intent, PERMISSION_REQUEST_CODE);
                }
                startServer();
                return;
            } else {
                startServer();
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissions = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
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
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            startServer();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 用户从设置返回，重新尝试获取目录（权限可能已开启）
            startServer();
        }
    }

    private void startServer() {
        if (pushServer != null) {
            pushServer.startServer();
        }
    }

    @Override
    public void onDestroyView() {
        if (pushServer != null) {
            pushServer.stopServer();
            pushServer = null;
        }
        if (ivQrCode != null) {
            BitmapDrawable drawable = (BitmapDrawable) ivQrCode.getDrawable();
            if (drawable != null && drawable.getBitmap() != null) {
                drawable.getBitmap().recycle();
            }
        }
        super.onDestroyView();
    }

    @Override
    public void onServerStarted(final String url) {
        if (tvPushUrl != null) {
            tvPushUrl.setText(url);
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
    }

    @Override
    public void onPushCompleted(final File file, final boolean isApk) {
        if (getContext() != null) {
            ToastUtil.showShort(getContext(), "文件已保存到星河助手文件夹");
            checkAndInstallApk(file);
        }
    }

    private void checkAndInstallApk(File file) {
        if (file == null || !file.exists()) return;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".apk")) {
            if (getContext() != null) {
                ApkInstallUtil.installApk(getContext(), file);
            }
        }
    }

    @Override
    public void onPushFailed(final String error) {
        if (getContext() != null) {
            ToastUtil.showShort(getContext(), "推送失败: " + error);
        }
    }
}
