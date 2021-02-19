package com.capitalone.dashboard.service;

import com.capitalone.dashboard.collector.GitHubCollectorTask;
import com.capitalone.dashboard.model.GitHubCollector;
import com.capitalone.dashboard.model.webhook.github.GitHubRepo;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.GitHubRepoRepository;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class GitHubServiceImpl implements GitHubService {
    private static final Log LOG = LogFactory.getLog(GitHubServiceImpl.class);

    private final BaseCollectorRepository<GitHubCollector> collectorRepository;
    private final GitHubRepoRepository gitHubRepoRepository;
    private static final String GITHUB_COLLECTOR_NAME = "GitHub";

    @Autowired
    public GitHubServiceImpl(BaseCollectorRepository<GitHubCollector> collectorRepository,
                             GitHubRepoRepository gitHubRepoRepository,
                             GitHubCollectorTask gitHubCollectorTask) {
        this.collectorRepository = collectorRepository;
        this.gitHubRepoRepository = gitHubRepoRepository;
    }

    public ResponseEntity<String> cleanup() {
        GitHubCollector collector = collectorRepository.findByName(GITHUB_COLLECTOR_NAME);
        if (Objects.isNull(collector))
            return ResponseEntity.status(HttpStatus.OK).body(GITHUB_COLLECTOR_NAME + " collector is not found");
        List<GitHubRepo> repos = gitHubRepoRepository.findObsoleteGitHubRepos(collector.getId());
        if (CollectionUtils.isEmpty(repos))
            return ResponseEntity.status(HttpStatus.OK).body("No more Obsolete GitHub repo found");
        int count = repos.size();
        gitHubRepoRepository.delete(repos);
        LOG.info(GITHUB_COLLECTOR_NAME + " cleanup - " + count + " obsolete GitHub repo's deleted. ");
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(GITHUB_COLLECTOR_NAME + " cleanup - " + count + " obsolete GitHub repo's deleted. ");
    }
}
