package com.xinghe.helper.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.MainActivity;
import com.xinghe.helper.R;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        SharedPreferences sp = getSharedPreferences("xinghe_helper", MODE_PRIVATE);
        if (sp.getBoolean("disclaimer_accepted", false)) {
            startMainActivity();
            return;
        }

        TextView btnConfirm = findViewById(R.id.btnConfirm);
        TextView btnCancel = findViewById(R.id.btnCancel);

        btnConfirm.setOnClickListener(v -> {
            sp.edit().putBoolean("disclaimer_accepted", true).apply();
            startMainActivity();
        });

        btnCancel.setOnClickListener(v -> finish());

        btnConfirm.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    btnCancel.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        btnCancel.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    btnConfirm.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        btnConfirm.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnConfirm.setScaleX(1.05f);
                btnConfirm.setScaleY(1.05f);
            } else {
                btnConfirm.setScaleX(1f);
                btnConfirm.setScaleY(1f);
            }
        });

        btnCancel.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnCancel.setScaleX(1.05f);
                btnCancel.setScaleY(1.05f);
            } else {
                btnCancel.setScaleX(1f);
                btnCancel.setScaleY(1f);
            }
        });

        btnConfirm.requestFocus();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
