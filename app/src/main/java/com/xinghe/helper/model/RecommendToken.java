package com.xinghe.helper.model;

public class RecommendToken {
    private String tokenCode;
    private String appName;

    public RecommendToken() {}

    public RecommendToken(String tokenCode, String appName) {
        this.tokenCode = tokenCode;
        this.appName = appName;
    }

    public String getTokenCode() { return tokenCode; }
    public void setTokenCode(String tokenCode) { this.tokenCode = tokenCode; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}
