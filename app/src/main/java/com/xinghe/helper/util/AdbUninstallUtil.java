package com.xinghe.helper.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AdbUninstallUtil {

    public interface UninstallCallback {
        void onResult(String packageName, boolean success, String message);
    }

    public static void uninstall(String packageName, UninstallCallback callback) {
        boolean success = false;
        String message = "";

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pm", "uninstall", packageName});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            process.waitFor();
            String result = output.toString();
            if (result.contains("Success")) {
                success = true;
                message = "卸载成功";
            } else {
                message = result.isEmpty() ? "卸载失败" : result;
            }
        } catch (Exception e) {
            message = e.getMessage();
        }

        if (!success) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm uninstall " + packageName});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
                process.waitFor();
                String result = output.toString();
                if (result.contains("Success")) {
                    success = true;
                    message = "卸载成功";
                } else {
                    if (message.isEmpty()) message = result.isEmpty() ? "卸载失败" : result;
                }
            } catch (Exception e) {
                if (message.isEmpty()) message = e.getMessage();
            }
        }

        if (callback != null) {
            callback.onResult(packageName, success, message);
        }
    }
}
