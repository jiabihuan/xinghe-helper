package com.xinghe.helper.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class AdbInstallUtil {

    public interface InstallCallback {
        void onResult(boolean success, String message);
    }

    public static boolean isAdbAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pm", "list", "packages", "-f"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
                if (output.length() > 100) break;
            }
            process.waitFor();
            return output.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void install(Context context, File apkFile, InstallCallback callback) {
        boolean success = false;
        String message = "";

        try {
            String apkPath = apkFile.getAbsolutePath();
            Process process = Runtime.getRuntime().exec(new String[]{"pm", "install", "-r", apkPath});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line);
            }
            process.waitFor();
            String result = output.toString();
            if (result.contains("Success")) {
                success = true;
                message = "安装成功";
            } else {
                message = result.isEmpty() ? errorOutput.toString() : result;
                if (message.isEmpty()) message = "安装失败";
            }
        } catch (Exception e) {
            message = e.getMessage();
        }

        if (!success) {
            try {
                String apkPath = apkFile.getAbsolutePath();
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm install -r " + apkPath});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder output = new StringBuilder();
                StringBuilder errorOutput = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line);
                }
                process.waitFor();
                String result = output.toString();
                if (result.contains("Success")) {
                    success = true;
                    message = "安装成功";
                } else {
                    if (message.isEmpty()) {
                        message = result.isEmpty() ? errorOutput.toString() : result;
                        if (message.isEmpty()) message = "安装失败";
                    }
                }
            } catch (Exception e) {
                if (message.isEmpty()) message = e.getMessage();
            }
        }

        if (callback != null) {
            callback.onResult(success, message);
        }
    }
}