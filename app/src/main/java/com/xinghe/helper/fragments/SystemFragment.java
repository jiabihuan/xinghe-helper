package com.xinghe.helper.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;
import com.xinghe.helper.util.SystemInfoUtil;

public class SystemFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_system, container, false);
        bindInfo(view);
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

        // 操作系统
        setRow(view, R.id.row_android_version, "Android版本", SystemInfoUtil.getAndroidVersion());
        setRow(view, R.id.row_api_level, "API级别", String.valueOf(SystemInfoUtil.getAndroidSdk()));
        setRow(view, R.id.row_build_id, "构建ID", SystemInfoUtil.getBuildId());
        setRow(view, R.id.row_security_patch, "安全补丁", SystemInfoUtil.getSecurityPatch());
        setRow(view, R.id.row_kernel, "内核版本", SystemInfoUtil.getKernelVersion());

        // 屏幕信息
        setRow(view, R.id.row_resolution, "分辨率", SystemInfoUtil.getScreenResolution(getContext()));
        setRow(view, R.id.row_dpi, "屏幕密度", SystemInfoUtil.getScreenDensityDpi(getContext()));

        // 硬件信息
        setRow(view, R.id.row_cpu, "处理器", SystemInfoUtil.getCpuInfo());
        setRow(view, R.id.row_cpu_abi, "CPU架构", SystemInfoUtil.getCpuAbi());
        setRow(view, R.id.row_cpu_cores, "CPU核心数", SystemInfoUtil.getCpuCores());
        setRow(view, R.id.row_cpu_freq, "最大频率", SystemInfoUtil.getCpuMaxFreq());
        setRow(view, R.id.row_hardware, "硬件平台", SystemInfoUtil.getHardware());
        setRow(view, R.id.row_board, "主板", SystemInfoUtil.getBoard());

        // 内存 & 存储
        setRow(view, R.id.row_total_memory, "总内存", SystemInfoUtil.getTotalMemory(getContext()));
        setRow(view, R.id.row_available_memory, "可用内存", SystemInfoUtil.getAvailableMemory(getContext()));
        setRow(view, R.id.row_storage, "存储(已用/总)", SystemInfoUtil.getStorageInfo());

        // 网络
        setRow(view, R.id.row_ip, "IP地址", SystemInfoUtil.getIpAddress());
        setRow(view, R.id.row_mac, "MAC地址", SystemInfoUtil.getMacAddress());

        // 其他
        setRow(view, R.id.row_uptime, "运行时长", SystemInfoUtil.getUptime());
        setRow(view, R.id.row_timezone, "时区", SystemInfoUtil.getTimeZone());
        setRow(view, R.id.row_locale, "语言", SystemInfoUtil.getLocale());
    }

    public boolean handleBackPress() {
        return false;
    }
}
