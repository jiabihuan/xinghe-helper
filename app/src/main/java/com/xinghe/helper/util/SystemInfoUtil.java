package com.xinghe.helper.util;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

public class SystemInfoUtil {

    public static String getDeviceBrand() {
        return Build.BRAND;
    }

    public static String getDeviceModel() {
        return Build.MODEL;
    }

    public static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE;
    }

    public static int getAndroidSdk() {
        return Build.VERSION.SDK_INT;
    }

    public static String getBuildId() {
        return Build.ID;
    }

    public static String getBuildNumber() {
        return Build.DISPLAY;
    }

    public static String getSerialNumber() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Build.getSerial();
        } else {
            return Build.SERIAL;
        }
    }

    public static String getProductName() {
        return Build.PRODUCT;
    }

    public static String getBoard() {
        return Build.BOARD;
    }

    public static String getHardware() {
        return Build.HARDWARE;
    }

    public static String getBootloader() {
        return Build.BOOTLOADER;
    }

    public static String getRadioVersion() {
        return Build.getRadioVersion();
    }

    public static String getScreenResolution(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            return metrics.widthPixels + " × " + metrics.heightPixels;
        }
        return "未知";
    }

    public static String getScreenDensity(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            return String.format(Locale.getDefault(), "%.2f", metrics.density);
        }
        return "未知";
    }

    public static String getScreenDensityDpi(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            return String.valueOf(metrics.densityDpi);
        }
        return "未知";
    }

    public static String getCpuInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            StringBuilder cpuInfo = new StringBuilder();
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 10) {
                if (line.contains("Processor") || line.contains("model name") || 
                    line.contains("Hardware") || line.contains("CPU architecture")) {
                    cpuInfo.append(line.trim()).append("\n");
                    count++;
                }
            }
            reader.close();
            return cpuInfo.toString().trim();
        } catch (IOException e) {
            return "获取失败";
        }
    }

    public static String getCpuAbi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            StringBuilder abi = new StringBuilder();
            for (String a : Build.SUPPORTED_ABIS) {
                abi.append(a).append(" ");
            }
            return abi.toString().trim();
        } else {
            return Build.CPU_ABI + " " + Build.CPU_ABI2;
        }
    }

    public static String getTotalMemory() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line = reader.readLine();
            reader.close();
            if (line != null && line.startsWith("MemTotal")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    long kb = Long.parseLong(parts[1]);
                    if (kb >= 1024 * 1024) {
                        return String.format(Locale.getDefault(), "%.2f GB", kb / (1024.0 * 1024));
                    } else {
                        return String.format(Locale.getDefault(), "%.0f MB", kb / 1024.0);
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return "未知";
    }

    public static String getAvailableMemory() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemAvailable")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        long kb = Long.parseLong(parts[1]);
                        if (kb >= 1024 * 1024) {
                            return String.format(Locale.getDefault(), "%.2f GB", kb / (1024.0 * 1024));
                        } else {
                            return String.format(Locale.getDefault(), "%.0f MB", kb / 1024.0);
                        }
                    }
                    break;
                }
            }
            reader.close();
        } catch (IOException e) {
            // ignore
        }
        return "未知";
    }

    public static String getKernelVersion() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/version"));
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                return line;
            }
        } catch (IOException e) {
            // ignore
        }
        return "未知";
    }

    public static String getOsType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        } else {
            return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        }
    }

    public static String getTimeZone() {
        return java.util.TimeZone.getDefault().getDisplayName();
    }

    public static String getLocale() {
        return Locale.getDefault().toString();
    }

    public static String getFingerprint() {
        return Build.FINGERPRINT;
    }

    public static String getSecurityPatch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Build.VERSION.SECURITY_PATCH;
        }
        return "未知";
    }
}
