package com.xinghe.helper.fragments;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;
import com.xinghe.helper.coredata.CoreData;
import com.xinghe.helper.model.PasswordApp;
import com.xinghe.helper.util.ToastUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class InstallFragment extends Fragment {

    private static final int CODE_LENGTH = 4;
    private static final String KEY_ACTION_BACKSPACE = "backspace";
    private static final String KEY_ACTION_CLEAR = "clear";
    private static final String KEY_ACTION_OK = "ok";

    private boolean changingText;
    private EditText[] codeViews;
    private int currentCodeIndex;
    private View keyboardFirstKey;
    private View keyboardOkKey;
    private LinearLayout layoutKeyboard;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService requestExecutor = Executors.newCachedThreadPool();
    private Future<?> requestFuture;
    private View rootView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_install, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.rootView = view;

        codeViews = new EditText[]{
                view.findViewById(R.id.etCode0),
                view.findViewById(R.id.etCode1),
                view.findViewById(R.id.etCode2),
                view.findViewById(R.id.etCode3)
        };
        layoutKeyboard = view.findViewById(R.id.layoutKeyboard);

        initCodeInputs();
        initCustomKeyboard();
        currentCodeIndex = 0;
        showCustomKeyboard();
        updateCodeCursor();
        focusKeyboard();
        focusKeyboardLater();
    }

    @Override
    public void onDestroyView() {
        cancelRequest();
        mainHandler.removeCallbacksAndMessages(null);
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        requestExecutor.shutdownNow();
        super.onDestroy();
    }

    private void initCodeInputs() {
        InputFilter filter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                StringBuilder builder = new StringBuilder();
                for (int i = start; i < end; i++) {
                    char c = Character.toUpperCase(source.charAt(i));
                    if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                        builder.append(c);
                    }
                }
                return builder.toString();
            }
        };

        for (int i = 0; i < codeViews.length; i++) {
            final int index = i;
            codeViews[i].setFilters(new InputFilter[]{filter, new InputFilter.LengthFilter(1)});
            codeViews[i].setSelectAllOnFocus(true);
            disableSystemKeyboard(codeViews[i]);
            codeViews[i].setFocusable(false);
            codeViews[i].setFocusableInTouchMode(false);
            codeViews[i].setClickable(false);
            codeViews[i].setLongClickable(false);
            codeViews[i].setCursorVisible(false);

            codeViews[i].setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        currentCodeIndex = index;
                        ((EditText) v).selectAll();
                    }
                }
            });

            codeViews[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentCodeIndex = index;
                }
            });

            codeViews[i].setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == 0) {
                        return handleKey(index, keyCode);
                    }
                    return false;
                }
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
        if (keyCode == 67) {
            if (codeViews[index].getText().length() > 0) {
                codeViews[index].setText((CharSequence) null);
                return true;
            } else if (index <= 0) {
                return false;
            } else {
                codeViews[index - 1].requestFocus();
                codeViews[index - 1].setText((CharSequence) null);
                return true;
            }
        } else if (keyCode != 23 && keyCode != 66) {
            return false;
        } else {
            if (isCustomKeyboardVisible()) {
                submitCode();
            } else {
                showCustomKeyboard();
            }
            return true;
        }
    }

    private void initCustomKeyboard() {
        if (layoutKeyboard == null) return;

        keyboardFirstKey = null;
        keyboardOkKey = null;
        layoutKeyboard.removeAllViews();

        addKeyboardRow(getResources().getStringArray(R.array.password_keyboard_row1));
        addKeyboardRow(getResources().getStringArray(R.array.password_keyboard_row2));

        addActionRow();
    }

    private void addActionRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(17);
        row.setOrientation(0);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, -2);
        rowParams.topMargin = getResources().getDimensionPixelSize(R.dimen.dp8);
        layoutKeyboard.addView(row, rowParams);

        TextView clearBtn = createKeyboardKey(getString(R.string.password_key_clear));
        TextView backspaceBtn = createKeyboardKey(getString(R.string.password_key_backspace));
        TextView okBtn = createKeyboardKey(getString(R.string.password_key_ok));

        LinearLayout.LayoutParams wideParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.dp72),
                getResources().getDimensionPixelSize(R.dimen.dp40));
        int keyMargin = dpToPx(4);
        wideParams.leftMargin = keyMargin;
        wideParams.rightMargin = keyMargin;
        clearBtn.setLayoutParams(wideParams);
        backspaceBtn.setLayoutParams(wideParams);
        okBtn.setLayoutParams(wideParams);

        row.addView(clearBtn);
        row.addView(backspaceBtn);
        row.addView(okBtn);
    }

    private void addKeyboardRow(String[] labels) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(17);
        row.setOrientation(0);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, -2);
        rowParams.topMargin = getResources().getDimensionPixelSize(R.dimen.dp8);
        layoutKeyboard.addView(row, rowParams);

        for (String label : labels) {
            TextView keyView = createKeyboardKey(label);
            row.addView(keyView);
        }
    }

    private TextView createKeyboardKey(String label) {
        TextView keyView = new TextView(getContext());
        String action = getKeyboardAction(label);
        int keyWidth = getResources().getDimensionPixelSize(R.dimen.dp40);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(keyWidth, getResources().getDimensionPixelSize(R.dimen.dp40));
        int keyMargin = dpToPx(4);
        params.leftMargin = keyMargin;
        params.rightMargin = keyMargin;
        keyView.setLayoutParams(params);
        keyView.setBackgroundResource(R.drawable.bg_action_button);
        keyView.setClickable(true);
        keyView.setFocusable(true);
        keyView.setFocusableInTouchMode(true);
        keyView.setGravity(17);
        keyView.setSingleLine(true);
        keyView.setText(label);
        keyView.setTextColor(getResources().getColor(R.color.home_text_primary));
        keyView.setTextSize(0, getResources().getDimension(R.dimen.sp14));
        keyView.setTag(action);

        keyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleKeyboardKey((TextView) v);
            }
        });

        keyView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != 0) {
                    return false;
                }
                if (keyCode == 67) {
                    deleteCodeFromKeyboard();
                    return true;
                } else if (keyCode == 23 || keyCode == 66) {
                    if (v.getTag() != null) {
                        v.performClick();
                        return true;
                    } else if (getCurrentCode().length() < 4) {
                        v.performClick();
                        return true;
                    } else {
                        submitCodeFromKeyboard();
                        return true;
                    }
                }
                return false;
            }
        });

        if (keyboardFirstKey == null) {
            keyboardFirstKey = keyView;
        }
        if (KEY_ACTION_OK.equals(action)) {
            keyboardOkKey = keyView;
        }

        return keyView;
    }

    private int dpToPx(int value) {
        return Math.round(TypedValue.applyDimension(1, value, getResources().getDisplayMetrics()));
    }

    private String getKeyboardAction(String label) {
        if (getString(R.string.password_key_backspace).equals(label)) {
            return KEY_ACTION_BACKSPACE;
        }
        if (getString(R.string.password_key_clear).equals(label)) {
            return KEY_ACTION_CLEAR;
        }
        if (getString(R.string.password_key_ok).equals(label)) {
            return KEY_ACTION_OK;
        }
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
            submitCode();
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
        if (index < 0) {
            index = 3;
        }

        changingText = true;
        codeViews[index].setText(value.substring(0, 1).toUpperCase(Locale.US));
        codeViews[index].setSelection(codeViews[index].length());
        changingText = false;

        if (index < 3) {
            currentCodeIndex = index + 1;
        } else {
            currentCodeIndex = index;
        }

        updateCodeCursor();
        focusKeyboardOkIfCodeFull();
    }

    private void deletePreviousCode() {
        int index = getFocusedCodeIndex();
        if (index < 0 && ((index = currentCodeIndex) >= 4 || codeViews[index].getText().length() == 0)) {
            index = getLastFilledCodeIndex();
        }
        if (index < 0) return;

        if (codeViews[index].getText().length() > 0) {
            codeViews[index].setText((CharSequence) null);
            currentCodeIndex = index;
            updateCodeCursor();
            return;
        }

        if (index > 0) {
            codeViews[index - 1].setText((CharSequence) null);
            currentCodeIndex = index - 1;
        }
        updateCodeCursor();
    }

    private void clearCode() {
        changingText = true;
        for (EditText editText : codeViews) {
            editText.setText((CharSequence) null);
        }
        changingText = false;
        currentCodeIndex = 0;
        updateCodeCursor();
    }

    private void updateCodeCursor() {
        if (codeViews == null) return;

        int firstEmptyCodeIndex = getFirstEmptyCodeIndex();
        if (firstEmptyCodeIndex < 0) {
            firstEmptyCodeIndex = 3;
        }
        currentCodeIndex = firstEmptyCodeIndex;

        for (int i = 0; i < codeViews.length; i++) {
            boolean isSelected = i == firstEmptyCodeIndex;
            codeViews[i].setSelected(isSelected);
            codeViews[i].setCursorVisible(false);

            if (isSelected && codeViews[i].getText().length() == 0) {
                codeViews[i].setHint("|");
            } else {
                codeViews[i].setHint((CharSequence) null);
            }
        }
    }

    public void deleteCodeFromKeyboard() {
        deletePreviousCode();
    }

    public void submitCodeFromKeyboard() {
        submitCode();
    }

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

        if (index < codeViews.length - 1) {
            codeViews[index + 1].requestFocus();
        } else {
            submitCode();
        }
    }

    public String getCurrentCode() {
        StringBuilder builder = new StringBuilder();
        for (EditText editText : codeViews) {
            builder.append(editText.getText().toString());
        }
        return builder.toString();
    }

    private void submitCode() {
        String code = getCurrentCode();
        if (code.length() < 4) {
            if (getContext() != null) {
                ToastUtil.showShort(getContext(), R.string.password_empty);
            }
        } else if (getContext() != null) {
            makeRequest(code);
        }
    }

    private void makeRequest(final String token) {
        final Context context = getContext();
        if (context == null) return;

        cancelRequest();
        CoreData.tokenResultState = 0;

        showShortOnMain("验证中...");

        requestFuture = requestExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlStr = CoreData.HTTP_BASE_URL + "/api/code/" + token;
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

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

                        JSONObject root = new JSONObject(response.toString());
                        if (root.optBoolean("success", false)) {
                            List<PasswordApp> apps = parsePasswordApps(root);
                            if (apps != null && !apps.isEmpty()) {
                                final PasswordApp app = apps.get(0);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        downloadAndInstall(app);
                                    }
                                });
                            } else {
                                showShortOnMain("未找到应用");
                            }
                        } else {
                            showShortOnMain(root.optString("message", "验证失败"));
                        }
                    } else {
                        showShortOnMain("服务器错误: " + responseCode);
                    }
                    conn.disconnect();
                } catch (final Exception e) {
                    showShortOnMain("网络错误: " + e.getMessage());
                }
            }
        });
    }

    private void downloadAndInstall(final PasswordApp app) {
        final Context context = getContext();
        if (context == null || app == null) return;

        showShortOnMain("开始下载: " + app.getName());

        requestExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String downloadUrl = app.getDownloadUrl();
                    if (!downloadUrl.startsWith("http")) {
                        downloadUrl = CoreData.HTTP_BASE_URL + downloadUrl;
                    }

                    URL url = new URL(downloadUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);

                    java.io.File outputDir = context.getExternalCacheDir();
                    if (outputDir == null) {
                        outputDir = context.getCacheDir();
                    }
                    java.io.File apkFile = new java.io.File(outputDir, app.getName() + ".apk");

                    java.io.InputStream inputStream = conn.getInputStream();
                    java.io.FileOutputStream outputStream = new java.io.FileOutputStream(apkFile);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    outputStream.close();
                    inputStream.close();
                    conn.disconnect();

                    if (apkFile.exists() && apkFile.length() > 0) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showShortOnMain("下载完成，准备安装");
                                com.xinghe.helper.util.ApkInstallUtil.installApk(context, apkFile);
                            }
                        });
                    } else {
                        showShortOnMain("下载失败");
                    }

                } catch (final Exception e) {
                    showShortOnMain("下载错误: " + e.getMessage());
                }
            }
        });
    }

    private List<PasswordApp> parsePasswordApps(JSONObject root) {
        if (root == null) return Collections.emptyList();

        try {
            JSONObject data = root.optJSONObject("data");
            if (data == null) return Collections.emptyList();

            JSONArray apps = data.optJSONArray("apps");
            if (apps == null || apps.length() == 0) return Collections.emptyList();

            List<PasswordApp> result = new ArrayList<>();
            for (int i = 0; i < apps.length(); i++) {
                JSONObject item = apps.optJSONObject(i);
                if (item != null) {
                    PasswordApp app = new PasswordApp(
                            item.optLong("app_id"),
                            item.optString("name"),
                            item.optString("package_name"),
                            item.optString("version_name"),
                            item.optLong("version_code"),
                            item.optInt("min_android_api"),
                            item.optString("download_url"),
                            item.optString("md5"),
                            item.optLong("size"),
                            item.optString("icon_url"),
                            item.optLong("icon_size"),
                            item.optString("description"),
                            item.optString("category"),
                            (float) item.optDouble("rating")
                    );
                    result.add(app);
                }
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void cancelRequest() {
        if (requestFuture != null) {
            requestFuture.cancel(true);
            requestFuture = null;
        }
    }

    private void showShortOnMain(final String message) {
        if (message == null || message.length() == 0) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getContext() != null) {
                    ToastUtil.showShort(getContext(), message);
                }
            }
        });
    }

    private void focusKeyboard() {
        if (keyboardFirstKey != null) {
            keyboardFirstKey.requestFocus();
        }
    }

    private void focusKeyboardLater() {
        if (keyboardFirstKey != null) {
            keyboardFirstKey.post(new Runnable() {
                @Override
                public void run() {
                    focusKeyboard();
                }
            });
        }
    }

    private void focusKeyboardOkIfCodeFull() {
        if (getFirstEmptyCodeIndex() < 0 && keyboardOkKey != null) {
            keyboardOkKey.requestFocus();
        }
    }

    private int getFocusedCodeIndex() {
        for (int i = 0; i < codeViews.length; i++) {
            if (codeViews[i].hasFocus()) {
                return i;
            }
        }
        return -1;
    }

    private int getFirstEmptyCodeIndex() {
        for (int i = 0; i < codeViews.length; i++) {
            if (codeViews[i].getText().length() == 0) {
                return i;
            }
        }
        return -1;
    }

    private int getLastFilledCodeIndex() {
        for (int i = codeViews.length - 1; i >= 0; i--) {
            if (codeViews[i].getText().length() > 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isCustomKeyboardVisible() {
        return layoutKeyboard != null && layoutKeyboard.getVisibility() == 0;
    }

    private void showCustomKeyboard() {
        if (layoutKeyboard != null) {
            layoutKeyboard.setVisibility(0);
        }
    }

    private void hideCustomKeyboard() {
        if (layoutKeyboard != null) {
            layoutKeyboard.setVisibility(8);
        }
    }

    private void disableSystemKeyboard(EditText editText) {
        editText.setRawInputType(1);
        if (Build.VERSION.SDK_INT >= 21) {
            editText.setShowSoftInputOnFocus(false);
            return;
        }
        try {
            java.lang.reflect.Method method = EditText.class.getMethod("setShowSoftInputOnFocus", Boolean.TYPE);
            method.setAccessible(true);
            method.invoke(editText, false);
        } catch (Exception e) {
            editText.setInputType(0);
        }
    }
}
