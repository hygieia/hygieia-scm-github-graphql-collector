package com.capitalone.dashboard.collector;


import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.BaseModel;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.GitHubRepo;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.GitHubRepoRepository;
import com.capitalone.dashboard.repository.GitRequestRepository;
import com.capitalone.dashboard.util.CommitPullMatcher;
import com.capitalone.dashboard.util.GithubRepoMatcher;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CollectorTask that fetches Commit information from GitHub
 */
@Component
public class GitHubCollectorTask extends CollectorTask<Collector> {
    private static final Log LOG = LogFactory.getLog(GitHubCollectorTask.class);

    private final BaseCollectorRepository<Collector> collectorRepository;
    private final GitHubRepoRepository gitHubRepoRepository;
    private final CommitRepository commitRepository;
    private final GitRequestRepository gitRequestRepository;
    private final GitHubClient gitHubClient;
    private final GitHubSettings gitHubSettings;
    private final ComponentRepository dbComponentRepository;
    private static final long FOURTEEN_DAYS_MILLISECONDS = 14 * 24 * 60 * 60 * 1000;
    private static final String REPO_NAME = "repoName";
    private static final String ORG_NAME = "orgName";
    private AtomicInteger count = new AtomicInteger(0);


    @Autowired
    public GitHubCollectorTask(TaskScheduler taskScheduler,
                               BaseCollectorRepository<Collector> collectorRepository,
                               GitHubRepoRepository gitHubRepoRepository,
                               CommitRepository commitRepository,
                               GitRequestRepository gitRequestRepository,
                               GitHubClient gitHubClient,
                               GitHubSettings gitHubSettings,
                               ComponentRepository dbComponentRepository) {
        super(taskScheduler, "GitHub");
        this.collectorRepository = collectorRepository;
        this.gitHubRepoRepository = gitHubRepoRepository;
        this.commitRepository = commitRepository;
        this.gitHubClient = gitHubClient;
        this.gitHubSettings = gitHubSettings;
        this.dbComponentRepository = dbComponentRepository;
        this.gitRequestRepository = gitRequestRepository;
    }

    @Override
    public Collector getCollector() {
        Collector protoType = new Collector();
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
        protoType.setAllFields(allOptions);

        Map<String, Object> uniqueOptions = new HashMap<>();
        uniqueOptions.put(GitHubRepo.REPO_URL, "");
        uniqueOptions.put(GitHubRepo.BRANCH, "");
        protoType.setUniqueFields(uniqueOptions);
        return protoType;
    }

