package com.capitalone.dashboard.service;

import com.capitalone.dashboard.collector.GitHubCollectorTask;
import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.model.webhook.github.GitHubRepo;
import com.capitalone.dashboard.repository.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
    private static final Log LOG = LogFactory.getLog(GitHubServiceImpl.class);

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
                             CollectorItemRepository collectorItemRepository, CommitRepository commitRepository,
                             GitHubCollectorTask gitHubCollectorTask) {
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
        gitHubRepoRepository.delete(repos);
        LOG.info(GITHUB_COLLECTOR_NAME + " cleanup - " + count + " obsolete GitHub repo's deleted. ");
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(GITHUB_COLLECTOR_NAME + " cleanup - " + count + " obsolete GitHub repo's deleted. ");
    }

    public ResponseEntity<String> syncPullRequest(String title, String repoUrl, String branch){
        ObjectId githubCollectorId = collectorRepository.findByName(GITHUB_COLLECTOR_NAME).getId();

        // get the collector item id from the collectorItems should result in only one
        CollectorItem collectorItem = collectorItemRepository.findRepoByUrlAndBranch(githubCollectorId, repoUrl, branch);
        if(Objects.isNull(collectorItem)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(String.format("Unable to find collector item for repo: %s", repoUrl));
        }
        ObjectId collectorItemID = collectorItem.getId();

        // go to that collectorItemID in components and get all the SCM urls
        List<Dashboard> dashboard = dashboardRepository.findByTitle(title);
        if(dashboard.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(String.format("Unable to find dashboard for title: %s", title));
        }
        ObjectId componentId = dashboard.get(0).getWidgets().get(0).getComponentId();

        com.capitalone.dashboard.model.Component component = componentRepository.findOne(componentId);
        if(Objects.isNull(component)){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(String.format("Unable to find component with componentId: %s", componentId));
        }
        //to lower case so we can ignore case when comparing for gitRequestsToFix
        List<String> SCMs = component.getCollectorItems().get(CollectorType.SCM).stream().map(scm -> scm.getOptions().get("url").toString().toLowerCase()).collect(Collectors.toList());

        String responseString = "SyncPullRequest: "; // will be updated during the data cleaning

        // get all git requests with collectorItemID
        List<GitRequest> allGitRequests = gitRequestRepository.findByCollectorItemIdAndRequestType(collectorItemID, "pull");
        if (allGitRequests.isEmpty()){
            responseString += "[No GitRequests found] ";
        }

        // filter list of gitRequests, if scmUrl not in SCMs list add that gitRequest to the list so we can fix later
        List<GitRequest> gitRequestsToFix = allGitRequests.stream().filter(GR -> !SCMs.contains(GR.getScmUrl().toLowerCase())).collect(Collectors.toList());

        // iterate through the git requests with the wrong collectorItemId and correct them
        List<GitRequest> fixedGitRequests = new ArrayList<>();
        int failedUpdateCount = 0;
        for (GitRequest gr: gitRequestsToFix) {
            CollectorItem collItem = collectorItemRepository.findRepoByUrlAndBranch(githubCollectorId, gr.getScmUrl(), gr.getScmBranch());
            if (Objects.nonNull(collItem)){
                gr.setCollectorItemId(collItem.getId());
                gitRequestRepository.save(gr);
                LOG.info(String.format("GitRequest with wrong collectorItemId: %s \tcorrect CollectorItemId: %s", gr.getScmUrl(), collItem.getId().toString()));
                fixedGitRequests.add(gr);
            }
            else {
                LOG.info(String.format("Unable to update gitRequest: Unable to find collector item for repo: %s", gr.getScmUrl()));
                failedUpdateCount++;
            }
        }

        // getting all the commits by collectorItemId (timestamp ignored by using min and max values)
        List<Commit> allCommits = commitRepository.findByCollectorItemIdAndScmCommitTimestampIsBetween(collectorItemID, 0, System.currentTimeMillis());
        if(allCommits.isEmpty()){
            responseString += "[No commits found] ";
        }

        // filter for commits that do not belong and iterate through the list to fix them
        List<Commit> commitsToFix = allCommits.stream().filter(com -> !SCMs.contains(com.getScmUrl().toLowerCase())).collect(Collectors.toList());
        List<Commit> fixedCommits = new ArrayList<>();
        for (Commit commit: commitsToFix) {
            CollectorItem collItem = collectorItemRepository.findRepoByUrlAndBranch(githubCollectorId, commit.getScmUrl(), commit.getScmBranch());
            if(Objects.nonNull(collItem)) {
                commit.setCollectorItemId(collItem.getId());
                commitRepository.save(commit);
                LOG.info(String.format("Commit with wrong collectorItemId: %s \tcorrect CollectorItemId: %s", commit.getScmUrl(), collItem.getId().toString()));
                fixedCommits.add(commit);
            }
            else{
                LOG.info(String.format("Unable to update commit: Unable to find collector item for repo: %s", commit.getScmUrl()));
                failedUpdateCount++;
            }
        }

        if (failedUpdateCount > 0){
            responseString += "[FAILED TO UPDATE: " + String.valueOf(failedUpdateCount) + " Document(s) please check logs for more details] ";
        }

        // converting the list of documents changed to a map with count of scm occurrences
        Map<String, Long> fixedGitMap = fixedGitRequests.stream().collect(groupingBy(GR -> GR.getScmUrl(), counting()));
        Map<String, Long> fixedCommitMap = fixedCommits.stream().collect(groupingBy(com -> com.getScmUrl(), counting()));
        responseString += "Successfully corrected the following gitRequests: " + fixedGitMap + " ";
        responseString += "Successfully corrected the following commits: " +  fixedCommitMap;
        return ResponseEntity.status(HttpStatus.OK).body(responseString);

    }
}
