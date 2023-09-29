# Due to changes in the priorities, this project is currently not being supported. The project is archived as of 6/1/2023 and will be available in a read-only state. Please note, since archival, the project is not maintained or reviewed 

## Hygieia Collector for Github leveraging graphql

[![Build Status](https://travis-ci.com/Hygieia/hygieia-scm-github-graphql-collector.svg?branch=master)](https://travis-ci.com/Hygieia/hygieia-scm-github-graphql-collector)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Hygieia_hygieia-scm-github-graphql-collector&metric=alert_status)](https://sonarcloud.io/dashboard?id=Hygieia_hygieia-scm-github-graphql-collector)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/Hygieia/hygieia-scm-github-graphql-collector.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Hygieia/hygieia-scm-github-graphql-collector/alerts/)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/Hygieia/hygieia-scm-github-graphql-collector.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Hygieia/hygieia-scm-github-graphql-collector/context:java)
[![Maven Central](https://img.shields.io/maven-central/v/com.capitalone.dashboard/github-graphql-scm-collector.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.capitalone.dashboard%22%20AND%20a:%22github-graphql-scm-collector%22)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Gitter Chat](https://badges.gitter.im/Join%20Chat.svg)](https://www.apache.org/licenses/LICENSE-2.0)
<br>
<br>

# Table of Contents
* [Setup Instructions](#setup-instructions)
* [Sample Application Properties](#sample-application-properties)
* [Run collector with Docker](#run-collector-with-docker)

### Setup Instructions

To configure the GitHub graphql Collector, execute the following steps:

*	**Step 1 - Artifact Preparation:**

	Please review the two options in Step 1 to find the best fit for you. 
	
	***Option 1 - Download the artifact:***
	
	You can download the SNAPSHOTs from the SNAPSHOT directory [here](https://oss.sonatype.org/content/repositories/snapshots/com/capitalone/dashboard/github-graphql-scm-collector/) or from the maven central repository [here](https://search.maven.org/artifact/com.capitalone.dashboard/github-graphql-scm-collector).  
	
	***Option 2 - Build locally:***

	To configure the Github graphql collector, git clone the [collector repo](https://github.com/Hygieia/hygieia-scm-github-graphql-collector).  Then, execute the following steps:

	To package the collector into an executable JAR file, run the Maven build from the `\hygieia-scm-github-graphql-collector` directory of your source code installation:

	```bash
	mvn install
	```

	The output file `[collector name].jar` is generated in the `hygieia-scm-github-graphql-collector\target` folder.

	Once you have chosen an option in Step 1, please proceed: 

*   **Step 2: Set Parameters in Application Properties File**

Set the configurable parameters in the `application.properties` file to connect to the Dashboard MongoDB database instance, including properties required by the GitHub Collector.

For information about sourcing the application properties file, refer to the [Spring Boot Documentation](http://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference/htmlsingle/#boot-features-external-config-application-property-files).

To configure parameters for the GitHub Collector, refer to the sample [application.properties](#sample-application-properties) section.

*   **Step 3: Deploy the Executable File**

To deploy the `[collector name].jar` file, change directory to `hygieia-scm-github-graphql-collector\target`, and then execute the following from the command prompt:

```
java -jar [collector name].jar --spring.config.name=github --spring.config.location=[path to application.properties file]
```

### Sample Application Properties

The sample `application.properties` lists parameter values to configure the GitHub graphql Collector. Set the parameters based on your environment setup.

```properties
	# Database Name
	dbname=dashboarddb

	# Database HostName - default is localhost
	dbhost=localhost

	# Database Port - default is 27017
	dbport=27017

	# MongoDB replicaset
	dbreplicaset=[false if you are not using MongoDB replicaset]
	dbhostport=[host1:port1,host2:port2,host3:port3]

	# Database Username - default is blank
	dbusername=dashboarduser

	# Database Password - default is blank
	dbpassword=dbpassword
	
	# Proxy URL
	github.proxy=
	
	# Proxy Port
	github.proxyPort=
	
	# Proxy user if auth is required
	github.proxyUser=
	
	# Proxy password if auth is required
	github.proxyPassword=
	
	# Logging File location
	logging.file=./logs/github.log

	# Collector schedule (required)
	github.cron=0 0/5 * * * *

	github.host=github.com
	
	github.firstRunHistoryDays=
	github.rateLimitThreshold=
	github.graphqlUrl=baseurl/api/graphql
	github.baseApiUrl=baseurl/api/v3

	# Maximum number of previous days from current date, when fetching commits
	github.commitThresholdDays=15
	
	# A filter of commits with subject containing the pattern that will be filtered
	github.notBuiltCommits[0]=
	github.notBuiltCommits[1]=

	# Optional: Error threshold count after which collector stops collecting for a collector item. Default is 2.
	github.errorThreshold=1

	# This is the key generated using the Encryption class in core
	github.key=<your-generated-key>

	# Personal access token generated from github and used for making authentiated calls
	github.personalAccessToken=

	# Github repository Connect Timeout value in milliseconds, default value is 20000 (20s)
	github.connectTimeout=

	# Github repository Read Timeout value in milliseconds, default value is 20000 (20s) 
	github.readTimeout=
	
	github.commitPullSyncTime=
	github.offsetMinutes=
	github.fetchCount=
	github.searchCriteria=
```

## Run collector with Docker

You can install Hygieia by using a docker image from docker hub. This section gives detailed instructions on how to download and run with Docker. 

*	**Step 1: Download**

	Navigate to the docker hub location of your collector [here](https://hub.docker.com/u/hygieiadoc) and download the latest image (most recent version is preferred).  Tags can also be used, if needed.

*	**Step 2: Run with Docker**

	```Docker run -e SKIP_PROPERTIES_BUILDER=true -v properties_location:/hygieia/config image_name```
	
	- <code>-e SKIP_PROPERTIES_BUILDER=true</code>  <br />
	indicates whether you want to supply a properties file for the java application. If false/omitted, the script will build a properties file with default values
	- <code>-v properties_location:/hygieia/config</code> <br />
	if you want to use your own properties file that located outside of docker container, supply the path here. 
		- Example: <code>-v /Home/User/Document/application.properties:/hygieia/config</code>
