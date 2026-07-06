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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
        btnDownload = view.findViewById(R.id.btnDownload);

        initCodeInputs();
        initCustomKeyboard();
        updateCodeCursor();
        keyboardVisible = false;
        animatingKeyboard = false;
        updateDownloadButton(false);
        updateCodeBoxBackgrounds();

        // 默认让第一个输入框获得焦点但不弹出键盘，给用户提示感
        View firstCode = codeViews[0];
        firstCode.post(new Runnable() {
            @Override
            public void run() {
                firstCode.requestFocus();
            }
        });

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitCode();
            }
        });

        btnDownload.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    btnDownload.setTextColor(getResources().getColor(R.color.white));
                } else {
                    btnDownload.setTextColor(0xFF4CAF50);
                }
            }
        });

        btnDownload.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == 0 && (keyCode == 23 || keyCode == 66)) {
                    submitCode();
                    return true;
                }
                return false;
            }
        });
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
            codeViews[i].setCursorVisible(false);

            codeViews[i].setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    updateCodeBoxBackgrounds();
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
                    showCustomKeyboardWithAnimation();
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
            // 返回/退格键：删除当前或上一个输入框的内容
            if (codeViews[index].getText().length() > 0) {
                codeViews[index].setText((CharSequence) null);
                return true;
            } else if (index > 0) {
                codeViews[index - 1].requestFocus();
                codeViews[index - 1].setText((CharSequence) null);
                return true;
            } else {
                // 已经在第一个框且为空，如果键盘显示则收起键盘
                if (keyboardVisible) {
                    hideCustomKeyboardWithAnimation();
                    return true;
                }
                return false;
            }
        } else if (keyCode == 23 || keyCode == 66) {
            // OK/确定键：弹出键盘，或提交
            if (getCurrentCode().length() == 4) {
                submitCode();
            } else {
                showCustomKeyboardWithAnimation();
                focusKeyboard();
            }
            return true;
        }
        return false;
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

        int keySize = getResources().getDimensionPixelSize(R.dimen.dp48);
        LinearLayout.LayoutParams wideParams = new LinearLayout.LayoutParams(
                keySize,
                keySize);
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
        int keySize = getResources().getDimensionPixelSize(R.dimen.dp36);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(keySize, keySize);
        int keyMargin = dpToPx(4);
        params.leftMargin = keyMargin;
        params.rightMargin = keyMargin;
        keyView.setLayoutParams(params);
        keyView.setBackgroundResource(R.drawable.selector_key);
        keyView.setClickable(true);
        keyView.setFocusable(true);
        keyView.setFocusableInTouchMode(true);
        keyView.setGravity(17);
        keyView.setSingleLine(true);
        keyView.setText(label);
        keyView.setTextColor(getResources().getColor(R.color.home_text_primary));
        keyView.setTextSize(0, getResources().getDimension(R.dimen.sp14));
        keyView.setTag(action);

        keyView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    keyView.setTextColor(getResources().getColor(R.color.accent));
                } else {
                    keyView.setTextColor(getResources().getColor(R.color.home_text_primary));
                }
            }
        });

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
                    v.performClick();
                    return true;
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
            if (getCurrentCode().length() == 4) {
                submitCode();
            }
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
        updateCodeBoxBackgrounds();
        updateDownloadButton(true);
        focusKeyboardOkIfCodeFull();
    }

    private void deletePreviousCode() {
        int index = getFocusedCodeIndex();
        if (index < 0) {
            index = currentCodeIndex;
        }
        if (index >= 4 || index < 0) {
            index = getLastFilledCodeIndex();
        }
        if (index < 0) {
            // 已经全部清空，收起键盘
            if (keyboardVisible) {
                hideCustomKeyboardWithAnimation();
            }
            return;
        }

        if (codeViews[index].getText().length() > 0) {
            codeViews[index].setText((CharSequence) null);
            currentCodeIndex = index;
            updateCodeCursor();
            updateCodeBoxBackgrounds();
            updateDownloadButton(true);

            if (getCurrentCode().length() == 0 && keyboardVisible) {
                hideCustomKeyboardWithAnimation();
            }
            return;
        }

        if (index > 0) {
            codeViews[index - 1].setText((CharSequence) null);
            currentCodeIndex = index - 1;
            updateCodeCursor();
            updateCodeBoxBackgrounds();
            updateDownloadButton(true);

            if (getCurrentCode().length() == 0 && keyboardVisible) {
                hideCustomKeyboardWithAnimation();
            }
        } else {
            if (keyboardVisible) {
                hideCustomKeyboardWithAnimation();
            }
        }
    }

    private void clearCode() {
        changingText = true;
        for (EditText editText : codeViews) {
            editText.setText((CharSequence) null);
        }
        changingText = false;
        currentCodeIndex = 0;
        updateCodeCursor();
        updateCodeBoxBackgrounds();
        updateDownloadButton(true);
    }

    private void updateCodeCursor() {
        if (codeViews == null) return;

        int firstEmptyCodeIndex = getFirstEmptyCodeIndex();
        if (firstEmptyCodeIndex < 0) {
            firstEmptyCodeIndex = 3;
        }
        currentCodeIndex = firstEmptyCodeIndex;

        for (int i = 0; i < codeViews.length; i++) {
            codeViews[i].setCursorVisible(false);
        }
    }

    private void updateDownloadButton(boolean animate) {
        if (btnDownload == null) return;
        boolean full = getCurrentCode().length() == 4;
        if (full && btnDownload.getVisibility() != View.VISIBLE) {
            btnDownload.setVisibility(View.VISIBLE);
            if (animate) {
                btnDownload.startAnimation(AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_in));
            }
        } else if (!full && btnDownload.getVisibility() == View.VISIBLE) {
            if (animate) {
                Animation fadeOut = AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_out);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (btnDownload != null) {
                            btnDownload.setVisibility(View.INVISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
                btnDownload.startAnimation(fadeOut);
            } else {
                btnDownload.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void updateCodeBoxBackgrounds() {
        for (int i = 0; i < codeViews.length; i++) {
            if (codeViews[i].hasFocus()) {
                codeViews[i].setBackgroundResource(R.drawable.bg_code_digit_active);
            } else {
                codeViews[i].setBackgroundResource(R.drawable.bg_code_digit);
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

        updateDownloadButton(true);

        if (index < codeViews.length - 1) {
            codeViews[index + 1].requestFocus();
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
                HttpURLConnection conn = null;
                try {
                    String urlStr = CoreData.HTTP_BASE_URL + "/api/codes/" + token;
                    URL url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
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
                        final List<PasswordApp> apps = parsePasswordApps(root);
                        if (apps != null && !apps.isEmpty()) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    openAppList(token);
                                }
                            });
                        } else {
                            showShortOnMain("未找到应用");
                        }
                    } else {
                        showShortOnMain("验证失败");
                    }
                } catch (final Exception e) {
                    showShortOnMain("网络错误，请检查网络连接");
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    private void openAppDetail(String code) {
        if (getActivity() == null) return;
        AppDetailFragment detailFragment = AppDetailFragment.newInstance(code);
        getActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack(null)
                .commit();
    }

    private void openAppList(String code) {
        if (getActivity() == null) return;
        AppListFragment fragment = AppListFragment.newInstance(code);
        getActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    private List<PasswordApp> parsePasswordApps(JSONObject root) {
        if (root == null) return Collections.emptyList();

        try {
            String type = root.optString("type", "single");
            List<PasswordApp> result = new ArrayList<>();

            if ("merged".equals(type)) {
                JSONArray apps = root.optJSONArray("apps");
                if (apps == null || apps.length() == 0) return Collections.emptyList();
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject item = apps.optJSONObject(i);
                    PasswordApp app = parseAppItem(item);
                    if (app != null) {
                        result.add(app);
                    }
                }
            } else {
                JSONObject item = root.optJSONObject("app");
                PasswordApp app = parseAppItem(item);
                if (app != null) {
                    result.add(app);
                }
            }

            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private PasswordApp parseAppItem(JSONObject item) {
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
                "",
                0.0f
        );
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
        return layoutKeyboard != null && layoutKeyboard.getVisibility() == View.VISIBLE;
    }

    private void showCustomKeyboardWithAnimation() {
        if (layoutKeyboard == null || keyboardVisible || animatingKeyboard) return;
        animatingKeyboard = true;
        layoutKeyboard.setVisibility(View.VISIBLE);
        Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_bottom);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                keyboardVisible = true;
                animatingKeyboard = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        layoutKeyboard.startAnimation(anim);
    }

    private void hideCustomKeyboardWithAnimation() {
        if (layoutKeyboard == null || !keyboardVisible || animatingKeyboard) return;
        animatingKeyboard = true;
        Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_out_bottom);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                if (layoutKeyboard != null) {
                    layoutKeyboard.setVisibility(View.GONE);
                }
                keyboardVisible = false;
                animatingKeyboard = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
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
        
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return true;
            }
        });
    }
}
