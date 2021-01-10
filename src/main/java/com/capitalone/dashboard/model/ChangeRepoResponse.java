package com.capitalone.dashboard.model;

import java.util.HashSet;
import java.util.Set;

public class ChangeRepoResponse {
    Set<GitHubParsed> changeRepos;
    long latestEventId;
    long latestEventTimestamp;

    public ChangeRepoResponse(Set<GitHubParsed> changeRepos, long latestEventId, long latestEventTimestamp) {
        this.changeRepos = changeRepos;
        this.latestEventId = latestEventId;
        this.latestEventTimestamp = latestEventTimestamp;
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
}
