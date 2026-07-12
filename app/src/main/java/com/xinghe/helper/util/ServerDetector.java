package com.xinghe.helper.util;

import com.xinghe.helper.coredata.CoreData;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerDetector {

    private static final int TIMEOUT_MS = 5000;
    private static String bestServer = null;
    private static boolean detected = false;

    public interface DetectCallback {
        void onDone(String serverUrl);
    }

    public static synchronized String getBestServer() {
        if (bestServer != null) {
            return bestServer;
        }
        return CoreData.SERVER_URLS[0];
    }

    public static synchronized boolean isDetected() {
        return detected;
    }

    public static void detectAsync(final DetectCallback callback) {
        if (detected && bestServer != null) {
            if (callback != null) {
                callback.onDone(bestServer);
            }
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] servers = CoreData.SERVER_URLS;
                final long[] responseTimes = new long[servers.length];
                final CountDownLatch latch = new CountDownLatch(servers.length);
                ExecutorService executor = Executors.newFixedThreadPool(servers.length);

                for (int i = 0; i < servers.length; i++) {
                    final int index = i;
                    final String server = servers[i];
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            long start = System.currentTimeMillis();
                            boolean success = pingServer(server);
                            if (success) {
                                responseTimes[index] = System.currentTimeMillis() - start;
                            } else {
                                responseTimes[index] = Long.MAX_VALUE;
                            }
                            latch.countDown();
                        }
                    });
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                executor.shutdown();

                int bestIndex = -1;
                long bestTime = Long.MAX_VALUE;
                for (int i = 0; i < responseTimes.length; i++) {
                    if (responseTimes[i] < bestTime) {
                        bestTime = responseTimes[i];
                        bestIndex = i;
                    }
                }

                synchronized (ServerDetector.class) {
                    if (bestIndex >= 0) {
                        bestServer = servers[bestIndex];
                        CoreData.HTTP_BASE_URL = bestServer;
                    }
                    detected = true;
                }

                if (callback != null) {
                    callback.onDone(bestServer != null ? bestServer : servers[0]);
                }
            }
        }).start();
    }

    private static boolean pingServer(String serverUrl) {
        HttpURLConnection conn = null;
        try {
            String testUrl = serverUrl;
            if (!testUrl.endsWith("/")) {
                testUrl += "/";
            }
            URL url = new URL(testUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            return code == 200 || code == 301 || code == 302 || code == 404 || code == 403;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
