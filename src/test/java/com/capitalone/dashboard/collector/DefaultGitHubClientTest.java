package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestClient;
import com.capitalone.dashboard.client.RestOperationsSupplier;
import com.capitalone.dashboard.collector.DefaultGitHubClient.RedirectedStatus;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.ChangeRepoResponse;
import com.capitalone.dashboard.model.GitHubParsed;
import com.capitalone.dashboard.model.GitHubRepo;
import com.google.common.io.Resources;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import java.net.MalformedURLException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultGitHubClientTest {
    private static final Log LOG = LogFactory.getLog(DefaultGitHubClientTest.class);

    @Mock private RestOperationsSupplier restOperationsSupplier;
    @Mock private RestOperations rest;

    private RestClient restClient;
    private GitHubSettings settings;
    private DefaultGitHubClient defaultGitHubClient;
    private static final String URL_USER = "http://mygithub.com/api/v3/users/";

    @Before
    public void init() {
        when(restOperationsSupplier.get()).thenReturn(rest);
        settings = new GitHubSettings();
        defaultGitHubClient = new DefaultGitHubClient(settings, new RestClient(restOperationsSupplier));
        defaultGitHubClient.setLdapMap(new HashMap<>());

    }

    @Test
    public void getLDAPDN_With_Underscore() {
        String userhyphen = "this-has-underscore";
        String userUnderscore = "this_has_underscore";

        when(rest.exchange(eq(URL_USER + userhyphen), eq(HttpMethod.GET),
                eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>(goodLdapResponse(), HttpStatus.OK));
        String ldapUser = defaultGitHubClient.getLDAPDN(getGitRepo(),userUnderscore);
        assertEquals(ldapUser, "CN=ldapUser,OU=Developers,OU=All Users,DC=cof,DC=ds,DC=mycompany,DC=com");
        assertTrue(defaultGitHubClient.getLdapMap().containsKey(userhyphen));
        assertFalse(defaultGitHubClient.getLdapMap().containsKey(userUnderscore));
        assertEquals(defaultGitHubClient.getLdapMap().get(userhyphen), "CN=ldapUser,OU=Developers,OU=All Users,DC=cof,DC=ds,DC=mycompany,DC=com");
        assertNull(defaultGitHubClient.getLdapMap().get(userUnderscore));
    }

    @Test
    public void getLDAPDN_With_Hyphen() {
        String userhyphen = "this-has-hyphen";

        when(rest.exchange(eq(URL_USER + userhyphen), eq(HttpMethod.GET),
                eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>(goodLdapResponse(), HttpStatus.OK));
        String ldapUser = defaultGitHubClient.getLDAPDN(getGitRepo(),userhyphen);
        assertEquals(ldapUser, "CN=ldapUser,OU=Developers,OU=All Users,DC=cof,DC=ds,DC=mycompany,DC=com");
        assertTrue(defaultGitHubClient.getLdapMap().containsKey(userhyphen));
    }

    @Test
    public void getLDAPDNSimple() {
        String user = "someuser";

        when(rest.exchange(eq(URL_USER + user), eq(HttpMethod.GET),
                eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>(goodLdapResponse(), HttpStatus.OK));
        String ldapUser = defaultGitHubClient.getLDAPDN(getGitRepo(),user);
        assertEquals(ldapUser, "CN=ldapUser,OU=Developers,OU=All Users,DC=cof,DC=ds,DC=mycompany,DC=com");
        assertTrue(defaultGitHubClient.getLdapMap().containsKey(user));
    }

    @Test
    public void getLDAPDN_NotFound() {
        String user = "someuser-unknown";

        when(rest.exchange(eq(URL_USER + user), eq(HttpMethod.GET),
                eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));
        String ldapUser = defaultGitHubClient.getLDAPDN(getGitRepo(),user);
        assertNull(ldapUser);
        assertFalse(defaultGitHubClient.getLdapMap().containsKey(user));
    }

    @Test
    public void testCheckForRedirectedRepo() throws MalformedURLException, HygieiaException {
        GitHubRepo repo = getGitRepo();
        GitHubParsed gitHubParsed = new GitHubParsed(repo.getRepoUrl());

        String notRedirectedBody = "{\"html_url\": \"" + repo.getRepoUrl() + "\"}";

        String url = gitHubParsed.getBaseApiUrl() + "repos/" + gitHubParsed.getOrgName() + '/' + gitHubParsed.getRepoName();
        when(rest.exchange(eq(url), eq(HttpMethod.GET),
                eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>(notRedirectedBody, HttpStatus.OK));

        RedirectedStatus status = defaultGitHubClient.checkForRedirectedRepo(repo);
        assertFalse(status.isRedirected());

        String redirectedRepoUrl = "http://mygithub.com/user/redirectedrepo";
        String redirectedBody = "{\"html_url\": \"" + redirectedRepoUrl + "\"}";

        when(rest.exchange(eq(url), eq(HttpMethod.GET),
            eq(null), eq(String.class)))
            .thenReturn(new ResponseEntity<>(redirectedBody, HttpStatus.OK));

        status = defaultGitHubClient.checkForRedirectedRepo(repo);
        assertTrue(status.isRedirected());
        assertEquals(status.getRedirectedUrl(), redirectedRepoUrl);
    }

    @Test
    public void getLDAPDN_OtherCharacters() {
        String user = "someuser@#$%&($@#---unknown";

        when(rest.exchange(eq(URL_USER + user), eq(HttpMethod.GET),
                eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>(goodLdapResponse(), HttpStatus.OK));
        String ldapUser = defaultGitHubClient.getLDAPDN(getGitRepo(),user);
        assertEquals(ldapUser, "CN=ldapUser,OU=Developers,OU=All Users,DC=cof,DC=ds,DC=mycompany,DC=com");
        assertTrue(defaultGitHubClient.getLdapMap().containsKey(user));
    }

    @Test
    public void testGetChangedRepo() throws Exception {
        String url = settings.getBaseApiUrl() + "events";
        String changeEventsData = getData("ChangeEvents.json");
        when(rest.exchange(eq(url), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>(changeEventsData, HttpStatus.OK));
        ChangeRepoResponse changeRepoResponse = defaultGitHubClient.getChangedRepos(0, 0);
        assertEquals(2, changeRepoResponse.getChangeRepos().size());
        assertEquals(62674204, changeRepoResponse.getLatestEventId());
        assertEquals(1611673668000L, changeRepoResponse.getLatestEventTimestamp());
    }

    private GitHubRepo getGitRepo() {
        GitHubRepo repo = new GitHubRepo();
        repo.setBranch("master");
        repo.setRepoUrl("http://mygithub.com/user/repo");
        return repo;
    }

    private String goodLdapResponse() {
        return "{ \"ldap_dn\": \"CN=ldapUser,OU=Developers,OU=All Users,DC=cof,DC=ds,DC=mycompany,DC=com\"}";
    }

    private String getData(String filename) throws Exception {
        return IOUtils.toString(Resources.getResource(filename));
    }

}