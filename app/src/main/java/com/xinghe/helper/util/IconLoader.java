package com.xinghe.helper.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.xinghe.helper.coredata.CoreData;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IconLoader {

    private static IconLoader instance;
    private LruCache<String, Bitmap> iconCache;
    private ExecutorService iconLoaderExecutor;
    private Handler mainHandler;

    private IconLoader() {
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory / 8;
        iconCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        iconLoaderExecutor = Executors.newFixedThreadPool(8);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized IconLoader getInstance() {
        if (instance == null) {
            instance = new IconLoader();
        }
        return instance;
    }

    public void loadIcon(String url, ImageView imageView) {
        if (url == null || url.length() == 0) return;
        Bitmap cached = iconCache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        imageView.setTag(url);
        iconLoaderExecutor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                String fullUrl = url;
                if (!url.startsWith("http")) fullUrl = CoreData.HTTP_BASE_URL + url;
                URL u = new URL(fullUrl);
                conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    byte[] data = readAllBytes(is);
                    is.close();
                    if (data != null && data.length > 0) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                        int reqSize = dpToPx(imageView.getContext(), 80);
                        int inSampleSize = 1;
                        if (opts.outWidth > reqSize || opts.outHeight > reqSize) {
                            int halfWidth = opts.outWidth / 2;
                            int halfHeight = opts.outHeight / 2;
                            while ((halfWidth / inSampleSize) >= reqSize
                                    && (halfHeight / inSampleSize) >= reqSize) {
                                inSampleSize *= 2;
                            }
                        }
                        opts.inJustDecodeBounds = false;
                        opts.inSampleSize = inSampleSize;
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                        if (bmp != null) {
                            iconCache.put(url, bmp);
                            mainHandler.post(() -> {
                                Object tag = imageView.getTag();
                                if (tag != null && tag.equals(url)) imageView.setImageBitmap(bmp);
                            });
                        }
                    }
                }
            } catch (Exception e) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private byte[] readAllBytes(InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            buf.write(buffer, 0, len);
        }
        return buf.toByteArray();
    }

    private int dpToPx(android.content.Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
