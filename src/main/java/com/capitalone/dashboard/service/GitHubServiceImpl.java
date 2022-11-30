package com.capitalone.dashboard.service;


import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.model.webhook.github.GitHubRepo;
import com.capitalone.dashboard.repository.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Component
public class GitHubServiceImpl implements GitHubService {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubServiceImpl.class);

    private final BaseCollectorRepository<GitHubCollector> collectorRepository;
    private final GitHubRepoRepository gitHubRepoRepository;
    private final GitRequestRepository gitRequestRepository;
    private final ComponentRepository componentRepository;
    private final DashboardRepository dashboardRepository;
    private final CollectorItemRepository collectorItemRepository;
    private final CommitRepository commitRepository;
    private static final String GITHUB_COLLECTOR_NAME = "GitHub";

    @Autowired
    public GitHubServiceImpl(BaseCollectorRepository<GitHubCollector> collectorRepository,
                             GitHubRepoRepository gitHubRepoRepository, GitRequestRepository gitRequestRepository,
                             ComponentRepository componentRepository, DashboardRepository dashboardRepository,
                             CollectorItemRepository collectorItemRepository, CommitRepository commitRepository){
        this.collectorRepository = collectorRepository;
        this.gitHubRepoRepository = gitHubRepoRepository;
        this.gitRequestRepository = gitRequestRepository;
        this.componentRepository = componentRepository;
        this.dashboardRepository = dashboardRepository;
        this.collectorItemRepository = collectorItemRepository;
        this.commitRepository = commitRepository;
    }

    public ResponseEntity<String> cleanup() {
        GitHubCollector collector = collectorRepository.findByName(GITHUB_COLLECTOR_NAME);
        if (Objects.isNull(collector))
            return ResponseEntity.status(HttpStatus.OK).body(GITHUB_COLLECTOR_NAME + " collector is not found");
        List<GitHubRepo> repos = gitHubRepoRepository.findObsoleteGitHubRepos(collector.getId());
        if (CollectionUtils.isEmpty(repos))
            return ResponseEntity.status(HttpStatus.OK).body("No more Obsolete GitHub repo found");
        int count = repos.size();
        gitHubRepoRepository.deleteAll(repos);
        LOG.info(GITHUB_COLLECTOR_NAME + " cleanup - " + count + " obsolete GitHub repo's deleted. ");
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(GITHUB_COLLECTOR_NAME + " cleanup - " + count + " obsolete GitHub repo's deleted. ");
    }

    public ResponseEntity<String> syncPullRequest(String servName, String appName, String altIdentifier ){

        if (StringUtils.isEmpty(servName) || StringUtils.isEmpty(appName)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("servName and appName are required fields");
        }

        // use servName and appName to get dashboard -> component -> scm's
        Dashboard dashboard = dashboardRepository.findByConfigurationItemBusServNameIgnoreCaseAndConfigurationItemBusAppNameIgnoreCase(servName, appName);
        if(Objects.isNull(dashboard)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(String.format("Unable to find dashboard for {servName: %s, appName: %s}", servName, appName));
        }

        if(dashboard.getWidgets().isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(String.format("Unable to find component for {servName: %s, appName: %s}", servName, appName));
        }
        ObjectId componentId = dashboard.getWidgets().get(0).getComponentId();

        com.capitalone.dashboard.model.Component component = componentRepository.findById(componentId).orElse(null);
        if(Objects.isNull(component)){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(String.format("Unable to find component with componentId: %s", componentId));
        }

        if(Objects.isNull(component.getCollectorItems().get(CollectorType.SCM))){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(String.format("Unable to find any repos attached to {servName: %s, appName: %s}",servName, appName));
        }

        // use scm data to collect PRs and Commits, then check if they are valid for the assigned collectorItemId
        if (StringUtils.isBlank(altIdentifier)){
            int gitCount = 0;
            int commitCount = 0;

            for (CollectorItem scm:component.getCollectorItems().get(CollectorType.SCM)){
                // Collect documents -> compare url of documents to scm url -> delete if not a match
                List<GitRequest> allGitRequests = gitRequestRepository.findByCollectorItemIdAndRequestType(scm.getId(), "pull");
                List<GitRequest> gitRequestsToFix = allGitRequests.stream().filter(GR -> !scm.getOptions().get("url").toString().equalsIgnoreCase(GR.getScmUrl())).collect(Collectors.toList());
                gitRequestsToFix.forEach(GR -> gitRequestRepository.deleteById(GR.getId()));
                gitCount+= gitRequestsToFix.size();

                List<Commit> allCommits = commitRepository.findByCollectorItemIdAndScmCommitTimestampIsBetween(scm.getId(), 0, System.currentTimeMillis());
                List<Commit> commitsToFix = allCommits.stream().filter(com -> !scm.getOptions().get("url").equals(com.getScmUrl())).collect(Collectors.toList());
                commitsToFix.forEach(com -> commitRepository.deleteById(com.getId()));
                commitCount += commitsToFix.size();
            }

            return ResponseEntity.status(HttpStatus.OK).body(String.format("syncPullRequest :: Component Level :: Removed %d pull requests and %d commits", gitCount, commitCount));
        }else {
            CollectorItem repo = component.getCollectorItems().get(CollectorType.SCM).stream().filter(scm -> altIdentifier.equalsIgnoreCase(scm.getAltIdentifier())).findFirst().orElse(null);

            if (Objects.isNull(repo)){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(String.format("Unable to find collector item for repo: %s", altIdentifier));
            }
            List<GitRequest> allGitRequests = gitRequestRepository.findByCollectorItemIdAndRequestType(repo.getId(), "pull");
            List<GitRequest> gitRequestsToFix = allGitRequests.stream().filter(GR -> !repo.getOptions().get("url").toString().equalsIgnoreCase(GR.getScmUrl())).collect(Collectors.toList());
            gitRequestsToFix.forEach(GR -> gitRequestRepository.deleteById(GR.getId()));

            List<Commit> allCommits = commitRepository.findByCollectorItemIdAndScmCommitTimestampIsBetween(repo.getId(), 0, System.currentTimeMillis());
            List<Commit> commitsToFix = allCommits.stream().filter(com -> !repo.getOptions().get("url").toString().equalsIgnoreCase(com.getScmUrl())).collect(Collectors.toList());
            commitsToFix.forEach(com -> commitRepository.deleteById(com.getId()));
            return ResponseEntity.status(HttpStatus.OK).body(String.format("syncPullRequest :: Removed %d pull requests and %d commits", gitRequestsToFix.size(), commitsToFix.size()));
        }
    }
}
