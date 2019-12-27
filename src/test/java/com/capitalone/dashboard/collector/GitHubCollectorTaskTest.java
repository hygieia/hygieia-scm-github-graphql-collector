package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitType;
import com.capitalone.dashboard.model.Component;
import com.capitalone.dashboard.model.GitHubRateLimit;
import com.capitalone.dashboard.model.GitHubRepo;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.GitHubRepoRepository;
import com.capitalone.dashboard.repository.GitRequestRepository;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GitHubCollectorTaskTest {

    @Mock private GitHubRepoRepository gitHubRepoRepository;
    @Mock private GitRequestRepository gitRequestRepository;
    @Mock private GitHubClient gitHubClient;
    @Mock private GitHubSettings gitHubSettings;
    @Mock private ComponentRepository dbComponentRepository;
    @Mock private CommitRepository commitRepository;

    @Mock private GitHubRepo repo1;
    @Mock private GitHubRepo repo2;

    @Mock private Commit commit;
    @Mock private GitRequest gitRequest;

    @InjectMocks private GitHubCollectorTask task;

    @Test
    public void collect_testCollect() throws MalformedURLException, HygieiaException {
        when(dbComponentRepository.findAll()).thenReturn(components());

        Set<ObjectId> gitID = new HashSet<>();
        gitID.add(new ObjectId("111ca42a258ad365fbb64ecc"));
        when(gitHubRepoRepository.findByCollectorIdIn(gitID)).thenReturn(getGitHubs());

        Collector collector = new Collector();
        collector.setEnabled(true);
        collector.setName("collector");
        collector.setId(new ObjectId("111ca42a258ad365fbb64ecc"));

        when(gitHubRepoRepository.findEnabledGitHubRepos(collector.getId())).thenReturn(getEnabledRepos());

        when(gitRequestRepository.findNonMergedRequestNumberAndLastUpdated(any())).thenReturn(new ArrayList<>());
        when(gitHubSettings.getErrorThreshold()).thenReturn(1);

        when(gitHubClient.isUnderRateLimit()).thenReturn(true);
        when(gitHubClient.getCommits()).thenReturn(getCommits());

        when(commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo1.getId(), "1")).thenReturn(null);

        when(commitRepository.countCommitsByCollectorItemId(repo1.getId())).thenReturn(1L);
        task.collect(collector);

        //verify that orphaned repo is disabled
        assertEquals("repo2.no.collectoritem", repo2.getNiceName());
        assertEquals(false, repo2.isEnabled());

        //verify that repo1 is enabled
        assertEquals("repo1-ci1", repo1.getNiceName());
        assertEquals(true, repo1.isEnabled());

        //verify that save is called once for the commit item
        Mockito.verify(commitRepository, times(1)).save(commit);
    }


    @Test
    public void collect_testCollect_with_Threshold_0() throws MalformedURLException, HygieiaException {
        when(dbComponentRepository.findAll()).thenReturn(components());

        Set<ObjectId> gitID = new HashSet<>();
        gitID.add(new ObjectId("111ca42a258ad365fbb64ecc"));
        when(gitHubRepoRepository.findByCollectorIdIn(gitID)).thenReturn(getGitHubs());

        Collector collector = new Collector();
        collector.setEnabled(true);
        collector.setName("collector");
        collector.setId(new ObjectId("111ca42a258ad365fbb64ecc"));

        when(gitHubRepoRepository.findEnabledGitHubRepos(collector.getId())).thenReturn(getEnabledRepos());

        when(gitHubSettings.getErrorThreshold()).thenReturn(0);

        when(gitHubClient.getCommits()).thenReturn(getCommits());

        when(commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo1.getId(), "1")).thenReturn(null);
        when(gitHubClient.isUnderRateLimit()).thenReturn(true);
        when(commitRepository.countCommitsByCollectorItemId(repo1.getId())).thenReturn(1L);

        task.collect(collector);

        //verify that orphaned repo is disabled
        assertEquals("repo2.no.collectoritem", repo2.getNiceName());
        assertEquals(false, repo2.isEnabled());

        //verify that repo1 is enabled
        assertEquals("repo1-ci1", repo1.getNiceName());
        assertEquals(true, repo1.isEnabled());

        //verify that save is called once for the commit item
        Mockito.verify(commitRepository, times(1)).save(commit);
    }

    @Test
    public void collect_testCollect_with_Threshold_1() throws MalformedURLException, HygieiaException {
        when(dbComponentRepository.findAll()).thenReturn(components());

        Set<ObjectId> gitID = new HashSet<>();
        gitID.add(new ObjectId("111ca42a258ad365fbb64ecc"));
        when(gitHubRepoRepository.findByCollectorIdIn(gitID)).thenReturn(getGitHubs());

        Collector collector = new Collector();
        collector.setEnabled(true);
        collector.setName("collector");
        collector.setId(new ObjectId("111ca42a258ad365fbb64ecc"));

        when(gitHubRepoRepository.findEnabledGitHubRepos(collector.getId())).thenReturn(getEnabledRepos());

        when(gitHubSettings.getErrorThreshold()).thenReturn(1);

        when(gitHubClient.getCommits()).thenReturn(getCommits());
        when(gitHubClient.getIssues()).thenReturn(getGitRequests());
//  Need to correct - Topo - 7/31      when(gitHubClient.getPulls(repo1, "close",true)).thenReturn(getGitRequests());

        when(commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo1.getId(), "1")).thenReturn(null);

        when(gitHubClient.isUnderRateLimit()).thenReturn(true);
        when(commitRepository.countCommitsByCollectorItemId(repo1.getId())).thenReturn(1L);

        task.collect(collector);

        //verify that orphaned repo is disabled
        assertEquals("repo2.no.collectoritem", repo2.getNiceName());
        assertEquals(false, repo2.isEnabled());

        //verify that repo1 is enabled
        assertEquals("repo1-ci1", repo1.getNiceName());
        assertEquals(true, repo1.isEnabled());

        //verify that save is called once for the commit item
        Mockito.verify(commitRepository, times(1)).save(commit);
    }

    @Test
    public void collect_testCollect_with_Threshold_1_Error_1() throws MalformedURLException, HygieiaException {
        when(dbComponentRepository.findAll()).thenReturn(components());

        Set<ObjectId> gitID = new HashSet<>();
        gitID.add(new ObjectId("111ca42a258ad365fbb64ecc"));
        when(gitHubRepoRepository.findByCollectorIdIn(gitID)).thenReturn(getGitHubs());

        Collector collector = new Collector();
        collector.setEnabled(true);
        collector.setName("collector");
        collector.setId(new ObjectId("111ca42a258ad365fbb64ecc"));

        when(gitHubRepoRepository.findEnabledGitHubRepos(collector.getId())).thenReturn(getEnabledReposWithErrorCount1());

        when(gitHubSettings.getErrorThreshold()).thenReturn(1);

        when(gitHubClient.getCommits()).thenReturn(getCommits());

        when(commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo1.getId(), "1")).thenReturn(null);

        when(gitHubClient.isUnderRateLimit()).thenReturn(true);
        when(commitRepository.countCommitsByCollectorItemId(repo1.getId())).thenReturn(1L);
        
        task.collect(collector);

        //verify that orphaned repo is disabled
        assertEquals("repo2.no.collectoritem", repo2.getNiceName());
        assertEquals(false, repo2.isEnabled());

        //verify that repo1 is enabled
        assertEquals("repo1-ci1", repo1.getNiceName());
        assertEquals(true, repo1.isEnabled());

        //verify that save is called once for the commit item
        Mockito.verify(commitRepository, times(1)).save(commit);
    }

    @Test
    public void collect_testCollect_handleAbuseRateLimit() throws MalformedURLException, HygieiaException {
        when(dbComponentRepository.findAll()).thenReturn(components());

        Set<ObjectId> gitID = new HashSet<>();
        gitID.add(new ObjectId("111ca42a258ad365fbb64ecc"));
        when(gitHubRepoRepository.findByCollectorIdIn(gitID)).thenReturn(getGitHubs());

        Collector collector = new Collector();
        collector.setEnabled(true);
        collector.setName("collector");
        collector.setId(new ObjectId("111ca42a258ad365fbb64ecc"));

        when(gitHubRepoRepository.findEnabledGitHubRepos(collector.getId())).thenReturn(getEnabledRepos());

        when(gitRequestRepository.findNonMergedRequestNumberAndLastUpdated(any())).thenReturn(new ArrayList<>());
        when(gitHubSettings.getErrorThreshold()).thenReturn(1);
        HttpStatusCodeException hc = prepareHttpStatusCodeException(HttpStatus.UNAUTHORIZED, "hit the Abuse Rate Limit.",
                "You have triggered an abuse detection mechanism and have been temporarily blocked from content creation. Please retry your request again later.");

        when(gitHubClient.isUnderRateLimit()).thenReturn(true);
        GitHubRepo repo = Mockito.mock(GitHubRepo.class);
        doThrow(hc).when(gitHubClient).fireGraphQL(any(GitHubRepo.class), anyBoolean(), anyMap(), anyMap());

        long startTime = System.currentTimeMillis();
        task.collect(collector);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        //verify
        assertTrue("Should have a wait time of 1000ms.", duration > 1000);
        assertTrue("Should have an error", repo1.getErrors().size() > 0);
        assertEquals("hit the Abuse Rate Limit.", repo1.getErrors().get(0).getErrorMessage());
    }

    private ArrayList<Commit> getCommits() {
        ArrayList<Commit> commits = new ArrayList<>();
        commit = new Commit();
        commit.setTimestamp(System.currentTimeMillis());
        commit.setScmUrl("http://testcurrenturl");
        commit.setScmBranch("master");
        commit.setScmRevisionNumber("1");
        commit.setScmParentRevisionNumbers(Collections.singletonList("2"));
        commit.setScmAuthor("author");
        commit.setScmCommitLog("This is a test commit");
        commit.setScmCommitTimestamp(System.currentTimeMillis());
        commit.setNumberOfChanges(1);
        commit.setType(CommitType.New);
        commits.add(commit);
        return commits;
    }
    private ArrayList<GitRequest> getGitRequests() {
        ArrayList<GitRequest> gitRequests = new ArrayList<>();
        gitRequest = new GitRequest();
        gitRequest.setTimestamp(System.currentTimeMillis());
        gitRequest.setScmUrl("http://testcurrenturl");
        gitRequest.setScmBranch("master");
        gitRequest.setScmRevisionNumber("1");
        gitRequest.setScmAuthor("author");
        gitRequest.setScmCommitLog("This is a test commit");
        gitRequest.setScmCommitTimestamp(System.currentTimeMillis());
        gitRequests.add(gitRequest);
        return gitRequests;
    }
    private List<GitHubRepo> getEnabledRepos() {
        List<GitHubRepo> gitHubs = new ArrayList<>();
        repo1 = new GitHubRepo();
        repo1.setEnabled(true);
        repo1.setId(new ObjectId("1c1ca42a258ad365fbb64ecc"));
        repo1.setCollectorId(new ObjectId("111ca42a258ad365fbb64ecc"));
        repo1.setNiceName("repo1-ci1");
        repo1.setRepoUrl("http://current");
        gitHubs.add(repo1);
        return gitHubs;
    }

    private List<GitHubRepo> getEnabledReposWithErrorCount1() {
        List<GitHubRepo> gitHubs = new ArrayList<>();
        repo1 = new GitHubRepo();
        repo1.setEnabled(true);
        repo1.setId(new ObjectId("1c1ca42a258ad365fbb64ecc"));
        repo1.setCollectorId(new ObjectId("111ca42a258ad365fbb64ecc"));
        repo1.setNiceName("repo1-ci1");
        repo1.setRepoUrl("http://current");
        CollectionError error = new CollectionError("Error","Error");
        repo1.getErrors().add(error);
        gitHubs.add(repo1);
        return gitHubs;
    }

    private ArrayList<GitHubRepo> getGitHubs() {
        ArrayList<GitHubRepo> gitHubs = new ArrayList<>();

        repo1 = new GitHubRepo();
        repo1.setEnabled(true);
        repo1.setId(new ObjectId("1c1ca42a258ad365fbb64ecc"));
        repo1.setCollectorId(new ObjectId("111ca42a258ad365fbb64ecc"));
        repo1.setNiceName("repo1-ci1");
        repo1.setRepoUrl("http://current");

        repo2 = new GitHubRepo();
        repo2.setEnabled(true);
        repo2.setId(new ObjectId("1c4ca42a258ad365fbb64ecc"));
        repo2.setCollectorId(new ObjectId("111ca42a258ad365fbb64ecc"));
        repo2.setNiceName("repo2.no.collectoritem");
        repo2.setRepoUrl("http://obsolete");

        gitHubs.add(repo1);
        gitHubs.add(repo2);

        return gitHubs;
    }

    private ArrayList<com.capitalone.dashboard.model.Component> components() {
        ArrayList<com.capitalone.dashboard.model.Component> cArray = new ArrayList<>();
        com.capitalone.dashboard.model.Component c = new Component();
        c.setId(new ObjectId());
        c.setName("COMPONENT1");
        c.setOwner("JOHN");

        CollectorType scmType = CollectorType.SCM;
        CollectorItem ci1 = new CollectorItem();
        ci1.setId(new ObjectId("1c1ca42a258ad365fbb64ecc"));
        ci1.setNiceName("ci1");
        ci1.setEnabled(true);
        ci1.setPushed(false);
        ci1.setCollectorId(new ObjectId("111ca42a258ad365fbb64ecc"));
        c.addCollectorItem(scmType, ci1);

        CollectorItem ci2 = new CollectorItem();
        ci2.setId(new ObjectId("1c2ca42a258ad365fbb64ecc"));
        ci2.setNiceName("ci2");
        ci2.setEnabled(true);
        ci2.setPushed(false);
        ci2.setCollectorId(new ObjectId("111ca42a258ad365fbb64ecc"));
        c.addCollectorItem(scmType, ci2);

        CollectorItem ci3 = new CollectorItem();
        ci3.setId(new ObjectId("1c3ca42a258ad365fbb64ecc"));
        ci3.setNiceName("ci3");
        ci3.setEnabled(true);
        ci3.setPushed(false);
        ci3.setCollectorId(new ObjectId("222ca42a258ad365fbb64ecc"));
        c.addCollectorItem(scmType, ci3);

        cArray.add(c);

        return cArray;
    }

    private GitHubRateLimit getOkRateLimit() {
        GitHubRateLimit rateLimit = new GitHubRateLimit();
        rateLimit.setRemaining(100);
        return rateLimit;
    }

    private GitHubRateLimit getBadRateLimit() {
        GitHubRateLimit rateLimit = new GitHubRateLimit();
        rateLimit.setRemaining(0);
        return rateLimit;
    }

    private static HttpStatusCodeException prepareHttpStatusCodeException(HttpStatus statusCode, String msg, String responseBody) {
        HttpStatusCodeException exception = Mockito.mock(HttpStatusCodeException.class);
        HttpHeaders header = Mockito.mock(HttpHeaders.class);
        Mockito.when(exception.getStatusCode()).thenReturn(statusCode);
        Mockito.when(exception.getMessage()).thenReturn(msg);
        ArrayList retryAfter = new ArrayList<String>();
        retryAfter.add("1");
        Mockito.when(exception.getResponseHeaders()).thenReturn(header);
        Mockito.when(header.get(anyString())).thenReturn(retryAfter);

        Mockito.when(exception.getResponseBodyAsString()).thenReturn(responseBody);
        return exception;
    }


}