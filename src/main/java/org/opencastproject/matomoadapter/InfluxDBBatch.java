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
import org.influxdb.InfluxDBFactory;
import org.influxdb.InfluxDBIOException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Contains utility functions related to InfluxDB
 */
public final class InfluxDBBatch {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private final BatchPoints batch;
  private final ArrayList<Impression> filterList;

  private int count = 0;

  public InfluxDBBatch(final InfluxDBConfig config) {
    this.batch = BatchPoints.database(config.getDb()).retentionPolicy(config.getRetentionPolicy()).build();
    this.filterList = new ArrayList<>();
  }

  public void addToFilter(final Impression newImpression) {

    final String episodeId = newImpression.getEpisodeId();

    if (this.filterList.contains(newImpression)) {

      final Impression old = this.filterList.get(this.filterList.indexOf(newImpression));

      final int plays = old.getPlays() + newImpression.getPlays();
      final int visitors = old.getVisitors() + newImpression.getVisitors();
      final int finishes = old.getFinishes() + newImpression.getFinishes();

      final Impression combine = new Impression(episodeId, old.getOrganizationId(), old.getSeriesId(),
                                                plays, visitors, finishes, old.getDate());

      this.filterList.remove(old);
      this.filterList.add(combine);

    }
    else {
      this.filterList.add(newImpression);
    }
  }

  public ArrayList<Impression> getFilteredList() { return this.filterList; }

  public void addToBatch(final Point p) {
    count++;
    System.out.println("Count of points: " + count);
    this.batch.point(p);
  }

  public void writeBatch(final InfluxDB influxDB) {
    try {
      final Pong pong = influxDB.ping();
      if (!pong.isGood()) {
        LOGGER.error("INFLUXPINGERROR, not good");
      }
    } catch (final InfluxDBIOException e) {
      LOGGER.error("INFLUXPINGERROR, {}", e.getMessage());
    }
    influxDB.write(this.batch);
  }

  /**
   * Write the given point to InfluxDB
   * @param config Configuration (for retention policies etc.)
   * @param influxDB The InfluxDB connection
   * @param p The point to write
   */
  public static void writePointToInflux(final InfluxDBConfig config, final InfluxDB influxDB, final Point p) {
    try {
      final Pong pong = influxDB.ping();
      if (!pong.isGood()) {
        LOGGER.error("INFLUXPINGERROR, not good");
      }
    } catch (final InfluxDBIOException e) {
      LOGGER.error("INFLUXPINGERROR, {}", e.getMessage());
    }
    if (config.getRetentionPolicy() != null)
      influxDB.write(config.getDb(), config.getRetentionPolicy(), p);
    else
      influxDB.write(p);
  }

  /**
   * Connect and configure InfluxDB from a configuration
   * @param config InfluxDB configuration
   * @return A connected InfluxDB instance
   */
  public static InfluxDB connect(final InfluxDBConfig config) {
    InfluxDB influxDB = null;
    try {
      influxDB = InfluxDBFactory.connect(config.getHost(), config.getUser(), config.getPassword());

      influxDB.setDatabase(config.getDb());
      influxDB.enableBatch();
      if (config.getLogLevel().equals("debug")) {
        influxDB.setLogLevel(InfluxDB.LogLevel.FULL);
      } else if (config.getLogLevel().equals("info")) {
        influxDB.setLogLevel(InfluxDB.LogLevel.BASIC);
      } else {
        LOGGER.error(
                "Invalid InfluxDB log level \"" + config.getLogLevel() + "\": available are \"debug\" and \"info\"");
        System.exit(ExitStatuses.INVALID_INFLUXDB_CONFIG);
      }
      return influxDB;
    } catch (final Exception e) {
      if (influxDB != null) {
        influxDB.close();
      }
      throw e;
    }
  }
}
