package com.capitalone.dashboard.controller;

import com.capitalone.dashboard.collector.GitHubCollectorTask;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.GitHubRepo;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.GitHubRepoRepository;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@Validated
public class GitHubController {
    private static final Log LOG = LogFactory.getLog(GitHubController.class);

    private final BaseCollectorRepository<Collector> collectorRepository;
    private final GitHubRepoRepository gitHubRepoRepository;
    private final GitHubCollectorTask gitHubCollectorTask;
    private static final String GITHUB_COLLECTOR_NAME = "GitHub";

    @Autowired
    public GitHubController(BaseCollectorRepository<Collector> collectorRepository,
                            GitHubRepoRepository gitHubRepoRepository,
                            GitHubCollectorTask gitHubCollectorTask) {
        this.collectorRepository = collectorRepository;
        this.gitHubRepoRepository = gitHubRepoRepository;
        this.gitHubCollectorTask = gitHubCollectorTask;
    }

    @RequestMapping(value = "/refresh", method = GET, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> refresh(@Valid String url) throws HygieiaException {
        if (Objects.isNull(url)) return ResponseEntity.status(HttpStatus.OK).body("URL is null");
        Collector collector = collectorRepository.findByName(GITHUB_COLLECTOR_NAME);
        if (Objects.isNull(collector))
            return ResponseEntity.status(HttpStatus.OK).body(GITHUB_COLLECTOR_NAME + " collector is not found");
        List<GitHubRepo> repos = gitHubRepoRepository.findByCollectorIdAndUrl(collector.getId(), url);
        if (CollectionUtils.isEmpty(repos))
            return ResponseEntity.status(HttpStatus.OK).body("No GitHub repos found for URL: " + url);
        int count = 0;
        gitHubCollectorTask.collectProcess(collector, repos);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(GITHUB_COLLECTOR_NAME + " refresh completed successfully for repo URL: " + url
                        + " Records updated: " + collector.getLastExecutionRecordCount());
    }


    @RequestMapping(value = "/cleanup", method = GET, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cleanup() throws HygieiaException {
        Collector collector = collectorRepository.findByName(GITHUB_COLLECTOR_NAME);
        if (Objects.isNull(collector))
            return ResponseEntity.status(HttpStatus.OK).body(GITHUB_COLLECTOR_NAME + " collector is not found");
        List<GitHubRepo> repos = gitHubRepoRepository.findObsoleteGitHubRepos(collector.getId());
        if (CollectionUtils.isEmpty(repos))
            return ResponseEntity.status(HttpStatus.OK).body("No more Obsolete GitHub repo found");
        int count = repos.size();
        gitHubRepoRepository.delete(repos);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(GITHUB_COLLECTOR_NAME + " cleanup - " + count + " Obsolete GitHub repo's deleted. ");
    }
}