    @Override
    public BaseCollectorRepository<Collector> getCollectorRepository() {
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
    private void clean(Collector collector) {
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
            if (repo.isPushed()) {return;}
            repo.setEnabled(uniqueIDs.contains(repo.getId()));
            repoList.add(repo);
        });
        gitHubRepoRepository.save(repoList);
    }


   @Override
   public void collect(Collector collector){
       setupProxy();
       clean(collector);
       List<GitHubRepo> enabledRepos = enabledRepos(collector);
       if(gitHubSettings.getSearchCriteria() != null){
           String searchCriteria[] = gitHubSettings.getSearchCriteria().split(Pattern.quote("|"));
           if(REPO_NAME.equalsIgnoreCase(searchCriteria[0])){
               enabledRepos = enabledRepos.stream().filter(repo -> GithubRepoMatcher.repoNameMatcher(repo.getRepoUrl(),searchCriteria[1])).collect(Collectors.toList());
           }else if(ORG_NAME.equalsIgnoreCase(searchCriteria[0])) {
               enabledRepos = enabledRepos.stream().filter(repo -> GithubRepoMatcher.orgNameMatcher(repo.getRepoUrl(), searchCriteria[1])).collect(Collectors.toList());
           }
       }
       LOG.info("GitHubCollectorTask:collect start, total enabledRepos=" + enabledRepos.size());
       LOG.warn("error threshold = " + gitHubSettings.getErrorThreshold());
       collectProcess(collector, enabledRepos );

   }


    @SuppressWarnings({"PMD.AvoidDeeplyNestedIfStmts"})
    public void collectProcess(Collector collector, List<GitHubRepo> enabledRepos) {
        long start = System.currentTimeMillis();
        int repoCount = 0;
        int commitCount = 0;
        int pullCount = 0;
        int issueCount = 0;
        count.set(0);

        for (GitHubRepo repo : enabledRepos) {
            repoCount++;
            long repoStart = System.currentTimeMillis();
            String repoUrl = repo==null?"null":(repo.getRepoUrl() + "/tree/" + repo.getBranch());
            String statusString = "UNKNOWN";
            long lastUpdated = repo==null?0:repo.getLastUpdated();
            try {
                if (repo==null) throw new HygieiaException("Repository returned from github is null", HygieiaException.BAD_DATA);
                boolean firstRun = ((repo.getLastUpdated() == 0) || ((start - repo.getLastUpdated()) > FOURTEEN_DAYS_MILLISECONDS));
                if (!repo.checkErrorOrReset(gitHubSettings.getErrorResetWindow(), gitHubSettings.getErrorThreshold())) {
                    statusString = "SKIPPED, errorThreshold exceeded";
                } else if (!gitHubClient.isUnderRateLimit()) {
                    LOG.error("GraphQL API rate limit reached after " + (System.currentTimeMillis() - start) / 1000 + " seconds since start. Stopping processing");
                    // add wait time (default = 0.3s)
                    statusString = "SKIPPED, rateLimit exceeded, sleep for " + gitHubSettings.getWaitTime();
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

                        gitHubClient.fireGraphQL(repo, firstRun, existingPRMap, existingIssueMap);

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
                        statusString = "SUCCESS, pulls=" + pullCount1 + ", commits=" + commitCount1 + ", issues=" + issueCount1;
                    } catch (HttpStatusCodeException hc) {
                        LOG.error("Error fetching commits for:" + repo.getRepoUrl(), hc);
                        statusString = "EXCEPTION, " + hc.getClass().getCanonicalName();
                        CollectionError error = new CollectionError(hc.getStatusCode().toString(), hc.getMessage());
                        if (hc.getStatusCode() == HttpStatus.UNAUTHORIZED || hc.getStatusCode() == HttpStatus.FORBIDDEN) {
                            LOG.error("Received 401/403 HttpStatusCodeException from GitHub. Status code=" + hc.getStatusCode() + " ResponseBody="+hc.getResponseBodyAsString());
                            int retryAfterSeconds = NumberUtils.toInt(hc.getResponseHeaders().get(DefaultGitHubClient.RETRY_AFTER).get(0));
                            long startSleeping = System.currentTimeMillis();
                            LOG.info("Should Retry-After: " + retryAfterSeconds + " sec. Start sleeping at: " + new DateTime(startSleeping).toString("yyyy-MM-dd hh:mm:ss.SSa") );
                            sleep(retryAfterSeconds*1000L);
                            long endSleeping = System.currentTimeMillis();
                            LOG.info("Waking up after [" + (endSleeping-startSleeping)/1000L + "] sec, " +
                                    "at: " + new DateTime(endSleeping).toString("yyyy-MM-dd hh:mm:ss.SSa"));
                        }
                        repo.getErrors().add(error);
                    } catch (RestClientException | MalformedURLException ex) {
                        LOG.error("Error fetching commits for:" + repo.getRepoUrl(), ex);
                        statusString = "EXCEPTION, " + ex.getClass().getCanonicalName();
                        CollectionError error = new CollectionError(CollectionError.UNKNOWN_HOST, ex.getMessage());
                        repo.getErrors().add(error);
                    } catch (HygieiaException he) {
                        LOG.error("Error fetching commits for:" + repo.getRepoUrl(), he);
                        statusString = "EXCEPTION, " + he.getClass().getCanonicalName();
                        CollectionError error = new CollectionError(String.valueOf(he.getErrorCode()), he.getMessage());
                        repo.getErrors().add(error);
                    }
                    gitHubRepoRepository.save(repo);
                }
            } catch (Throwable e) {
                statusString = "EXCEPTION, " + e.getClass().getCanonicalName();
                LOG.error("Unexpected exception when collecting url=" + repoUrl, e);
            } finally {
                String age = readableAge(lastUpdated, start);
                long itemProcessTime = System.currentTimeMillis() - repoStart;
                LOG.info(String.format("%d of %d, repository=%s, itemProcessTime=%d lastUpdated=%d [%s], status=%s",
                        repoCount, enabledRepos.size(), repoUrl, itemProcessTime, lastUpdated, age, statusString));
            }
        }
        long end = System.currentTimeMillis();
        long elapsedSeconds = (end - start) / 1000;
        count.set(commitCount);
        LOG.info(String.format("GitHubCollectorTask:collect stop, totalProcessSeconds=%d, totalRepoCount=%d, totalNewPulls=%d, totalNewCommits=%d totalNewIssues=%d",
                elapsedSeconds, repoCount, pullCount, commitCount, issueCount));

        collector.setLastExecutionRecordCount(repoCount+pullCount+commitCount+issueCount);
    }



    private String readableAge(long lastUpdated, long start) {
        if (lastUpdated<=0) return "never before";
        else if (start-lastUpdated>48*3600000) return ((start-lastUpdated)/(24*3600000)) + " days ago";
        else if (start-lastUpdated>2*3600000) return ((start-lastUpdated)/3600000) + " hours ago";
        else return ((start-lastUpdated)/60000) + " minutes ago";
    }

    private void setupProxy() {
        String proxyUrl = gitHubSettings.getProxyUrl();
        String proxyPort = gitHubSettings.getProxyPort();
        String proxyUser = gitHubSettings.getProxyUser();
        String proxyPassword = gitHubSettings.getProxyPassword();

        if (!StringUtils.isEmpty(proxyUrl) && !StringUtils.isEmpty(proxyPort)) {
            System.setProperty("http.proxyHost", proxyUrl);
            System.setProperty("https.proxyHost", proxyUrl);
            System.setProperty("http.proxyPort", proxyPort);
            System.setProperty("https.proxyPort", proxyPort);

            if (!StringUtils.isEmpty(proxyUser) && !StringUtils.isEmpty(proxyPassword)) {
                System.setProperty("http.proxyUser", proxyUser);
                System.setProperty("https.proxyUser", proxyUser);
                System.setProperty("http.proxyPassword", proxyPassword);
                System.setProperty("https.proxyPassword", proxyPassword);
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
        orphanSaveList.forEach( c -> LOG.info( "Updating orphan " + c.getScmRevisionNumber() + " " +
                new DateTime(c.getScmCommitTimestamp()).toString("yyyy-MM-dd hh:mm:ss.SSa") + " with pull " + c.getPullNumber()));
        long start = System.currentTimeMillis();
        commitRepository.save(orphanSaveList);
        LOG.info("-- Saved Orphan Commits= " + orphanSaveList.size() + "; Duration= " + (System.currentTimeMillis()-start) + " milliseconds");
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
            for(Commit c : newCommits) {
                c.setCollectorItemId(repo.getId());
                if(commitRepository.save(c) != null) {
                    count++;
                }
            }
        } else {
            Collection<Commit> nonDupCommits = gitHubClient.getCommits().stream()
                    .<Map<String, Commit>> collect(HashMap::new,(m,c)->m.put(c.getScmRevisionNumber(), c), Map::putAll)
                    .values();
            for (Commit commit : nonDupCommits) {
                LOG.debug(commit.getTimestamp() + ":::" + commit.getScmCommitLog());
                if (isNewCommit(repo, commit)) {
                    commit.setCollectorItemId(repo.getId());
                    commitRepository.save(commit);
                    count++;
                }
            }
        }
        LOG.info("-- Saved Commits = " + count + "; Duration = " + (System.currentTimeMillis()-start) + " milliseconds");
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
            if (existing == null) {
                entry.setCollectorItemId(repo.getId());
                count++;
            } else {
                entry.setId(existing.getId());
                entry.setCollectorItemId(repo.getId());
            }
            gitRequestRepository.save(entry);
        }
        LOG.info("-- Saved " + type  + ":" + count + (isPull? (" " + pullNumbers):""));
        return count;
    }


    private List<GitHubRepo> enabledRepos(Collector collector) {
        List<GitHubRepo> repos = gitHubRepoRepository.findEnabledGitHubRepos(collector.getId());

        if (CollectionUtils.isEmpty(repos)) { return new ArrayList<>(); }

        repos.sort(Comparator.comparing(GitHubRepo::getLastUpdated));

        LOG.info("# of collections: " + repos.size());
        return repos;
    }

    private boolean isNewCommit(GitHubRepo repo, Commit commit) {
        return commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo.getId(), commit.getScmRevisionNumber()) == null;
    }
}

