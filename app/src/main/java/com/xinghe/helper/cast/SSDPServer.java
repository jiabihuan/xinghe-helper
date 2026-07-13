package com.xinghe.helper.cast;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.UUID;

public class SSDPServer {

    private static final String TAG = "SSDPServer";
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;

    private final Context context;
    private final String uuid;
    private final int httpPort;
    private final String deviceName;

    private MulticastSocket socket;
    private InetAddress group;
    private boolean running = false;
    private Thread receiveThread;
    private Thread announceThread;

    public SSDPServer(Context context, String deviceName, int httpPort) {
        this.context = context.getApplicationContext();
        this.deviceName = deviceName;
        this.httpPort = httpPort;
        this.uuid = UUID.randomUUID().toString();
    }

    public void start() {
        if (running) return;
        running = true;
        try {
            group = InetAddress.getByName(SSDP_ADDRESS);
            socket = new MulticastSocket(SSDP_PORT);
            socket.setReuseAddress(true);
            socket.joinGroup(group);

            receiveThread = new Thread(this::receiveLoop, "SSDP-Receive");
            receiveThread.start();

            announceThread = new Thread(this::announceLoop, "SSDP-Announce");
            announceThread.start();

            Log.d(TAG, "SSDP Server started on port " + SSDP_PORT);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start SSDP server", e);
            running = false;
        }
    }

    public void stop() {
        running = false;
        if (socket != null) {
            try {
                socket.leaveGroup(group);
            } catch (Exception ignored) {}
            socket.close();
            socket = null;
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[2048];
        while (running && socket != null) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                if (msg.startsWith("M-SEARCH")) {
                    handleMSearch(packet.getAddress(), packet.getPort());
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "Receive error", e);
            }
        }
    }

    private void announceLoop() {
        try {
            for (int i = 0; i < 3; i++) {
                sendAnnounce("ssdp:alive");
                Thread.sleep(200);
            }
            while (running) {
                Thread.sleep(30000);
                sendAnnounce("ssdp:alive");
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Log.e(TAG, "Announce error", e);
        }
    }

    private void handleMSearch(InetAddress addr, int port) {
        String[] types = {
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1",
            "urn:schemas-upnp-org:service:RenderingControl:1",
            "uuid:" + uuid
        };
        for (String type : types) {
            sendResponse(addr, port, type);
        }
    }

    private void sendResponse(InetAddress addr, int port, String st) {
        try {
            String ip = getLocalIpAddress();
            if (ip == null) return;

            String date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).format(new Date());
            String response = "HTTP/1.1 200 OK\r\n" +
                    "CACHE-CONTROL: max-age=1800\r\n" +
                    "DATE: " + date + "\r\n" +
                    "EXT:\r\n" +
                    "LOCATION: http://" + ip + ":" + httpPort + "/description.xml\r\n" +
                    "SERVER: Linux/3.0.1 UPnP/1.0 DLNADOC/1.50 AndroidCast/1.0\r\n" +
                    "ST: " + st + "\r\n" +
                    "USN: uuid:" + uuid + "::" + st + "\r\n" +
                    "\r\n";

            byte[] data = response.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            if (socket != null) {
                socket.send(packet);
            }
        } catch (Exception e) {
            Log.e(TAG, "Send response error", e);
        }
    }

    private void sendAnnounce(String nts) {
        try {
            String ip = getLocalIpAddress();
            if (ip == null) return;

            String[] types = {
                "upnp:rootdevice",
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "uuid:" + uuid
            };
            for (String type : types) {
                String msg = "NOTIFY * HTTP/1.1\r\n" +
                        "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n" +
                        "CACHE-CONTROL: max-age=1800\r\n" +
                        "LOCATION: http://" + ip + ":" + httpPort + "/description.xml\r\n" +
                        "NT: " + type + "\r\n" +
                        "NTS: " + nts + "\r\n" +
                        "USN: uuid:" + uuid + "::" + type + "\r\n" +
                        "SERVER: Linux/3.0.1 UPnP/1.0 DLNADOC/1.50 AndroidCast/1.0\r\n" +
                        "\r\n";

                byte[] data = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, group, SSDP_PORT);
                if (socket != null) {
                    socket.send(packet);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Announce send error", e);
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> addr = intf.getInetAddresses(); addr.hasMoreElements();) {
                    InetAddress inetAddr = addr.nextElement();
                    if (!inetAddr.isLoopbackAddress() && inetAddr instanceof java.net.Inet4Address) {
                        return inetAddr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getLocalIpAddress error", e);
        }
        return null;
    }

    public String getUuid() { return uuid; }
    public int getHttpPort() { return httpPort; }
    public String getDeviceName() { return deviceName; }
}
