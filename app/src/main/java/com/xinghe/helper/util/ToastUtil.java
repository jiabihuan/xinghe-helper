package com.xinghe.helper.util;

import android.content.Context;
import android.widget.Toast;

public class ToastUtil {
    public static void showShort(Context context, String message) {
        if (context != null && message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void showShort(Context context, int resId) {
        if (context != null) {
            Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
        }
    }

    public static void showLong(Context context, String message) {
        if (context != null && message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
