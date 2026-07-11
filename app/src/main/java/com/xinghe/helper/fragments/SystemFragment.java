package com.xinghe.helper.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;
import com.xinghe.helper.util.SystemInfoUtil;

import java.util.Locale;

public class SystemFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_system, container, false);

        bindInfo(view);

        return view;
    }

    private void bindInfo(View view) {
        addInfo(view, "设备品牌", SystemInfoUtil.getDeviceBrand());
        addInfo(view, "设备型号", SystemInfoUtil.getDeviceModel());
        addInfo(view, "制造商", SystemInfoUtil.getDeviceManufacturer());
        addInfo(view, "产品名称", SystemInfoUtil.getProductName());

        addSectionTitle(view, R.id.tvSectionOs, "操作系统");
        addInfo(view, "Android版本", SystemInfoUtil.getAndroidVersion());
        addInfo(view, "API级别", String.valueOf(SystemInfoUtil.getAndroidSdk()));
        addInfo(view, "构建ID", SystemInfoUtil.getBuildId());
        addInfo(view, "构建版本", SystemInfoUtil.getBuildNumber());
        addInfo(view, "安全补丁", SystemInfoUtil.getSecurityPatch());

        addSectionTitle(view, R.id.tvSectionScreen, "屏幕信息");
        addInfo(view, "分辨率", SystemInfoUtil.getScreenResolution(getContext()));
        addInfo(view, "屏幕密度", SystemInfoUtil.getScreenDensity(getContext()));
        addInfo(view, "DPI", SystemInfoUtil.getScreenDensityDpi(getContext()));

        addSectionTitle(view, R.id.tvSectionHardware, "硬件信息");
        addInfo(view, "处理器", SystemInfoUtil.getCpuInfo());
        addInfo(view, "CPU架构", SystemInfoUtil.getCpuAbi());
        addInfo(view, "主板", SystemInfoUtil.getBoard());
        addInfo(view, "硬件", SystemInfoUtil.getHardware());
        addInfo(view, "引导程序", SystemInfoUtil.getBootloader());

        addSectionTitle(view, R.id.tvSectionMemory, "内存信息");
        addInfo(view, "总内存", SystemInfoUtil.getTotalMemory());
        addInfo(view, "可用内存", SystemInfoUtil.getAvailableMemory());

        addSectionTitle(view, R.id.tvSectionSystem, "系统信息");
        addInfo(view, "内核版本", SystemInfoUtil.getKernelVersion());
        addInfo(view, "设备序列号", SystemInfoUtil.getSerialNumber());
        addInfo(view, "时区", SystemInfoUtil.getTimeZone());
        addInfo(view, "语言", SystemInfoUtil.getLocale());
    }

    private void addSectionTitle(View view, int resId, String title) {
        TextView tv = view.findViewById(resId);
        if (tv != null) {
            tv.setText(title);
        }
    }

    private void addInfo(View view, String label, String value) {
        int labelId = getResources().getIdentifier("tv_" + label.toLowerCase(Locale.getDefault()).replace(" ", "_").replace("/", "_"), "id", getContext().getPackageName());
        int valueId = getResources().getIdentifier("tv_" + label.toLowerCase(Locale.getDefault()).replace(" ", "_").replace("/", "_") + "_value", "id", getContext().getPackageName());
        
        TextView tvLabel = view.findViewById(labelId);
        TextView tvValue = view.findViewById(valueId);
        
        if (tvLabel != null) tvLabel.setText(label);
        if (tvValue != null) tvValue.setText(value != null ? value : "未知");
    }

    public boolean handleBackPress() {
        return false;
    }
}
