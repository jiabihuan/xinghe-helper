package com.xinghe.helper.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public class SystemInfoUtil {

    private static String cachedGpuInfo = null;

    private static String getProp(String propName) {
        try {
            Process process = Runtime.getRuntime().exec("getprop " + propName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } catch (Exception e) {
        }
        return "";
    }

    private static String execShell(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            reader.close();
            process.waitFor();
            return result.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getDeviceBrand() {
        String prop = getProp("ro.product.brand");
        return prop.isEmpty() ? Build.BRAND : prop;
    }

    public static String getDeviceModel() {
        String prop = getProp("ro.product.model");
        return prop.isEmpty() ? Build.MODEL : prop;
    }

    public static String getDeviceManufacturer() {
        String prop = getProp("ro.product.manufacturer");
        return prop.isEmpty() ? Build.MANUFACTURER : prop;
    }

    public static String getProductName() {
        String prop = getProp("ro.product.name");
        return prop.isEmpty() ? Build.PRODUCT : prop;
    }

    public static String getDeviceName() {
        String prop = getProp("ro.product.device");
        return prop.isEmpty() ? Build.DEVICE : prop;
    }

    public static String getAndroidVersion() {
        String prop = getProp("ro.build.version.release");
        return prop.isEmpty() ? Build.VERSION.RELEASE : prop;
    }

    public static int getAndroidSdk() {
        String prop = getProp("ro.build.version.sdk");
        if (!prop.isEmpty()) {
            try { return Integer.parseInt(prop); } catch (NumberFormatException e) { }
        }
        return Build.VERSION.SDK_INT;
    }

    public static String getBuildId() {
        String prop = getProp("ro.build.id");
        return prop.isEmpty() ? Build.ID : prop;
    }

    public static String getBuildNumber() {
        String prop = getProp("ro.build.display.id");
        return prop.isEmpty() ? Build.DISPLAY : prop;
    }

    public static String getSerialNumber() {
        try {
            String prop = getProp("ro.serialno");
            if (!prop.isEmpty()) return prop;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Build.getSerial();
            }
            return Build.SERIAL;
        } catch (SecurityException e) {
            return "权限受限";
        }
    }

    public static String getSecurityPatch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String prop = getProp("ro.build.version.security_patch");
            return prop.isEmpty() ? Build.VERSION.SECURITY_PATCH : prop;
        }
        return "未知";
    }

    public static String getScreenResolution(Context context) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);
                return metrics.widthPixels + " × " + metrics.heightPixels;
            }
        } catch (Exception e) {
        }
        return "未知";
    }

    public static String getScreenDensityDpi(Context context) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);
                int dpi = metrics.densityDpi;
                String category;
                if (dpi <= 120) category = "ldpi";
                else if (dpi <= 160) category = "mdpi";
                else if (dpi <= 240) category = "hdpi";
                else if (dpi <= 320) category = "xhdpi";
                else if (dpi <= 480) category = "xxhdpi";
                else if (dpi <= 640) category = "xxxhdpi";
                else category = "tvdpi";
                return dpi + "dpi (" + category + ")";
            }
        } catch (Exception e) {
        }
        return "未知";
    }

    public static String getCpuInfo() {
        String socManu = getProp("ro.soc.manufacturer");
        String socModel = getProp("ro.soc.model");
        if (!socManu.isEmpty() || !socModel.isEmpty()) {
            return (socManu.isEmpty() ? "" : socManu + " ") + socModel;
        }
        String chipname = getProp("ro.chipname");
        if (!chipname.isEmpty()) return chipname;
        String hardware = getProp("ro.hardware");
        if (!hardware.isEmpty() && !hardware.equals("qcom")) return hardware;
        try {
            String cpuInfo = execShell("cat /proc/cpuinfo | head -30");
            if (!cpuInfo.isEmpty()) {
                StringBuilder result = new StringBuilder();
                for (String line : cpuInfo.split("\n")) {
                    if (line.contains("Hardware") || line.contains("Processor") ||
                        line.contains("model name") || line.contains("cpu model")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length >= 2) {
                            String val = parts[1].trim();
                            if (!result.toString().contains(val)) {
                                result.append(val).append("  ");
                            }
                        }
                    }
                }
                String r = result.toString().trim();
                if (!r.isEmpty()) return r;
            }
        } catch (Exception e) {
        }
        return Build.HARDWARE;
    }

    public static String getCpuAbi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_ABIS[0];
        }
        return Build.CPU_ABI;
    }

    public static String getCpuCores() {
        return String.valueOf(Runtime.getRuntime().availableProcessors());
    }

    public static String getCpuMaxFreq() {
        String[] paths = {
            "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu4/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
        };
        long maxKhz = 0;
        for (String path : paths) {
            String f = execShell("cat " + path);
            if (!f.isEmpty()) {
                try {
                    long khz = Long.parseLong(f.trim());
                    if (khz > maxKhz) maxKhz = khz;
                } catch (Exception e) {
                }
            }
        }
        if (maxKhz > 0) {
            if (maxKhz >= 1000000) {
                return String.format(Locale.getDefault(), "%.2f GHz", maxKhz / 1000000.0);
            } else {
                return String.format(Locale.getDefault(), "%d MHz", maxKhz / 1000);
            }
        }
        return "未知";
    }

    public static String getGpuInfo() {
        if (cachedGpuInfo != null) return cachedGpuInfo;
        String result = "";
        String prop = getProp("ro.hardware.egl");
        if (!prop.isEmpty()) result = prop;
        if (result.isEmpty()) {
            String vendor = getProp("ro.gpu.vendor");
            String renderer = getProp("ro.gpu.renderer");
            if (!vendor.isEmpty() || !renderer.isEmpty()) {
                result = (vendor.isEmpty() ? "" : vendor + " ") + renderer;
            }
        }
        if (result.isEmpty()) {
            String gpu = execShell("cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null");
            if (!gpu.isEmpty()) result = gpu;
        }
        if (result.isEmpty()) {
            String gpu = execShell("cat /proc/mali 2>/dev/null | head -10");
            if (!gpu.isEmpty()) {
                for (String line : gpu.split("\n")) {
                    if (line.contains("GPU") || line.contains("model") || line.contains("version")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length >= 2) {
                            result = parts[1].trim();
                            break;
                        }
                    }
                }
            }
        }
        if (result.isEmpty()) {
            String gl = execShell("dumpsys SurfaceFlinger 2>/dev/null | grep -i 'gles\\|gpu\\|opengl' | head -5");
            if (!gl.isEmpty()) {
                for (String line : gl.split("\n")) {
                    if (line.toLowerCase().contains("renderer") || line.toLowerCase().contains("gpu")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length >= 2) {
                            result = parts[1].trim();
                            break;
                        }
                    }
                }
            }
        }
        if (result.isEmpty()) {
            String hardware = getProp("ro.hardware");
            if (hardware.contains("mt") || hardware.contains("MT")) {
                result = "Mali GPU (MTK)";
            } else if (hardware.contains("qcom") || hardware.contains("msm")) {
                result = "Adreno GPU (高通)";
            } else if (hardware.contains("exynos")) {
                result = "Mali GPU (三星)";
            } else if (hardware.contains("kirin") || hardware.contains("hi")) {
                result = "Mali GPU (海思)";
            } else {
                result = "未知GPU";
            }
        }
        cachedGpuInfo = result;
        return result;
    }

    public static String getTotalMemory(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long totalMb = mi.totalMem / (1024 * 1024);
                if (totalMb >= 1024) {
                    return String.format(Locale.getDefault(), "%.1f GB", totalMb / 1024.0);
                } else {
                    return totalMb + " MB";
                }
            }
        } catch (Exception e) {
        }
        String mem = execShell("cat /proc/meminfo | grep MemTotal");
        if (!mem.isEmpty()) {
            try {
                String[] parts = mem.split("\\s+");
                if (parts.length >= 2) {
                    long kb = Long.parseLong(parts[1]);
                    long mb = kb / 1024;
                    if (mb >= 1024) {
                        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0);
                    }
                    return mb + " MB";
                }
            } catch (Exception e) {
            }
        }
        return "未知";
    }

    public static String getAvailableMemory(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long availMb = mi.availMem / (1024 * 1024);
                if (availMb >= 1024) {
                    return String.format(Locale.getDefault(), "%.1f GB", availMb / 1024.0);
                } else {
                    return availMb + " MB";
                }
            }
        } catch (Exception e) {
        }
        return "未知";
    }

    public static String getStorageInfo() {
        String total = execShell("df /data | tail -1 | awk '{print $2}'");
        String used = execShell("df /data | tail -1 | awk '{print $3}'");
        if (!total.isEmpty() && !used.isEmpty()) {
            try {
                long totalKb = Long.parseLong(total.trim());
                long usedKb = Long.parseLong(used.trim());
                long totalGb = totalKb / (1024 * 1024);
                long usedGb = usedKb / (1024 * 1024);
                if (totalGb >= 1) {
                    return String.format(Locale.getDefault(), "%.1f / %.1f GB", usedGb * 1.0, totalGb * 1.0);
                } else {
                    return String.format(Locale.getDefault(), "%d / %d MB", usedKb / 1024, totalKb / 1024);
                }
            } catch (NumberFormatException e) {
            }
        }
        return "未知";
    }

    public static String getKernelVersion() {
        String prop = getProp("ro.kernel.version");
        if (!prop.isEmpty()) return prop;
        String uname = execShell("uname -r");
        if (!uname.isEmpty()) return uname;
        String procVer = execShell("cat /proc/version");
        if (!procVer.isEmpty() && procVer.length() > 80) {
            return procVer.substring(0, 80) + "...";
        }
        return procVer.isEmpty() ? "未知" : procVer;
    }

    public static String getTimeZone() {
        return java.util.TimeZone.getDefault().getID();
    }

    public static String getLocale() {
        return Locale.getDefault().toString();
    }

    public static String getFingerprint() {
        String prop = getProp("ro.build.fingerprint");
        return prop.isEmpty() ? Build.FINGERPRINT : prop;
    }

    public static String getBoard() {
        String prop = getProp("ro.product.board");
        return prop.isEmpty() ? Build.BOARD : prop;
    }

    public static String getHardware() {
        String prop = getProp("ro.hardware");
        return prop.isEmpty() ? Build.HARDWARE : prop;
    }

    public static String getBootloader() {
        String prop = getProp("ro.bootloader");
        return prop.isEmpty() ? Build.BOOTLOADER : prop;
    }

    public static String getIpAddress() {
        String ip = execShell("ip addr show wlan0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1");
        if (!ip.isEmpty()) return ip;
        ip = execShell("ifconfig wlan0 | grep 'inet addr' | awk -F: '{print $2}' | awk '{print $1}'");
        return ip.isEmpty() ? "未连接" : ip;
    }

    public static String getMacAddress() {
        String mac = getProp("wifi.interface");
        if (!mac.isEmpty()) {
            String addr = execShell("cat /sys/class/net/" + mac + "/address");
            if (!addr.isEmpty()) return addr;
        }
        String addr = execShell("cat /sys/class/net/wlan0/address");
        return addr.isEmpty() ? "未知" : addr;
    }

    public static String getUptime() {
        try {
            long uptimeSec = android.os.SystemClock.elapsedRealtime() / 1000;
            long days = uptimeSec / 86400;
            long hours = (uptimeSec % 86400) / 3600;
            long mins = (uptimeSec % 3600) / 60;
            if (days > 0) {
                return days + "天 " + hours + "小时 " + mins + "分钟";
            } else if (hours > 0) {
                return hours + "小时 " + mins + "分钟";
            } else {
                return mins + "分钟";
            }
        } catch (Exception e) {
            return "未知";
        }
    }

    public static String getRootStatus() {
        String su = execShell("which su");
        if (su != null && !su.isEmpty()) return "已ROOT";
        String test = execShell("su -c 'id'");
        if (test != null && test.contains("uid=0")) return "已ROOT";
        return "未ROOT";
    }

    public static String getBootloaderStatus() {
        String prop = getProp("ro.boot.verifiedbootstate");
        if (!prop.isEmpty()) {
            if ("green".equalsIgnoreCase(prop)) return "已锁定";
            if ("orange".equalsIgnoreCase(prop)) return "已解锁";
            return prop;
        }
        prop = getProp("ro.boot.flash.locked");
        if (!prop.isEmpty()) {
            return "1".equals(prop) ? "已锁定" : "已解锁";
        }
        return "未知";
    }

    public static String getSystemVersion() {
        String prop = getProp("ro.build.version.incremental");
        if (!prop.isEmpty()) return prop;
        prop = getProp("ro.system.build.version.incremental");
        if (!prop.isEmpty()) return prop;
        return "未知";
    }

    public static String getBluetoothMac() {
        String prop = getProp("ro.boot.btmacaddr");
        if (!prop.isEmpty()) return prop;
        prop = getProp("persist.service.bdroid.bdaddr");
        if (!prop.isEmpty()) return prop;
        return "未知";
    }
}
