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

import org.opencastproject.matomoadapter.influxdbclient.InfluxDBProcessor;
import org.opencastproject.matomoadapter.influxdbclient.ViewImpression;
import org.opencastproject.matomoadapter.matclient.MatomoClient;
import org.opencastproject.matomoadapter.matclient.MatomoUtils;
import org.opencastproject.matomoadapter.occlient.OpencastClient;
import org.opencastproject.matomoadapter.occlient.OpencastUtils;

import org.influxdb.InfluxDBIOException;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Context;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

public final class Main {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private Main() {
  }

  public static void main(final String[] args) {
    // Preliminaries: command line parsing, config file parsing
    final CommandLine commandLine = CommandLine.parse(args);
    final ConfigFile configFile = ConfigFile.readFile(commandLine.getConfigFile());
    final Path p = configFile.getPathToDate();
    // Log configuration
    configureLogManually();

    try {
      // Initialize all clients (Opencast, Matomo)
      final MatomoClient matClient = new MatomoClient(configFile.getMatomoConfig(), LOGGER);
      final OpencastClient ocClient = new OpencastClient(configFile.getOpencastConfig(), LOGGER);
      // Connect and configure InfluxDB
      final InfluxDBProcessor influxPro = new InfluxDBProcessor(configFile.getInfluxDBConfig(), LOGGER);

      // Schedule a task for updates
      final Timer timer = new Timer("Timer");
      final long delay = 1000L;
      // Period between executions
      final long period = 1000L * 60L * 60L * 24L * configFile.getInterval();
      final TimerTask scheduledTask = new TimerTask() {
        public void run() {
          getStatisticsDaily(matClient, ocClient, influxPro, p);
          LOGGER.info("Statistics updated on: {}, Next update on: {}", LocalDate.now(),
                  LocalDate.now().plusDays(configFile.getInterval()));
        }
      };
      timer.scheduleAtFixedRate(scheduledTask, delay, period);

    } catch (final ClientConfigurationException e) {
      LOGGER.error("Client configuration error: ", e);
      System.exit(ExitStatuses.CLIENT_CONFIGURATION_ERROR);
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
   * Checks the last date in file and updates statistics for each day between that date and today.
   * After finishing a day, updates the date file.
   *
   * @param matClient Matomo external API client instance
   * @param ocClient Opencast external API client instance
   * @param influxPro InfluxDBProcessor instance
   * @param p Path to file containing the last update date
   */
  private static void getStatisticsDaily(final MatomoClient matClient, final OpencastClient ocClient,
                                         final InfluxDBProcessor influxPro, final Path p) {

    try {
      // Check the file with last updated date. If no date is present set to yesterday
      final LocalDate lastDate = Files.lines(p).findFirst().isPresent() ?
              LocalDate.parse(Files.lines(p).findFirst().get()) :
              LocalDate.now().minusDays(1);

      final LocalDate dateNow = LocalDate.now();
      // Days between today and the last update
      final int days = (int) ChronoUnit.DAYS.between(lastDate, dateNow);

      // Execute following steps for each day between the last update and today
      for (int i = days; i > 0; i--) {
        // Used as timestamp for InfluxDB points
        final OffsetDateTime date = OffsetDateTime.now().minusDays(i);
        // Get statistics for current date (queryDate)
        getStatistics(matClient, ocClient, influxPro, date);
        // Write current date into file
        final Writer fileWriter = new FileWriter(String.valueOf(p), false);
        fileWriter.write(dateNow.minusDays(i - 1).toString());
        fileWriter.flush();
        fileWriter.close();
      }
    } catch (final IOException e) {
      LOGGER.error("File handling error: ", e);
      System.exit(ExitStatuses.FILE_HANDLING_ERROR);
    }
  }

  /**
   * Inserts/Updates date from Matomo into InfluxDB. In the first phase all view-related
   * date is updated (plays, finishes, visits). Secondly, segment-related statistics are
   * fetched.
   *
   * @param matClient Matomo external API client instance
   * @param ocClient Opencast external API client instance
   * @param influxPro InfluxDBProcessor instance
   * @param date Date for the requests
   */
  private static void getStatistics(final MatomoClient matClient, final OpencastClient ocClient,
                                    final InfluxDBProcessor influxPro, final OffsetDateTime date) {

    // Used as seed in reduce method, as well as starting point in the second phase. After the first phase,
    // it contains all the unique episode impressions from one day.
    final ArrayList<ViewImpression> seed = new ArrayList<>();

    // First, get all statistical data for all viewed episodes on given date
    MatomoUtils.getViewed(matClient, date)
            // Convert raw JSONObjects to Impressions
            .flatMap(json -> OpencastUtils.makeImpression(ocClient, json, date)
                    .subscribeOn(Schedulers.io()))
            // Filter out / unite duplicate impressions. Outgoing stream contains unique episode impressions
            .reduce(seed, OpencastUtils::filterImpressions)
            .flattenAsFlowable(impressions -> impressions)
            // Get Points from Impressions
            .flatMap(viewImpression -> Flowable.just(viewImpression)
                    .subscribeOn(Schedulers.io()).map(ViewImpression::toPoint))
            // Add all points to InfluxDB batch, instead of writing each point separately
            .blockingSubscribe(influxPro::addToBatch, Main::processError, 2048);

    // Write view statistics to InfluxDB
    influxPro.writeBatchReset();

    // List of unique impressions tells us, for which episodes we need to fetch segment data
    Flowable.just(seed).flatMapIterable(impressions -> impressions)
            // Request segment statistics and build SegmentsPoints
            .flatMap(viewImpression -> MatomoUtils.makeSegmentsImpression(matClient, viewImpression, date)
                    .subscribeOn(Schedulers.io()))
            // If an InfluxDB point for an episode exists, overwrite it. Otherwise, insert point normally
            .flatMap(seg -> Utils.checkSegments(seg, influxPro)
                    .subscribeOn(Schedulers.io()))
            .blockingSubscribe(influxPro::addToBatch, Main::processError, 2048);

    // (Over-)Write segment statistics to InfluxDB
    influxPro.writeBatchReset();
  }

  /**
   * Examine an exception, print a nice error message and exit
   *
   * @param e The error to analyze
   */
  private static void processError(final Throwable e) {
    if (e instanceof ParsingJsonSyntaxException) {
      LOGGER.error("Couldn't parse json: " + ((ParsingJsonSyntaxException) e).getJson(), e);
      System.exit(ExitStatuses.JSON_SYNTAX_ERROR);
    } else if (e instanceof ClientConfigurationException) {
      LOGGER.error("Client configuration error:", e);
      System.exit(ExitStatuses.CLIENT_CONFIGURATION_ERROR);
    } else {
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
