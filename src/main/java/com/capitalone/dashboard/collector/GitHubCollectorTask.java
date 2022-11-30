package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.BaseModel;
import com.capitalone.dashboard.model.ChangeRepoResponse;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorItemMetadata;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.GitHubCollector;
import com.capitalone.dashboard.model.GitHubParsed;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.model.webhook.github.GitHubRepo;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CollectorItemMetadataRepository;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.GitHubRepoRepository;
import com.capitalone.dashboard.repository.GitRequestRepository;
import com.capitalone.dashboard.util.CommitPullMatcher;
import com.capitalone.dashboard.util.GithubRepoMatcher;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CollectorTask that fetches Commit information from GitHub
 */
@Component
public class GitHubCollectorTask extends CollectorTask<GitHubCollector> {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubCollectorTask.class);

    private final BaseCollectorRepository<GitHubCollector> collectorRepository;
    private final GitHubRepoRepository gitHubRepoRepository;
    private final CommitRepository commitRepository;
    private final GitRequestRepository gitRequestRepository;
    private final GitHubClient gitHubClient;
    private final GitHubSettings gitHubSettings;
    private final ComponentRepository dbComponentRepository;
    private final CollectorItemMetadataRepository collectorItemMetadataRepository;
    private static final long ONE_DAY_MILLISECONDS = 24 * 60 * 60 * 1000;
    private static final long ONE_SECOND_IN_MILLISECONDS = 1000;
    private static final long FOURTEEN_DAYS_MILLISECONDS = 14 * ONE_DAY_MILLISECONDS;
    private static final String REPO_NAME = "repoName";
    private static final String ORG_NAME = "orgName";
    private AtomicInteger count = new AtomicInteger(0);


    @Autowired
    public GitHubCollectorTask(TaskScheduler taskScheduler,
                               BaseCollectorRepository<GitHubCollector> collectorRepository,
                               GitHubRepoRepository gitHubRepoRepository,
                               CommitRepository commitRepository,
                               GitRequestRepository gitRequestRepository,
                               GitHubClient gitHubClient,
                               GitHubSettings gitHubSettings,
                               ComponentRepository dbComponentRepository,
                               CollectorItemMetadataRepository collectorItemMetadataRepository) {
        super(taskScheduler, "GitHub");
        this.collectorRepository = collectorRepository;
        this.gitHubRepoRepository = gitHubRepoRepository;
        this.commitRepository = commitRepository;
        this.gitHubClient = gitHubClient;
        this.gitHubSettings = gitHubSettings;
        this.dbComponentRepository = dbComponentRepository;
        this.gitRequestRepository = gitRequestRepository;
        this.collectorItemMetadataRepository = collectorItemMetadataRepository;
    }

    @Override
    public GitHubCollector getCollector() {
        GitHubCollector existingCollector = null;
        /**
         * ClassCastException maybe happen when first migrating from collector to Github collector, once we run the collector
         * the data will get updated
          */
        try {
            existingCollector = collectorRepository.findByName("GitHub");
        } catch (ClassCastException ignore) {}

        if(!Objects.isNull(existingCollector)) return existingCollector;

        GitHubCollector protoType = new GitHubCollector();
        protoType.setName("GitHub");
        protoType.setCollectorType(CollectorType.SCM);
        protoType.setOnline(true);
        protoType.setEnabled(true);

        Map<String, Object> allOptions = new HashMap<>();
        allOptions.put(GitHubRepo.REPO_URL, "");
        allOptions.put(GitHubRepo.BRANCH, "");
        allOptions.put(GitHubRepo.USER_ID, "");
        allOptions.put(GitHubRepo.PASSWORD, "");
        allOptions.put(GitHubRepo.PERSONAL_ACCESS_TOKEN, "");
        allOptions.put(GitHubRepo.TYPE, "");
        protoType.setAllFields(allOptions);

        Map<String, Object> uniqueOptions = new HashMap<>();
        uniqueOptions.put(GitHubRepo.REPO_URL, "");
        uniqueOptions.put(GitHubRepo.BRANCH, "");
        protoType.setUniqueFields(uniqueOptions);

        return protoType;
    }

    @Override
    public BaseCollectorRepository<GitHubCollector> getCollectorRepository() {
        return collectorRepository;
    }

    @Override
    public String getCron() {
        return gitHubSettings.getCron();
    }

    /**
     * Clean up unused deployment collector items
     *
     * @param collector the {@link Collector}
     */
    @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts") // agreed, fixme
    private void clean(GitHubCollector collector) {
        // clean up once a day
        if ((System.currentTimeMillis() - collector.getLastCleanUpTimestamp()) < ONE_DAY_MILLISECONDS) {
            return;
        }
        Set<ObjectId> uniqueIDs = new HashSet<>();
        /*
          Logic: For each component, retrieve the collector item list of the type SCM.
          Store their IDs in a unique set ONLY if their collector IDs match with GitHub collectors ID.
         */
        for (com.capitalone.dashboard.model.Component comp : dbComponentRepository.findAll()) {
            if (!MapUtils.isEmpty(comp.getCollectorItems())) {
                List<CollectorItem> itemList = comp.getCollectorItems().get(CollectorType.SCM);
                if (itemList != null) {
                    itemList.stream().filter(ci -> ci != null && Objects.equals(ci.getCollectorId(), collector.getId())).map(BaseModel::getId).forEach(uniqueIDs::add);
                }
            }
        }
        /*
          Logic: Get all the collector items from the collector_item collection for this collector.
          If their id is in the unique set (above), keep them enabled; else, disable them.
         */
        List<GitHubRepo> repoList = new ArrayList<>();
        Set<ObjectId> gitID = new HashSet<>();
        gitID.add(collector.getId());
        gitHubRepoRepository.findByCollectorIdIn(gitID).stream().filter(Objects::nonNull).forEach(repo -> {
            if (repo.isPushed()) {
                return;
            }
            repo.setEnabled(uniqueIDs.contains(repo.getId()));
            repoList.add(repo);
        });
        gitHubRepoRepository.saveAll(repoList);
        collector.setLastCleanUpTimestamp(System.currentTimeMillis());
    }


    @Override
    public void collect(GitHubCollector collector) {
        setupProxy();
        clean(collector);
        List<GitHubRepo> enabledRepos = enabledRepos(collector);
        ChangeRepoResponse changeRepoResponse = null;
        if (gitHubSettings.isCollectChangedReposOnly()) {
            try {
                changeRepoResponse = gitHubClient.getChangedRepos(collector.getLatestProcessedEventId(), collector.getLatestProcessedEventTimestamp());
                Set<GitHubRepo> changedRepos = reposToCollect(collector, enabledRepos, changeRepoResponse);
                enabledRepos = new ArrayList<>(changedRepos);
            } catch (MalformedURLException | HygieiaException e) {
                LOG.error("Error fetching changed repos:", e);
            }
        }

        if (gitHubSettings.getSearchCriteria() != null) {
            String[] searchCriteria = gitHubSettings.getSearchCriteria().split(Pattern.quote("|"));
            if (REPO_NAME.equalsIgnoreCase(searchCriteria[0])) {
                enabledRepos = enabledRepos.stream().filter(repo -> GithubRepoMatcher.repoNameMatcher(repo.getRepoUrl(), searchCriteria[1])).collect(Collectors.toList());
            } else if (ORG_NAME.equalsIgnoreCase(searchCriteria[0])) {
                enabledRepos = enabledRepos.stream().filter(repo -> GithubRepoMatcher.orgNameMatcher(repo.getRepoUrl(), searchCriteria[1])).collect(Collectors.toList());
            }
        }
        LOG.info("GitHubCollectorTask:collect start, total enabledRepos=" + enabledRepos.size());
        LOG.warn("error threshold error_threshold=" + gitHubSettings.getErrorThreshold());
        collectProcess(collector, enabledRepos);

        if (changeRepoResponse != null) {
            long processTime = System.currentTimeMillis() - changeRepoResponse.getLastFetchTimestamp();
            long waitTime = changeRepoResponse.getPollIntervalWaitTime() * ONE_SECOND_IN_MILLISECONDS;
            if (processTime < waitTime) {
                long diff = waitTime - processTime;
                LOG.info(String.format("Waiting for Github event poll interval : %s milliseconds", diff));
                sleep(diff);
            }
        }

        collectorRepository.save(collector);
    }

    public Set<GitHubRepo> reposToCollect(GitHubCollector collector, List<GitHubRepo> enabledRepos, ChangeRepoResponse changeRepoResponse) {
        Set<GitHubParsed> changeRepos = changeRepoResponse.getChangeRepos();
        Map<String, GitHubParsed> changedReposMap = changeRepos.stream().collect(Collectors.toMap(g -> g.getUrl().toLowerCase(), Function.identity()));
        Set<GitHubRepo> repoSet = enabledRepos.stream().filter(e -> changedReposMap.containsKey(e.getRepoUrl().toLowerCase()) && !e.isPushed()).collect(Collectors.toSet());
        collector.setLatestProcessedEventId(changeRepoResponse.getLatestEventId());
        collector.setLatestProcessedEventTimestamp(changeRepoResponse.getLatestEventTimestamp());
        if (collectPrivateRepos(collector)) {
            Set<GitHubRepo> privateRepos = enabledRepos
                    .stream()
                    .filter(e -> ((!StringUtils.isEmpty(e.getPassword()) && !StringUtils.isEmpty(e.getUserId()))
                            || !StringUtils.isEmpty(e.getPersonalAccessToken())))
                    .collect(Collectors.toSet());

            repoSet.addAll(privateRepos);
            collector.setLastPrivateRepoCollectionTimestamp(System.currentTimeMillis());
        }
        return repoSet;
    }

    @SuppressWarnings({"PMD.AvoidDeeplyNestedIfStmts"})
    public void collectProcess(Collector collector, List<GitHubRepo> reposToCollect) {
        long start = System.currentTimeMillis();
        int repoCount = 0;
        int commitCount = 0;
        int pullCount = 0;
        int issueCount = 0;
        count.set(0);

        int offSetMinutes = collectPrivateRepos(collector) ? gitHubSettings.getPrivateRepoOffsetMinutes() : gitHubSettings.getOffsetMinutes();

        for (GitHubRepo repo : reposToCollect) {
            repoCount++;
            long repoStart = System.currentTimeMillis();
            String repoUrl = repo == null ? "null" : (repo.getRepoUrl() + "/tree/" + repo.getBranch());
            String statusString = "UNKNOWN";
            long lastUpdated = repo == null ? 0 : repo.getLastUpdated();
            try {
                if (repo == null)
                    throw new HygieiaException("Repository returned from github is null", HygieiaException.BAD_DATA);
                boolean firstRun = ((repo.getLastUpdated() == 0) || ((start - repo.getLastUpdated()) > FOURTEEN_DAYS_MILLISECONDS));
                if (!repo.checkErrorOrReset(gitHubSettings.getErrorResetWindow(), gitHubSettings.getErrorThreshold())) {
                    statusString = "SKIPPED, errorThreshold exceeded";
                } else if (!gitHubClient.isUnderRateLimit()) {
                    LOG.error(String.format("GraphQL API rate limit reached after %d seconds since start. Stopping processing", (System.currentTimeMillis() - start) / 1000));
                    // add wait time (default = 0.3s)
                    statusString = String.format("SKIPPED, rateLimit exceeded, sleep for %d", gitHubSettings.getWaitTime());
                    sleep(gitHubSettings.getWaitTime());
                } else {
                    try {
                        List<GitRequest> allRequests = gitRequestRepository.findRequestNumberAndLastUpdated(repo.getId());

                        Map<Long, String> existingPRMap = allRequests.stream().filter(r -> Objects.equals(r.getRequestType(), "pull")).collect(
                                Collectors.toMap(GitRequest::getUpdatedAt, GitRequest::getNumber,
                                        (oldValue, newValue) -> oldValue
                                )
                        );

                        Map<Long, String> existingIssueMap = allRequests.stream().filter(r -> Objects.equals(r.getRequestType(), "issue")).collect(
                                Collectors.toMap(GitRequest::getUpdatedAt, GitRequest::getNumber,
                                        (oldValue, newValue) -> oldValue
                                )
                        );

                        gitHubClient.fireGraphQL(repo, firstRun, existingPRMap, existingIssueMap, offSetMinutes);

                        // Get all the commits
                        int commitCount1 = processCommits(repo);
                        commitCount += commitCount1;

                        //Get all the Pull Requests
                        int pullCount1 = processPRorIssueList(repo, allRequests.stream().filter(r -> Objects.equals(r.getRequestType(), "pull")).collect(Collectors.toList()), "pull");
                        pullCount += pullCount1;

                        //Get all the Issues
                        int issueCount1 = processPRorIssueList(repo, allRequests.stream().filter(r -> Objects.equals(r.getRequestType(), "issue")).collect(Collectors.toList()), "issue");
                        issueCount += issueCount;

                        // Due to timing of PRs and Commits in PR merge event, some commits may not be included in the response and will not be connected to a PR.
                        // This is the place attempting to re-connect the commits and PRs in case they were missed during previous run.

                        processOrphanCommits(repo);

                        repo.setLastUpdated(System.currentTimeMillis());
                        // if everything went alright, there should be no error!
                        repo.getErrors().clear();
                        statusString = String.format("SUCCESS, pulls=%d, commits=%d, issues=%d", pullCount1, commitCount1, issueCount1);
                    } catch (HttpStatusCodeException hc) {
                        LOG.error(String.format("Error fetching commits for:%s", repo.getRepoUrl()), hc);
                        statusString = String.format("EXCEPTION, %s", hc.getClass().getCanonicalName());
                        CollectionError error = new CollectionError(hc.getStatusCode().toString(), hc.getMessage());
                        if (hc.getStatusCode() == HttpStatus.UNAUTHORIZED || hc.getStatusCode() == HttpStatus.FORBIDDEN) {
                            LOG.error(String.format("Received 401/403 HttpStatusCodeException from GitHub. Status code=%s ResponseBody=%s", hc.getStatusCode(), hc.getResponseBodyAsString()));
                            int retryAfterSeconds = NumberUtils.toInt(hc.getResponseHeaders().get(DefaultGitHubClient.RETRY_AFTER).get(0));
                            long startSleeping = System.currentTimeMillis();
                            LOG.info(String.format("Should Retry-After: %d sec. Start sleeping at: %s", retryAfterSeconds, new DateTime(startSleeping).toString("yyyy-MM-dd hh:mm:ss.SSa")));
                            sleep(retryAfterSeconds * 1000L);
                            long endSleeping = System.currentTimeMillis();
                            LOG.info(String.format("Waking up after [%d] sec, at: %s", (endSleeping - startSleeping) / 1000L, new DateTime(endSleeping).toString("yyyy-MM-dd hh:mm:ss.SSa")));
                        }
                        if (hc.getStatusCode() == HttpStatus.NOT_FOUND) {
                            LOG.error(String.format("Received 404 HttpStatusCodeException from GitHub. Status code=%s ResponseBody=%s", hc.getStatusCode(), hc.getResponseBodyAsString()));
                            LOG.info(String.format("Deleting Github repo from collector-items=%s ", repoUrl));
                            gitHubRepoRepository.deleteById(repo.getId());
                        }
                        repo.getErrors().add(error);
                    } catch (RestClientException | MalformedURLException ex) {
                        LOG.error(String.format("Error fetching commits for:%s", repo.getRepoUrl()), ex);
                        statusString = String.format("EXCEPTION, %s", ex.getClass().getCanonicalName());
                        CollectionError error = new CollectionError(CollectionError.UNKNOWN_HOST, ex.getMessage());
                        repo.getErrors().add(error);
                    } catch (HygieiaException he) {
                        LOG.error(String.format("Error fetching commits for:%s", repo.getRepoUrl()), he);
                        statusString = String.format("EXCEPTION, %s", he.getClass().getCanonicalName());
                        CollectionError error = new CollectionError(String.valueOf(he.getErrorCode()), he.getMessage());
                        repo.getErrors().add(error);
                    }


                    //save the collectorItem
                    gitHubRepoRepository.save(repo);

                    //enrich the metadata
                    enrichMetadata(repo);
                }
            } catch (Throwable e) {
                statusString = String.format("EXCEPTION, %s", e.getClass().getCanonicalName());
                LOG.error(String.format("Unexpected exception when collecting url=%s", repoUrl), e);
            } finally {
                String age = readableAge(lastUpdated, start);
                long itemProcessTime = System.currentTimeMillis() - repoStart;
                LOG.info(String.format("%d of %d, repository=%s, itemProcessTime=%d lastUpdated=%d [%s], status=%s",
                        repoCount, reposToCollect.size(), repoUrl, itemProcessTime, lastUpdated, age, statusString));
            }
        }
        long end = System.currentTimeMillis();
        long elapsedSeconds = (end - start) / 1000;
        count.set(commitCount);
        LOG.info(String.format("GitHubCollectorTask:collect stop, totalProcessSeconds=%d, totalRepoCount=%d, totalNewPulls=%d, totalNewCommits=%d totalNewIssues=%d",
                elapsedSeconds, repoCount, pullCount, commitCount, issueCount));

        collector.setLastExecutionRecordCount(repoCount + pullCount + commitCount + issueCount);
        collector.setLastExecutedSeconds(elapsedSeconds);
    }


    private boolean collectPrivateRepos (Collector collector) {
        if(collector == null || !(collector instanceof GitHubCollector)) return true; // treat it as first run and collect everything.
        GitHubCollector gitHubCollector = (GitHubCollector) collector;
        return ((System.currentTimeMillis() - gitHubCollector.getLastPrivateRepoCollectionTimestamp() > gitHubSettings.getPrivateRepoCollectionTime()));
    }

    private static String readableAge(long lastUpdated, long start) {
        if (lastUpdated <= 0) return "never before";
        else if (start - lastUpdated > 48 * 3600000) return ((start - lastUpdated) / (24 * 3600000)) + " days ago";
        else if (start - lastUpdated > 2 * 3600000) return ((start - lastUpdated) / 3600000) + " hours ago";
        else return ((start - lastUpdated) / 60000) + " minutes ago";
    }

    private void setupProxy() {
        String proxyUrl = gitHubSettings.getProxyUrl();
        String proxyPort = gitHubSettings.getProxyPort();
        String proxyUser = gitHubSettings.getProxyUser();
        //String proxyPassword = gitHubSettings.getProxyPassword();

        if (!StringUtils.isEmpty(proxyUrl) && !StringUtils.isEmpty(proxyPort)) {
            System.setProperty("http.proxyHost", proxyUrl);
            System.setProperty("https.proxyHost", proxyUrl);
            System.setProperty("http.proxyPort", proxyPort);
            System.setProperty("https.proxyPort", proxyPort);

            if (!StringUtils.isEmpty(proxyUser) && !StringUtils.isEmpty(gitHubSettings.getProxyPassword())) {
                System.setProperty("http.proxyUser", proxyUser);
                System.setProperty("https.proxyUser", proxyUser);
                System.setProperty("http.proxyPassword", gitHubSettings.getProxyPassword());
                System.setProperty("https.proxyPassword", gitHubSettings.getProxyPassword());
            }
        }
    }



    // Retrieves a st of previous commits and Pulls and tries to reconnect them
    private void processOrphanCommits(GitHubRepo repo) {
        long refTime = Math.min(System.currentTimeMillis() - gitHubSettings.getCommitPullSyncTime(), gitHubClient.getRepoOffsetTime(repo));
        List<Commit> orphanCommits = commitRepository.findCommitsByCollectorItemIdAndTimestampAfterAndPullNumberIsNull(repo.getId(), refTime);
        List<GitRequest> pulls = gitRequestRepository.findByCollectorItemIdAndMergedAtIsBetween(repo.getId(), refTime, System.currentTimeMillis());
        orphanCommits = CommitPullMatcher.matchCommitToPulls(orphanCommits, pulls);
        List<Commit> orphanSaveList = orphanCommits.stream().filter(c -> !StringUtils.isEmpty(c.getPullNumber())).collect(Collectors.toList());
        orphanSaveList.forEach(c -> LOG.info("Updating orphan " + c.getScmRevisionNumber() + ' ' +
                new DateTime(c.getScmCommitTimestamp()).toString("yyyy-MM-dd hh:mm:ss.SSa") + " with pull " + c.getPullNumber()));
        long start = System.currentTimeMillis();
        commitRepository.saveAll(orphanSaveList);
        LOG.info("-- Saved Orphan Commits= " + orphanSaveList.size() + ", Duration= " + (System.currentTimeMillis() - start) + " milliseconds");
    }

    /**
     * Process commits
     *
     * @param repo
     * @return count added
     */
    private int processCommits(GitHubRepo repo) {
        int count = 0;
        Long existingCount = commitRepository.countCommitsByCollectorItemId(repo.getId());
        long start = System.currentTimeMillis();
        if (existingCount == 0) {
            List<Commit> newCommits = gitHubClient.getCommits();
            for (Commit c : newCommits) {

                if (repo.getRepoUrl().equalsIgnoreCase(c.getScmUrl()) && repo.getBranch().equalsIgnoreCase(c.getScmBranch())){
                    c.setCollectorItemId(repo.getId());
                }

                if (commitRepository.save(c) != null) {
                    count++;
                }
            }
        } else {
            Collection<Commit> nonDupCommits = gitHubClient.getCommits().stream()
                    .<Map<String, Commit>>collect(HashMap::new, (m, c) -> m.put(c.getScmRevisionNumber(), c), Map::putAll)
                    .values();
            for (Commit commit : nonDupCommits) {
                LOG.debug(String.format("%d:::%s", commit.getTimestamp(), commit.getScmCommitLog()));
                if (isNewCommit(repo, commit)) {

                    if (repo.getRepoUrl().equalsIgnoreCase(commit.getScmUrl()) && repo.getBranch().equalsIgnoreCase(commit.getScmBranch())){
                        commit.setCollectorItemId(repo.getId());
                    }

                    commitRepository.save(commit);
                    count++;
                }
            }
        }
        LOG.info("-- Saved Commits saved_commits=" + count + ", saved_commits_duration=" + (System.currentTimeMillis() - start) + " milliseconds");
        return count;
    }

    private int processPRorIssueList(GitHubRepo repo, List<GitRequest> existingList, String type) {
        int count = 0;
        boolean isPull = "pull".equalsIgnoreCase(type);
        List<GitRequest> entries = isPull ? gitHubClient.getPulls() : gitHubClient.getIssues();

        if (CollectionUtils.isEmpty(entries)) return 0;

        List<String> pullNumbers = new ArrayList<>();

        for (GitRequest entry : entries) {
            Optional<GitRequest> existingOptional = existingList.stream().filter(r -> Objects.equals(r.getNumber(), entry.getNumber())).findFirst();
            GitRequest existing = existingOptional.orElse(null);
            if (isPull) {
                if (pullNumbers.size() < 10) {
                    pullNumbers.add(entry.getNumber());
                } else if (pullNumbers.size() == 10) {
                    pullNumbers.add("...");
                }
            }
            if (existing != null) {
                LOG.info(String.format("Existing PR - collectorItemId=%s, RepoURL=%s, RepoBranch=%s\nEntryURL=%s, EntryBranch=%s", repo.getId().toString(), repo.getRepoUrl(), repo.getBranch(), entry.getScmUrl(), entry.getScmBranch()));
                entry.setId(existing.getId());
            }else {
                count++;
                LOG.info(String.format("Non Existing PR - collectorItemId=%s, RepoURL=%s, RepoBranch=%s\nEntryURL=%s, EntryBranch=%s", repo.getId().toString(), repo.getRepoUrl(), repo.getBranch(), entry.getScmUrl(), entry.getScmBranch()));
            }
            if (repo.getRepoUrl().equalsIgnoreCase(entry.getScmUrl()) && repo.getBranch().equalsIgnoreCase(entry.getScmBranch())){
                entry.setCollectorItemId(repo.getId());
            }
            gitRequestRepository.save(entry);
        }
        LOG.info("-- Saved " + type + '=' + count + (isPull ? pullNumbers : 0));
        return count;
    }


    private List<GitHubRepo> enabledRepos(Collector collector) {
        List<GitHubRepo> repos = gitHubRepoRepository.findEnabledGitHubRepos(collector.getId());

        if (CollectionUtils.isEmpty(repos)) {
            return new ArrayList<>();
        }

        repos.sort(Comparator.comparing(GitHubRepo::getLastUpdated));

        LOG.info("# of collections: " + repos.size());
        return repos;
    }


    private boolean isNewCommit(GitHubRepo repo, Commit commit) {
        return commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo.getId(), commit.getScmRevisionNumber()) == null;
    }

    // Get Metadata for repo
    private void enrichMetadata(GitHubRepo repo) {
        try {
            CollectorItemMetadata collectorItemMetadata = collectorItemMetadataRepository
                    .findDistinctTopByCollectorIdAndCollectorItemId(repo.getCollectorId(), repo.getId());

            if(collectorItemMetadata == null) {
                collectorItemMetadata = new CollectorItemMetadata();
            }

            gitHubClient.fetchMetadata(repo, collectorItemMetadata);

            if (Objects.isNull(collectorItemMetadata)) return;
            collectorItemMetadataRepository.save(collectorItemMetadata);
        }
        catch (Exception e) {
            LOG.info("Exception occurred while retrieving Metadata error_metadata_repo="+ repo.getRepoUrl() + ", message=" + e.getMessage());
        }
    }
}

