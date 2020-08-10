/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.matomoadapter;

import org.opencastproject.matomoadapter.influxdbclient.InfluxDBConfig;
import org.opencastproject.matomoadapter.matclient.MatomoConfig;
import org.opencastproject.matomoadapter.occlient.OpencastConfig;

import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * Represents all options that can are contained in the configuration file (immutable)
 * <p>
 * The class cannot be constructed directly, see <code>readFile</code> to create it.
 */
public final class ConfigFile {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  // Constants for the options so we don't repeat ourselves
  // InfluxDB options
  private static final String INFLUXDB_URI = "influxdb.uri";
  private static final String INFLUXDB_DB_NAME = "influxdb.db-name";
  private static final String INFLUXDB_USER = "influxdb.user";
  private static final String INFLUXDB_PASSWORD = "influxdb.password";
  private static final String INFLUXDB_RETENTION_POLICY = "influxdb.retention-policy";
  private static final String INFLUXDB_LOG_LEVEL = "influxdb.log-level";
  // Matomo options
  private static final String MATOMO_URI = "matomo.uri";
  private static final String MATOMO_SITEID = "matomo.siteid";
  private static final String MATOMO_TOKEN = "matomo.token";
  private static final String MATOMO_RATE = "matomo.rate-limit";
  private static final String MATOMO_TIMEOUT = "matomo.timeout";
  // Opencast options
  private static final String OPENCAST_URI = "opencast.external-api.uri";
  private static final String OPENCAST_USER = "opencast.external-api.user";
  private static final String OPENCAST_PASSWORD = "opencast.external-api.password";
  private static final String OPENCAST_ORGAID = "opencast.organizationid";
  private static final String OPENCAST_CACHE_SIZE = "opencast.external-api.max-cache-size";
  private static final String OPENCAST_EXPIRATION_DURATION = "opencast.external-api.cache-expiration-duration";
  private static final String OPENCAST_RATE = "opencast.rate-limit";
  private static final String OPENCAST_TIMEOUT = "opencast.timeout";
  // Path to last date file
  private static final String ADAPTER_PATH_DATE = "adapter.date-file";
  private static final String ADAPTER_TIME_INTERVAL = "adapter.time-interval";
  // Config objects
  private final InfluxDBConfig influxDBConfig;
  private final MatomoConfig matomoConfig;
  private final OpencastConfig opencastConfig;
  private final Path lastDatePath;
  private final int interval;

  private ConfigFile(
          final InfluxDBConfig influxDBConfig,
          final MatomoConfig matomoConfig,
          final OpencastConfig opencastConfig,
          final Path lastDatePath,
          final int interval) {
    this.influxDBConfig = influxDBConfig;
    this.matomoConfig = matomoConfig;
    this.opencastConfig = opencastConfig;
    this.lastDatePath = lastDatePath;
    this.interval = interval;
  }

  /**
   * Read and parse config file. Also check for faulty values in critical fields.
   *
   * @param p Path to config file
   * @return Creates and returns config object with all properties saved within
   */
  public static ConfigFile readFile(final Path p) {
    final Properties parsed = new Properties();

    // Try parsing the config file. Write into Properties object
    try (FileReader reader = new FileReader(p.toFile())) {
      parsed.load(reader);
    } catch (final FileNotFoundException e) {
      LOGGER.error("Couldn't find config file \"{}\"", p);
      System.exit(ExitStatuses.CONFIG_FILE_NOT_FOUND);
    } catch (final IOException e) {
      LOGGER.error("Error parsing config file \"{}\": {}", p, e.getMessage());
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }

    // Path to file with last update date
    final Path pathToLastDate = Path.of(parsed.getProperty(ADAPTER_PATH_DATE));
    final int timeInterval = checkIntProperty(ADAPTER_TIME_INTERVAL, "1", parsed, p);

    // Initialized the ConfigFile Object with filled in properties for InfluxDB, Matomo and Opencast
    return new ConfigFile(initInfluxDB(parsed, p),
                          initMatomo(parsed, p),
                          initOpencast(parsed, p),
                          pathToLastDate,
                          timeInterval);
  }

