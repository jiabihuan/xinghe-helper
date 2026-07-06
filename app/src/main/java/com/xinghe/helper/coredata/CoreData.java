package com.xinghe.helper.coredata;

import com.xinghe.helper.model.PasswordApp;
import com.xinghe.helper.model.RecommendToken;

import java.util.ArrayList;
import java.util.List;

public class CoreData {
    public static String EXTERNAL_FILE_PATH = null;
    public static String FILE_PATH = null;
    public static String FILE_UDP_ADDR = null;
    public static String HTTP_BASE_URL = "http://172.245.61.121:8000";
    public static final int PROTOCOL_VER = 1;
    public static String externalFooter;
    public static String externalMarqueeInfo;
    public static String externalTopInfo;
    public static PasswordApp selfApp;
    public static int tokenResultState;
    public static boolean udpTokenAppsFailed;
    public static boolean udpTokenAppsProbePending;
    public static boolean IS_PHONE = false;
    public static boolean gotExternalFile = false;
    public static final List<RecommendToken> recommendTokens = new ArrayList<>();
}
