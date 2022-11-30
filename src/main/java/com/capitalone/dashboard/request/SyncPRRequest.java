package com.capitalone.dashboard.request;

public class SyncPRRequest extends BaseRequest {
    private String servName;
    private String appName;
    private String repoUrl;
    private String altIdentifier;


    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getAltIdentifier() {
        return altIdentifier;
    }

    public void setAltIdentifier(String altIdentifier) {
        this.altIdentifier = altIdentifier;
    }

    public void setAppName(String appName) { this.appName = appName;}

    public String getAppName() { return appName; }

    public void setServName(String servName) { this.servName = servName; }

    public String getServName() { return servName; }
}
