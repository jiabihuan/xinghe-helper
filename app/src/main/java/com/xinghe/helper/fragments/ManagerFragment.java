package com.xinghe.helper.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
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
    private PopupWindow actionPopup;
    private View lastFocusedView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manager, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerApps = view.findViewById(R.id.recyclerApps);
        tvState = view.findViewById(R.id.tvState);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 6);
        recyclerApps.setLayoutManager(layoutManager);
        int spacing = getResources().getDimensionPixelSize(R.dimen.dp12);
        recyclerApps.addItemDecoration(new GridSpacingItemDecoration(6, spacing, true));
        adapter = new AppUninstallAdapter(this);
        recyclerApps.setAdapter(adapter);
        loadApps();
    }

    @Override
    public void onDestroyView() {
        dismissActionPopup();
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
    public void onAppClicked(InstalledApp app, View itemView) {
        showActionPopup(app, itemView);
    }

    private void showActionPopup(final InstalledApp app, View anchorView) {
        dismissActionPopup();
        lastFocusedView = anchorView;

        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup_app_actions, null);
        final TextView btnOpen = popupView.findViewById(R.id.btnOpen);
        final TextView btnUninstall = popupView.findViewById(R.id.btnUninstall);

        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissActionPopup();
                onOpenApp(app);
            }
        });

        btnUninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissActionPopup();
                onUninstallApp(app);
            }
        });

        btnOpen.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    btnUninstall.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismissActionPopup();
                    if (lastFocusedView != null) {
                        lastFocusedView.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });

        btnUninstall.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    btnOpen.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismissActionPopup();
                    if (lastFocusedView != null) {
                        lastFocusedView.requestFocus();
                    }
                    return true;
                }
                return false;
            }
        });

        btnOpen.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    btnOpen.setTextColor(0xFFFFFFFF);
                } else {
                    btnOpen.setTextColor(getResources().getColor(R.color.home_text_primary));
                }
            }
        });

        btnUninstall.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    btnUninstall.setTextColor(0xFFFFFFFF);
                } else {
                    btnUninstall.setTextColor(0xFFFF5252);
                }
            }
        });

        actionPopup = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        actionPopup.setFocusable(true);
        actionPopup.setOutsideTouchable(true);
        actionPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                actionPopup = null;
            }
        });

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();

        int[] location = new int[2];
        anchorView.getLocationInWindow(location);
        int anchorX = location[0];
        int anchorY = location[1];
        int anchorWidth = anchorView.getWidth();
        int anchorHeight = anchorView.getHeight();

        int x = anchorX + (anchorWidth - popupWidth) / 2;
        int y = anchorY + anchorHeight + 10;

        View rootView = getView();
        if (rootView != null) {
            int screenWidth = rootView.getWidth();
            if (x < 20) x = 20;
            if (x + popupWidth > screenWidth - 20) {
                x = screenWidth - popupWidth - 20;
            }
        }

        final View finalAnchorView = anchorView;
        actionPopup.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y);

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (actionPopup != null && actionPopup.isShowing()) {
                    btnOpen.requestFocus();
                }
            }
        }, 50);
    }

    private void dismissActionPopup() {
        if (actionPopup != null && actionPopup.isShowing()) {
            actionPopup.dismiss();
        }
        actionPopup = null;
    }

    private void onOpenApp(InstalledApp app) {
        Context context = getContext();
        if (context == null || app == null) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (intent == null) {
                ToastUtil.showShort(context, "打开失败");
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            ToastUtil.showShort(context, "打开失败");
        }
    }

    private void onUninstallApp(InstalledApp app) {
        Context context = getContext();
        if (context == null || app == null) return;
        try {
            String packageName = app.getPackageName();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                uninstallWithPackageInstaller(context, packageName);
            } else {
                Uri packageUri = Uri.parse("package:" + packageName);
                if (startIntent(new Intent(Intent.ACTION_DELETE, packageUri))) {
                    return;
                } else if (startIntent(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri))) {
                    return;
                } else {
                    ToastUtil.showShort(context, "请在设置中卸载");
                    openAppDetails(packageUri);
                }
            }
        } catch (Exception e) {
            ToastUtil.showShort(context, "卸载失败");
        }
    }

    private void uninstallWithPackageInstaller(Context context, String packageName) {
        try {
            android.content.pm.PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            
            Intent intent = new Intent(context, UninstallReceiver.class);
            intent.putExtra("packageName", packageName);
            
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT :
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                    context, 0, intent, flags);
            
            packageInstaller.uninstall(packageName, pendingIntent.getIntentSender());
        } catch (Exception e) {
            ToastUtil.showShort(context, "卸载失败: " + e.getMessage());
            Uri packageUri = Uri.parse("package:" + packageName);
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
