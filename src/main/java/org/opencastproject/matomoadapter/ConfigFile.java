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

import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
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
  // Opencast options
  private static final String OPENCAST_URI = "opencast.external-api.uri";
  private static final String OPENCAST_USER = "opencast.external-api.user";
  private static final String OPENCAST_PASSWORD = "opencast.external-api.password";
  // Path to last date file
  private static final String ADAPTER_PATH = "adapter.date-file";
  // Config objects
  private final InfluxDBConfig influxDBConfig;
  private final MatomoConfig matomoConfig;
  private final OpencastConfig opencastConfig;
  private final Path time;

  private ConfigFile(
          final InfluxDBConfig influxDBConfig,
          final MatomoConfig matomoConfig,
          final OpencastConfig opencastConfig,
          final Path time) {
    this.influxDBConfig = influxDBConfig;
    this.matomoConfig = matomoConfig;
    this.opencastConfig = opencastConfig;
    this.time = time;
  }

  public static ConfigFile readFile(final Path p) {
    final Properties parsed = new Properties();

    // Try parsing the config file. Write into Properties object
    try (final FileReader reader = new FileReader(p.toFile())) {
      parsed.load(reader);
    } catch (final FileNotFoundException e) {
      LOGGER.error("Couldn't find config file \"{}\"", p);
      System.exit(ExitStatuses.CONFIG_FILE_NOT_FOUND);
    } catch (final IOException e) {
      LOGGER.error("Error parsing config file \"{}\": {}", p, e.getMessage());
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }

    // Parse InfluxDB config
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

    /*
     * TODO: Add check for Matomo config - valid SiteID and token
     */

    // Parse Matomo config
    final String matomoHost = parsed.getProperty(MATOMO_URI);
    final String matomoSiteId = parsed.getProperty(MATOMO_SITEID);
    final String matomoToken = parsed.getProperty(MATOMO_TOKEN);

    // Create new Matomo config object
    final MatomoConfig matomoConfig = matomoHost != null && matomoSiteId != null && matomoToken != null ?
            new MatomoConfig(matomoHost, matomoSiteId, matomoToken) :
            null;

    // Parse Opencast config
    final String opencastHost = parsed.getProperty(OPENCAST_URI);
    final String opencastUser = parsed.getProperty(OPENCAST_USER);
    final String opencastPassword = parsed.getProperty(OPENCAST_PASSWORD);

    // Create new Opencast config object
    final OpencastConfig opencastConfig = opencastHost != null && opencastUser != null && opencastPassword != null ?
            new OpencastConfig(opencastHost, opencastUser, opencastPassword) :
            null;

    final Path time = Path.of(parsed.getProperty(ADAPTER_PATH));

    // Initialized the ConfigFile Object with filled in properties for both InfluxDB and Opencast
    return new ConfigFile(new InfluxDBConfig(parsed.getProperty(INFLUXDB_URI),
                                             influxDbUser,
                                             parsed.getProperty(INFLUXDB_PASSWORD),
                                             influxDbDbName,
                                             parsed.getProperty(INFLUXDB_RETENTION_POLICY),
                                             parsed.getProperty(INFLUXDB_LOG_LEVEL, "info")),
                          matomoConfig,
                          opencastConfig,
                          time);
  }

  public InfluxDBConfig getInfluxDBConfig() {
    return this.influxDBConfig;
  }

  public MatomoConfig getMatomoConfig() {
    return this.matomoConfig;
  }

  public OpencastConfig getOpencastConfig() {
    return this.opencastConfig;
  }

  public Path getPathToTime() { return this.time; }
}
