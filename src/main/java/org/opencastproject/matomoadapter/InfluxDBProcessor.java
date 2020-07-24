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
import org.influxdb.dto.Query;
import org.influxdb.impl.InfluxDBMapper;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.reactivex.Flowable;

/**
 * Contains utility functions related to InfluxDB
 */
public final class InfluxDBProcessor {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private BatchPoints batch;
  // TEST TEST TEST TEST
  private int count;

  public InfluxDBProcessor(final InfluxDBConfig config) {
    this.batch = BatchPoints.database(config.getDb()).retentionPolicy(config.getRetentionPolicy()).build();
    this.count = 0;
  }

  /**
   * Checks, if an entry of segments for an episode already exists in InfluxDB. If it does,
   * the entry is overwritten, otherwise, an InfluxDB point is created from Segments
   * object.
   * Update and delete are not natively supported on point basis by InfluxDB. Therefore, existing
   * points are overwritten.
   *
   * @param seg Segments objects generated from Matomo request
   * @param influxDB InfluxDB instance
   * @param config InfluxDB configuration
   * @return Point from Segments
   */
  public static Flowable<Point> checkSegments(final SegmentsImpression seg, final InfluxDB influxDB,
          final InfluxDBConfig config, final ConcurrentLinkedQueue<String> cou) {

    final String episodeId = seg.getEpisodeId();
    final String db = config.getDb();
    final String rp = config.getRetentionPolicy();

    final String queryString = String.format("SELECT * FROM %s.%s.segments_daily WHERE episodeId='%s'", db, rp, episodeId);

    // Map the result of the InfluxDB query to a list
    final InfluxDBMapper mapper = new InfluxDBMapper(influxDB);
    final List<SegmentsPoint> segmentsPointList = mapper.query(new Query(queryString, db), SegmentsPoint.class);

    // If an entry of segments for this episode exists
    if (!segmentsPointList.isEmpty()) {
      // In order to overwrite an entry, the new one needs to have the same timestamp.
      // Implication: "new" updates will always be written with the oldest timestamp of the episode
      final Instant time = segmentsPointList.get(0).getTime();

      // TEST TEST TEST TEST
      cou.add("te");
      System.out.println("WIP counter: " + cou.size());
      return Flowable.just(new SegmentsImpression(seg, time).toPoint());
    }

    // TEST TEST TEST TEST
    cou.add("te");
    System.out.println("WIP counter: " + cou.size());

    // If no point in InfluxDB exists yet, return new point from Segments
    return Flowable.just(seg.toPoint());
  }

  public void addToBatch(final Point p) {
    // TEST TEST TEST TEST TEST
    this.count++;
    this.batch.point(p);
  }

  /**
   * Push the whole batch to InfluxDB.
   *
   * @param influxDB A connected InfluxDB instance
   */
  public void writeBatchReset(final InfluxDB influxDB) {
    try {
      final Pong pong = influxDB.ping();
      if (!pong.isGood()) {
        LOGGER.error("INFLUXPINGERROR, not good");
      }
    } catch (final InfluxDBIOException e) {
      LOGGER.error("INFLUXPINGERROR, {}", e.getMessage());
    }

    influxDB.write(this.batch);

    // TEST TEST TEST TEST TEST
    System.out.println("Count of points in batch: " + this.count);
    this.count = 0;

    final String rp = this.batch.getRetentionPolicy();
    final String db = this.batch.getDatabase();
    this.batch = BatchPoints.database(db).retentionPolicy(rp).build();
  }

  /**
   * Connect and configure InfluxDB from a configuration
   *
   * @param config InfluxDB configuration
   * @return A connected InfluxDB instance
   */
  public static InfluxDB connect(final InfluxDBConfig config) {
    InfluxDB influxDB = null;
    try {
      influxDB = InfluxDBFactory.connect(config.getHost(), config.getUser(), config.getPassword());

      influxDB.setDatabase(config.getDb());
      influxDB.setRetentionPolicy(config.getRetentionPolicy());
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