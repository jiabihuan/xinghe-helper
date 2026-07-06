package com.xinghe.helper;

import android.app.Application;
import android.os.Build;

import com.xinghe.helper.coredata.CoreData;

import java.io.File;

public class App extends Application {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        CoreData.FILE_PATH = getFilesDir().getAbsolutePath();
        File externalCache = getExternalCacheDir();
        if (externalCache != null) {
            CoreData.EXTERNAL_FILE_PATH = externalCache.getAbsolutePath() + File.separator + "sub";
        }
        instance = this;
    }

    public static App getInstance() {
        return instance;
    }
}
