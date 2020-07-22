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
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;

/**
 * Contains utility functions related to InfluxDB
 */
public final class InfluxDBUtils {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private BatchPoints batch;
  private final ArrayList<Impression> filterList;

  // TEST TEST TEST TEST
  private int count = 0;

  public InfluxDBUtils(final InfluxDBConfig config) {
    this.batch = BatchPoints.database(config.getDb()).retentionPolicy(config.getRetentionPolicy()).build();
    this.filterList = new ArrayList<>();
  }

  /**
   * Checks all emitted Impressions for duplicates. Duplicates are merged into one Impression.
   *
   * @param newImpression Newly emitted Impression
   */
  public void addToFilter(final Impression newImpression) {

    final String episodeId = newImpression.getEpisodeId();

    // If the list already contains an Impression with the same episodeID as the new Impression, merge
    // both into one Impression.
    if (this.filterList.contains(newImpression)) {

      final Impression old = this.filterList.get(this.filterList.indexOf(newImpression));
      // Merge stats of old and new Impression
      final int plays = old.getPlays() + newImpression.getPlays();
      final int visitors = old.getVisitors() + newImpression.getVisitors();
      final int finishes = old.getFinishes() + newImpression.getFinishes();

      final Impression combined = new Impression(episodeId, old.getOrganizationId(), old.getSeriesId(),
                                                plays, visitors, finishes, old.getDate());

      this.filterList.remove(old);
      this.filterList.add(combined);

    }
    else {
      this.filterList.add(newImpression);
    }
  }

  public ArrayList<Impression> getFilteredList() { return this.filterList; }

  public void addToBatch(final Point p) {
    // TEST TEST TEST TEST TEST
    count++;
    System.out.println("Count of points: " + count);
    this.batch.point(p);
  }

  private static List<SegmentsPoint> getPointFromDB(final InfluxDB influxDB, final String db, final String episodeId) {
    final QueryResult queryResult = influxDB.query(new Query(
            "SELECT * FROM opencast1.infinite.segments_daily WHERE episodeId='" + episodeId + "'", db));

    final InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
    final List<SegmentsPoint> segmentsPointList = resultMapper.toPOJO(queryResult, SegmentsPoint.class);

    return segmentsPointList.isEmpty() ? null : segmentsPointList;
  }

  public static Flowable<Point> checkSegments(final Segments seg, final InfluxDB influxDB) throws JSONException {
    final String episodeId = seg.getEpisodeId();
    // check if record in db is present
    // If not: return Flowable.just(Segments::toPoint)
    // combine pojo with segment
    // save pojo, return Flowable.empty

    final QueryResult queryResult = influxDB.query(new Query(
            "SELECT * FROM opencast1.infinite.segments_daily WHERE episodeId='" + episodeId + "'",
            "opencast1"));

    final InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
    final List<SegmentsPoint> segmentsPointList = resultMapper.toPOJO(queryResult, SegmentsPoint.class);

    if (!segmentsPointList.isEmpty()) {

      // Extract JSONArray from POJO list and from Segments
      // Combine the two JSONArrays
      // Set new JSONArray segments in POJO (toString!)

      JSONArray arrayPOJO = new JSONArray(segmentsPointList.get(0).getSegments());
      JSONArray arraySeg = new JSONArray(seg.getSegments());

      for (int i = 0; i < arrayPOJO.length(); i++) {

        JSONObject itemPOJO = (JSONObject)arrayPOJO.get(i);
        JSONObject itemSeg = (JSONObject)arraySeg.get(i);

        int plays = Integer.parseInt(itemPOJO.getString("nb_plays")) +
                Integer.parseInt(itemSeg.getString("nb_plays"));

        int rate = Integer.parseInt(itemPOJO.getString("play_rate")) +
                Integer.parseInt(itemSeg.getString("play_rate"));

        itemPOJO.put("nb_plays", String.valueOf(plays));
        itemPOJO.put("play_rate", String.valueOf(rate));
      }

      segmentsPointList.get(0).setSegments(arrayPOJO.toString());

      return Flowable.empty();
    } else {
      return Flowable.just(seg.toPoint());
    }
  }

  /**
   * Push the whole batch to InfluxDB.
   *
   * @param influxDB A connected InfluxDB instance
   */
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
}
