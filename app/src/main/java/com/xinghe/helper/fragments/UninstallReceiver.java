package com.xinghe.helper.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class UninstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = getResultCode();
        String packageName = intent.getStringExtra("packageName");
        
        if (status == android.app.Activity.RESULT_OK) {
            Toast.makeText(context, "卸载成功", Toast.LENGTH_SHORT).show();
        } else {
            String errorMsg = getResultData();
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(context, "卸载失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "卸载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
}