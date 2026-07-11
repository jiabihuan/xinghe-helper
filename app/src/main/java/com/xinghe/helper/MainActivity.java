package com.xinghe.helper;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.xinghe.helper.activity.BasicTransNavActivity;
import com.xinghe.helper.fragments.InstallFragment;
import com.xinghe.helper.fragments.ManagerFragment;
import com.xinghe.helper.fragments.RemoteFragment;
import com.xinghe.helper.fragments.SystemFragment;
import com.xinghe.helper.util.AdbStatusManager;

public class MainActivity extends BasicTransNavActivity {

    private TextView navInstall;
    private TextView navRemote;
    private TextView navManager;
    private TextView navSystem;

    private FragmentManager fragmentManager;
    private Fragment installFragment;
    private Fragment remoteFragment;
    private Fragment managerFragment;
    private Fragment systemFragment;
    private Fragment currentFragment;

    private long lastBackPressTime = 0;
    private static final long BACK_PRESS_INTERVAL = 2000;
    private boolean navFocused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();

        navInstall = findViewById(R.id.nav_install);
        navRemote = findViewById(R.id.nav_remote);
        navManager = findViewById(R.id.nav_manager);
        navSystem = findViewById(R.id.nav_system);

        installFragment = new InstallFragment();
        remoteFragment = new RemoteFragment();
        managerFragment = new ManagerFragment();
        systemFragment = new SystemFragment();

        navInstall.setOnClickListener(v -> {
            updateNav(0);
            switchFragment(installFragment);
        });

        navRemote.setOnClickListener(v -> {
            updateNav(1);
            switchFragment(remoteFragment);
        });

        navManager.setOnClickListener(v -> {
            updateNav(2);
            switchFragment(managerFragment);
        });

        navSystem.setOnClickListener(v -> {
            updateNav(3);
            switchFragment(systemFragment);
        });

        View.OnFocusChangeListener navFocusListener = (v, hasFocus) -> {
            if (hasFocus) {
                if (v == navInstall) updateNav(0);
                else if (v == navRemote) updateNav(1);
                else if (v == navManager) updateNav(2);
                else if (v == navSystem) updateNav(3);
            }
        };

        navInstall.setOnFocusChangeListener(navFocusListener);
        navRemote.setOnFocusChangeListener(navFocusListener);
        navManager.setOnFocusChangeListener(navFocusListener);
        navSystem.setOnFocusChangeListener(navFocusListener);

        updateNav(0);
        switchFragment(installFragment);
        navInstall.requestFocus();

        checkDisclaimer();
    }

    private void checkDisclaimer() {
        SharedPreferences sp = getSharedPreferences("xinghe_helper", MODE_PRIVATE);
        if (sp.getBoolean("disclaimer_accepted", false)) {
            checkAdbConnection();
            return;
        }
        showDisclaimerDialog();
    }

    private void showDisclaimerDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_disclaimer, null);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
        }

        TextView btnConfirm = view.findViewById(R.id.btnConfirm);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        btnConfirm.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("xinghe_helper", MODE_PRIVATE);
            sp.edit().putBoolean("disclaimer_accepted", true).apply();
            dialog.dismiss();
            checkAdbConnection();
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
        btnConfirm.requestFocus();
    }

    private void checkAdbConnection() {
        navInstall.postDelayed(() -> {
            AdbStatusManager.getInstance().checkAdbStatus(new AdbStatusManager.AdbCheckCallback() {
                @Override
                public void onAdbAvailable() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "ADB已连接，将使用静默安装模式", Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onAdbUnavailable() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "未检测到ADB权限，将使用普通安装模式", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }, 500);
    }

    private void switchFragment(Fragment targetFragment) {
        if (currentFragment == targetFragment) return;

        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }
        if (!targetFragment.isAdded()) {
            transaction.add(R.id.fragmentContainer, targetFragment);
        } else {
            transaction.show(targetFragment);
        }
        transaction.commit();
        currentFragment = targetFragment;
    }

    @Override
    public void onBackPressed() {
        View focused = getCurrentFocus();
        boolean isNavFocused = (focused == navInstall || focused == navRemote || focused == navManager || focused == navSystem);

        if (!isNavFocused) {
            if (currentFragment instanceof ManagerFragment) {
                if (((ManagerFragment) currentFragment).handleBackPress()) {
                    return;
                }
                // 应用管理：返回键回到导航栏
                focusNav();
                lastBackPressTime = 0;
                return;
            }
            if (currentFragment instanceof SystemFragment) {
                focusNav();
                lastBackPressTime = 0;
                return;
            }
            if (currentFragment instanceof InstallFragment) {
                if (((InstallFragment) currentFragment).handleBackPress()) {
                    return;
                }
                // 口令页：handleBackPress 返回 false 说明口令已空且键盘已收起
                // 直接走退出提示逻辑
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
                    super.onBackPressed();
                } else {
                    lastBackPressTime = currentTime;
                    Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            focusNav();
            lastBackPressTime = 0;
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
            super.onBackPressed();
        } else {
            lastBackPressTime = currentTime;
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
        }
    }

    private void focusNav() {
        if (currentFragment == installFragment) navInstall.requestFocus();
        else if (currentFragment == remoteFragment) navRemote.requestFocus();
        else if (currentFragment == managerFragment) navManager.requestFocus();
        else if (currentFragment == systemFragment) navSystem.requestFocus();
        else navInstall.requestFocus();
    }

    private void updateNav(int index) {
        navInstall.setTextColor(getResources().getColor(R.color.home_text_hint));
        navRemote.setTextColor(getResources().getColor(R.color.home_text_hint));
        navManager.setTextColor(getResources().getColor(R.color.home_text_hint));
        navSystem.setTextColor(getResources().getColor(R.color.home_text_hint));

        switch (index) {
            case 0:
                navInstall.setTextColor(getResources().getColor(R.color.home_text_primary));
                break;
            case 1:
                navRemote.setTextColor(getResources().getColor(R.color.home_text_primary));
                break;
            case 2:
                navManager.setTextColor(getResources().getColor(R.color.home_text_primary));
                break;
            case 3:
                navSystem.setTextColor(getResources().getColor(R.color.home_text_primary));
                break;
        }
    }
}