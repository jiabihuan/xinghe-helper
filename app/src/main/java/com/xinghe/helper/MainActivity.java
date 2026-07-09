package com.xinghe.helper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.xinghe.helper.activity.AppListActivity;
import com.xinghe.helper.activity.AppManagerActivity;
import com.xinghe.helper.activity.BasicTransNavActivity;
import com.xinghe.helper.activity.RemoteActivity;

public class MainActivity extends BasicTransNavActivity {

    private TextView navInstall;
    private TextView navRemote;
    private TextView navManager;

    private long lastBackPressTime = 0;
    private static final long BACK_PRESS_INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navInstall = findViewById(R.id.nav_install);
        navRemote = findViewById(R.id.nav_remote);
        navManager = findViewById(R.id.nav_manager);

        navInstall.setOnClickListener(v -> {
            updateNav(0);
            Intent intent = new Intent(MainActivity.this, AppListActivity.class);
            intent.putExtra("code", "");
            startActivity(intent);
        });

        navRemote.setOnClickListener(v -> {
            updateNav(1);
            startActivity(new Intent(MainActivity.this, RemoteActivity.class));
        });

        navManager.setOnClickListener(v -> {
            updateNav(2);
            startActivity(new Intent(MainActivity.this, AppManagerActivity.class));
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
        navInstall.requestFocus();
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