package com.xinghe.helper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.xinghe.helper.activity.BasicTransNavActivity;
import com.xinghe.helper.fragments.InstallFragment;
import com.xinghe.helper.fragments.ManagerFragment;
import com.xinghe.helper.fragments.RemoteFragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends BasicTransNavActivity {

    private TextView navInstall;
    private TextView navRemote;
    private TextView navManager;

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

        navInstall = findViewById(R.id.nav_install);
        navRemote = findViewById(R.id.nav_remote);
        navManager = findViewById(R.id.nav_manager);

        installFragment = new InstallFragment();
        remoteFragment = new RemoteFragment();
        managerFragment = new ManagerFragment();

        navInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFragment(installFragment);
                updateNav(0);
            }
        });

        navRemote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFragment(remoteFragment);
                updateNav(1);
            }
        });

        navManager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFragment(managerFragment);
                updateNav(2);
            }
        });

        View.OnFocusChangeListener navFocusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (v == navInstall) updateNav(0);
                    else if (v == navRemote) updateNav(1);
                    else if (v == navManager) updateNav(2);
                }
            }
        };

        navInstall.setOnFocusChangeListener(navFocusListener);
        navRemote.setOnFocusChangeListener(navFocusListener);
        navManager.setOnFocusChangeListener(navFocusListener);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, installFragment)
                    .commit();
            currentFragment = installFragment;
            updateNav(0);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment instanceof InstallFragment) {
            boolean handled = ((InstallFragment) currentFragment).onBackPressed();
            if (handled) {
                lastBackPressTime = 0;
                return;
            }
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
            super.onBackPressed();
        } else {
            lastBackPressTime = currentTime;
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchFragment(Fragment fragment) {
        if (currentFragment == fragment) return;

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (!fragment.isAdded()) {
            transaction.add(R.id.fragmentContainer, fragment);
        }
        transaction.hide(currentFragment).show(fragment).commit();
        currentFragment = fragment;
        lastBackPressTime = 0;
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
