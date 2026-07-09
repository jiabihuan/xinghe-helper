package com.xinghe.helper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.xinghe.helper.activity.AppListActivity;
import com.xinghe.helper.activity.BasicTransNavActivity;
import com.xinghe.helper.fragments.InstallFragment;
import com.xinghe.helper.fragments.ManagerFragment;
import com.xinghe.helper.fragments.RemoteFragment;

public class MainActivity extends BasicTransNavActivity {

    private TextView navInstall;
    private TextView navRemote;
    private TextView navManager;

    private FragmentManager fragmentManager;
    private Fragment installFragment;
    private Fragment remoteFragment;
    private Fragment managerFragment;
    private Fragment currentFragment;

    private long lastBackPressTime = 0;
    private static final long BACK_PRESS_INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();

        navInstall = findViewById(R.id.nav_install);
        navRemote = findViewById(R.id.nav_remote);
        navManager = findViewById(R.id.nav_manager);

        installFragment = new InstallFragment();
        remoteFragment = new RemoteFragment();
        managerFragment = new ManagerFragment();

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

        View.OnFocusChangeListener navFocusListener = (v, hasFocus) -> {
            if (hasFocus) {
                if (v == navInstall) updateNav(0);
                else if (v == navRemote) updateNav(1);
                else if (v == navManager) updateNav(2);
            }
        };

        navInstall.setOnFocusChangeListener(navFocusListener);
        navRemote.setOnFocusChangeListener(navFocusListener);
        navManager.setOnFocusChangeListener(navFocusListener);

        updateNav(0);
        switchFragment(installFragment);
        navInstall.requestFocus();
    }

    private void switchFragment(Fragment targetFragment) {
        if (currentFragment == targetFragment) return;

        fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, targetFragment)
                .commit();
        currentFragment = targetFragment;
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
            super.onBackPressed();
        } else {
            lastBackPressTime = currentTime;
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNav(int index) {
        navInstall.setTextColor(getResources().getColor(R.color.home_text_hint));
        navRemote.setTextColor(getResources().getColor(R.color.home_text_hint));
        navManager.setTextColor(getResources().getColor(R.color.home_text_hint));

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
        }
    }
}