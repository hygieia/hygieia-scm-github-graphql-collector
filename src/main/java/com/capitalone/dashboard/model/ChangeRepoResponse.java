package com.capitalone.dashboard.model;

import java.util.HashSet;
import java.util.Set;

public class ChangeRepoResponse {
    Set<GitHubParsed> changeRepos;
    long latestEventId;
    long latestEventTimestamp;
    long lastFetchTimestamp;
    long pollIntervalWaitTime;

    public ChangeRepoResponse(Set<GitHubParsed> changeRepos, long latestEventId, long latestEventTimestamp, long lastFetchTimestamp, long pollIntervalWaitTime) {
        this.changeRepos = changeRepos;
        this.latestEventId = latestEventId;
        this.latestEventTimestamp = latestEventTimestamp;
        this.lastFetchTimestamp = lastFetchTimestamp;
        this.pollIntervalWaitTime = pollIntervalWaitTime;
    }

    public Set<GitHubParsed> getChangeRepos() {
        return changeRepos;
    }

    public void setChangeRepos(Set<GitHubParsed> changeRepos) {
        this.changeRepos = changeRepos;
    }

    public long getLatestEventId() {
        return latestEventId;
    }

    public void setLatestEventId(long latestEventId) {
        this.latestEventId = latestEventId;
    }

    public long getLatestEventTimestamp() {
        return latestEventTimestamp;
    }

    public void setLatestEventTimestamp(long latestEventTimestamp) {
        this.latestEventTimestamp = latestEventTimestamp;
    }

    public long getLastFetchTimestamp() {
        return lastFetchTimestamp;
    }

    public void setLastFetchTimestamp(long lastFetchTimestamp) {
        this.lastFetchTimestamp = lastFetchTimestamp;
    }

    public long getPollIntervalWaitTime() {
        return pollIntervalWaitTime;
    }

    public void setPollIntervalWaitTime(long pollIntervalWaitTime) {
        this.pollIntervalWaitTime = pollIntervalWaitTime;
    }
}
