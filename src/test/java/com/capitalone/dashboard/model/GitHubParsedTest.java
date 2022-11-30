package com.capitalone.dashboard.model;

import com.capitalone.dashboard.misc.HygieiaException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class GitHubParsedTest {

	@Test
	public void ParseHttpsGH() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://github.com/myorg/myrepo");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://api.github.com/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://api.github.com/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://api.github.com/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpGH() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("http://github.com/myorg/myrepo");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("http://api.github.com/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("http://api.github.com/", gitHubParsed.getBaseApiUrl());
		assertEquals("http://api.github.com/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpsGHE() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://internal.github.com/myorg/myrepo");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://internal.github.com/api/v3/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://internal.github.com/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://internal.github.com/api/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpGHE() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("http://internal.github.com/myorg/myrepo");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("http://internal.github.com/api/v3/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("http://internal.github.com/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("http://internal.github.com/api/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpsGHEWithPort() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://internal.github.com:8080/myorg/myrepo");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://internal.github.com:8080/api/v3/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://internal.github.com:8080/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://internal.github.com:8080/api/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpsGHEWithPortAndGit() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://internal.github.com:8080/myorg/myrepo.git");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://internal.github.com:8080/api/v3/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://internal.github.com:8080/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://internal.github.com:8080/api/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpsGHEWithPortAndLongPathAndGit() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://internal.github.com:8080/ghe/myorg/myrepo.git");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://internal.github.com:8080/ghe/api/v3/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://internal.github.com:8080/ghe/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://internal.github.com:8080/ghe/api/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpsGHEWithPortAndLongPathAndGitAndUpdateForRedirect()
			throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://internal.github.com:8080/ghe/myorg/myrepo.git");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://internal.github.com:8080/ghe/api/v3/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://internal.github.com:8080/ghe/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://internal.github.com:8080/ghe/api/graphql", gitHubParsed.getGraphQLUrl());
		assertEquals("https://internal.github.com:8080/ghe/myorg/myrepo", gitHubParsed.getUrl());

		gitHubParsed.updateForRedirect("https://internal.github.com:8080/ghe/myneworg/myrepo.git");
		assertEquals("myneworg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://internal.github.com:8080/ghe/api/v3/repos/myneworg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://internal.github.com:8080/ghe/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://internal.github.com:8080/ghe/api/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseWithExceptionOne() throws MalformedURLException, HygieiaException {
		assertThrows(HygieiaException.class, () -> {
			GitHubParsed gitHubParsed = new GitHubParsed("https://github.com/myorg");
		});

	}

	@Test
	public void ParseWithExceptionTwo() throws MalformedURLException, HygieiaException {
		assertThrows(MalformedURLException.class, () -> {
			GitHubParsed gitHubParsed = new GitHubParsed("htt://github.com/myorg");
		});
	}

	@Test
	public void ParseWithEmpty() throws MalformedURLException, HygieiaException {

		assertThrows(HygieiaException.class, () -> {
			GitHubParsed gitHubParsed = new GitHubParsed(null);
		});
	}

	@Test
	public void ParseHttpsFromGHAPI() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://api.github.com/repos/myorg/myrepo");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://api.github.com/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://api.github.com/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://api.github.com/graphql", gitHubParsed.getGraphQLUrl());
	}

	@Test
	public void ParseHttpsFromGHEAPI() throws MalformedURLException, HygieiaException {
		GitHubParsed gitHubParsed = new GitHubParsed("https://github.mycompany.com/api/v3/repos/myorg/myrepo");
		assertEquals("myorg", gitHubParsed.getOrgName());
		assertEquals("myrepo", gitHubParsed.getRepoName());
		assertEquals("https://github.mycompany.com/api/v3/repos/myorg/myrepo", gitHubParsed.getApiUrl());
		assertEquals("https://github.mycompany.com/api/v3/", gitHubParsed.getBaseApiUrl());
		assertEquals("https://github.mycompany.com/api/graphql", gitHubParsed.getGraphQLUrl());
		assertEquals("https://github.mycompany.com/myorg/myrepo", gitHubParsed.getUrl());
	}
}