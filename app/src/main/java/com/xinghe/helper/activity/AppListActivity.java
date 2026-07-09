package com.xinghe.helper.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xinghe.helper.R;
import com.xinghe.helper.coredata.CoreData;
import com.xinghe.helper.model.PasswordApp;
import com.xinghe.helper.util.DownloadManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppListActivity extends AppCompatActivity {

    private String code;
    private List<PasswordApp> allApps = new ArrayList<>();
    private List<PasswordApp> filteredApps = new ArrayList<>();
    private List<CategoryInfo> categories = new ArrayList<>();
    private int currentCategoryIndex = 0;
    private Set<Long> selectedAppIds = new HashSet<>();

    private TextView tvCodeInfo;
    private LinearLayout categoryTabs;
    private HorizontalScrollView categoryScroll;
    private RecyclerView appRecyclerView;
    private TextView tvSelectedCount;
    private TextView btnDownload;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private AppAdapter adapter;
    private final ConcurrentHashMap<String, Bitmap> iconCache = new ConcurrentHashMap<>();

    private View downloadPopupView;
    private LinearLayout downloadsContainer;
    private android.widget.PopupWindow downloadPopup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        code = getIntent().getStringExtra("code");
        if (code == null) code = "";

        tvCodeInfo = findViewById(R.id.tvCodeInfo);
        categoryTabs = findViewById(R.id.categoryTabs);
        categoryScroll = findViewById(R.id.categoryScroll);
        appRecyclerView = findViewById(R.id.appRecyclerView);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnDownload = findViewById(R.id.btnDownload);

        tvCodeInfo.setText("口令: " + code);

        btnDownload.setOnClickListener(v -> {
            List<PasswordApp> selectedApps = getSelectedApps();
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, "请先选择应用", Toast.LENGTH_SHORT).show();
                return;
            }
            startDownloads(selectedApps);
        });

        btnDownload.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnDownload.setTextColor(getResources().getColor(R.color.white));
            } else {
                btnDownload.setTextColor(0xFF4CAF50);
            }
        });

        btnDownload.setOnKeyListener((v, keyCode, event) -> {
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

        GridLayoutManager layoutManager = new GridLayoutManager(this, 6);
        appRecyclerView.setLayoutManager(layoutManager);
        appRecyclerView.setItemAnimator(null);
        int spacing = getResources().getDimensionPixelSize(R.dimen.dp8);
        appRecyclerView.addItemDecoration(new GridSpacingItemDecoration(6, spacing, true));
        adapter = new AppAdapter();
        appRecyclerView.setAdapter(adapter);

        loadAppList();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (downloadPopup != null && downloadPopup.isShowing()) {
                dismissDownloadPopup();
                return true;
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        dismissDownloadPopup();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadAppList() {
        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject root = null;

                String multiUrl = CoreData.HTTP_BASE_URL + "/api/codes/multi/" + code;
                URL url = new URL(multiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    root = new JSONObject(response.toString());
                }
                conn.disconnect();
                conn = null;

                if (root == null) {
                    String singleUrl = CoreData.HTTP_BASE_URL + "/api/codes/single/" + code;
                    url = new URL(singleUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        root = new JSONObject(response.toString());
                    }
                }

                if (root != null) {
                    String type = root.optString("type", "single");

                    if ("single".equals(type)) {
                        JSONObject appJson = root.getJSONObject("app");
                        PasswordApp app = parseApp(appJson);
                        allApps.add(app);
                        selectedAppIds.add(app.getAppId());
                    } else {
                        JSONArray appsArray = root.getJSONArray("apps");
                        for (int i = 0; i < appsArray.length(); i++) {
                            JSONObject appJson = appsArray.getJSONObject(i);
                            allApps.add(parseApp(appJson));
                        }

                        JSONArray catsArray = root.optJSONArray("categories");
                        if (catsArray != null && catsArray.length() > 0) {
                            for (int i = 0; i < catsArray.length(); i++) {
                                JSONObject cat = catsArray.getJSONObject(i);
                                categories.add(new CategoryInfo(
                                        cat.optLong("id"),
                                        cat.optString("name")
                                ));
                            }
                        }
                    }

                    mainHandler.post(this::setupUI);
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(AppListActivity.this, "口令不存在", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            } catch (final Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(AppListActivity.this, "网络错误，请检查网络连接", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private PasswordApp parseApp(JSONObject item) {
        if (item == null) return null;
        return new PasswordApp(
                item.optLong("id"),
                item.optString("name"),
                item.optString("package_name"),
                item.optString("version_name"),
                item.optLong("version_code"),
                21,
                item.optString("download_url"),
                "",
                item.optLong("apk_size"),
                item.optString("icon_url"),
                0,
                item.optString("description"),
                item.optString("category_name", ""),
                0.0f
        );
    }

    private void setupUI() {
        if (allApps.size() == 1) {
            categoryTabs.setVisibility(View.GONE);
            filteredApps.addAll(allApps);
        } else {
            setupCategoryTabs();
            filterByCategory(0);
        }

        updateSelectedCount();
        adapter.notifyDataSetChanged();

        if (allApps.size() == 1) {
            btnDownload.setText("立即下载");
        }

        mainHandler.postDelayed(() -> {
            if (adapter != null && adapter.getItemCount() > 0 && appRecyclerView != null) {
                appRecyclerView.scrollToPosition(0);
                appRecyclerView.post(() -> {
                    GridLayoutManager lm = (GridLayoutManager) appRecyclerView.getLayoutManager();
                    if (lm != null) {
                        View first = lm.findViewByPosition(0);
                        if (first != null) {
                            View focusTarget = first.findViewById(R.id.layoutItem);
                            if (focusTarget != null) {
                                focusTarget.requestFocus();
                            } else {
                                first.requestFocus();
                            }
                        }
                    }
                });
            } else if (categoryTabs != null && categoryTabs.getChildCount() > 0) {
                categoryTabs.getChildAt(0).requestFocus();
            }
        }, 200);
    }

    private void setupCategoryTabs() {
        categoryTabs.removeAllViews();

        TextView allTab = createCategoryTab("全部", 0);
        allTab.setSelected(true);
        categoryTabs.addView(allTab);

        for (int i = 0; i < categories.size(); i++) {
            final int index = i + 1;
            CategoryInfo cat = categories.get(i);
            TextView tab = createCategoryTab(cat.name, index);
            categoryTabs.addView(tab);
        }
    }

    private TextView createCategoryTab(String name, final int index) {
        TextView tab = new TextView(this);
        tab.setText(name);
        tab.setTextColor(getResources().getColor(R.color.home_text_primary));
        tab.setTextSize(16);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(24, 10, 24, 10);
        tab.setBackgroundResource(R.drawable.selector_category_tab);
        tab.setFocusable(true);
        tab.setFocusableInTouchMode(true);
        tab.setClickable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 16, 0);
        tab.setLayoutParams(params);

        tab.setOnClickListener(v -> {
            filterByCategory(index);
            tab.requestFocus();
        });

        tab.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    filterByCategory(index);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int nextIndex = index + 1;
                    if (nextIndex < categoryTabs.getChildCount()) {
                        View nextTab = categoryTabs.getChildAt(nextIndex);
                        if (nextTab != null) {
                            nextTab.requestFocus();
                            categoryScroll.smoothScrollBy(nextTab.getLeft() - categoryScroll.getScrollX(), 0);
                        }
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    int prevIndex = index - 1;
                    if (prevIndex >= 0) {
                        View prevTab = categoryTabs.getChildAt(prevIndex);
                        if (prevTab != null) {
                            prevTab.requestFocus();
                            categoryScroll.smoothScrollBy(prevTab.getLeft() - categoryScroll.getScrollX(), 0);
                        }
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusFirstApp();
                    return true;
                }
            }
            return false;
        });

        tab.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tab.setTextColor(getResources().getColor(R.color.white));
                v.post(() -> {
                    int scrollX = categoryScroll.getScrollX();
                    int tabLeft = v.getLeft();
                    int tabRight = v.getRight();
                    int visibleWidth = categoryScroll.getWidth();
                    if (tabLeft < scrollX) {
                        categoryScroll.scrollBy(tabLeft - scrollX - 20, 0);
                    } else if (tabRight > scrollX + visibleWidth) {
                        categoryScroll.scrollBy(tabRight - scrollX - visibleWidth + 20, 0);
                    }
                });
            } else {
                if (index == currentCategoryIndex) {
                    tab.setTextColor(getResources().getColor(R.color.white));
                } else {
                    tab.setTextColor(getResources().getColor(R.color.home_text_primary));
                }
            }
        });

        return tab;
    }

    private void filterByCategory(int categoryIndex) {
        currentCategoryIndex = categoryIndex;

        for (int i = 0; i < categoryTabs.getChildCount(); i++) {
            View child = categoryTabs.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setSelected(i == categoryIndex);
                if (i == categoryIndex) {
                    ((TextView) child).setTextColor(getResources().getColor(R.color.white));
                } else {
                    ((TextView) child).setTextColor(getResources().getColor(R.color.home_text_primary));
                }
            }
        }

        filteredApps.clear();
        if (categoryIndex == 0) {
            filteredApps.addAll(allApps);
        } else {
            CategoryInfo cat = categories.get(categoryIndex - 1);
            for (PasswordApp app : allApps) {
                if (app.getCategory() != null && app.getCategory().equals(cat.name)) {
                    filteredApps.add(app);
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void focusFirstApp() {
        if (appRecyclerView == null || adapter == null || adapter.getItemCount() == 0) return;
        appRecyclerView.scrollToPosition(0);
        appRecyclerView.post(() -> {
            GridLayoutManager lm = (GridLayoutManager) appRecyclerView.getLayoutManager();
            if (lm != null) {
                View first = lm.findViewByPosition(0);
                if (first != null) {
                    View focusTarget = first.findViewById(R.id.layoutItem);
                    if (focusTarget != null) {
                        focusTarget.requestFocus();
                    } else {
                        first.requestFocus();
                    }
                }
            }
        });
    }

    private void focusLastApp() {
        if (appRecyclerView == null || adapter == null || adapter.getItemCount() == 0) return;
        int lastPos = adapter.getItemCount() - 1;
        appRecyclerView.scrollToPosition(lastPos);
        appRecyclerView.post(() -> {
            GridLayoutManager lm = (GridLayoutManager) appRecyclerView.getLayoutManager();
            if (lm != null) {
                View last = lm.findViewByPosition(lastPos);
                if (last != null) {
                    View focusTarget = last.findViewById(R.id.layoutItem);
                    if (focusTarget != null) {
                        focusTarget.requestFocus();
                    } else {
                        last.requestFocus();
                    }
                }
            }
        });
    }

    private List<PasswordApp> getSelectedApps() {
        List<PasswordApp> result = new ArrayList<>();
        for (PasswordApp app : allApps) {
            if (selectedAppIds.contains(app.getAppId())) {
                result.add(app);
            }
        }
        return result;
    }

    private void updateSelectedCount() {
        int count = selectedAppIds.size();
        tvSelectedCount.setText("已选 " + count + " 个");
        btnDownload.setEnabled(count > 0);
        btnDownload.setAlpha(count > 0 ? 1.0f : 0.5f);
    }

    private void startDownloads(List<PasswordApp> apps) {
        showDownloadPopup(apps);

        DownloadManager dm = DownloadManager.getInstance();
        dm.clearTasks();
        dm.setListener(new DownloadManager.DownloadListener() {
            @Override
            public void onProgress(int index, int percent, long downloaded, long total) {
                updateProgressItem(index, percent, downloaded, total, false);
            }

            @Override
            public void onComplete(int index, java.io.File apkFile) {
                updateProgressItem(index, 100, 0, 0, true);
            }

            @Override
            public void onError(int index, String error) {
                updateProgressItemError(index, "下载失败");
            }

            @Override
            public void onCancelled(int index) {
                updateProgressItemCancelled(index);
            }

            @Override
            public void onAllComplete() {
                mainHandler.postDelayed(() -> dismissDownloadPopup(), 2000);
            }
        });

        dm.addTasks(apps, this);
    }

    private void showDownloadPopup(List<PasswordApp> apps) {
        downloadPopupView = LayoutInflater.from(this).inflate(R.layout.dialog_download_big, null);
        downloadsContainer = downloadPopupView.findViewById(R.id.downloadsContainer);
        TextView tvTitlePopup = downloadPopupView.findViewById(R.id.tvTitle);
        TextView tvSubtitle = downloadPopupView.findViewById(R.id.tvSubtitle);
        TextView btnBackground = downloadPopupView.findViewById(R.id.btnBackground);

        if (apps.size() > 1) {
            tvTitlePopup.setText("正在下载");
            tvSubtitle.setText("共 " + apps.size() + " 个应用");
        } else {
            tvTitlePopup.setText("正在下载");
            tvSubtitle.setText(apps.get(0).getName());
        }

        for (int i = 0; i < apps.size(); i++) {
            final int index = i;
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_download_big, downloadsContainer, false);
            TextView tvName = itemView.findViewById(R.id.tvAppName);
            TextView tvSizeInfo = itemView.findViewById(R.id.tvSizeInfo);
            TextView tvPercent = itemView.findViewById(R.id.tvPercent);
            ProgressBar progressBar = itemView.findViewById(R.id.progressBar);
            TextView tvStatus = itemView.findViewById(R.id.tvStatus);
            final TextView btnCancel = itemView.findViewById(R.id.btnCancel);

            tvName.setText(apps.get(i).getName());
            tvSizeInfo.setText(formatSize(apps.get(i).getSize()));
            tvPercent.setText("0%");
            progressBar.setProgress(0);
            tvStatus.setText("等待中...");

            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DownloadManager dm = DownloadManager.getInstance();
                    dm.cancelTask(index);
                    btnCancel.setVisibility(View.GONE);
                }
            });

            btnCancel.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == 23 || keyCode == 66) {
                        v.performClick();
                        return true;
                    }
                    return false;
                }
            });

            btnCancel.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        btnCancel.setTextColor(0xFFFFFFFF);
                    } else {
                        btnCancel.setTextColor(0xFFFF6B6B);
                    }
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
            }
            downloadsContainer.addView(itemView, params);
        }

        btnBackground.setOnClickListener(v -> dismissDownloadPopup());

        downloadPopup = new android.widget.PopupWindow(downloadPopupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        downloadPopup.setOutsideTouchable(false);
        downloadPopup.setFocusable(true);

        downloadPopupView.setFocusableInTouchMode(true);
        downloadPopupView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                    dismissDownloadPopup();
                    return true;
                }
                return false;
            }
        });

        View root = getWindow().getDecorView().findViewById(android.R.id.content);
        root.post(() -> {
            if (downloadPopup != null && downloadPopupView != null) {
                downloadPopup.showAtLocation(root, Gravity.CENTER, 0, 0);
                mainHandler.postDelayed(() -> {
                    if (downloadPopupView != null) {
                        View btn = downloadPopupView.findViewById(R.id.btnBackground);
                        if (btn != null) {
                            btn.requestFocus();
                        }
                    }
                }, 100);
            }
        });
    }

    private void updateProgressItem(int index, int percent, long downloaded, long total, boolean completed) {
        if (downloadsContainer == null) return;
        if (index < 0 || index >= downloadsContainer.getChildCount()) return;
        View itemView = downloadsContainer.getChildAt(index);
        if (itemView == null) return;

        TextView tvPercent = itemView.findViewById(R.id.tvPercent);
        ProgressBar progressBar = itemView.findViewById(R.id.progressBar);
        TextView tvSizeInfo = itemView.findViewById(R.id.tvSizeInfo);
        TextView tvStatus = itemView.findViewById(R.id.tvStatus);
        TextView btnCancel = itemView.findViewById(R.id.btnCancel);

        if (completed) {
            tvPercent.setText("100%");
            progressBar.setProgress(100);
            tvStatus.setText("下载完成");
            tvStatus.setTextColor(getResources().getColor(R.color.success));
            if (btnCancel != null) {
                btnCancel.setVisibility(View.GONE);
            }
        } else {
            tvPercent.setText(percent + "%");
            progressBar.setProgress(percent);
            if (total > 0) {
                float mbDownloaded = downloaded / (1024f * 1024f);
                float mbTotal = total / (1024f * 1024f);
                tvSizeInfo.setText(String.format(Locale.getDefault(), "%.1f / %.1f MB", mbDownloaded, mbTotal));
            }
            tvStatus.setText("下载中...");
        }
    }

    private void updateProgressItemError(int index, String error) {
        if (downloadsContainer == null) return;
        if (index < 0 || index >= downloadsContainer.getChildCount()) return;
        View itemView = downloadsContainer.getChildAt(index);
        if (itemView == null) return;

        TextView tvStatus = itemView.findViewById(R.id.tvStatus);
        TextView btnCancel = itemView.findViewById(R.id.btnCancel);
        tvStatus.setText(error);
        tvStatus.setTextColor(getResources().getColor(R.color.error));
        if (btnCancel != null) {
            btnCancel.setVisibility(View.GONE);
        }
    }

    private void updateProgressItemCancelled(int index) {
        if (downloadsContainer == null) return;
        if (index < 0 || index >= downloadsContainer.getChildCount()) return;
        View itemView = downloadsContainer.getChildAt(index);
        if (itemView == null) return;

        TextView tvStatus = itemView.findViewById(R.id.tvStatus);
        TextView btnCancel = itemView.findViewById(R.id.btnCancel);
        ProgressBar progressBar = itemView.findViewById(R.id.progressBar);
        tvStatus.setText("已取消");
        tvStatus.setTextColor(getResources().getColor(R.color.home_text_hint));
        if (btnCancel != null) {
            btnCancel.setVisibility(View.GONE);
        }
        if (progressBar != null) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF888888));
        }
    }

    private void dismissDownloadPopup() {
        if (downloadPopup != null && downloadPopup.isShowing()) {
            downloadPopup.dismiss();
        }
        downloadPopup = null;
        downloadPopupView = null;
        downloadsContainer = null;
    }

    private String formatSize(long size) {
        if (size <= 0) return "未知大小";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    private static class CategoryInfo {
        long id;
        String name;

        CategoryInfo(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_grid_select, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PasswordApp app = filteredApps.get(position);
            holder.tvName.setText(app.getName());
            holder.tvSize.setText(formatSize(app.getSize()));

            boolean isSelected = selectedAppIds.contains(app.getAppId());
            holder.tvCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            String iconUrl = app.getIconUrl();
            if (iconUrl != null && iconUrl.length() > 0) {
                loadIcon(iconUrl, holder.ivIcon);
            } else {
                holder.ivIcon.setImageResource(android.R.color.transparent);
            }

            holder.layoutItem.setOnClickListener(v -> {
                long appId = app.getAppId();
                if (selectedAppIds.contains(appId)) {
                    selectedAppIds.remove(appId);
                } else {
                    selectedAppIds.add(appId);
                }
                updateSelectedCount();
                notifyItemChanged(holder.getAdapterPosition());
            });

            holder.layoutItem.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    GridLayoutManager lm = (GridLayoutManager) appRecyclerView.getLayoutManager();
                    if (lm == null) return false;
                    int span = lm.getSpanCount();

                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        long appId = app.getAppId();
                        if (selectedAppIds.contains(appId)) {
                            selectedAppIds.remove(appId);
                        } else {
                            selectedAppIds.add(appId);
                        }
                        updateSelectedCount();
                        notifyItemChanged(pos);
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (pos < getItemCount() - 1 && (pos + 1) % span != 0) {
                            int nextPos = pos + 1;
                            moveFocusTo(nextPos, lm);
                        }
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (pos > 0 && pos % span != 0) {
                            int prevPos = pos - 1;
                            moveFocusTo(prevPos, lm);
                        } else if (pos < span) {
                            if (categoryTabs != null && categoryTabs.getChildCount() > 0
                                    && currentCategoryIndex < categoryTabs.getChildCount()) {
                                View tab = categoryTabs.getChildAt(currentCategoryIndex);
                                if (tab != null) {
                                    tab.requestFocus();
                                }
                            }
                        }
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (pos >= span) {
                            int abovePos = pos - span;
                            moveFocusTo(abovePos, lm);
                        } else {
                            if (categoryTabs != null && categoryTabs.getChildCount() > 0
                                    && currentCategoryIndex < categoryTabs.getChildCount()) {
                                View tab = categoryTabs.getChildAt(currentCategoryIndex);
                                if (tab != null) {
                                    tab.requestFocus();
                                }
                            }
                        }
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int belowPos = pos + span;
                        if (belowPos < getItemCount()) {
                            moveFocusTo(belowPos, lm);
                        } else {
                            if (btnDownload != null && btnDownload.isEnabled()) {
                                btnDownload.requestFocus();
                            }
                        }
                        return true;
                    }
                }
                return false;
            });
        }

        private void moveFocusTo(int targetPos, GridLayoutManager lm) {
            View targetView = lm.findViewByPosition(targetPos);
            if (targetView != null) {
                View focusTarget = targetView.findViewById(R.id.layoutItem);
                if (focusTarget != null) {
                    focusTarget.requestFocus();
                } else {
                    targetView.requestFocus();
                }
            } else {
                appRecyclerView.scrollToPosition(targetPos);
                appRecyclerView.post(() -> {
                    View v = lm.findViewByPosition(targetPos);
                    if (v != null) {
                        View ft = v.findViewById(R.id.layoutItem);
                        if (ft != null) {
                            ft.requestFocus();
                        } else {
                            v.requestFocus();
                        }
                    }
                });
            }
        }

        private void loadIcon(String url, ImageView imageView) {
            if (url == null || url.length() == 0) return;
            Bitmap cached = iconCache.get(url);
            if (cached != null) {
                imageView.setImageBitmap(cached);
                return;
            }
            imageView.setTag(url);
            executor.submit(() -> {
                HttpURLConnection conn = null;
                try {
                    String fullUrl = url;
                    if (!url.startsWith("http")) {
                        fullUrl = CoreData.HTTP_BASE_URL + url;
                    }
                    URL u = new URL(fullUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    if (conn.getResponseCode() == 200) {
                        InputStream is = conn.getInputStream();
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        is.close();
                        if (bmp != null) {
                            iconCache.put(url, bmp);
                            mainHandler.post(() -> {
                                Object tag = imageView.getTag();
                                if (tag != null && tag.equals(url)) {
                                    imageView.setImageBitmap(bmp);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    if (conn != null) conn.disconnect();
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredApps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvSize;
            TextView tvCheck;
            View layoutItem;

            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvName = itemView.findViewById(R.id.tvName);
                tvSize = itemView.findViewById(R.id.tvSize);
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
