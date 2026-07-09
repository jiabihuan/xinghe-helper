package com.xinghe.helper.util;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 远程推送 HTTP 服务器（基于 NanoHTTPD）
 * 参考开源方案：
 * - matan-h/Transfer：使用 NanoHTTPD 在 Android 上搭建本地文件服务器
 * - WPSeven/Android-Http-File-Server：使用 NanoHTTPD 流式上传大文件
 * 改造点：
 * 1. 用成熟的 NanoHTTPD 替代手写 HTTP 解析，避免协议解析错误导致的"未知错误"。
 * 2. 使用 NanoHTTPD 内置的 multipart/form-data 解析，稳定可靠。
 * 3. 上传文件先落到缓存，再移动到根目录"星河助手"文件夹，避免写入失败损坏文件。
 * 4. APK 文件保存后自动拉起安装，其它文件仅保存。
 */
public class RemotePushServer extends NanoHTTPD {

    private static final int PORT = 8080;

    private static final String INDEX_HTML = "<!DOCTYPE html>" +
            "<html>" +
            "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<title>星河助手远程推送</title>" +
            "<style>" +
            "body{font-family:Arial,sans-serif;background:#0d1b3e;color:#fff;text-align:center;padding:40px 20px;margin:0;}" +
            ".container{max-width:480px;margin:0 auto;background:#0a2a5a;border-radius:16px;padding:30px;box-shadow:0 4px 20px rgba(0,0,0,0.3);}" +
            "h1{font-size:24px;margin-bottom:10px;}" +
            ".tip{color:#a0a0b0;font-size:14px;margin-bottom:30px;}" +
            "input[type=file]{display:none;}" +
            ".btn{display:block;width:100%;padding:14px 0;margin:12px 0;border-radius:24px;font-size:16px;cursor:pointer;border:none;}" +
            ".select{background:#4CAF50;color:#fff;}" +
            ".push{background:#2196F3;color:#fff;display:none;}" +
            ".progress{height:6px;background:#1a2d52;border-radius:3px;margin-top:20px;overflow:hidden;display:none;}" +
            ".bar{height:100%;background:#4CAF50;width:0%;transition:width 0.2s;}" +
            ".status{margin-top:15px;font-size:14px;color:#a0a0b0;}" +
            "</style></head>" +
            "<body>" +
            "<div class=\"container\">" +
            "<h1>星河助手远程推送</h1>" +
            "<p class=\"tip\">选择文件推送到电视（APK自动安装，其他文件保存到星河助手文件夹）</p>" +
            "<input type=\"file\" id=\"file\" onchange=\"onFileSelected()\">" +
            "<button class=\"btn select\" onclick=\"document.getElementById('file').click()\">选择文件</button>" +
            "<button class=\"btn push\" id=\"pushBtn\" onclick=\"upload()\">推送到电视</button>" +
            "<div class=\"progress\" id=\"progress\"><div class=\"bar\" id=\"bar\"></div></div>" +
            "<p class=\"status\" id=\"status\"></p>" +
            "</div>" +
            "<script>" +
            "var file=null;" +
            "function onFileSelected(){file=document.getElementById('file').files[0];if(file){document.querySelector('.select').textContent='已选择: '+file.name;document.getElementById('pushBtn').style.display='block';}}" +
            "function upload(){if(!file){alert('请先选择文件');return;}var xhr=new XMLHttpRequest();var progress=document.getElementById('progress');var bar=document.getElementById('bar');var status=document.getElementById('status');progress.style.display='block';status.textContent='上传中...';xhr.upload.onprogress=function(e){if(e.lengthComputable){bar.style.width=(e.loaded/e.total*100)+'%';}};xhr.onreadystatechange=function(){if(xhr.readyState===4){if(xhr.status===200){status.textContent='推送成功';bar.style.width='100%';}else{var msg=xhr.responseText||xhr.statusText||'未知错误';try{var data=JSON.parse(xhr.responseText);if(data.message)msg=data.message;}catch(e){}status.textContent='推送失败: '+msg;}}};xhr.open('POST','/upload');var formData=new FormData();formData.append('file',file);xhr.send(formData);}" +
            "</script></body></html>";

    private final Context context;
    private final Handler mainHandler;
    private OnPushListener listener;

