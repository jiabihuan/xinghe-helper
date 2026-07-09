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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xinghe.helper.R;
import com.xinghe.helper.model.InstalledApp;
import com.xinghe.helper.util.ToastUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ManagerFragment extends Fragment {

    private AppManagerAdapter adapter;
    private ExecutorService appLoadExecutor;
    private Future<?> appLoadFuture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RecyclerView recyclerApps;
    private TextView tvState;
    private TextView tvSelectedCount;
    private TextView btnUninstall;

    private final Set<String> selectedPackages = new HashSet<>();
    private final List<InstalledApp> apps = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manager, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerApps = view.findViewById(R.id.recyclerApps);
        tvState = view.findViewById(R.id.tvState);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        btnUninstall = view.findViewById(R.id.btnUninstall);

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 6);
        recyclerApps.setLayoutManager(layoutManager);
        recyclerApps.setItemAnimator(null);
        int spacing = getResources().getDimensionPixelSize(R.dimen.dp12);
        recyclerApps.addItemDecoration(new GridSpacingItemDecoration(6, spacing, true));
        adapter = new AppManagerAdapter();
        recyclerApps.setAdapter(adapter);

        btnUninstall.setOnClickListener(v -> uninstallSelected());
        btnUninstall.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) btnUninstall.setTextColor(getResources().getColor(R.color.white));
            else btnUninstall.setTextColor(0xFF4CAF50);
        });
        btnUninstall.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusLastApp();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        updateSelectedCount();
        loadApps();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (recyclerApps != null && adapter != null) {
            loadApps();
        }
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
                final List<InstalledApp> loaded = queryInstalledApps(appContext);
                if (!Thread.currentThread().isInterrupted()) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isAdded() && recyclerApps != null && adapter != null && tvState != null) {
                                showApps(loaded);
                            }
                        }
                    });
                }
            }
        });
    }

    private static List<InstalledApp> queryInstalledApps(Context context) {
        List<InstalledApp> result = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> applicationInfos = packageManager.getInstalledApplications(0);
        String selfPackage = context.getPackageName();
        for (ApplicationInfo info : applicationInfos) {
            if (Thread.currentThread().isInterrupted()) {
                return result;
            }
            if (!selfPackage.equals(info.packageName)) {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(info.packageName);
                if (launchIntent != null) {
                    result.add(new InstalledApp(
                            info.loadLabel(packageManager).toString(),
                            info.packageName,
                            info.loadIcon(packageManager)));
                }
            }
        }
        Collections.sort(result, new Comparator<InstalledApp>() {
            @Override
            public int compare(InstalledApp left, InstalledApp right) {
                return left.getAppName().compareToIgnoreCase(right.getAppName());
            }
        });
        return result;
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

    private void showApps(List<InstalledApp> loaded) {
        apps.clear();
        apps.addAll(loaded);
        selectedPackages.clear();
        adapter.notifyDataSetChanged();
        tvState.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE);
        tvState.setText(loaded.isEmpty() ? R.string.empty_apps : R.string.app_list_loading);
        recyclerApps.setVisibility(loaded.isEmpty() ? View.GONE : View.VISIBLE);
        updateSelectedCount();
        if (!loaded.isEmpty()) {
            recyclerApps.scrollToPosition(0);
            recyclerApps.post(() -> {
                GridLayoutManager lm = (GridLayoutManager) recyclerApps.getLayoutManager();
                if (lm != null) {
                    View first = lm.findViewByPosition(0);
                    if (first != null) {
                        View focusTarget = first.findViewById(R.id.layoutItem);
                        if (focusTarget != null) focusTarget.requestFocus();
                        else first.requestFocus();
                    }
                }
            });
        }
    }

    private void updateSelectedCount() {
        int count = selectedPackages.size();
        tvSelectedCount.setText("已选 " + count + " 个");
        btnUninstall.setEnabled(count > 0);
        btnUninstall.setAlpha(count > 0 ? 1.0f : 0.5f);
    }

    private void uninstallSelected() {
        if (selectedPackages.isEmpty()) {
            ToastUtil.showShort(getContext(), "请先选择应用");
            return;
        }
        List<InstalledApp> toUninstall = new ArrayList<>();
        for (InstalledApp app : apps) {
            if (selectedPackages.contains(app.getPackageName())) {
                toUninstall.add(app);
            }
        }
        if (toUninstall.isEmpty()) return;
        uninstallApp(toUninstall.get(0));
    }

    private void uninstallApp(InstalledApp app) {
        Context context = getContext();
        if (context == null || app == null) return;
        try {
            String packageName = app.getPackageName();
            Uri packageUri = Uri.parse("package:" + packageName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                intent.setData(packageUri);
                if (startIntent(intent)) return;
            }

            Intent deleteIntent = new Intent(Intent.ACTION_DELETE);
            deleteIntent.setData(packageUri);
            if (startIntent(deleteIntent)) return;

            ToastUtil.showShort(context, "请在设置中卸载");
            openAppDetails(packageUri);
        } catch (Exception e) {
            ToastUtil.showShort(context, "卸载失败");
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

    private void focusLastApp() {
        if (recyclerApps == null || adapter == null || adapter.getItemCount() == 0) return;
        int lastPos = adapter.getItemCount() - 1;
        recyclerApps.scrollToPosition(lastPos);
        recyclerApps.post(() -> {
            GridLayoutManager lm = (GridLayoutManager) recyclerApps.getLayoutManager();
            if (lm != null) {
                View last = lm.findViewByPosition(lastPos);
                if (last != null) {
                    View focusTarget = last.findViewById(R.id.layoutItem);
                    if (focusTarget != null) focusTarget.requestFocus();
                    else last.requestFocus();
                }
            }
        });
    }

    private class AppManagerAdapter extends RecyclerView.Adapter<AppManagerAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_manager, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            InstalledApp app = apps.get(position);
            holder.ivIcon.setImageDrawable(app.getIcon());
            holder.tvName.setText(app.getAppName());

            boolean isSelected = selectedPackages.contains(app.getPackageName());
            holder.tvCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            holder.layoutItem.setOnClickListener(v -> {
                toggleSelection(app);
                notifyItemChanged(holder.getAdapterPosition());
            });

            holder.layoutItem.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;
                    GridLayoutManager lm = (GridLayoutManager) recyclerApps.getLayoutManager();
                    if (lm == null) return false;
                    int span = lm.getSpanCount();

                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        toggleSelection(app);
                        notifyItemChanged(pos);
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (pos < getItemCount() - 1 && (pos + 1) % span != 0) {
                            moveFocusTo(pos + 1, lm);
                        }
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (pos > 0 && pos % span != 0) moveFocusTo(pos - 1, lm);
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (pos >= span) moveFocusTo(pos - span, lm);
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int belowPos = pos + span;
                        if (belowPos < getItemCount()) moveFocusTo(belowPos, lm);
                        else if (btnUninstall != null && btnUninstall.isEnabled()) btnUninstall.requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }

        private void toggleSelection(InstalledApp app) {
            if (selectedPackages.contains(app.getPackageName())) {
                selectedPackages.remove(app.getPackageName());
            } else {
                selectedPackages.add(app.getPackageName());
            }
            updateSelectedCount();
        }

        private void moveFocusTo(int targetPos, GridLayoutManager lm) {
            View targetView = lm.findViewByPosition(targetPos);
            if (targetView != null) {
                View focusTarget = targetView.findViewById(R.id.layoutItem);
                if (focusTarget != null) focusTarget.requestFocus();
                else targetView.requestFocus();
            } else {
                recyclerApps.scrollToPosition(targetPos);
                recyclerApps.post(() -> {
                    View v = lm.findViewByPosition(targetPos);
                    if (v != null) {
                        View ft = v.findViewById(R.id.layoutItem);
                        if (ft != null) ft.requestFocus();
                        else v.requestFocus();
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvCheck;
            View layoutItem;

            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvName = itemView.findViewById(R.id.tvName);
                tvCheck = itemView.findViewById(R.id.tvCheck);
                layoutItem = itemView.findViewById(R.id.layoutItem);
            }
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
