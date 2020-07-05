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

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBIOException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;


public final class Main {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private Main() {
  }

  public static void main(final String[] args) {
    // Preliminaries: command line parsing, config file parsing
    final CommandLine commandLine = CommandLine.parse(args);
    final ConfigFile configFile = ConfigFile.readFile(commandLine.getConfigFile());

    configureLogManually();

    // Connect and configure InfluxDB
    try (final InfluxDB influxDB = InfluxDBUtils.connect(configFile.getInfluxDBConfig())) {

      // Create a Matomo HTTP client (this might be a nop, if no Matomo token is given)
      final MatomoClient matClient = new MatomoClient(configFile.getMatomoConfig());
      final OpencastClient occlient = new OpencastClient(configFile.getOpencastConfig());

      MatomoUtils.getResources(LOGGER, matClient, configFile.getMatomoConfig().getSiteId(), configFile.getMatomoConfig().getToken(),
              "")
              .flatMap(json -> OpencastUtils.makeImpression(LOGGER, occlient, json))
              .map(Impression::toPoint)
              .blockingSubscribe(p -> InfluxDBUtils.writePointToInflux(configFile.getInfluxDBConfig(), influxDB, p),
                      Main::processError,
                      2048);



      /*Flowable<JSONObject> code = MatomoUtils.getResources(LOGGER, matClient, configFile.getMatomoConfig().getSiteId(),
              configFile.getMatomoConfig().getToken(), "visitServerHour==12");
      code.blockingSubscribe(p -> System.out.println("Plays: " + p.getInt("nb_plays")), 2048);*/

    } catch (final MatomoClientConfigurationException e) {

      LOGGER.error("Matomo configuration error: ", e);
      System.exit(ExitStatuses.MATOMO_CLIENT_CONFIGURATION_ERROR);

    } catch (final OpencastClientConfigurationException e) {

      LOGGER.error("Opencast configuration error: ", e);
      System.exit(ExitStatuses.OPENCAST_CLIENT_CONFIGURATION_ERROR);

    } catch (final InfluxDBIOException e) {

      if (e.getCause() != null) {
        LOGGER.error("InfluxDB error: " + e.getCause().getMessage());
      } else {
        LOGGER.error("InfluxDB error: " + e.getMessage());
      }
      System.exit(ExitStatuses.INFLUXDB_RUNTIME_ERROR);
    }
  }

  /**
   * Examine an exception, print a nice error message and exit
   *
   * @param e The error to analyze
   */
  private static void processError(final Throwable e) {
    if (e instanceof FileNotFoundException) {
      LOGGER.error("Log file \"" + e.getMessage() + "\" not found", e);
      System.exit(ExitStatuses.LOG_FILE_NOT_FOUND);
    } else if (e instanceof ParsingJsonSyntaxException) {
      LOGGER.error("Couldn't parse json: " + ((ParsingJsonSyntaxException) e).getJson(), e);
      System.exit(ExitStatuses.JSON_SYNTAX_ERROR);
    } else if (e instanceof OpencastClientConfigurationException) {
      LOGGER.error("Opencast configuration error:", e);
      System.exit(ExitStatuses.OPENCAST_CLIENT_CONFIGURATION_ERROR);
    } else if (e instanceof MatomoClientConfigurationException) {
      LOGGER.error("Matomo configuration error:", e);
      System.exit(ExitStatuses.MATOMO_CLIENT_CONFIGURATION_ERROR);
    }else {
      LOGGER.error("Error:", e);
    }
    System.exit(ExitStatuses.UNKNOWN);
  }

  /**
   * Configure logging without a configuration file. In that case, stdout/stderr should be used.
   */
  private static void configureLogManually() {
    final PatternLayoutEncoder ple = new PatternLayoutEncoder();

    final Context lc = (Context) LoggerFactory.getILoggerFactory();
    ple.setPattern("%date %level %logger{10} %msg%n");
    ple.setContext(lc);
    ple.start();

    final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    logger.detachAndStopAllAppenders();
    final ConsoleAppender<ILoggingEvent> newAppender = new ConsoleAppender<>();
    newAppender.setEncoder(ple);
    newAppender.setContext(lc);
    newAppender.start();
    logger.addAppender(newAppender);
    logger.setLevel(Level.INFO);
    logger.setAdditive(true);
  }
}
