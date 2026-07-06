package com.xinghe.helper.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
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

public class ManagerFragment extends Fragment implements AppUninstallAdapter.OnAppActionListener {

    private AppUninstallAdapter adapter;
    private ExecutorService appLoadExecutor;
    private Future<?> appLoadFuture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RecyclerView recyclerApps;
    private TextView tvState;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manager, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerApps = view.findViewById(R.id.recyclerApps);
        tvState = view.findViewById(R.id.tvState);
        recyclerApps.setLayoutManager(new GridLayoutManager(getContext(), 5));
        adapter = new AppUninstallAdapter(this);
        recyclerApps.setAdapter(adapter);
        loadApps();
    }

    @Override
    public void onDestroyView() {
        cancelLoadApps();
        mainHandler.removeCallbacksAndMessages(null);
        if (recyclerApps != null) {
            recyclerApps.setAdapter(null);
        }
        recyclerApps = null;
        tvState = null;
        adapter = null;
        super.onDestroyView();
    }

    private void loadApps() {
        Context context = getContext();
        if (context == null) return;

        final Context appContext = context.getApplicationContext();
        if (appLoadExecutor == null || appLoadExecutor.isShutdown()) {
            appLoadExecutor = Executors.newSingleThreadExecutor();
        }
        cancelLoadFuture();
        tvState.setVisibility(View.VISIBLE);
        recyclerApps.setVisibility(View.GONE);
        appLoadFuture = appLoadExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final List<InstalledApp> apps = queryInstalledApps(appContext);
                if (!Thread.currentThread().isInterrupted()) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isAdded() && recyclerApps != null && adapter != null && tvState != null) {
                                showApps(apps);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onResume() {
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
        Collections.sort(apps, new Comparator<InstalledApp>() {
            @Override
            public int compare(InstalledApp left, InstalledApp right) {
                return left.getAppName().compareToIgnoreCase(right.getAppName());
            }
        });
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
    public void onOpenApp(InstalledApp app) {
        if (getContext() == null) return;
        Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (intent == null) {
            ToastUtil.showShort(getContext(), "打开失败");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onUninstallApp(InstalledApp app) {
        if (getContext() == null) return;
        Uri packageUri = Uri.parse("package:" + app.getPackageName());
        if (startIntent(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri))) {
            // do nothing
        } else if (startIntent(new Intent(Intent.ACTION_DELETE, packageUri))) {
            // do nothing
        } else {
            ToastUtil.showShort(getContext(), "请在设置中卸载");
            openAppDetails(packageUri);
        }
    }

    private boolean startIntent(Intent intent) {
        if (getContext() == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager packageManager = getContext().getPackageManager();
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
        if (getContext() == null) return;
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            ToastUtil.showShort(getContext(), "卸载失败");
        }
    }
}
