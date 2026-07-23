package com.xinghe.helper.util;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.text.format.Formatter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import fi.iki.elonen.NanoHTTPD;

public class PhpLocalServer extends NanoHTTPD {

    private static final int DEFAULT_PORT = 8765;
    private static final int EXEC_TIMEOUT_SECONDS = 60;
    private static final int PHP_SOCKET_TIMEOUT_SECONDS = 8;
    private static final String BUNDLED_PHP_VERSION = "android-php-runtime-v4";

    private final Context context;
    private int actualPort = DEFAULT_PORT;
    private File documentRoot;

    public PhpLocalServer(Context context) {
        super(DEFAULT_PORT);
        this.context = context.getApplicationContext();
        this.documentRoot = getDocumentRoot();
    }

    public void startServer() throws IOException {
        try {
            documentRoot = getDocumentRoot();
            installBundledPhpIfNeeded();
            actualPort = findAvailablePort(DEFAULT_PORT);
            if (actualPort <= 0) {
                throw new IOException("找不到可用端口");
            }
            try {
                java.lang.reflect.Field field = NanoHTTPD.class.getDeclaredField("myPort");
                field.setAccessible(true);
                field.setInt(this, actualPort);
            } catch (Exception ignored) {
            }
            start();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            IOException wrapped = new IOException("PHP服务初始化异常: " + e.getMessage());
            wrapped.initCause(e);
            throw wrapped;
        }
    }

    public String getServerUrl() {
        return "http://" + getLocalIpAddress() + ":" + actualPort;
    }

