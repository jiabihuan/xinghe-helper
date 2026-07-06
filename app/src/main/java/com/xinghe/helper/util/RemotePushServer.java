package com.xinghe.helper.util;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 远程推送 HTTP 服务器
 * 原理：在电视端启动一个轻量级 HTTP 服务器，同局域网内的手机/电脑通过浏览器
 * 访问电视 IP:端口，选择 APK 文件上传。电视端保存 APK 后调用系统安装器安装。
 * 参考 GitHub 上众多 Android 局域网传文件项目的实现思路：ServerSocket + 简易 HTTP 协议解析。
 */
public class RemotePushServer {

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
            "<p class=\"tip\">选择APK文件推送到电视</p>" +
            "<input type=\"file\" id=\"file\" accept=\".apk\" onchange=\"onFileSelected()\">" +
            "<button class=\"btn select\" onclick=\"document.getElementById('file').click()\">选择APK文件</button>" +
            "<button class=\"btn push\" id=\"pushBtn\" onclick=\"upload()\">推送到电视</button>" +
            "<div class=\"progress\" id=\"progress\"><div class=\"bar\" id=\"bar\"></div></div>" +
            "<p class=\"status\" id=\"status\"></p>" +
            "</div>" +
            "<script>" +
            "var file=null;" +
            "function onFileSelected(){file=document.getElementById('file').files[0];if(file){document.querySelector('.select').textContent='已选择: '+file.name;document.getElementById('pushBtn').style.display='block';}}" +
            "function upload(){if(!file){alert('请先选择APK文件');return;}var xhr=new XMLHttpRequest();var progress=document.getElementById('progress');var bar=document.getElementById('bar');var status=document.getElementById('status');progress.style.display='block';status.textContent='上传中...';xhr.upload.onprogress=function(e){if(e.lengthComputable){bar.style.width=(e.loaded/e.total*100)+'%';}};xhr.onreadystatechange=function(){if(xhr.readyState===4){if(xhr.status===200){status.textContent='推送成功，电视正在安装';bar.style.width='100%';}else{status.textContent='推送失败: '+xhr.responseText;}}};xhr.open('POST','/upload');xhr.send(file);}" +
            "</script></body></html>";

    private ServerSocket serverSocket;
    private Thread serverThread;
    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private OnPushListener listener;

    public interface OnPushListener {
        void onServerStarted(String url);
        void onServerStopped();
        void onPushStarted();
        void onPushCompleted(File apkFile);
        void onPushFailed(String error);
    }

    public RemotePushServer(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newCachedThreadPool();
    }

