package com.xinghe.helper.fragments;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.xinghe.helper.R;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class RemoteFragment extends Fragment {

    private TextView tvPushUrl;
    private TextView tvIpAddress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_remote, container, false);

        tvPushUrl = view.findViewById(R.id.tv_push_url);
        tvIpAddress = view.findViewById(R.id.tv_ip_address);

        String ip = getLocalIpAddress();
        if (ip != null && !ip.isEmpty()) {
            tvPushUrl.setText("http://" + ip + ":3000");
            tvIpAddress.setText("设备IP: " + ip);
        }

        return view;
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) requireActivity().getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipInt = wifiInfo.getIpAddress();
                String ip = intToIp(ipInt);
                if (ip != null && !ip.equals("0.0.0.0")) {
                    return ip;
                }
            }

            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress() != null) {
                        String ip = inetAddress.getHostAddress();
                        if (ip.indexOf(':') < 0 && ip.startsWith("192.168.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String intToIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
    }
}
