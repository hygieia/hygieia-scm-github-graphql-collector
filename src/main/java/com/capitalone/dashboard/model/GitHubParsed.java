package com.capitalone.dashboard.model;

import com.capitalone.dashboard.misc.HygieiaException;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.IntStream;

public class GitHubParsed {
    private String url;
    private String apiUrl;
    private String baseApiUrl;
    private String graphQLUrl;
    private String orgName;
    private String repoName;

    private static final String SEGMENT_API = "/api/v3/repos";
    private static final String BASE_API = "/api/v3/";
    private static final String PUBLIC_GITHUB_BASE_API = "api.github.com/";
    private static final String PUBLIC_GITHUB_REPO_HOST = "api.github.com/repos";
    private static final String PUBLIC_GITHUB_HOST_NAME = "github.com";

    private static final String SEGMENT_GRAPHQL = "/api/graphql";
    private static final String PUBLIC_GITHUB_GRAPHQL = "api.github.com/graphql";



    public GitHubParsed(String url) throws MalformedURLException, HygieiaException {
        this.url = url;
        parse();
    }

    public void updateForRedirect(String redirectedUrl) throws MalformedURLException, HygieiaException {
        this.url = redirectedUrl;
        parse();
    }

    private void parse() throws MalformedURLException, HygieiaException {
        if (StringUtils.isEmpty(url)) {
            throw new HygieiaException("Empty github repo URL: ", HygieiaException.BAD_DATA);
        }
        if (url.endsWith(".git")) {
            url = url.substring(0, url.lastIndexOf(".git"));
        }
        
        if (url.contains(BASE_API) || url.contains(PUBLIC_GITHUB_BASE_API)) {
            convertUrl();
        }
        URL u = new URL(url);
        String host = u.getHost();
        String protocol = u.getProtocol();
        String path = u.getPath();
        String[] parts = path.split("/");
        if (parts.length < 3) {
            throw new HygieiaException("Bad github repo URL: " + url, HygieiaException.BAD_DATA);
        }
        orgName = parts[1];
        repoName = parts[2];
        if (host.startsWith(PUBLIC_GITHUB_HOST_NAME)) {
            baseApiUrl = protocol + "://" + PUBLIC_GITHUB_BASE_API;
            apiUrl = protocol + "://" + PUBLIC_GITHUB_REPO_HOST + path;
            graphQLUrl = protocol + "://" + PUBLIC_GITHUB_GRAPHQL;
        } else if (parts.length>3) {
            orgName = parts[parts.length-2];
            repoName = parts[parts.length-1];
            StringBuilder baseUrl = new StringBuilder(protocol + "://" + host);
            if (u.getPort()>0) baseUrl.append(':').append(u.getPort());
            IntStream.range(1, parts.length - 2).forEach(i -> baseUrl.append('/').append(parts[i]));
            apiUrl = baseUrl + SEGMENT_API + '/' + orgName + '/' + repoName;
            baseApiUrl = baseUrl + BASE_API;
            graphQLUrl = baseUrl + SEGMENT_GRAPHQL;
        } else {
            String baseUrl = protocol + "://" + host;
            if (u.getPort()>0) baseUrl += ":" + u.getPort();
            apiUrl = baseUrl + SEGMENT_API + path;
            baseApiUrl = baseUrl + BASE_API;
            graphQLUrl = baseUrl + SEGMENT_GRAPHQL;
        }
    }

    private void convertUrl()  {
        if (url.contains(BASE_API)) {
            if (url.contains(SEGMENT_API)) {
                url = url.replace(SEGMENT_API, "");
            } else {
                url = url.replace(BASE_API, "/");
            }
        } else {
            url = url.replace(PUBLIC_GITHUB_BASE_API, PUBLIC_GITHUB_HOST_NAME);
        }
    }

    public String getUrl() {
        return url;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getBaseApiUrl() {
        return baseApiUrl;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getGraphQLUrl() {
        return graphQLUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GitHubParsed)) return false;

        GitHubParsed that = (GitHubParsed) o;

        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }
}
