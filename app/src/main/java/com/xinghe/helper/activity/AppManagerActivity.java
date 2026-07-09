package com.xinghe.helper.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xinghe.helper.R;
import com.xinghe.helper.adapter.AppUninstallAdapter;
import com.xinghe.helper.model.InstalledApp;
import com.xinghe.helper.util.ToastUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AppManagerActivity extends AppCompatActivity implements AppUninstallAdapter.OnAppActionListener {

    private AppUninstallAdapter adapter;
    private ExecutorService appLoadExecutor;
    private Future<?> appLoadFuture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RecyclerView recyclerApps;
    private TextView tvState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);

        recyclerApps = findViewById(R.id.recyclerApps);
        tvState = findViewById(R.id.tvState);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 6);
        recyclerApps.setLayoutManager(layoutManager);
        int spacing = getResources().getDimensionPixelSize(R.dimen.dp12);
        recyclerApps.addItemDecoration(new GridSpacingItemDecoration(6, spacing, true));
        adapter = new AppUninstallAdapter(this);
        recyclerApps.setAdapter(adapter);

        loadApps();
    }

    @Override
    protected void onDestroy() {
        cancelLoadApps();
        mainHandler.removeCallbacksAndMessages(null);
        if (recyclerApps != null) {
            recyclerApps.setAdapter(null);
        }
        recyclerApps = null;
        tvState = null;
        adapter = null;
        super.onDestroy();
    }

    private void loadApps() {
        if (appLoadExecutor == null || appLoadExecutor.isShutdown()) {
            appLoadExecutor = Executors.newSingleThreadExecutor();
        }
        cancelLoadFuture();
        tvState.setVisibility(View.VISIBLE);
        recyclerApps.setVisibility(View.GONE);
        appLoadFuture = appLoadExecutor.submit(() -> {
            final List<InstalledApp> apps = queryInstalledApps(AppManagerActivity.this);
            if (!Thread.currentThread().isInterrupted()) {
                mainHandler.post(() -> {
                    showApps(apps);
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recyclerApps != null && adapter != null && tvState != null) {
            loadApps();
        }
    }

    private static List<InstalledApp> queryInstalledApps(Context context) {
        List<InstalledApp> apps = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> applicationInfos = packageManager.getInstalledApplications(0);
        String selfPackage = context.getPackageName();
        for (ApplicationInfo info : applicationInfos) {
            if (Thread.currentThread().isInterrupted()) {
                return apps;
            }
            if (!selfPackage.equals(info.packageName)) {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(info.packageName);
                if (launchIntent != null) {
                    apps.add(new InstalledApp(
                            info.loadLabel(packageManager).toString(),
                            info.packageName,
                            info.loadIcon(packageManager)));
                }
            }
        }
        Collections.sort(apps, (left, right) -> left.getAppName().compareToIgnoreCase(right.getAppName()));
        return apps;
    }

    private void cancelLoadApps() {
        cancelLoadFuture();
        if (appLoadExecutor != null) {
            appLoadExecutor.shutdownNow();
            appLoadExecutor = null;
        }
    }

    private void cancelLoadFuture() {
        if (appLoadFuture != null) {
            appLoadFuture.cancel(true);
            appLoadFuture = null;
        }
    }

    private void showApps(List<InstalledApp> apps) {
        adapter.setApps(apps);
        tvState.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        tvState.setText(apps.isEmpty() ? R.string.empty_apps : R.string.app_list_loading);
        recyclerApps.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
        if (!apps.isEmpty()) {
            recyclerApps.requestFocus();
        }
    }

    @Override
    public void onAppOpen(InstalledApp app) {
        onOpenAppInternal(app);
    }

    @Override
    public void onAppUninstall(InstalledApp app) {
        onUninstallAppInternal(app);
    }

    private void onOpenAppInternal(InstalledApp app) {
        if (app == null) return;
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (intent == null) {
                ToastUtil.showShort(this, "打开失败");
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            ToastUtil.showShort(this, "打开失败");
        }
    }

    private void onUninstallAppInternal(InstalledApp app) {
        if (app == null) return;
        try {
            String packageName = app.getPackageName();
            Uri packageUri = Uri.parse("package:" + packageName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                intent.setData(packageUri);
                if (startIntent(intent)) {
                    return;
                }
            }

            Intent deleteIntent = new Intent(Intent.ACTION_DELETE);
            deleteIntent.setData(packageUri);
            if (startIntent(deleteIntent)) {
                return;
            }

            ToastUtil.showShort(this, "请在设置中卸载");
            openAppDetails(packageUri);
        } catch (Exception e) {
            ToastUtil.showShort(this, "卸载失败");
        }
    }

    private boolean startIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager packageManager = getPackageManager();
        if (intent.resolveActivity(packageManager) == null) {
            return false;
        }
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openAppDetails(Uri packageUri) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            ToastUtil.showShort(this, "卸载失败");
        }
    }

    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view,
                                   RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;
                if (position < spanCount) {
                    outRect.top = spacing;
                }
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) {
                    outRect.top = spacing;
                }
            }
        }
    }
}