# opencast-matomo-adapter #

Query [Matomo](https://matomo.org/) external API for daily episode statistics tracked in [Opencast](https://opencast.org) and push data points to [InfluxDB](https://www.influxdata.com).

## How it works ##

When started, the adapter will periodically query Matomo external API for episode and media segment statistics. For each viewed episode on a given date, one data point gets created with general metrics (number of views, plays and unique visitors) and another data point containing play rates for video segments. Episode meta data (seriesId) gets fetched from Opencast and the data points are written to InfluxDB. If an entry for an episode's segments already exists, the freshly fetched data is combined with the old entry.

The concept is based on the [opencast-influxdb-adapter](https://github.com/opencast/opencast-influxdb-adapter).

## Command line parameters ##

    --config-file=/etc/opencast-matomo-adapter.properties

Path to the configuration file (see below). Mandatory.

## Configuration file ##

There’s a documented sample configuration file located in `docs/opencast-matomo-adapter.properties`.

### InfluxDB configuration ###

    influxdb.uri=http://localhost:8086

URI of the InfluxDB database

    influxdb.user=root

User name for logging into the InfluxDB database

    influxdb.password=root

Password for the InfluxDB database

    influxdb.db-name=opencast

Name of the InfluxDB database. Note that the adapter will *not* create the database

    influxdb.log-level=info

Log level of the InfluxDB client. Possible values are `info` and `debug`

    influxdb.retention-policy=infinite
    
The [retention policy](https://docs.influxdata.com/influxdb/v1.7/guides/downsampling_and_retention/) to use for the InfluxDB points. You can omit this, in which case the default retention policy will be used.    
    
### Opencast configuration ###

    opencast.external-api.uri=https://organization.api.opencast.com
    
The (External API) URI the adapter connects to to find out an episode’s metadata

    opencast.external-api.user=admin
    
Opencast External API login credentials, user name

    opencast.external-api.password=password

Opencast External API login credentials, password
    
    opencast.organizationid=mh_default_org
    
Opencast organizationId, which is stored with data points. By default is set to `mh_default_org`  
 
    opencast.external-api.cache-expiration-duration=P7DT0M
    
The adapter has an optional cache included that stores event metadata for faster retrieval. It’s evicted time-based, and you can control the time after a cache entry has been *written* that it is evicted again. Note that the special value `PT0M` (or any duration that equates to zero) disables the cache.   

    opencast.external-api.max-cache-size=10000
    
Maximum number of cache entries before eviction. Can be 0 of off to disable caching     
    
    opencast.rate-limit=0
 
Limits HTTP requests per second for Opencast external API. Can be off or 0 to disable rate limiting   
    
    opencast.timeout=10
    
HTTP request timeout timer for Opencast external API. Can be off and is set to 10 seconds by default

### Matomo configuration ###

    matomo.uri=https://example.matomo.com

The Matomo (External API) URI the adapter connects to to query user data

    matomo.siteid=1

Registered siteId associated with the Opencast instance in Matomo

    matomo.token=exampletoken

Access Token for Matomo API authentication

    matomo.rate-limit=0

Limits HTTP requests per second for Matomo external API. Can be off or 0 to disable rate limiting

    matomo.timeout=10

HTTP request timeout timer for Opencast external API. Can be off and is set to 10 seconds by default

### General configuration ###

    adapter.date-file=/path/to/last_date.txt

Mandatory. Absolute path to the file, which contains the date of the last update. The date is stored in the following format: YYYY-MM-DD. The adapter will run an update routine for each day between the current date and the stored date. After a run the last update date is set to current day. If no date is given, the adapter defaults to previous day.

    adapter.log-configuration-file=logback-sample.xml

[Logback](https://logback.qos.ch/manual/configuration.html) configuration file for the adapter. You can leave this out, in which case only standard output and standard error are used for logging. This might make sense if you use systemd or something similar to control logging.

    adapter.time-interval=1

Time interval in days between runs. Is set to 1 day by default. 

## Opencast ##

### External API ###

The adapter will try to retrieve metadata for every event it encounters using the Opencast External API. Specifically, it will use the =/api/events/{episodeId}= endpoint, passing the episode ID and the configured authorization parameters. If the episode is not found within Opencast, it gets evicted from processing stream.

Note that this endpoint is available as of version *1.3.0* of the External API.

Also, note that the user that is configured to query the External API has to have access to the episodes in the log files. This implies the user has `ROLE_ADMIN` or `ROLE_ORGANIZATION_ADMIN`.

### Matomo integration ###

In order to enable user tracking you have to set up the according [player configuration file](https://github.com/opencast/opencast/blob/develop/etc/ui-config/mh_default_org/theodul/config.yml), provided that Opencast is already [registered](https://matomo.org/docs/manage-websites/) in your Matomo instance. Furthermore, the adapter depends on the [Media Analytics Plugin](https://plugins.matomo.org/MediaAnalytics) for Matomo, as all statistics for episodes are queried through this plugin.

### InfluxDB integration ###

If you wish to integrate general metrics statistics (views, plays, visitors) in Opencast, see the [statistics integration page](https://docs.opencast.org/r/8.x/admin/#configuration/admin-ui/statistics/). Some *Statistics Provider* example files are located in `docs/providers`. Alternatively use [opencast-stats-app]() to view generated statistics.

## InfluxDB ##

### Supported versions ###

The adapter was tested on InfluxDB 1.8, although it should be fairly backwards-compatible, we cannot say for sure it’ll work with anything less than that version.

### Setup ###

The adapter assumes that an InfluxDB database was created (see the `influxdb.db-name` property). Let’s assume the database is really called `opencast`. To create it, simply execute (for example, using the `influx` command line tool):

``` sql
CREATE DATABASE opencast
```

The adapter writes into measurements called `impressions_daily` and `segments_daily`. The data within `impressions_daily` is already downsampled to daily entries, which means that no additional retention policies or continuous queries have to be defined in order to ensure compatibility with Opencast. Segment data is not compatible with Opecast since it is stored as *String* and not time series based.

If you still wish to downsample precise data over time (for example aggregate general metrics after a semester), see the [InfluxDB documentation on downsampling and data retention](https://docs.influxdata.com/influxdb/v1.8/guides/downsampling_and_retention/) for more information on that.

## Installation ##

### Requirements ###

To build the adapter, you need:

  * Maven
  * JDK, at least version 1.8

To run the adapter, you need:

  * JRE, at least version 1.8

### Building and Running ###

To build the adapter, execute the following:

``` shell
mvn package
```

You’ll end up with a `tar.gz` in the `build/` directory, containing…

  * a runnable `jar` file
  * a systemd service file
  * a `logback.xml` file
  
To run the adapter, execute the following:
  
``` shell
java -jar $adapter.jar --config-file=/etc/opencast-matomo-adapter.properties
```
