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
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    // TEST TEST TEST TEST
    final long start = System.nanoTime();

    // Preliminaries: command line parsing, config file parsing
    final CommandLine commandLine = CommandLine.parse(args);
    final ConfigFile configFile = ConfigFile.readFile(commandLine.getConfigFile());
    final Path p = configFile.getPathToDate();

    // Log configuration
    configureLogManually();

    // Connect and configure InfluxDB
    try (final InfluxDB influxDB = InfluxDBProcessor.connect(configFile.getInfluxDBConfig())) {

      // Create Matomo and Opencast HTTP clients
      final MatomoClient matClient = new MatomoClient(configFile.getMatomoConfig());
      final OpencastClient ocClient = new OpencastClient(configFile.getOpencastConfig());
      // Create a file writer for last date information
      final Writer fileWriter;
      // Check the file with last updated date. If no date is present set to yesterday
      final LocalDate lastDate = Files.lines(p).findFirst().isPresent() ?
              LocalDate.parse(Files.lines(p).findFirst().get()) :
              LocalDate.now().minusDays(1);

      final LocalDate dateNow = LocalDate.now();

      // Days between today and the last update
      final int days = (int)ChronoUnit.DAYS.between(lastDate, dateNow);

      getViewStats(matClient, ocClient, influxDB, configFile, days, dateNow);

      /*
       * TODO: Save date after every daily run, not after all runs
       */


      fileWriter = new FileWriter(String.valueOf(p), false);
      fileWriter.write(dateNow.toString());
      fileWriter.flush();

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
    } catch (final IOException e) {
      e.printStackTrace();
    }

    // TEST TEST TEST TEST
    final long end = System.nanoTime();
    System.out.println(end - start);

  }

  /**
   * Inserts/Updates date from Matomo into InfluxDB. In the first phase all view-related
   * date is updated (plays, finishes, visits). Secondly, segment-related statistics are
   * fetched.
   *
   * @param matClient Matomo external API client instance
   * @param ocClient Opencast external API client instance
   * @param influxDB InfluxDB instance
   * @param configFile Objects containing all configurations
   * @param days Number of days between the last update and today
   * @param dateNow Today's date
   */
  private static void getViewStats(final MatomoClient matClient, final OpencastClient ocClient,
                                   final InfluxDB influxDB, final ConfigFile configFile, final int days,
                                   final LocalDate dateNow) {

    // TEST TEST TEST TEST
    final ArrayList<String> count = new ArrayList<>();
    final ConcurrentLinkedQueue<String> counter = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<String> counter2 = new ConcurrentLinkedQueue<>();

    // Processes all InfluxDB operations
    final InfluxDBProcessor influxPro = new InfluxDBProcessor(configFile.getInfluxDBConfig());
    // Used as seed in reduce method, as well as starting point in the second phase
    final ArrayList<Impression> seed = new ArrayList<>();

    // Execute following steps for each day between the last update and today
    for (int i = days; i > 0; i--) {

      // Used as timestamp for InfluxDB points
      final OffsetDateTime influxTime = OffsetDateTime.now().minusDays(i);
      // Needed for Matomo external API calls
      final String queryDate = dateNow.minusDays(i).toString();

      // First, get all statistical data for all viewed episodes on given date
      MatomoUtils.getResources(LOGGER, matClient, queryDate)
              // Convert raw JSONObjects to Impressions
              .flatMap(json -> OpencastUtils.makeImpression(LOGGER, ocClient, json, influxTime, count).subscribeOn(Schedulers.io()))
              // Filter out / unite duplicates
              .reduce(seed, OpencastUtils::filterImpressions)
              .flattenAsFlowable(impressions -> impressions)
              // Get Points from Impressions
              .flatMap(impression -> Flowable.just(impression).subscribeOn(Schedulers.io()).map(Impression::toPoint))
              // Add all points to InfluxDB batch, instead of writing each separately
              .blockingSubscribe(influxPro::addToBatch, Main::processError, 2048);

      influxPro.writeBatchReset(influxDB);

      /*
       * TODO: New workflow for segments: for each confirmed episode, store every idSubtable within Impression
       *  Then in the second phase, request stats for each idSubtable and combine them. Finally if data point already
       *  exists, fetch it and combine new and old --> output a point (write/overwrite)
       */

      // List of unique Impressions tells us, for which episodes we need to fetch segment data
      Flowable.just(seed).flatMapIterable(impressions -> impressions)
              // Request segment statistics and build SegmentsPoints
              .flatMap(impression -> MatomoUtils.makeSegmentsImpression(LOGGER, matClient, impression,
                      queryDate, influxTime, counter))
              // If a InfluxDB point for an episode exists, overwrite it. Otherwise,
              .flatMap(seg -> InfluxDBProcessor.checkSegments(seg, influxDB, configFile.getInfluxDBConfig(), counter2))
              .blockingSubscribe(influxPro::addToBatch, Main::processError, 2048);

      // TEST TEST TEST TEST
      System.out.println("Size of cache: " + ocClient.getCache().size());
      System.out.println("Size of filtered list: " + seed.size());

      influxPro.writeBatchReset(influxDB);

      // Remove all elements from list for it to be reused
      seed.clear();

      // TEST TEST TEST TEST
      counter.clear();
      counter2.clear();
    }

    // TEST TEST TEST TEST
    System.out.println("Saved Opencast requests: " + count.size());
    System.out.println("Different segments: " + counter.size());
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