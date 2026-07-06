package com.xinghe.helper.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import java.io.File;

public class ApkInstallUtil {
    public static void installApk(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists()) {
            return;
        }

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", apkFile);
            installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(apkFile);
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(context, "安装失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
