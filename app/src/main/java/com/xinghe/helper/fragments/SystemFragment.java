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

    private void bindInfo(View view) {
        ((TextView) view.findViewById(R.id.tv_value_brand)).setText(SystemInfoUtil.getDeviceBrand());
        ((TextView) view.findViewById(R.id.tv_value_model)).setText(SystemInfoUtil.getDeviceModel());
        ((TextView) view.findViewById(R.id.tv_value_manufacturer)).setText(SystemInfoUtil.getDeviceManufacturer());
        ((TextView) view.findViewById(R.id.tv_value_product)).setText(SystemInfoUtil.getProductName());

        ((TextView) view.findViewById(R.id.tv_value_android_version)).setText(SystemInfoUtil.getAndroidVersion());
        ((TextView) view.findViewById(R.id.tv_value_api_level)).setText(String.valueOf(SystemInfoUtil.getAndroidSdk()));
        ((TextView) view.findViewById(R.id.tv_value_build_id)).setText(SystemInfoUtil.getBuildId());
        ((TextView) view.findViewById(R.id.tv_value_security_patch)).setText(SystemInfoUtil.getSecurityPatch());

        ((TextView) view.findViewById(R.id.tv_value_screen_resolution)).setText(SystemInfoUtil.getScreenResolution(getContext()));
        ((TextView) view.findViewById(R.id.tv_value_screen_density)).setText(SystemInfoUtil.getScreenDensity(getContext()));

        ((TextView) view.findViewById(R.id.tv_value_cpu)).setText(SystemInfoUtil.getCpuInfo());
        ((TextView) view.findViewById(R.id.tv_value_cpu_abi)).setText(SystemInfoUtil.getCpuAbi());
        ((TextView) view.findViewById(R.id.tv_value_board)).setText(SystemInfoUtil.getBoard());

        ((TextView) view.findViewById(R.id.tv_value_total_memory)).setText(SystemInfoUtil.getTotalMemory());
        ((TextView) view.findViewById(R.id.tv_value_available_memory)).setText(SystemInfoUtil.getAvailableMemory());

        ((TextView) view.findViewById(R.id.tv_value_kernel)).setText(SystemInfoUtil.getKernelVersion());
        ((TextView) view.findViewById(R.id.tv_value_serial)).setText(SystemInfoUtil.getSerialNumber());
        ((TextView) view.findViewById(R.id.tv_value_timezone)).setText(SystemInfoUtil.getTimeZone());
    }

    public boolean handleBackPress() {
        return false;
    }
}
