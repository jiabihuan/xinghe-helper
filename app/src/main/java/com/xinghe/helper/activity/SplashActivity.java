package com.xinghe.helper.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

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
        TextView tvPrivacy = findViewById(R.id.tvPrivacy);
        TextView tvUserAgreement = findViewById(R.id.tvUserAgreement);

        btnConfirm.setOnClickListener(v -> {
            sp.edit().putBoolean("disclaimer_accepted", true).apply();
            startMainActivity();
        });

        btnCancel.setOnClickListener(v -> finish());

        tvPrivacy.setOnClickListener(v -> Toast.makeText(SplashActivity.this, "隐私政策功能开发中", Toast.LENGTH_SHORT).show());
        tvUserAgreement.setOnClickListener(v -> Toast.makeText(SplashActivity.this, "用户协议功能开发中", Toast.LENGTH_SHORT).show());

        btnConfirm.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    btnCancel.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    btnCancel.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    tvPrivacy.requestFocus();
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
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    btnConfirm.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    btnConfirm.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    tvUserAgreement.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        tvPrivacy.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    tvUserAgreement.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    tvUserAgreement.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
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

        tvUserAgreement.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    tvPrivacy.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    tvPrivacy.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
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

        btnConfirm.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnConfirm.setBackgroundResource(R.drawable.bg_dialog_button_focus);
            } else {
                btnConfirm.setBackgroundResource(R.drawable.bg_dialog_button);
            }
        });

        btnCancel.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                btnCancel.setBackgroundResource(R.drawable.bg_dialog_button_focus);
            } else {
                btnCancel.setBackgroundResource(R.drawable.bg_dialog_button);
            }
        });

        tvPrivacy.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tvPrivacy.setTextColor(0xFFFFFFFF);
            } else {
                tvPrivacy.setTextColor(0xFFFF6B6B);
            }
        });

        tvUserAgreement.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tvUserAgreement.setTextColor(0xFFFFFFFF);
            } else {
                tvUserAgreement.setTextColor(0xFFFF6B6B);
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
