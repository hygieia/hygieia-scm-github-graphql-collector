package com.capitalone.dashboard.service;

import com.capitalone.dashboard.misc.HygieiaException;
import org.springframework.http.ResponseEntity;

public interface GitHubService {
    ResponseEntity<String> cleanup() throws HygieiaException;
}
