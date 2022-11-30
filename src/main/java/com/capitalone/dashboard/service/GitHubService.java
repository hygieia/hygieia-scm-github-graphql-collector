package com.capitalone.dashboard.service;

import org.springframework.http.ResponseEntity;

public interface GitHubService {
    ResponseEntity<String> cleanup();

    ResponseEntity<String> syncPullRequest(String servName, String appName, String altIdentifier);
}
