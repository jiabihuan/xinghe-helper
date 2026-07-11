package com.xinghe.helper.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
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
import com.xinghe.helper.util.DensityUtil;
import com.xinghe.helper.util.DownloadManager;
import com.xinghe.helper.util.IconLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AppListActivity extends AppCompatActivity {

    private static final String KEY_ACTION_BACKSPACE = "backspace";
    private static final String KEY_ACTION_CLEAR = "clear";
    private static final String KEY_ACTION_OK = "ok";

    private boolean changingText;
    private boolean keyboardVisible;
    private boolean animatingKeyboard;
    private EditText[] codeViews;
    private int currentCodeIndex;
    private View keyboardFirstKey;
    private View keyboardOkKey;
    private LinearLayout layoutKeyboard;
    private TextView btnDownload;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService requestExecutor = Executors.newCachedThreadPool();
    private Future<?> requestFuture;

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
    private TextView btnDownloadList;

    private LruCache<String, Bitmap> iconCache;
    private final ExecutorService iconLoaderExecutor = Executors.newFixedThreadPool(8);
    private AppAdapter adapter;

    private View downloadPopupView;
    private LinearLayout downloadsContainer;
    private android.widget.PopupWindow downloadPopup;

    private LinearLayout layoutPassword;
    private LinearLayout layoutAppList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list_new);

        code = getIntent().getStringExtra("code");

        layoutPassword = findViewById(R.id.layoutPassword);
        layoutAppList = findViewById(R.id.layoutAppList);

        if (code != null && !code.isEmpty()) {
            layoutPassword.setVisibility(View.GONE);
            layoutAppList.setVisibility(View.VISIBLE);
            initAppList();
            loadAppList();
        } else {
            layoutPassword.setVisibility(View.VISIBLE);
            layoutAppList.setVisibility(View.GONE);
            initPasswordInput();
        }
    }

    private void initPasswordInput() {
        codeViews = new EditText[]{
                findViewById(R.id.etCode0),
                findViewById(R.id.etCode1),
                findViewById(R.id.etCode2),
                findViewById(R.id.etCode3)
        };
        layoutKeyboard = findViewById(R.id.layoutKeyboard);
        btnDownload = findViewById(R.id.btnDownload);

        initCodeInputs();
        initCustomKeyboard();
        updateCodeCursor();
        keyboardVisible = true;
        animatingKeyboard = false;
        updateDownloadButton(false);
        updateCodeBoxBackgrounds();

        View firstCode = codeViews[0];
        firstCode.post(() -> firstCode.requestFocus());

        btnDownload.setOnClickListener(v -> submitCode());

        btnDownload.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnDownload.setTextColor(getResources().getColor(R.color.white));
            } else {
                btnDownload.setTextColor(0xFF4CAF50);
            }
        });

        btnDownload.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    submitCode();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusLastKeyOfKeyboard();
                    return true;
                }
            }
            return false;
        });
    }

    private void initCodeInputs() {
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {
            StringBuilder builder = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = Character.toUpperCase(source.charAt(i));
                if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                    builder.append(c);
                }
            }
            return builder.toString();
        };

        for (int i = 0; i < codeViews.length; i++) {
            final int index = i;
            codeViews[i].setFilters(new InputFilter[]{filter, new InputFilter.LengthFilter(1)});
            codeViews[i].setSelectAllOnFocus(true);
            disableSystemKeyboard(codeViews[i]);
            codeViews[i].setCursorVisible(false);

            codeViews[i].setOnFocusChangeListener((v, hasFocus) -> {
                updateCodeBoxBackgrounds();
                if (hasFocus) {
                    currentCodeIndex = index;
                    ((EditText) v).selectAll();
                }
            });

            codeViews[i].setOnClickListener(v -> {
                currentCodeIndex = index;
                showCustomKeyboardWithAnimation();
            });

            codeViews[i].setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return handleKey(index, keyCode);
                }
                return false;
            });

            codeViews[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    handleTextChanged(index, s);
                }
            });
        }
    }

    private boolean handleKey(int index, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == 67) {
            deletePreviousCode();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (getCurrentCode().length() > 0) {
                deletePreviousCode();
                return true;
            }
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 23 || keyCode == 66) {
            if (getCurrentCode().length() == 4) {
                submitCode();
            } else {
                focusKeyboard();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusKeyboard();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (index > 0) {
                codeViews[index - 1].requestFocus();
                return true;
            }
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (index < 3) {
                codeViews[index + 1].requestFocus();
                return true;
            }
            return false;
        }
        return false;
    }

    private void initCustomKeyboard() {
        if (layoutKeyboard == null) return;
        keyboardFirstKey = null;
        keyboardOkKey = null;
        layoutKeyboard.removeAllViews();
        addKeyboardRow(getResources().getStringArray(R.array.password_keyboard_row1), true);
        addKeyboardRow(getResources().getStringArray(R.array.password_keyboard_row2), false);
    }

    private void addKeyboardRow(String[] labels, boolean isFirstRow) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = getResources().getDimensionPixelSize(R.dimen.dp8);
        layoutKeyboard.addView(row, rowParams);

        for (String label : labels) {
            TextView keyView = createKeyboardKey(label, false);
            row.addView(keyView);
        }

        if (isFirstRow) {
            TextView clearBtn = createKeyboardKey(getString(R.string.password_key_clear), true);
            row.addView(clearBtn);
        } else {
            TextView backspaceBtn = createKeyboardKey(getString(R.string.password_key_backspace), true);
            row.addView(backspaceBtn);
        }
    }

    private TextView createKeyboardKey(String label, boolean isAction) {
        TextView keyView = new TextView(this);
        String action = getKeyboardAction(label);
        int keySize = getResources().getDimensionPixelSize(R.dimen.dp54);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(keySize, keySize);
        int keyMargin = dpToPx(6);
        params.leftMargin = keyMargin;
        params.rightMargin = keyMargin;
        keyView.setLayoutParams(params);
        keyView.setBackgroundResource(R.drawable.selector_key);
        keyView.setClickable(true);
        keyView.setFocusable(true);
        keyView.setFocusableInTouchMode(true);
        keyView.setGravity(Gravity.CENTER);
        keyView.setSingleLine(true);
        keyView.setText(label);
        keyView.setTextColor(getResources().getColor(R.color.home_text_primary));
        keyView.setTextSize(0, getResources().getDimension(R.dimen.sp18));
        keyView.setTag(action);

        keyView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                keyView.setTextColor(getResources().getColor(R.color.accent));
            } else {
                keyView.setTextColor(getResources().getColor(R.color.home_text_primary));
            }
        });

        keyView.setOnClickListener(v -> handleKeyboardKey(keyView));

        keyView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == 67) {
                deleteCodeFromKeyboard();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (getCurrentCode().length() > 0) {
                    deleteCodeFromKeyboard();
                    return true;
                }
                return false;
            } else if (keyCode == 23 || keyCode == 66 || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                v.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                ViewParent parent = v.getParent();
                if (parent instanceof LinearLayout) {
                    LinearLayout row = (LinearLayout) parent;
                    int idx = row.indexOfChild(v);
                    ViewParent gp = row.getParent();
                    if (gp instanceof LinearLayout) {
                        LinearLayout keyboard = (LinearLayout) gp;
                        int rowIdx = keyboard.indexOfChild(row);
                        if (rowIdx < keyboard.getChildCount() - 1) {
                            LinearLayout nextRow = (LinearLayout) keyboard.getChildAt(rowIdx + 1);
                            if (idx < nextRow.getChildCount()) {
                                View nextKey = nextRow.getChildAt(idx);
                                if (nextKey != null) {
                                    nextKey.requestFocus();
                                    return true;
                                }
                            }
                            if (nextRow.getChildCount() > 0) {
                                nextRow.getChildAt(nextRow.getChildCount() - 1).requestFocus();
                                return true;
                            }
                        } else {
                            if (btnDownload != null && btnDownload.getVisibility() == View.VISIBLE) {
                                btnDownload.requestFocus();
                                return true;
                            }
                        }
                    }
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                ViewParent parent = v.getParent();
                if (parent instanceof LinearLayout) {
                    LinearLayout row = (LinearLayout) parent;
                    int idx = row.indexOfChild(v);
                    ViewParent gp = row.getParent();
                    if (gp instanceof LinearLayout) {
                        LinearLayout keyboard = (LinearLayout) gp;
                        int rowIdx = keyboard.indexOfChild(row);
                        if (rowIdx > 0) {
                            LinearLayout prevRow = (LinearLayout) keyboard.getChildAt(rowIdx - 1);
                            if (idx < prevRow.getChildCount()) {
                                View prevKey = prevRow.getChildAt(idx);
                                if (prevKey != null) {
                                    prevKey.requestFocus();
                                    return true;
                                }
                            }
                            if (prevRow.getChildCount() > 0) {
                                prevRow.getChildAt(prevRow.getChildCount() - 1).requestFocus();
                                return true;
                            }
                        } else {
                            if (codeViews != null && codeViews.length > 0) {
                                int codeIdx = Math.min(idx, codeViews.length - 1);
                                if (codeIdx < 0) codeIdx = 0;
                                codeViews[codeIdx].requestFocus();
                                return true;
                            }
                        }
                    }
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                ViewParent parent = v.getParent();
                if (parent instanceof LinearLayout) {
                    LinearLayout row = (LinearLayout) parent;
                    int idx = row.indexOfChild(v);
                    if (idx > 0) {
                        row.getChildAt(idx - 1).requestFocus();
                        return true;
                    }
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                ViewParent parent = v.getParent();
                if (parent instanceof LinearLayout) {
                    LinearLayout row = (LinearLayout) parent;
                    int idx = row.indexOfChild(v);
                    if (idx < row.getChildCount() - 1) {
                        row.getChildAt(idx + 1).requestFocus();
                        return true;
                    }
                }
                return true;
            }
            return false;
        });

        if (keyboardFirstKey == null) keyboardFirstKey = keyView;
        if (KEY_ACTION_OK.equals(action)) keyboardOkKey = keyView;

        return keyView;
    }

    private int dpToPx(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private String getKeyboardAction(String label) {
        if (getString(R.string.password_key_backspace).equals(label)) return KEY_ACTION_BACKSPACE;
        if (getString(R.string.password_key_clear).equals(label)) return KEY_ACTION_CLEAR;
        if (getString(R.string.password_key_ok).equals(label)) return KEY_ACTION_OK;
        return null;
    }

    private void handleKeyboardKey(TextView keyView) {
        keyView.requestFocus();
        Object action = keyView.getTag();
        if (KEY_ACTION_BACKSPACE.equals(action)) {
            deletePreviousCode();
            keyView.requestFocus();
        } else if (KEY_ACTION_CLEAR.equals(action)) {
            clearCode();
            keyView.requestFocus();
        } else if (KEY_ACTION_OK.equals(action)) {
            if (getCurrentCode().length() == 4) submitCode();
        } else {
            inputCodeValue(keyView.getText().toString());
        }
    }

    private void inputCodeValue(String value) {
        if (value == null || value.length() == 0) return;
        int index = currentCodeIndex;
        if (index < 0 || index >= 4 || codeViews[index].getText().length() > 0) {
            index = getFirstEmptyCodeIndex();
        }
        if (index < 0) index = 3;

        changingText = true;
        codeViews[index].setText(value.substring(0, 1).toUpperCase(Locale.US));
        codeViews[index].setSelection(codeViews[index].length());
        changingText = false;

        currentCodeIndex = index < 3 ? index + 1 : index;
        updateCodeCursor();
        updateCodeBoxBackgrounds();
        updateDownloadButton(true);
        focusKeyboardOkIfCodeFull();
    }

    private void deletePreviousCode() {
        int lastIndex = getLastFilledCodeIndex();
        if (lastIndex < 0) {
            focusFirstCodeView();
            return;
        }
        codeViews[lastIndex].setText(null);
        currentCodeIndex = lastIndex;
        updateCodeCursor();
        updateCodeBoxBackgrounds();
        updateDownloadButton(true);
        codeViews[lastIndex].requestFocus();
    }

    private void focusFirstCodeView() {
        if (codeViews != null && codeViews.length > 0 && codeViews[0] != null) {
            mainHandler.postDelayed(() -> {
                if (codeViews != null && codeViews[0] != null) {
                    codeViews[0].requestFocus();
                }
            }, 50);
        }
    }

    private void clearCode() {
        changingText = true;
        for (EditText editText : codeViews) editText.setText(null);
        changingText = false;
        currentCodeIndex = 0;
        updateCodeCursor();
        updateCodeBoxBackgrounds();
        updateDownloadButton(true);
        if (codeViews != null && codeViews.length > 0) {
            mainHandler.postDelayed(() -> {
                if (codeViews != null && codeViews[0] != null) codeViews[0].requestFocus();
            }, 50);
        }
    }

    private void updateCodeCursor() {
        if (codeViews == null) return;
        int firstEmptyCodeIndex = getFirstEmptyCodeIndex();
        if (firstEmptyCodeIndex < 0) firstEmptyCodeIndex = 3;
        currentCodeIndex = firstEmptyCodeIndex;
        for (EditText codeView : codeViews) codeView.setCursorVisible(false);
    }

    private void updateDownloadButton(boolean animate) {
        if (btnDownload == null) return;
        boolean full = getCurrentCode().length() == 4;
        if (full && btnDownload.getVisibility() != View.VISIBLE) {
            btnDownload.setVisibility(View.VISIBLE);
            if (animate) btnDownload.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        } else if (!full && btnDownload.getVisibility() == View.VISIBLE) {
            if (animate) {
                Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationEnd(Animation animation) { if (btnDownload != null) btnDownload.setVisibility(View.INVISIBLE); }
                    @Override public void onAnimationRepeat(Animation animation) {}
                });
                btnDownload.startAnimation(fadeOut);
            } else {
                btnDownload.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void updateCodeBoxBackgrounds() {
        for (int i = 0; i < codeViews.length; i++) {
            codeViews[i].setBackgroundResource(codeViews[i].hasFocus() ? R.drawable.bg_code_digit_active : R.drawable.bg_code_digit);
        }
    }

    private void deleteCodeFromKeyboard() { deletePreviousCode(); }

    private void handleTextChanged(int index, Editable editable) {
        if (changingText || editable.length() == 0) return;
        String value = editable.toString().toUpperCase(Locale.US);
        if (!value.equals(editable.toString())) {
            changingText = true;
            codeViews[index].setText(value);
            codeViews[index].setSelection(codeViews[index].length());
            changingText = false;
            return;
        }
        updateDownloadButton(true);
        if (index < codeViews.length - 1) codeViews[index + 1].requestFocus();
    }

    private String getCurrentCode() {
        StringBuilder builder = new StringBuilder();
        for (EditText editText : codeViews) builder.append(editText.getText().toString());
        return builder.toString();
    }

    private void submitCode() {
        code = getCurrentCode();
        if (code.length() < 4) {
            Toast.makeText(this, "请输入完整口令", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelRequest();
        CoreData.tokenResultState = 0;
        Toast.makeText(this, "验证中...", Toast.LENGTH_SHORT).show();

        requestFuture = requestExecutor.submit(() -> {
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
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
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
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
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
                            allApps.add(parseApp(appsArray.getJSONObject(i)));
                        }
                        JSONArray catsArray = root.optJSONArray("categories");
                        if (catsArray != null && catsArray.length() > 0) {
                            for (int i = 0; i < catsArray.length(); i++) {
                                JSONObject cat = catsArray.getJSONObject(i);
                                categories.add(new CategoryInfo(cat.optLong("id"), cat.optString("name")));
                            }
                        }
                    }
                    mainHandler.post(() -> showAppList());
                } else {
                    mainHandler.post(() -> Toast.makeText(AppListActivity.this, "口令不存在", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(AppListActivity.this, "网络错误", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
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

    private void showAppList() {
        layoutPassword.setVisibility(View.GONE);
        layoutAppList.setVisibility(View.VISIBLE);
        initAppList();
        setupUI();
    }

    private void initAppList() {
        tvCodeInfo = findViewById(R.id.tvCodeInfo);
        categoryTabs = findViewById(R.id.categoryTabs);
        categoryScroll = findViewById(R.id.categoryScroll);
        appRecyclerView = findViewById(R.id.appRecyclerView);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnDownloadList = findViewById(R.id.btnDownloadList);

        if (iconCache == null) {
            int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
            int cacheSize = maxMemory / 8;
            iconCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }
            };
        }

        tvCodeInfo.setText("口令: " + code);

        btnDownloadList.setOnClickListener(v -> {
            List<PasswordApp> selectedApps = getSelectedApps();
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, "请先选择应用", Toast.LENGTH_SHORT).show();
                return;
            }
            startDownloads(selectedApps);
        });

        btnDownloadList.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) btnDownloadList.setTextColor(getResources().getColor(R.color.white));
            else btnDownloadList.setTextColor(0xFF4CAF50);
        });

        btnDownloadList.setOnKeyListener((v, keyCode, event) -> {
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
    }

    private void loadAppList() {
        requestExecutor.submit(() -> {
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
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
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
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
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
                            allApps.add(parseApp(appsArray.getJSONObject(i)));
                        }
                        JSONArray catsArray = root.optJSONArray("categories");
                        if (catsArray != null && catsArray.length() > 0) {
                            for (int i = 0; i < catsArray.length(); i++) {
                                JSONObject cat = catsArray.getJSONObject(i);
                                categories.add(new CategoryInfo(cat.optLong("id"), cat.optString("name")));
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
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(AppListActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
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
        if (allApps.size() == 1) btnDownloadList.setText("立即下载");

        mainHandler.postDelayed(() -> {
            if (adapter != null && adapter.getItemCount() > 0 && appRecyclerView != null) {
                appRecyclerView.scrollToPosition(0);
                appRecyclerView.post(() -> {
                    GridLayoutManager lm = (GridLayoutManager) appRecyclerView.getLayoutManager();
                    if (lm != null) {
                        View first = lm.findViewByPosition(0);
                        if (first != null) {
                            View focusTarget = first.findViewById(R.id.layoutItem);
                            if (focusTarget != null) focusTarget.requestFocus();
                            else first.requestFocus();
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
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
                    if (tabLeft < scrollX) categoryScroll.scrollBy(tabLeft - scrollX - 20, 0);
                    else if (tabRight > scrollX + visibleWidth) categoryScroll.scrollBy(tabRight - scrollX - visibleWidth + 20, 0);
                });
            } else {
                if (index == currentCategoryIndex) tab.setTextColor(getResources().getColor(R.color.white));
                else tab.setTextColor(getResources().getColor(R.color.home_text_primary));
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
                if (i == categoryIndex) ((TextView) child).setTextColor(getResources().getColor(R.color.white));
                else ((TextView) child).setTextColor(getResources().getColor(R.color.home_text_primary));
            }
        }

        filteredApps.clear();
        if (categoryIndex == 0) filteredApps.addAll(allApps);
        else {
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
                    if (focusTarget != null) focusTarget.requestFocus();
                    else first.requestFocus();
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
                    if (focusTarget != null) focusTarget.requestFocus();
                    else last.requestFocus();
                }
            }
        });
    }

    private List<PasswordApp> getSelectedApps() {
        List<PasswordApp> result = new ArrayList<>();
        for (PasswordApp app : allApps) {
            if (selectedAppIds.contains(app.getAppId())) result.add(app);
        }
        return result;
    }

    private void updateSelectedCount() {
        int count = selectedAppIds.size();
        tvSelectedCount.setText("已选 " + count + " 个");
        btnDownloadList.setEnabled(count > 0);
        btnDownloadList.setAlpha(count > 0 ? 1.0f : 0.5f);
    }

    private void startDownloads(List<PasswordApp> apps) {
        showDownloadPopup(apps);
        DownloadManager dm = DownloadManager.getInstance();
        dm.clearTasks();
        dm.setListener(new DownloadManager.DownloadListener() {
            @Override public void onProgress(int index, int percent, long downloaded, long total) { updateProgressItem(index, percent, downloaded, total, false); }
            @Override public void onComplete(int index, java.io.File apkFile) { }
            @Override public void onError(int index, String error) { updateProgressItemError(index, "下载失败"); }
            @Override public void onCancelled(int index) { updateProgressItemCancelled(index); }
            @Override public void onAllComplete() { mainHandler.postDelayed(() -> dismissDownloadPopup(), 2000); }
            @Override public void onInstallStart(int index) { updateInstallStart(index); }
            @Override public void onInstallResult(int index, boolean success, String message) { updateInstallResult(index, success, message); }
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
            ImageView ivIcon = itemView.findViewById(R.id.ivAppIcon);
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

            String iconUrl = apps.get(i).getIconUrl();
            if (iconUrl != null && iconUrl.length() > 0) {
                IconLoader.getInstance().loadIcon(iconUrl, ivIcon);
            }

            btnCancel.setOnClickListener(v -> {
                DownloadManager dm = DownloadManager.getInstance();
                dm.cancelTask(index);
                btnCancel.setVisibility(View.GONE);
            });

            btnCancel.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
                return false;
            });

            btnCancel.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) btnCancel.setTextColor(0xFFFFFFFF);
                else btnCancel.setTextColor(0xFFFF6B6B);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
            downloadsContainer.addView(itemView, params);
        }

        btnBackground.setOnClickListener(v -> dismissDownloadPopup());
        btnBackground.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                v.performClick();
                return true;
            }
            return false;
        });
        btnBackground.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnBackground.setTextColor(getResources().getColor(R.color.white));
                btnBackground.setBackgroundResource(R.drawable.bg_button_focus);
            } else {
                btnBackground.setTextColor(getResources().getColor(R.color.accent));
                btnBackground.setBackgroundResource(R.drawable.bg_button);
            }
        });

        downloadPopup = new android.widget.PopupWindow(downloadPopupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        downloadPopup.setOutsideTouchable(false);
        downloadPopup.setFocusable(true);

        downloadPopupView.setFocusableInTouchMode(true);
        downloadPopupView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                dismissDownloadPopup();
                return true;
            }
            return false;
        });

        View root = getWindow().getDecorView().findViewById(android.R.id.content);
        root.post(() -> {
            if (downloadPopup != null && downloadPopupView != null) {
                downloadPopup.showAtLocation(root, Gravity.CENTER, 0, 0);
                mainHandler.postDelayed(() -> {
                    if (downloadPopupView != null) {
                        View btn = downloadPopupView.findViewById(R.id.btnBackground);
                        if (btn != null) btn.requestFocus();
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
            if (btnCancel != null) btnCancel.setVisibility(View.GONE);
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
        if (btnCancel != null) btnCancel.setVisibility(View.GONE);
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
        if (btnCancel != null) btnCancel.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF888888));
    }

    private void updateInstallStart(int index) {
        if (downloadsContainer == null) return;
        if (index < 0 || index >= downloadsContainer.getChildCount()) return;
        View itemView = downloadsContainer.getChildAt(index);
        if (itemView == null) return;

        TextView tvStatus = itemView.findViewById(R.id.tvStatus);
        TextView btnCancel = itemView.findViewById(R.id.btnCancel);
        TextView tvPercent = itemView.findViewById(R.id.tvPercent);
        ProgressBar progressBar = itemView.findViewById(R.id.progressBar);
        tvStatus.setText("正在安装...");
        tvStatus.setTextColor(getResources().getColor(R.color.accent));
        if (tvPercent != null) tvPercent.setText("安装中");
        if (progressBar != null) {
            progressBar.setIndeterminate(true);
            progressBar.setProgressTintList(getResources().getColorStateList(R.color.accent));
        }
        if (btnCancel != null) btnCancel.setVisibility(View.GONE);
    }

    private void updateInstallResult(int index, boolean success, String message) {
        if (downloadsContainer == null) return;
        if (index < 0 || index >= downloadsContainer.getChildCount()) return;
        View itemView = downloadsContainer.getChildAt(index);
        if (itemView == null) return;

        TextView tvStatus = itemView.findViewById(R.id.tvStatus);
        TextView tvPercent = itemView.findViewById(R.id.tvPercent);
        ProgressBar progressBar = itemView.findViewById(R.id.progressBar);
        if (success) {
            tvStatus.setText("已安装");
            tvStatus.setTextColor(getResources().getColor(R.color.success));
            if (tvPercent != null) tvPercent.setText("✓");
            if (progressBar != null) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
                progressBar.setProgressTintList(getResources().getColorStateList(R.color.success));
            }
        } else {
            if ("手动安装中".equals(message)) {
                tvStatus.setText("正在安装...");
                tvStatus.setTextColor(getResources().getColor(R.color.accent));
                if (tvPercent != null) tvPercent.setText("安装中");
            } else {
                tvStatus.setText("安装失败: " + message);
                tvStatus.setTextColor(getResources().getColor(R.color.error));
                if (tvPercent != null) tvPercent.setText("✗");
                if (progressBar != null) {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgressTintList(getResources().getColorStateList(R.color.error));
                }
            }
        }
    }

    private void dismissDownloadPopup() {
        if (downloadPopup != null && downloadPopup.isShowing()) downloadPopup.dismiss();
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

    private void focusKeyboard() {
        if (layoutKeyboard != null && layoutKeyboard.getVisibility() != View.VISIBLE) {
            layoutKeyboard.setVisibility(View.VISIBLE);
            keyboardVisible = true;
        }
        if (keyboardFirstKey != null) keyboardFirstKey.requestFocus();
    }

    private void focusLastKeyOfKeyboard() {
        if (layoutKeyboard == null || layoutKeyboard.getChildCount() < 2) return;
        View lastRow = layoutKeyboard.getChildAt(layoutKeyboard.getChildCount() - 1);
        if (lastRow instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) lastRow;
            if (row.getChildCount() > 0) row.getChildAt(row.getChildCount() - 1).requestFocus();
        }
    }

    private void focusKeyboardOkIfCodeFull() {
        if (getFirstEmptyCodeIndex() < 0 && btnDownload != null && btnDownload.getVisibility() == View.VISIBLE) {
            btnDownload.requestFocus();
        }
    }

    private int getFirstEmptyCodeIndex() {
        for (int i = 0; i < codeViews.length; i++) {
            if (codeViews[i].getText().length() == 0) return i;
        }
        return -1;
    }

    private int getLastFilledCodeIndex() {
        for (int i = codeViews.length - 1; i >= 0; i--) {
            if (codeViews[i].getText().length() > 0) return i;
        }
        return -1;
    }

    private void showCustomKeyboardWithAnimation() {
        if (layoutKeyboard == null || keyboardVisible || animatingKeyboard) return;
        animatingKeyboard = true;
        layoutKeyboard.setVisibility(View.VISIBLE);
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) { keyboardVisible = true; animatingKeyboard = false; }
            @Override public void onAnimationRepeat(Animation animation) {}
        });
        layoutKeyboard.startAnimation(anim);
    }

    private void disableSystemKeyboard(EditText editText) {
        editText.setInputType(0);
        editText.setRawInputType(0);
        editText.setShowSoftInputOnFocus(false);
        if (Build.VERSION.SDK_INT >= 26) {
            editText.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        editText.setOnEditorActionListener((v, actionId, event) -> true);
    }

    private void cancelRequest() {
        if (requestFuture != null) {
            requestFuture.cancel(true);
            requestFuture = null;
        }
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
        cancelRequest();
        mainHandler.removeCallbacksAndMessages(null);
        requestExecutor.shutdownNow();
        iconLoaderExecutor.shutdownNow();
        if (iconCache != null) {
            iconCache.evictAll();
            iconCache = null;
        }
        super.onDestroy();
    }

    private static class CategoryInfo {
        long id;
        String name;
        CategoryInfo(long id, String name) { this.id = id; this.name = name; }
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_grid_select, parent, false);
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
            if (iconUrl != null && iconUrl.length() > 0) loadIcon(iconUrl, holder.ivIcon);
            else holder.ivIcon.setImageResource(android.R.color.transparent);

            holder.layoutItem.setOnClickListener(v -> {
                long appId = app.getAppId();
                if (selectedAppIds.contains(appId)) selectedAppIds.remove(appId);
                else selectedAppIds.add(appId);
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
                        if (selectedAppIds.contains(appId)) selectedAppIds.remove(appId);
                        else selectedAppIds.add(appId);
                        updateSelectedCount();
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
                        else if (pos < span) {
                            if (categoryTabs != null && categoryTabs.getChildCount() > 0 && currentCategoryIndex < categoryTabs.getChildCount()) {
                                View tab = categoryTabs.getChildAt(currentCategoryIndex);
                                if (tab != null) tab.requestFocus();
                            }
                        }
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (pos >= span) moveFocusTo(pos - span, lm);
                        else {
                            if (categoryTabs != null && categoryTabs.getChildCount() > 0 && currentCategoryIndex < categoryTabs.getChildCount()) {
                                View tab = categoryTabs.getChildAt(currentCategoryIndex);
                                if (tab != null) tab.requestFocus();
                            }
                        }
                        return true;
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int belowPos = pos + span;
                        if (belowPos < getItemCount()) moveFocusTo(belowPos, lm);
                        else if (btnDownloadList != null && btnDownloadList.isEnabled()) btnDownloadList.requestFocus();
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
                if (focusTarget != null) focusTarget.requestFocus();
                else targetView.requestFocus();
            } else {
                appRecyclerView.scrollToPosition(targetPos);
                appRecyclerView.post(() -> {
                    View v = lm.findViewByPosition(targetPos);
                    if (v != null) {
                        View ft = v.findViewById(R.id.layoutItem);
                        if (ft != null) ft.requestFocus();
                        else v.requestFocus();
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
                            int reqSize = dpToPx(80);
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
                } catch (Exception e) {} finally {
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

        @Override
        public int getItemCount() { return filteredApps.size(); }

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
        public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;
                if (position < spanCount) outRect.top = spacing;
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) outRect.top = spacing;
            }
        }
    }
}