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
            Process process = Runtime.getRuntime().exec(new String[]{"pm", "list", "packages"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
                if (output.length() > 50) break;
            }
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line);
            }
            int exitCode = process.waitFor();
            reader.close();
            errorReader.close();
            if (exitCode == 0 && output.length() > 0) {
                return true;
            }
            try {
                Process suProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm list packages"});
                BufferedReader suReader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                StringBuilder suOutput = new StringBuilder();
                while ((line = suReader.readLine()) != null) {
                    suOutput.append(line);
                    if (suOutput.length() > 50) break;
                }
                int suExitCode = suProcess.waitFor();
                suReader.close();
                return suExitCode == 0 && suOutput.length() > 0;
            } catch (Exception e) {
                return false;
            }
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