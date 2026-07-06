package com.xinghe.helper.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;

import com.xinghe.helper.R;

public class BasicTransNavActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            window.clearFlags(201326592);
            window.getDecorView().setSystemUiVisibility(1792);
            window.addFlags(Integer.MIN_VALUE);
            window.setStatusBarColor(getResources().getColor(R.color.transparent));
            window.setNavigationBarColor(getResources().getColor(R.color.transparent));
        } else if (Build.VERSION.SDK_INT >= 19) {
            getWindow().setFlags(67108864, 67108864);
        }
    }
}
