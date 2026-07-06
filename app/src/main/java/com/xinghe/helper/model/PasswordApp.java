package com.xinghe.helper.model;

public class PasswordApp {
    private long appId;
    private String name;
    private String packageName;
    private String versionName;
    private long versionCode;
    private int minAndroidApi;
    private String downloadUrl;
    private String md5;
    private long size;
    private String iconUrl;
    private long iconSize;
    private String description;
    private String category;
    private float rating;

    public PasswordApp() {}

    public PasswordApp(long appId, String name, String packageName, String versionName,
                       long versionCode, int minAndroidApi, String downloadUrl,
                       String md5, long size, String iconUrl, long iconSize,
                       String description, String category, float rating) {
        this.appId = appId;
        this.name = name;
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.minAndroidApi = minAndroidApi;
        this.downloadUrl = downloadUrl;
        this.md5 = md5;
        this.size = size;
        this.iconUrl = iconUrl;
        this.iconSize = iconSize;
        this.description = description;
        this.category = category;
        this.rating = rating;
    }

    public long getAppId() { return appId; }
    public void setAppId(long appId) { this.appId = appId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public long getVersionCode() { return versionCode; }
    public void setVersionCode(long versionCode) { this.versionCode = versionCode; }
    public int getMinAndroidApi() { return minAndroidApi; }
    public void setMinAndroidApi(int minAndroidApi) { this.minAndroidApi = minAndroidApi; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public long getIconSize() { return iconSize; }
    public void setIconSize(long iconSize) { this.iconSize = iconSize; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
}