  /**
   * Helper method to parse numerical values.
   *
   * @param name Name of the field
   * @param def Default value
   * @param parsed Properties object
   * @param p Path to config file
   * @return The parsed value
   */
  private static int checkIntProperty(final String name, final String def, final Properties parsed, final Path p) {
    int value = 0;
    try {
      value = Integer.parseInt(parsed.getProperty(name, def));
      if (value < 0) {
        LOGGER.error(
                "Error parsing config file \"{}\": {} must be a positive value such as {}",
                p, name, def);
        System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
      }
    } catch (final NumberFormatException e) {
      LOGGER.error(
              "Error parsing config file \"{}\": {} must be a positive value such as {}",
              p, name, def);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    return value;
  }

  /**
   * Parses config file and initializes Opencast config object.
   *
   * @param parsed Properties object
   * @param p Path to config file
   * @return Opencast config object
   */
  private static OpencastConfig initOpencast(final Properties parsed, final Path p) {
    // Parse Opencast config
    final String opencastHost = parsed.getProperty(OPENCAST_URI);
    final String opencastUser = parsed.getProperty(OPENCAST_USER);
    final String opencastPassword = parsed.getProperty(OPENCAST_PASSWORD);
    final String opencastOrgaId = parsed.getProperty(OPENCAST_ORGAID, "mh_default_org");

    Duration opencastCacheExpirationDuration = Duration.ZERO;
    try {
      opencastCacheExpirationDuration = Duration.parse(parsed.getProperty(OPENCAST_EXPIRATION_DURATION, "PT0M"));
      if (opencastCacheExpirationDuration.isNegative()) {
        LOGGER.error(
                "Error parsing config file \"{}\": {} must be a positive ISO duration value such as \"PT5M\"",
                p, OPENCAST_EXPIRATION_DURATION);
        System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
      }
    } catch (final DateTimeParseException e) {
      LOGGER.error(
              "Error parsing config file \"{}\": {} must be a positive ISO duration value such as \"PT5M\"",
              p, OPENCAST_EXPIRATION_DURATION);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }

    final int opencastCacheSize = checkIntProperty(OPENCAST_CACHE_SIZE, "10000", parsed, p);
    final int opencastRateLimit = checkIntProperty(OPENCAST_RATE, "0", parsed, p);
    final int opencastTimeout = checkIntProperty(OPENCAST_TIMEOUT, "10", parsed, p);

    // Create new Opencast config object
    return opencastHost != null && opencastUser != null && opencastPassword != null ?
            new OpencastConfig(opencastHost, opencastUser, opencastPassword, opencastOrgaId,
                    opencastCacheSize, opencastCacheExpirationDuration, opencastRateLimit, opencastTimeout) :
            null;
  }

  /**
   * Parses config file and initializes Matomo config object.
   *
   * @param parsed Properties object
   * @param p Path to config file
   * @return Matomo config object
   */
  private static MatomoConfig initMatomo(final Properties parsed, final Path p) {
    // Parse Matomo config
    final String matomoHost = parsed.getProperty(MATOMO_URI);
    final String matomoToken = parsed.getProperty(MATOMO_TOKEN);

    final int matomoSiteId = checkIntProperty(MATOMO_SITEID, "-1", parsed, p);
    final int matomoRateLimit = checkIntProperty(MATOMO_RATE, "0", parsed, p);
    final int matomoTimeout = checkIntProperty(MATOMO_TIMEOUT, "10", parsed, p);

    // Create new Matomo config object
    return matomoHost != null && matomoToken != null ?
            new MatomoConfig(matomoHost, String.valueOf(matomoSiteId), matomoToken, matomoRateLimit, matomoTimeout) :
            null;
  }

  /**
   * Parses config file and initializes InfluxDB config object.
   *
   * @param parsed Properties object
   * @param p Path to config file
   * @return InfluxDB config object
   */
  private static InfluxDBConfig initInfluxDB(final Properties parsed, final Path p) {
    final String influxDbUser = parsed.getProperty(INFLUXDB_USER);
    if (influxDbUser.isEmpty()) {
      LOGGER.error("Error parsing config file \"{}\": {} cannot be empty", p, INFLUXDB_USER);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    final String influxDbDbName = parsed.getProperty(INFLUXDB_DB_NAME);
    if (influxDbDbName.isEmpty()) {
      LOGGER.error("Error parsing config file \"{}\": {} cannot be empty", p, INFLUXDB_DB_NAME);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    final String influxDbLogLevel = parsed.getProperty(INFLUXDB_LOG_LEVEL, "info");
    if (influxDbLogLevel.isEmpty() || !(influxDbLogLevel.equals("info") || influxDbLogLevel.equals("debug"))) {
      LOGGER.error(
              "Invalid InfluxDB log level \"" + influxDbLogLevel + "\": available are \"debug\" and \"info\"");
      System.exit(ExitStatuses.INVALID_INFLUXDB_CONFIG);
    }

    return new InfluxDBConfig(parsed.getProperty(INFLUXDB_URI),
            influxDbUser,
            parsed.getProperty(INFLUXDB_PASSWORD),
            influxDbDbName,
            parsed.getProperty(INFLUXDB_RETENTION_POLICY),
            parsed.getProperty(INFLUXDB_LOG_LEVEL, "info"));
  }

  public InfluxDBConfig getInfluxDBConfig() {
    return this.influxDBConfig;
  }

  public MatomoConfig getMatomoConfig() { return this.matomoConfig; }

  public OpencastConfig getOpencastConfig() { return this.opencastConfig; }

  public Path getPathToDate() { return this.lastDatePath; }

  public int getInterval() { return this.interval; }
}
