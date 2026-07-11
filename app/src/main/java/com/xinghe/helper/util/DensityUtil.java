package com.xinghe.helper.util;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class DensityUtil {

    private static final float DESIGN_WIDTH = 1920f;
    private static float appDensity = 0;
    private static float appScaledDensity = 0;

    public static void setDensity(android.app.Activity activity) {
        DisplayMetrics appDisplayMetrics = activity.getApplication().getResources().getDisplayMetrics();

        if (appDensity == 0) {
            appDensity = appDisplayMetrics.density;
            appScaledDensity = appDisplayMetrics.scaledDensity;
        }

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);

        float targetDensity = dm.widthPixels / DESIGN_WIDTH;
        float targetScaledDensity = targetDensity * (appScaledDensity / appDensity);
        int targetDensityDpi = (int) (160 * targetDensity);

        DisplayMetrics activityDisplayMetrics = activity.getResources().getDisplayMetrics();
        activityDisplayMetrics.density = targetDensity;
        activityDisplayMetrics.scaledDensity = targetScaledDensity;
        activityDisplayMetrics.densityDpi = targetDensityDpi;
    }
}
