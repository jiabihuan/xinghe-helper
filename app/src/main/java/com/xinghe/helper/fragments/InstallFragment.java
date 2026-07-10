package com.xinghe.helper.fragments;

import android.content.Context;
import android.content.Intent;
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
import android.view.ViewParent;
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
    private View spacerTop;
    private View spacerBottom;
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
        spacerTop = view.findViewById(R.id.spacerTop);
        spacerBottom = view.findViewById(R.id.spacerBottom);

        initCodeInputs();
        initCustomKeyboard();
        updateCodeCursor();
        keyboardVisible = false;
        animatingKeyboard = false;
        updateDownloadButton(false);
        updateCodeBoxBackgrounds();
        updateKeyboardVisibility(false, false);

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
                if (event.getAction() == 0) {
                    if (keyCode == 23 || keyCode == 66) {
                        submitCode();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        focusLastKeyOfKeyboard();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        setupNavFocusDown();
        if (codeViews != null && codeViews.length > 0) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (codeViews != null && codeViews[0] != null) {
                        codeViews[0].requestFocus();
                    }
                }
            }, 100);
        }
    }

    private void setupNavFocusDown() {
        if (getActivity() == null || codeViews == null || codeViews.length == 0) return;
        View navInstall = getActivity().findViewById(R.id.nav_install);
        if (navInstall != null) {
            navInstall.setNextFocusDownId(codeViews[0].getId());
            navInstall.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (codeViews != null && codeViews[0] != null) {
                        codeViews[0].requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }
        View navRemote = getActivity().findViewById(R.id.nav_remote);
        if (navRemote != null) {
            navRemote.setNextFocusDownId(codeViews[0].getId());
        }
        View navManager = getActivity().findViewById(R.id.nav_manager);
        if (navManager != null) {
            navManager.setNextFocusDownId(codeViews[0].getId());
        }
    }

    @Override
    public void onDestroyView() {
        cancelRequest();
        mainHandler.removeCallbacksAndMessages(null);
        rootView = null;
        super.onDestroyView();
    }

    public boolean handleBackPress() {
        // 口令页返回键逻辑：删除口令，从最后一位删起
        if (getCurrentCode().length() > 0) {
            deletePreviousCode();
            return true;
        }
        // 口令为空且键盘已收起，让 Activity 处理退出提示
        if (!keyboardVisible) {
            return false;
        }
        // 口令为空但键盘还显示着，先收起键盘
        updateKeyboardVisibility(false, true);
        focusFirstCodeView();
        return true;
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
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == 67) {
            deletePreviousCode();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (getCurrentCode().length() > 0) {
                deletePreviousCode();
                return true;
            }
            if (keyboardVisible) {
                updateKeyboardVisibility(false, true);
                focusFirstCodeView();
                return true;
            }
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 23 || keyCode == 66) {
            // OK/确定键：如果输入完成则提交，否则跳到键盘
            if (getCurrentCode().length() == 4) {
                submitCode();
            } else {
                showKeyboardAndFocus();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // 下键：跳到键盘
            showKeyboardAndFocus();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // 左键：左移一个输入框
            if (index > 0) {
                codeViews[index - 1].requestFocus();
                return true;
            }
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // 右键：右移一个输入框
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

    private void addActionRow() {
    }

    private void addKeyboardRow(String[] labels, boolean isFirstRow) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(17);
        row.setOrientation(0);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, -2);
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
        TextView keyView = new TextView(getContext());
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
        keyView.setGravity(17);
        keyView.setSingleLine(true);
        keyView.setText(label);
        keyView.setTextColor(getResources().getColor(R.color.home_text_primary));
        keyView.setTextSize(0, getResources().getDimension(R.dimen.sp18));
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
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (getCurrentCode().length() > 0) {
                        deleteCodeFromKeyboard();
                        return true;
                    }
                    // 口令为空，收起键盘
                    updateKeyboardVisibility(false, true);
                    focusFirstCodeView();
                    return true;
                } else if (keyCode == 23 || keyCode == 66 || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // 下键：如果是第二行最后（退格）且有下载按钮，跳到下载按钮；否则计算下一行同列
                    ViewParent parent = v.getParent();
                    if (parent instanceof LinearLayout) {
                        LinearLayout row = (LinearLayout) parent;
                        int idx = row.indexOfChild(v);
                        ViewParent gp = row.getParent();
                        if (gp instanceof LinearLayout) {
                            LinearLayout keyboard = (LinearLayout) gp;
                            int rowIdx = keyboard.indexOfChild(row);
                            if (rowIdx < keyboard.getChildCount() - 1) {
                                // 还有下一行
                                LinearLayout nextRow = (LinearLayout) keyboard.getChildAt(rowIdx + 1);
                                if (idx < nextRow.getChildCount()) {
                                    View nextKey = nextRow.getChildAt(idx);
                                    if (nextKey != null) {
                                        nextKey.requestFocus();
                                        return true;
                                    }
                                }
                                // 列数不够，跳到最后一个
                                if (nextRow.getChildCount() > 0) {
                                    nextRow.getChildAt(nextRow.getChildCount() - 1).requestFocus();
                                    return true;
                                }
                            } else {
                                // 最后一行，检查是否跳到下载按钮
                                if (btnDownload != null && btnDownload.getVisibility() == View.VISIBLE) {
                                    btnDownload.requestFocus();
                                    return true;
                                }
                            }
                        }
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    // 上键：第一行跳到对应的输入框，否则跳到上一行同列
                    ViewParent parent = v.getParent();
                    if (parent instanceof LinearLayout) {
                        LinearLayout row = (LinearLayout) parent;
                        int idx = row.indexOfChild(v);
                        ViewParent gp = row.getParent();
                        if (gp instanceof LinearLayout) {
                            LinearLayout keyboard = (LinearLayout) gp;
                            int rowIdx = keyboard.indexOfChild(row);
                            if (rowIdx > 0) {
                                // 上一行
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
                                // 第一行，跳到对应的输入框
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
                    // 左键：上一个按键
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
                    // 右键：下一个按键
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

    private boolean isFirstInRow(View v) {
        ViewParent parent = v.getParent();
        if (parent instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) parent;
            return row.indexOfChild(v) == 0;
        }
        return false;
    }

    private boolean isLastInRow(View v) {
        ViewParent parent = v.getParent();
        if (parent instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) parent;
            return row.indexOfChild(v) == row.getChildCount() - 1;
        }
        return false;
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
        Object action = keyView.getTag();
        if (KEY_ACTION_BACKSPACE.equals(action)) {
            deletePreviousCode();
            // 如果删除后还有内容，保持焦点在退格键上；如果清空了，焦点回到输入框
            if (getCurrentCode().length() > 0 && keyboardVisible) {
                keyView.requestFocus();
            }
        } else if (KEY_ACTION_CLEAR.equals(action)) {
            clearCode();
            // 清空后焦点回到第一个输入框，不要留在键盘按钮上（防止闪烁）
            focusFirstCodeView();
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

        // 输入第一个字符时显示键盘
        if (getCurrentCode().length() == 0) {
            updateKeyboardVisibility(true, true);
        }

        // 总是从第一个空格开始输入
        int index = getFirstEmptyCodeIndex();
        if (index < 0) {
            return;
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
        int lastIndex = getLastFilledCodeIndex();
        if (lastIndex < 0) {
            focusFirstCodeView();
            return;
        }

        codeViews[lastIndex].setText((CharSequence) null);
        currentCodeIndex = lastIndex;
        updateCodeCursor();
        updateCodeBoxBackgrounds();
        updateDownloadButton(true);

        codeViews[lastIndex].requestFocus();

        // 删除最后一个字符后隐藏键盘
        if (getCurrentCode().length() == 0) {
            updateKeyboardVisibility(false, true);
        }
    }

    private void focusFirstCodeView() {
        if (codeViews != null && codeViews.length > 0 && codeViews[0] != null) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (codeViews != null && codeViews[0] != null) {
                        codeViews[0].requestFocus();
                    }
                }
            }, 50);
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
        // 清空后隐藏键盘
        updateKeyboardVisibility(false, true);
        if (codeViews != null && codeViews.length > 0) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (codeViews != null && codeViews[0] != null) {
                        codeViews[0].requestFocus();
                    }
                }
            }, 50);
        }
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

    public boolean onBackPressed() {
        if (getCurrentCode().length() > 0) {
            deletePreviousCode();
            return true;
        }
        return false;
    }

    private void submitCode() {
        if (codeViews == null || getContext() == null || getActivity() == null) return;
        String code = getCurrentCode();
        if (code.length() < 4) {
            ToastUtil.showShort(getContext(), R.string.password_empty);
        } else {
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
                    JSONObject root = null;
                    
                    String multiUrl = CoreData.HTTP_BASE_URL + "/api/codes/multi/" + token;
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
                        String singleUrl = CoreData.HTTP_BASE_URL + "/api/codes/single/" + token;
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
                        showShortOnMain("口令不存在，请检查后重试");
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
        Intent intent = new Intent(getActivity(), com.xinghe.helper.activity.AppListActivity.class);
        intent.putExtra("code", code);
        startActivity(intent);
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

    private void showKeyboardAndFocus() {
        if (layoutKeyboard != null && layoutKeyboard.getVisibility() != View.VISIBLE) {
            updateKeyboardVisibility(true, true);
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (keyboardFirstKey != null) {
                    keyboardFirstKey.requestFocus();
                }
            }
        }, 200);
    }

    private void updateKeyboardVisibility(boolean show, boolean animate) {
        if (layoutKeyboard == null) return;
        boolean isVisible = layoutKeyboard.getVisibility() == View.VISIBLE;
        if (show == isVisible) return;

        if (show) {
            layoutKeyboard.setVisibility(View.VISIBLE);
            keyboardVisible = true;
            // 键盘显示时，调整上下间距权重，让内容整体上移
            if (spacerTop != null && spacerBottom != null) {
                LinearLayout.LayoutParams topParams = (LinearLayout.LayoutParams) spacerTop.getLayoutParams();
                topParams.weight = 0.4f;
                spacerTop.setLayoutParams(topParams);
                LinearLayout.LayoutParams bottomParams = (LinearLayout.LayoutParams) spacerBottom.getLayoutParams();
                bottomParams.weight = 0.6f;
                spacerBottom.setLayoutParams(bottomParams);
            }
        } else {
            layoutKeyboard.setVisibility(View.GONE);
            keyboardVisible = false;
            // 键盘隐藏时，上下间距权重相等，内容垂直居中
            if (spacerTop != null && spacerBottom != null) {
                LinearLayout.LayoutParams topParams = (LinearLayout.LayoutParams) spacerTop.getLayoutParams();
                topParams.weight = 1f;
                spacerTop.setLayoutParams(topParams);
                LinearLayout.LayoutParams bottomParams = (LinearLayout.LayoutParams) spacerBottom.getLayoutParams();
                bottomParams.weight = 1f;
                spacerBottom.setLayoutParams(bottomParams);
            }
        }
    }

    private void focusKeyboard() {
        showKeyboardAndFocus();
    }

    private void focusLastKeyOfKeyboard() {
        if (layoutKeyboard == null || layoutKeyboard.getChildCount() < 2) return;
        View lastRow = layoutKeyboard.getChildAt(layoutKeyboard.getChildCount() - 1);
        if (lastRow instanceof LinearLayout) {
            LinearLayout row = (LinearLayout) lastRow;
            if (row.getChildCount() > 0) {
                View lastKey = row.getChildAt(row.getChildCount() - 1);
                if (lastKey != null) {
                    lastKey.requestFocus();
                }
            }
        }
    }

    private void focusKeyboardOkIfCodeFull() {
        if (getFirstEmptyCodeIndex() < 0 && btnDownload != null && btnDownload.getVisibility() == View.VISIBLE) {
            btnDownload.requestFocus();
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
