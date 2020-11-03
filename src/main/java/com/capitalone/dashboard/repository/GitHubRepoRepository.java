package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.GitHubRepo;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface GitHubRepoRepository extends BaseCollectorItemRepository<GitHubRepo> {

    @Query(value="{ 'collectorId' : ?0, enabled: true}")
    List<GitHubRepo> findEnabledGitHubRepos(ObjectId collectorId);

    @Query(value="{ 'collectorId' : ?0, 'errors.errorCode' : '404'}")
    List<GitHubRepo> findObsoleteGitHubRepos(ObjectId collectorId);

    @Query(value="{ 'collectorId' : ?0, options.url : ?1}")
    List<GitHubRepo> findByCollectorIdAndUrl(ObjectId collectorId, String url);
}