    public File getDocumentRoot() {
        File base;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                base = new File(Environment.getExternalStorageDirectory(), "星河助手");
            } else {
                base = context.getExternalFilesDir(null);
            }
        } else {
            base = new File(Environment.getExternalStorageDirectory(), "星河助手");
        }
        if (base == null) base = context.getFilesDir();
        File root = new File(base, "php-www");
        if (!root.exists()) root.mkdirs();

        File index = new File(root, "index.php");
        if (!index.exists()) {
            try {
                FileOutputStream out = new FileOutputStream(index);
                String demo = "<?php\n"
                        + "echo '<h1>星河 PHP 服务已启动</h1>';\n"
                        + "echo '<p>当前时间：' . date('Y-m-d H:i:s') . '</p>';\n"
                        + "echo '<p>如果你看到 PHP 源码，说明盒子里还没有可用的 PHP 解释器。</p>';\n";
                out.write(demo.getBytes(StandardCharsets.UTF_8));
                out.close();
            } catch (IOException ignored) {
            }
        }
        return root;
    }

    public String getInterpreterStatus() {
        File php = findPhpInterpreter();
        if (php == null) {
            return "未检测到 PHP 解释器，请确认 APK 已内置 armeabi-v7a/php";
        }
        return "已检测到解释器：" + php.getAbsolutePath();
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        if (Method.POST.equals(method) && "/upload".equals(uri)) {
            return handleUpload(session);
        }

        if (Method.GET.equals(method) && "/".equals(uri)) {
            File indexPhp = new File(documentRoot, "index.php");
            if (indexPhp.exists() && findPhpInterpreter() != null) {
                return executePhp(indexPhp, session);
            }
            return htmlResponse(renderHomePage());
        }

        if (!Method.GET.equals(method)) {
            return textResponse(Response.Status.METHOD_NOT_ALLOWED, "Method Not Allowed");
        }

        File target = resolveFile(uri);
        if (target == null || !target.exists()) {
            return textResponse(Response.Status.NOT_FOUND, "文件不存在");
        }
        if (target.isDirectory()) {
            return htmlResponse(renderDirectory(target, uri));
        }
        if (target.getName().toLowerCase(Locale.US).endsWith(".php")) {
            return executePhp(target, session);
        }
        return serveStaticFile(target);
    }

    private Response handleUpload(IHTTPSession session) {
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
        } catch (Exception e) {
            return jsonResponse(false, "解析上传失败: " + e.getMessage(), Response.Status.INTERNAL_ERROR);
        }
        if (files.isEmpty()) {
            return jsonResponse(false, "没有收到文件", Response.Status.BAD_REQUEST);
        }

        String fileName = session.getParms().get("filename");
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "upload_" + System.currentTimeMillis() + ".php";
        }
        fileName = sanitizeFileName(fileName);
        String lower = fileName.toLowerCase(Locale.US);
        if (!(lower.endsWith(".php") || lower.endsWith(".html") || lower.endsWith(".htm")
                || lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".txt")
                || lower.endsWith(".json"))) {
            return jsonResponse(false, "只允许上传 php/html/css/js/txt/json 文件", Response.Status.BAD_REQUEST);
        }

        File temp = new File(files.values().iterator().next());
        File dest = new File(documentRoot, fileName);
        try {
            copyFile(temp, dest);
        } catch (IOException e) {
            return jsonResponse(false, "保存失败: " + e.getMessage(), Response.Status.INTERNAL_ERROR);
        }
        return jsonResponse(true, "上传成功: " + fileName, Response.Status.OK);
    }

    private Response executePhp(File script, IHTTPSession session) {
        File php = findPhpInterpreter();
        if (php == null) {
            String body = "<h1>未检测到 PHP 解释器</h1>"
                    + "<p>文件已经存在，但当前盒子不能直接执行 PHP。</p>"
                    + "<p>新版 APK 会优先自动释放内置的 armeabi-v7a PHP；如果仍看到此提示，说明 APK 暂未带上可执行 PHP，或设备架构不兼容。</p>"
                    + "<p>也可以手动把 Android 可执行的 php 或 php-cgi 放到以下任一位置：</p>"
                    + "<pre>" + escapeHtml(new File(documentRoot.getParentFile(), "php/php").getAbsolutePath()) + "\n"
                    + escapeHtml(new File(documentRoot.getParentFile(), "php/php-cgi").getAbsolutePath()) + "\n"
                    + escapeHtml(new File(context.getFilesDir(), "php/php").getAbsolutePath()) + "</pre>"
                    + "<p>放好后重新打开 PHP 服务页面。</p>"
                    + "<p><a href=\"/\">返回首页</a></p>";
            return htmlResponse(body);
        }

        try {
            File phpHome = getBundledPhpHome();
            File phpIni = new File(phpHome, "etc/php.ini");
            File phpTmpDir = new File(context.getCacheDir(), "php-tmp");
            File phpLibDir = new File(phpHome, "lib");
            File phpEtcDir = new File(phpHome, "etc");
            File phpTlsDir = new File(phpEtcDir, "tls");
            File phpCertFile = new File(phpTlsDir, "cert.pem");
            File phpOpenSslConf = new File(phpTlsDir, "openssl.cnf");
            File phpResolvConf = new File(phpEtcDir, "resolv.conf");
            File phpHosts = new File(phpEtcDir, "hosts");
            File phpResolvWrapper = new File(phpLibDir, "libresolv_wrapper.so");
            if (!phpTmpDir.exists()) phpTmpDir.mkdirs();
            boolean cgiMode = php.getName().contains("cgi");
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(php.getAbsolutePath());
            command.add("-n");
            command.add("-d");
            command.add("opcache.enable=0");
            command.add("-d");
            command.add("opcache.enable_cli=0");
            command.add("-d");
            command.add("opcache.lockfile_path=" + phpTmpDir.getAbsolutePath());
            command.add("-d");
            command.add("sys_temp_dir=" + phpTmpDir.getAbsolutePath());
            command.add("-d");
            command.add("upload_tmp_dir=" + phpTmpDir.getAbsolutePath());
            command.add("-d");
            command.add("date.timezone=Asia/Shanghai");
            command.add("-d");
            command.add("default_socket_timeout=" + PHP_SOCKET_TIMEOUT_SECONDS);
            command.add("-d");
            command.add("max_execution_time=" + Math.max(1, EXEC_TIMEOUT_SECONDS - 5));
            command.add("-d");
            command.add("display_errors=1");
            command.add("-d");
            command.add("log_errors=0");
            command.add("-d");
            command.add("error_reporting=E_ALL & ~E_NOTICE & ~E_DEPRECATED & ~E_STRICT");
            command.add("-d");
            command.add("html_errors=0");
            command.add("-d");
            command.add("allow_url_fopen=1");
            command.add("-d");
            command.add("user_agent=XinghePhpServer/1.0");
            if (phpCertFile.exists()) {
                command.add("-d");
                command.add("openssl.cafile=" + phpCertFile.getAbsolutePath());
                command.add("-d");
                command.add("curl.cainfo=" + phpCertFile.getAbsolutePath());
            }
            if (cgiMode) {
                if (phpIni.exists()) {
                    command.add("-c");
                    command.add(phpIni.getAbsolutePath());
                }
            } else if (phpIni.exists()) {
                command.add("-c");
                command.add(phpIni.getAbsolutePath());
                command.add(script.getAbsolutePath());
            } else {
                command.add(script.getAbsolutePath());
            }
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(documentRoot);
            Map<String, String> env = pb.environment();
            env.put("HOME", phpHome.getAbsolutePath());
            env.put("TMPDIR", phpTmpDir.getAbsolutePath());
            env.put("TEMP", phpTmpDir.getAbsolutePath());
            env.put("TMP", phpTmpDir.getAbsolutePath());
            env.put("TZ", "Asia/Shanghai");
            env.put("PREFIX", phpHome.getAbsolutePath());
            env.put("PHP_INI_SCAN_DIR", "");
            env.put("PATH", phpHome.getAbsolutePath() + ":" + new File(phpHome, "bin").getAbsolutePath() + ":/system/bin");
            env.put("LD_LIBRARY_PATH", phpLibDir.getAbsolutePath());
            if (phpResolvWrapper.exists()) {
                env.put("LD_PRELOAD", phpResolvWrapper.getAbsolutePath());
            }
            if (phpResolvConf.exists()) {
                env.put("RESOLV_WRAPPER_CONF", phpResolvConf.getAbsolutePath());
            }
            if (phpHosts.exists()) {
                env.put("RESOLV_WRAPPER_HOSTS", phpHosts.getAbsolutePath());
            }
            if (phpCertFile.exists()) {
                env.put("SSL_CERT_FILE", phpCertFile.getAbsolutePath());
                env.put("CURL_CA_BUNDLE", phpCertFile.getAbsolutePath());
            }
            if (phpTlsDir.exists()) {
                env.put("SSL_CERT_DIR", phpTlsDir.getAbsolutePath());
            }
            if (phpOpenSslConf.exists()) {
                env.put("OPENSSL_CONF", phpOpenSslConf.getAbsolutePath());
            }
            env.put("REQUEST_METHOD", session.getMethod().name());
            env.put("DOCUMENT_ROOT", documentRoot.getAbsolutePath());
            String queryString = session.getQueryParameterString() == null ? "" : session.getQueryParameterString();
            String scriptName = "/" + documentRoot.toURI().relativize(script.toURI()).getPath();
            env.put("QUERY_STRING", queryString);
            env.put("REQUEST_URI", scriptName + (queryString.isEmpty() ? "" : "?" + queryString));
            env.put("SCRIPT_NAME", scriptName);
            env.put("PHP_SELF", scriptName);
            env.put("SCRIPT_FILENAME", script.getAbsolutePath());
            env.put("SERVER_PROTOCOL", "HTTP/1.1");
            env.put("GATEWAY_INTERFACE", "CGI/1.1");
            env.put("SERVER_SOFTWARE", "XinghePhpServer/1.0");
            env.put("SERVER_NAME", getLocalIpAddress());
            env.put("SERVER_PORT", String.valueOf(actualPort));
            env.put("REMOTE_ADDR", "127.0.0.1");
            env.put("REMOTE_PORT", "0");
            env.put("HTTPS", "off");
            env.put("CONTENT_TYPE", "");
            env.put("CONTENT_LENGTH", "0");
            env.put("REDIRECT_STATUS", "200");
            Process process = pb.start();
            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());
            stdout.start();
            stderr.start();
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
            boolean finished = waitForProcess(process, EXEC_TIMEOUT_SECONDS * 1000L);
            if (!finished) {
                process.destroy();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                process.destroyForcibly();
                return textResponse(Response.Status.INTERNAL_ERROR,
                        "PHP 执行超时，已等待 " + EXEC_TIMEOUT_SECONDS + " 秒。\n"
                                + "脚本：" + script.getName() + "\n"
                                + "请求：" + session.getUri() + (queryString.isEmpty() ? "" : "?" + queryString) + "\n"
                                + "可能原因：脚本请求的外部接口无响应、当前 PHP runtime 缺少 curl/openssl 等联网扩展，或脚本内部进入长循环。\n"
                                + "PHP stderr：\n" + limitText(stderr.getText(), 2000) + "\n"
                                + "PHP stdout：\n" + limitText(stdout.getText(), 2000));
            }
            stdout.joinQuietly(1000);
            stderr.joinQuietly(1000);
            String output = stdout.getText();
            String error = stderr.getText();
            if (process.exitValue() != 0) {
                // Non-fatal warnings (Deprecated, Notice) still produce output; serve it
                if (output != null && output.length() > 20) {
                    return phpOutputResponse(output);
                }
                return textResponse(Response.Status.INTERNAL_ERROR, "PHP 执行失败:\n" + error + "\n" + output);
            }
            return phpOutputResponse(output);
        } catch (Exception e) {
            return textResponse(Response.Status.INTERNAL_ERROR, "PHP 执行异常: " + e.getMessage());
        }
    }

    private boolean waitForProcess(Process process, long timeoutMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (true) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException ignored) {
            }
            if (System.currentTimeMillis() - start >= timeoutMillis) {
                return false;
            }
            Thread.sleep(100);
        }
    }

    private static class StreamCollector extends Thread {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        StreamCollector(InputStream input) {
            this.input = input;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            int len;
            try {
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                    if (output.size() > 1024 * 1024) {
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        String getText() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }

        void joinQuietly(long timeoutMillis) {
            try {
                join(timeoutMillis);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private String limitText(String text, int maxLength) {
        if (text == null || text.isEmpty()) return "(无)";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n...(已截断)";
    }

    private File findPhpInterpreter() {
        File parent = documentRoot != null ? documentRoot.getParentFile() : null;
        File[] candidates = new File[]{
                getBundledPhpFile("php-cgi"),
                getBundledPhpFile("php"),
                new File(getBundledPhpHome(), "bin/php-cgi"),
                new File(getBundledPhpHome(), "bin/php"),
                parent == null ? null : new File(parent, "php/php-cgi"),
                parent == null ? null : new File(parent, "php/php"),
                new File(context.getFilesDir(), "php/php-cgi"),
                new File(context.getFilesDir(), "php/php")
        };
        for (File file : candidates) {
            if (file != null && file.exists() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    private void installBundledPhpIfNeeded() throws IOException {
        String abi = chooseAssetAbi();
        if (abi == null) throw new IOException("当前设备没有可用 ABI");
        File home = getBundledPhpHome();
        File marker = new File(home, ".version");
        File php = new File(home, "php");
        if (php.exists() && marker.exists()) {
            try {
                String version = readAll(new FileInputStream(marker)).trim();
                if (BUNDLED_PHP_VERSION.equals(version)) {
                    setExecutableBits(home);
                    return;
                }
            } catch (IOException ignored) {
            }
        }
        deleteRecursively(home);
        if (!home.exists()) home.mkdirs();
        String assetPath = "php/php-runtime-" + abi + ".zip";
        try {
            InputStream in = context.getAssets().open(assetPath);
            unzip(in, home);
            in.close();
            setExecutableBits(home);
            FileOutputStream out = new FileOutputStream(marker);
            out.write(BUNDLED_PHP_VERSION.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (IOException e) {
            deleteRecursively(home);
            if (e.getMessage() != null && e.getMessage().contains(assetPath)) {
                return;
            }
            throw new IOException("释放内置 PHP 环境失败: " + e.getMessage(), e);
        }
    }

    private String chooseAssetAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null) {
            for (String abi : abis) {
                if ("armeabi-v7a".equals(abi)) return "armeabi-v7a";
            }
            for (String abi : abis) {
                if ("arm64-v8a".equals(abi)) return "arm64-v8a";
            }
        }
        return "armeabi-v7a";
    }

    private File getBundledPhpFile(String name) {
        return new File(getBundledPhpHome(), name);
    }

    private File getBundledPhpHome() {
        return new File(context.getFilesDir(), "php-runtime");
    }

    private void unzip(InputStream input, File destDir) throws IOException {
        ZipInputStream zis = new ZipInputStream(input);
        ZipEntry entry;
        byte[] buffer = new byte[8192];
        String root = destDir.getCanonicalPath();
        while ((entry = zis.getNextEntry()) != null) {
            File outFile = new File(destDir, entry.getName());
            String outPath = outFile.getCanonicalPath();
            if (!outPath.startsWith(root)) {
                zis.closeEntry();
                continue;
            }
            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                FileOutputStream out = new FileOutputStream(outFile);
                int len;
                while ((len = zis.read(buffer)) != -1) out.write(buffer, 0, len);
                out.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private void setExecutableBits(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) setExecutableBits(child);
            }
        }
        file.setReadable(true, false);
        file.setExecutable(true, false);
        if (file.getName().equals("php") || file.getName().equals("php-cgi")) {
            file.setWritable(true, true);
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private Response phpOutputResponse(String output) {
        int headerEnd = output.indexOf("\r\n\r\n");
        int headerEndLf = output.indexOf("\n\n");
        if (headerEnd > 0 || headerEndLf > 0) {
            int split = headerEnd > 0 ? headerEnd + 4 : headerEndLf + 2;
            String headers = output.substring(0, split).trim();
            String body = output.substring(split);
            String mime = "text/html; charset=UTF-8";
            boolean hasCgiHeader = false;
            String location = null;
            Response.Status status = Response.Status.OK;
            Map<String, String> extraHeaders = new HashMap<>();
            for (String line : headers.split("\\r?\\n")) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-type:")) {
                    hasCgiHeader = true;
                    mime = line.substring(colon + 1).trim();
                } else if (lower.startsWith("status:")) {
                    hasCgiHeader = true;
                    status = parseCgiStatus(line.substring(colon + 1).trim());
                } else if (lower.startsWith("location:")) {
                    hasCgiHeader = true;
                    location = line.substring(colon + 1).trim();
                } else if (lower.startsWith("x-powered-by:")) {
                    hasCgiHeader = true;
                } else if (!lower.startsWith("transfer-encoding:")
                        && !lower.startsWith("content-length:")
                        && !lower.startsWith("connection:")) {
                    hasCgiHeader = true;
                    extraHeaders.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
            }
            if (hasCgiHeader) {
                if (location != null && !location.isEmpty()) {
                    if (status == Response.Status.OK) {
                        status = Response.Status.REDIRECT;
                    }
                    Response resp = newFixedLengthResponse(status, "text/html; charset=UTF-8", "");
                    resp.addHeader("Location", location);
                    resp.addHeader("Access-Control-Allow-Origin", "*");
                    for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                        resp.addHeader(entry.getKey(), entry.getValue());
                    }
                    return resp;
                }
                Response resp = newFixedLengthResponse(status, mime, body);
                for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                    resp.addHeader(entry.getKey(), entry.getValue());
                }
                return resp;
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", output);
    }

    private Response.Status parseCgiStatus(String value) {
        if (value == null) return Response.Status.OK;
        String trimmed = value.trim();
        int space = trimmed.indexOf(' ');
        String codeText = space > 0 ? trimmed.substring(0, space) : trimmed;
        try {
            int code = Integer.parseInt(codeText);
            Response.Status found = Response.Status.lookup(code);
            if (found != null) return found;
            if (code >= 300 && code < 400) return Response.Status.REDIRECT;
            if (code >= 400 && code < 500) return Response.Status.BAD_REQUEST;
            if (code >= 500) return Response.Status.INTERNAL_ERROR;
        } catch (Exception ignored) {
        }
        return Response.Status.OK;
    }

    private Response serveStaticFile(File file) {
        try {
            FileInputStream input = new FileInputStream(file);
            return newChunkedResponse(Response.Status.OK, guessMime(file.getName()), input);
        } catch (IOException e) {
            return textResponse(Response.Status.INTERNAL_ERROR, "读取文件失败");
        }
    }

    private String renderHomePage() {
        return "<h1>星河 PHP 本地服务</h1>"
                + "<p>服务目录：<code>" + escapeHtml(documentRoot.getAbsolutePath()) + "</code></p>"
                + "<p>解释器状态：" + escapeHtml(getInterpreterStatus()) + "</p>"
                + "<form><input type=\"file\" id=\"file\"><button type=\"button\" onclick=\"upload()\">上传文件</button></form>"
                + "<p><a href=\"/index.php\">访问 index.php</a> | <a href=\"/files/\">查看文件列表</a></p>"
                + "<script>"
                + "function upload(){var f=document.getElementById('file').files[0];if(!f){alert('请选择文件');return;}var x=new XMLHttpRequest();x.onreadystatechange=function(){if(x.readyState===4){alert(x.responseText);location.reload();}};x.open('POST','/upload?filename='+encodeURIComponent(f.name));var d=new FormData();d.append('file',f);x.send(d);}"
                + "</script>";
    }

    private String renderDirectory(File dir, String uri) {
        StringBuilder sb = new StringBuilder("<h1>文件列表</h1><p><a href=\"/\">返回首页</a></p><ul>");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String href = "/files/".equals(uri) || "/".equals(uri) ? "/" + encodeUrl(name) : uri + "/" + encodeUrl(name);
                sb.append("<li><a href=\"").append(href).append("\">")
                        .append(escapeHtml(name))
                        .append(file.isDirectory() ? "/" : "")
                        .append("</a></li>");
            }
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private File resolveFile(String uri) {
        try {
            if ("/files".equals(uri) || "/files/".equals(uri)) return documentRoot;
            String path = java.net.URLDecoder.decode(uri, "UTF-8");
            if (path.startsWith("/")) path = path.substring(1);
            if (path.startsWith("files/")) path = path.substring(6);
            File target = new File(documentRoot, path);
            String rootPath = documentRoot.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.startsWith(rootPath)) return null;
            return target;
        } catch (Exception e) {
            return null;
        }
    }

    private Response htmlResponse(String body) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>星河 PHP 服务</title><style>body{font-family:Arial,sans-serif;background:#101b3d;color:#fff;padding:24px;}a{color:#00d4ff}code,pre{background:#1c2d5a;padding:8px;border-radius:8px;display:block;white-space:pre-wrap}button{padding:10px 18px;border:0;border-radius:20px;background:#00d4ff;color:#00172a;font-weight:bold}</style></head><body>"
                + body + "</body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html);
    }

    private Response textResponse(Response.Status status, String text) {
        return newFixedLengthResponse(status, "text/plain; charset=UTF-8", text);
    }

    private Response jsonResponse(boolean success, String message, Response.Status status) {
        String json = "{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\"}";
        Response resp = newFixedLengthResponse(status, "application/json; charset=UTF-8", json);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        return resp;
    }

    private int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 30; port++) {
            ServerSocket socket = null;
            try {
                socket = new ServerSocket(port);
                socket.setReuseAddress(true);
                return port;
            } catch (IOException ignored) {
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return -1;
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    String wifiIp = Formatter.formatIpAddress(ip);
                    if (!"0.0.0.0".equals(wifiIp) && !"127.0.0.1".equals(wifiIp)) return wifiIp;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni == null || ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                java.util.Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr == null || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host != null && !host.contains(":")) return host;
                }
            }
        } catch (Exception ignored) {
        }
        return "0.0.0.0";
    }

    private void copyFile(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        out.flush();
        in.close();
        out.close();
    }

    private String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = input.read(buffer)) != -1) out.write(buffer, 0, len);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private String sanitizeFileName(String name) {
        int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSep >= 0) name = name.substring(lastSep + 1);
        return name.replaceAll("[\\r\\n\\t]", "").trim();
    }

    private String guessMime(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".txt")) return "text/plain; charset=UTF-8";
        return "application/octet-stream";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String encodeUrl(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }
}