    public interface OnPushListener {
        void onServerStarted(String url);
        void onServerStopped();
        void onPushStarted();
        void onPushCompleted(File file, boolean isApk);
        void onPushFailed(String error);
    }

    public RemotePushServer(Context context) {
        super(PORT);
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setListener(OnPushListener listener) {
        this.listener = listener;
    }

    public void startServer() {
        try {
            start();
            notifyServerStarted(getServerUrl());
        } catch (IOException e) {
            notifyServerError("启动失败: " + e.getMessage());
        }
    }

    public void stopServer() {
        stop();
        notifyServerStopped();
    }

    public String getServerUrl() {
        return "http://" + getLocalIpAddress() + ":" + PORT;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.GET.equals(method) && "/".equals(uri)) {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", INDEX_HTML);
        }

        if (Method.POST.equals(method) && "/upload".equals(uri)) {
            return handleUpload(session);
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
    }

    private Response handleUpload(IHTTPSession session) {
        notifyPushStarted();

        // 1. 先让 NanoHTTPD 把 multipart 数据解析到临时文件
        Map<String, String> files = new java.util.HashMap<>();
        try {
            session.parseBody(files);
        } catch (Exception e) {
            android.util.Log.e("RemotePushServer", "parseBody 失败: " + e.getMessage(), e);
            return jsonResponse(false, "解析上传数据失败: " + e.getMessage(), Response.Status.INTERNAL_ERROR);
        }

        if (files.isEmpty()) {
            return jsonResponse(false, "没有收到文件", Response.Status.BAD_REQUEST);
        }

        // 2. 取第一个上传的文件
        Map.Entry<String, String> entry = files.entrySet().iterator().next();
        String tempPath = entry.getValue();

        File tempFile = new File(tempPath);
        if (!tempFile.exists() || tempFile.length() == 0) {
            return jsonResponse(false, "上传文件为空", Response.Status.BAD_REQUEST);
        }

        // 3. 确定真实文件名
        String fileName = extractFileName(session, entry.getKey());
        if (fileName == null || fileName.isEmpty()) {
            fileName = "push_" + System.currentTimeMillis();
        }

        // 4. 移动到根目录"星河助手"文件夹
        File destDir = getXingheDir();
        if (destDir == null) {
            return jsonResponse(false, "无法获取存储目录，请检查存储权限", Response.Status.INTERNAL_ERROR);
        }

        File destFile = new File(destDir, fileName);
        // 如果同名，追加时间戳重命名
        if (destFile.exists()) {
            int dot = fileName.lastIndexOf('.');
            String name = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : "";
            destFile = new File(destDir, name + "_" + System.currentTimeMillis() + ext);
        }

        try {
            copyFile(tempFile, destFile);
        } catch (IOException e) {
            android.util.Log.e("RemotePushServer", "保存文件失败: " + e.getMessage(), e);
            return jsonResponse(false, "保存文件失败: " + e.getMessage(), Response.Status.INTERNAL_ERROR);
        } finally {
            // 清理 NanoHTTPD 生成的临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }

        if (!destFile.exists() || destFile.length() == 0) {
            return jsonResponse(false, "保存后文件为空", Response.Status.INTERNAL_ERROR);
        }

        boolean isApk = destFile.getName().toLowerCase(Locale.US).endsWith(".apk");
        notifyPushCompleted(destFile, isApk);
        return jsonResponse(true, "上传成功", Response.Status.OK);
    }

    private String extractFileName(IHTTPSession session, String key) {
        String contentDisposition = getHeaderIgnoreCase(session.getHeaders(), "content-disposition");
        if (contentDisposition != null) {
            int idx = contentDisposition.toLowerCase(Locale.US).indexOf("filename=\"");
            if (idx >= 0) {
                int start = idx + 10;
                int end = contentDisposition.indexOf("\"", start);
                if (end > start) {
                    return sanitizeFileName(contentDisposition.substring(start, end));
                }
            }
            // 兼容 filename*=UTF-8''xxx 这种编码
            int starIdx = contentDisposition.toLowerCase(Locale.US).indexOf("filename*=");
            if (starIdx >= 0) {
                String value = contentDisposition.substring(starIdx + 10).trim();
                int encodingEnd = value.indexOf("'");
                if (encodingEnd > 0) {
                    int nameStart = value.indexOf("'", encodingEnd + 1);
                    if (nameStart > 0) {
                        value = value.substring(nameStart + 1);
                    }
                }
                try {
                    value = java.net.URLDecoder.decode(value, "UTF-8");
                } catch (Exception ignored) {
                }
                return sanitizeFileName(value);
            }
        }

        // NanoHTTPD 的 getParms 可能包含原始文件名（部分版本）
        String rawName = session.getParms().get(key);
        if (rawName != null && !rawName.isEmpty() && !rawName.startsWith("NanoHTTPD-")) {
            return sanitizeFileName(rawName);
        }

        return null;
    }

    private String getHeaderIgnoreCase(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private String sanitizeFileName(String name) {
        if (name == null) return null;
        // 去除路径前缀
        int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSep >= 0) {
            name = name.substring(lastSep + 1);
        }
        // 尝试用 UTF-8 重新解码（防止 ISO-8859-1 编码的中文乱码）
        try {
            byte[] bytes = name.getBytes(StandardCharsets.ISO_8859_1);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            if (decoded.matches(".*[\\u0000-\\u001F\\uFFFD].*")) {
            } else {
                name = decoded;
            }
        } catch (Exception ignored) {
        }
        return name.trim();
    }

    private void copyFile(File src, File dst) throws IOException {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }

    private File getXingheDir() {
        File xingheDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                xingheDir = new File(Environment.getExternalStorageDirectory(), "星河助手");
            } else {
                // 没有所有文件访问权限时，只能写到应用外部存储
                File externalFiles = context.getExternalFilesDir(null);
                xingheDir = externalFiles != null ? new File(externalFiles, "星河助手") : null;
            }
        } else {
            xingheDir = new File(Environment.getExternalStorageDirectory(), "星河助手");
        }

        if (xingheDir != null && !xingheDir.exists()) {
            xingheDir.mkdirs();
        }

        if (xingheDir != null && xingheDir.exists() && xingheDir.canWrite()) {
            android.util.Log.d("RemotePushServer", "getXingheDir: " + xingheDir.getAbsolutePath());
            return xingheDir;
        }

        // 兜底：应用外部存储
        File fallback = context.getExternalFilesDir(null);
        if (fallback != null) {
            File dir = new File(fallback, "星河助手");
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }

        return context.getCacheDir();
    }

