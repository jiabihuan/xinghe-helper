package com.xinghe.helper.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.xinghe.helper.MainActivity;
import com.xinghe.helper.R;
import com.xinghe.helper.util.SystemInfoUtil;

public class SystemFragment extends Fragment {

    private Handler mainHandler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_system, container, false);
        mainHandler = new Handler(Looper.getMainLooper());
        bindInfo(view);

        TextView btnClose = view.findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToInstall();
                }
            });

            btnClose.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    btnClose.setBackgroundResource(R.drawable.bg_dialog_button_focus);
                } else {
                    btnClose.setBackgroundResource(R.drawable.bg_dialog_button);
                }
            });

            btnClose.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 23 || keyCode == 66) {
                        v.performClick();
                        return true;
                    }
                }
                return false;
            });

            btnClose.post(() -> btnClose.requestFocus());
        }

        loadGpuInfo(view);

        return view;
    }

    private void setRow(View parent, int rowId, String label, String value) {
        View row = parent.findViewById(rowId);
        if (row != null) {
            TextView tvLabel = row.findViewById(R.id.tv_label);
            TextView tvValue = row.findViewById(R.id.tv_value);
            if (tvLabel != null) tvLabel.setText(label);
            if (tvValue != null) tvValue.setText(value != null ? value : "未知");
        }
    }

    private void bindInfo(View view) {
        // 设备信息
        setRow(view, R.id.row_brand, "设备品牌", SystemInfoUtil.getDeviceBrand());
        setRow(view, R.id.row_model, "设备型号", SystemInfoUtil.getDeviceModel());
        setRow(view, R.id.row_manufacturer, "制造商", SystemInfoUtil.getDeviceManufacturer());
        setRow(view, R.id.row_device, "设备代号", SystemInfoUtil.getDeviceName());
        setRow(view, R.id.row_serial, "序列号", SystemInfoUtil.getSerialNumber());
        setRow(view, R.id.row_hardware, "硬件平台", SystemInfoUtil.getHardware());

        // 操作系统
        setRow(view, R.id.row_android_version, "Android版本", SystemInfoUtil.getAndroidVersion());
        setRow(view, R.id.row_api_level, "API级别", String.valueOf(SystemInfoUtil.getAndroidSdk()));
        setRow(view, R.id.row_build_id, "构建ID", SystemInfoUtil.getBuildId());
        setRow(view, R.id.row_security_patch, "安全补丁", SystemInfoUtil.getSecurityPatch());
        setRow(view, R.id.row_kernel, "内核版本", SystemInfoUtil.getKernelVersion());
        setRow(view, R.id.row_system_version, "系统版本", SystemInfoUtil.getSystemVersion());

        // 硬件信息
        setRow(view, R.id.row_cpu, "处理器", SystemInfoUtil.getCpuInfo());
        setRow(view, R.id.row_cpu_cores, "CPU核心数", SystemInfoUtil.getCpuCores());
        setRow(view, R.id.row_cpu_freq, "最大频率", SystemInfoUtil.getCpuMaxFreq());
        setRow(view, R.id.row_gpu, "显卡", "加载中...");
        setRow(view, R.id.row_cpu_abi, "CPU架构", SystemInfoUtil.getCpuAbi());
        setRow(view, R.id.row_board, "主板", SystemInfoUtil.getBoard());

        // 内存 & 存储
        setRow(view, R.id.row_total_memory, "总内存", SystemInfoUtil.getTotalMemory(getContext()));
        setRow(view, R.id.row_available_memory, "可用内存", SystemInfoUtil.getAvailableMemory(getContext()));
        setRow(view, R.id.row_storage, "存储(已用/总)", SystemInfoUtil.getStorageInfo());
        setRow(view, R.id.row_screen_res, "分辨率", SystemInfoUtil.getScreenResolution(getContext()));

        // 网络 & 其他
        setRow(view, R.id.row_ip, "IP地址", SystemInfoUtil.getIpAddress());
        setRow(view, R.id.row_mac, "MAC地址", SystemInfoUtil.getMacAddress());
        setRow(view, R.id.row_bt_mac, "蓝牙MAC", SystemInfoUtil.getBluetoothMac());
        setRow(view, R.id.row_root, "ROOT状态", SystemInfoUtil.getRootStatus());
        setRow(view, R.id.row_bootloader_status, "BL状态", SystemInfoUtil.getBootloaderStatus());
        setRow(view, R.id.row_uptime, "运行时长", SystemInfoUtil.getUptime());
    }

    private void loadGpuInfo(View view) {
        new Thread(() -> {
            final String gpu = SystemInfoUtil.getGpuInfo();
            mainHandler.post(() -> setRow(view, R.id.row_gpu, "显卡", gpu));
        }).start();
    }

    public boolean handleBackPress() {
        return false;
    }
}
