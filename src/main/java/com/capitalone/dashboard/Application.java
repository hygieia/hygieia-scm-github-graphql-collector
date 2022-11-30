package com.capitalone.dashboard;

import com.capitalone.dashboard.config.MongoConfig;
import com.capitalone.dashboard.config.RestApiAppConfig;
import com.capitalone.dashboard.config.WebMVCConfig;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Application configuration and bootstrap
 */
@SpringBootApplication
@EnableAutoConfiguration(exclude={MongoAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class, MongoDataAutoConfiguration.class})
@EnableEncryptableProperties
//@EnableMongoRepositories(basePackages = {"com.capitalone.dashboard.repository"})
public class Application extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class, RestApiAppConfig.class, WebMVCConfig.class, MongoConfig.class);
    }
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