    public void setListener(OnPushListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (serverThread != null && serverThread.isAlive()) {
            return;
        }
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    notifyServerStarted(getServerUrl());
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            Socket client = serverSocket.accept();
                            executor.submit(new ClientHandler(client));
                        } catch (SocketException e) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    notifyServerError(e.getMessage());
                }
            }
        });
        serverThread.start();
    }

    public void stop() {
        if (serverThread != null) {
            serverThread.interrupt();
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        executor.shutdownNow();
        notifyServerStopped();
    }

    public String getServerUrl() {
        return "http://" + getLocalIpAddress() + ":" + PORT;
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return Formatter.formatIpAddress(ip);
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "0.0.0.0";
        }
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

    private void notifyPushCompleted(final File apkFile) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onPushCompleted(apkFile);
            }
        });
    }

    private void notifyPushFailed(final String error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onPushFailed(error);
            }
        });
    }

    private class ClientHandler implements Runnable {
        private final Socket client;

        ClientHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            InputStream input = null;
            OutputStream output = null;
            try {
                client.setSoTimeout(60000);
                input = client.getInputStream();
                output = new BufferedOutputStream(client.getOutputStream());

                // 1. 读取 HTTP 请求头，直到 \r\n\r\n，保持字节流完整
                byte[] headerBytes = readHeader(input);
                if (headerBytes == null || headerBytes.length == 0) {
                    send400(output);
                    return;
                }
                String header = new String(headerBytes, StandardCharsets.UTF_8);
                String[] lines = header.split("\r\n");
                if (lines.length < 1) {
                    send400(output);
                    return;
                }

                // 2. 解析请求行
                String[] parts = lines[0].split(" ");
                if (parts.length < 2) {
                    send400(output);
                    return;
                }
                String method = parts[0];
                String path = parts[1];

                // 3. 解析 Content-Length / Content-Type
                int contentLength = 0;
                String contentType = null;
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i];
                    if (line == null) continue;
                    String lower = line.toLowerCase(Locale.US);
                    if (lower.startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (lower.startsWith("content-type:")) {
                        contentType = line.substring(13).trim();
                    }
                }

                if ("GET".equalsIgnoreCase(method) && "/".equals(path)) {
                    sendHtml(output, INDEX_HTML);
                } else if ("POST".equalsIgnoreCase(method) && "/upload".equals(path)) {
                    handleUpload(input, output, contentLength, contentType);
                } else {
                    send404(output);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (output != null) output.flush();
                } catch (IOException ignored) {
                }
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }

        /**
         * 从输入流读取 HTTP 头，遇到 \r\n\r\n 停止。不破坏请求体字节流。
         */
        private byte[] readHeader(InputStream input) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            int b;
            int state = 0; // 0:none, 1:\r, 2:\r\n, 3:\r\n\r
            while ((b = input.read()) != -1) {
                baos.write(b);
                if (b == '\r') {
                    state = (state == 2) ? 3 : 1;
                } else if (b == '\n') {
                    if (state == 1) {
                        state = 2;
                    } else if (state == 3) {
                        return baos.toByteArray();
                    } else {
                        state = 0;
                    }
                } else {
                    state = 0;
                }
            }
            return baos.toByteArray();
        }

        private void handleUpload(InputStream input, OutputStream output, int contentLength, String contentType) throws IOException {
            if (contentLength <= 0) {
                sendJson(output, "{\"success\":false,\"message\":\"没有上传数据\"}", 400);
                return;
            }

            notifyPushStarted();

            byte[] body = readFully(input, contentLength);
            if (body == null || body.length == 0) {
                sendJson(output, "{\"success\":false,\"message\":\"读取上传数据失败\"}", 500);
                notifyPushFailed("读取上传数据失败");
                return;
            }

            File apkFile;
            if (contentType != null && contentType.toLowerCase(Locale.US).contains("multipart/form-data")) {
                apkFile = parseMultipart(body, contentType);
            } else {
                apkFile = saveRawBody(body);
            }

            if (apkFile != null && apkFile.exists() && apkFile.length() > 0) {
                sendJson(output, "{\"success\":true,\"message\":\"上传成功\"}", 200);
                notifyPushCompleted(apkFile);
            } else {
                sendJson(output, "{\"success\":false,\"message\":\"保存失败\"}", 500);
                notifyPushFailed("保存文件失败");
            }
        }

        private byte[] readFully(InputStream input, int length) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(length);
            byte[] buffer = new byte[8192];
            int total = 0;
            int len;
            while (total < length && (len = input.read(buffer, 0, Math.min(buffer.length, length - total))) != -1) {
                baos.write(buffer, 0, len);
                total += len;
            }
            return baos.toByteArray();
        }

        private File saveRawBody(byte[] body) throws IOException {
            File dir = context.getExternalCacheDir();
            if (dir == null) dir = context.getCacheDir();
            File file = new File(dir, "push_" + System.currentTimeMillis() + ".apk");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(body);
            fos.close();
            return file;
        }

        private File parseMultipart(byte[] body, String contentType) throws IOException {
            String boundary = extractBoundary(contentType);
            if (boundary == null) return null;

            // multipart 文件内容在第一个 \r\n\r\n 和下一个 boundary 之间
            // 用 ISO-8859-1 查找边界位置，避免 UTF-8 多字节问题
            String text = new String(body, StandardCharsets.ISO_8859_1);
            String boundaryMarker = "--" + boundary;

            int firstBoundary = text.indexOf(boundaryMarker);
            if (firstBoundary < 0) return null;

            int contentStart = text.indexOf("\r\n\r\n", firstBoundary);
            if (contentStart < 0) return null;
            contentStart += 4;

            int nextBoundary = text.indexOf("\r\n" + boundaryMarker, contentStart);
            if (nextBoundary < 0) {
                nextBoundary = text.indexOf("--" + boundaryMarker, contentStart);
            }
            if (nextBoundary < 0) {
                nextBoundary = body.length;
            }

            File dir = context.getExternalCacheDir();
            if (dir == null) dir = context.getCacheDir();
            File file = new File(dir, "push_" + System.currentTimeMillis() + ".apk");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(body, contentStart, nextBoundary - contentStart);
            fos.close();
            return file;
        }

        private String extractBoundary(String contentType) {
            if (contentType == null) return null;
            int idx = contentType.toLowerCase(Locale.US).indexOf("boundary=");
            if (idx < 0) return null;
            String boundary = contentType.substring(idx + 9);
            // 去除可能的引号
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
            return boundary;
        }

        private void sendHtml(OutputStream output, String html) throws IOException {
            byte[] data = html.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));
            output.write(data);
        }

        private void sendJson(OutputStream output, String json, int code) throws IOException {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            String status = code == 200 ? "200 OK" : (code == 400 ? "400 Bad Request" : "500 Internal Server Error");
            String header = "HTTP/1.1 " + status + "\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));
            output.write(data);
        }

        private void send400(OutputStream output) throws IOException {
            String body = "Bad Request";
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));
            output.write(data);
        }

        private void send404(OutputStream output) throws IOException {
            String body = "Not Found";
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));
            output.write(data);
        }
    }
}