    private Response jsonResponse(boolean success, String message, Response.Status status) {
        String json = "{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\"}";
        Response resp = newFixedLengthResponse(status, "application/json; charset=UTF-8", json);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        return resp;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String getLocalIpAddress() {
        // 1. 优先从 WiFi 获取
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    String wifiIp = Formatter.formatIpAddress(ip);
                    if (!"0.0.0.0".equals(wifiIp) && !"127.0.0.1".equals(wifiIp)) {
                        return wifiIp;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. 遍历所有网络接口，找到内网 IPv4 地址
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni == null || ni.isLoopback() || !ni.isUp() || ni.isVirtual()) {
                    continue;
                }
                String name = ni.getName().toLowerCase(Locale.US);
                if (name.contains("lo") || name.contains("dummy") || name.contains("p2p")
                        || name.contains("tun") || name.contains("ppp") || name.contains("rmnet")) {
                    continue;
                }

                java.util.Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr == null || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                        continue;
                    }
                    String host = addr.getHostAddress();
                    if (host == null) continue;
                    if (host.contains(":")) continue;
                    if (isPrivateIpv4(host)) {
                        return host;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 3. 兜底：localhost
        try {
            String localhost = InetAddress.getLocalHost().getHostAddress();
            if (localhost != null && !localhost.startsWith("127.")) {
                return localhost;
            }
        } catch (Exception ignored) {
        }

        return "0.0.0.0";
    }

    private boolean isPrivateIpv4(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 10) return true;
            if (a == 172 && b >= 16 && b <= 31) return true;
            if (a == 192 && b == 168) return true;
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    private void notifyServerStarted(final String url) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onServerStarted(url);
            }
        });
    }

    private void notifyServerStopped() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onServerStopped();
            }
        });
    }

    private void notifyServerError(final String error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onPushFailed(error);
            }
        });
    }

    private void notifyPushStarted() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onPushStarted();
            }
        });
    }

    private void notifyPushCompleted(final File file, final boolean isApk) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onPushCompleted(file, isApk);
            }
        });
    }
}
