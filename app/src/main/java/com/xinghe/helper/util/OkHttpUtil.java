package com.xinghe.helper.util;

import com.xinghe.helper.coredata.CoreData;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OkHttpUtil {

    private static OkHttpClient client;

    static {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public static String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                return "";
            }
            return body.string();
        }
    }

    public static String getWithRetry(String url, int maxRetries) throws IOException {
        IOException lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return get(url);
            } catch (IOException e) {
                lastException = e;
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(1000 * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("请求被中断", ie);
                    }
                }
            }
        }
        throw lastException != null ? lastException : new IOException("请求失败");
    }

    public static String getWithServerFallback(String path) throws IOException {
        IOException lastException = null;
        String[] servers = CoreData.SERVER_URLS;
        
        for (int i = 0; i < servers.length; i++) {
            String server = servers[i];
            String fullUrl;
            if (path.startsWith("http")) {
                fullUrl = path;
            } else {
                String cleanPath = path.startsWith("/") ? path : "/" + path;
                fullUrl = server + cleanPath;
            }
            try {
                String result = get(fullUrl);
                if (i > 0) {
                    CoreData.HTTP_BASE_URL = server;
                }
                return result;
            } catch (IOException e) {
                lastException = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw lastException != null ? lastException : new IOException("所有线路均无法连接");
    }

    public static OkHttpClient getClient() {
        return client;
    }
}
