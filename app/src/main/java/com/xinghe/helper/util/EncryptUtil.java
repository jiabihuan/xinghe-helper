package com.xinghe.helper.util;

import android.util.Base64;

public class EncryptUtil {

    private static final String KEY = "xinghe_helper_2024";

    public static String encrypt(String input) {
        if (input == null || input.isEmpty()) return "";
        byte[] data = input.getBytes();
        byte[] keyBytes = KEY.getBytes();
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
        }
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            byte[] data = Base64.decode(encrypted, Base64.NO_WRAP);
            byte[] keyBytes = KEY.getBytes();
            byte[] result = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(result);
        } catch (Exception e) {
            return "";
        }
    }
}
