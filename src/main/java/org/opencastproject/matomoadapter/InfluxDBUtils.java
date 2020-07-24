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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.reactivex.Flowable;
import io.reactivex.annotations.NonNull;

/**
 * Contains utility functions related to InfluxDB
 */
public final class InfluxDBUtils {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private BatchPoints batch;
  // TEST TEST TEST TEST
  private int count = 0;

  public InfluxDBUtils(final InfluxDBConfig config) {
    this.batch = BatchPoints.database(config.getDb()).retentionPolicy(config.getRetentionPolicy()).build();
  }

  /**
   * Checks all emitted Impressions for duplicates. Duplicates are merged into one Impression.
   *
   * @param newImpression Newly emitted Impression
   */
  @NonNull
  public ArrayList<Impression> addToFilter(final ArrayList<Impression> oldList, final Impression newImpression) {

    final String episodeId = newImpression.getEpisodeId();

    // If the list already contains an Impression with the same episodeID as the new Impression, merge
    // both into one Impression.
    if (oldList.contains(newImpression)) {

      final Impression old = oldList.get(oldList.indexOf(newImpression));
      // Merge stats of old and new Impression
      final int plays = old.getPlays() + newImpression.getPlays();
      final int visitors = old.getVisitors() + newImpression.getVisitors();
      final int finishes = old.getFinishes() + newImpression.getFinishes();

      final Impression combined = new Impression(episodeId, old.getOrganizationId(), old.getSeriesId(),
              old.getStartDate(), plays,
              visitors, finishes, old.getDate());

      oldList.remove(old);
      oldList.add(combined);

    } else {
      oldList.add(newImpression);
    }

    return oldList;
  }

  /**
   * Checks, if an entry of segments for an episode already exists in InfluxDB. If it does,
   * the entry is updated/overwritten, otherwise, an InfluxDB point is created from Segments
   * object.
   *
   * @param seg Segments objects generated from Matomo request
   * @param influxDB InfluxDB instance
   * @param config InfluxDB configuration
   * @return Point from Segments, if no entry in InfluxDB exists
   */
  public static Flowable<Point> checkSegments(final SegmentsImpression seg, final InfluxDB influxDB,
          final InfluxDBConfig config, final ConcurrentLinkedQueue<String> counter) {

    final String episodeId = seg.getEpisodeId();
    final String db = config.getDb();
    final String rp = config.getRetentionPolicy();

    final String queryString = String.format("SELECT * FROM %s.%s.segments_daily WHERE episodeId='%s'", db, rp, episodeId);

    // Map the result of the InfluxDB query to a list
    final InfluxDBMapper mapper = new InfluxDBMapper(influxDB);
    final List<SegmentsPoint> segmentsPointList = mapper.query(new Query(queryString, db), SegmentsPoint.class);

    // If an entry of segments for this episode exists
    if (!segmentsPointList.isEmpty()) {

      try {

        // Convert segment strings to JSONArray
        final JSONArray arrayPOJO = new JSONArray(segmentsPointList.get(0).getSegments());
        final JSONArray arraySeg = new JSONArray(seg.getSegments());

        if (arrayPOJO.length() != arraySeg.length()) {
          counter.add("sh");
          return Flowable.empty();
        }


        // Traverse the JSONArrays and update each value
        for (int i = 0; i < arrayPOJO.length(); i++) {

          // Create JSONObject entities from the array, which can be updated dynamically
          final JSONObject itemPOJO = (JSONObject) arrayPOJO.get(i);
          final JSONObject itemSeg = (JSONObject) arraySeg.get(i);
          // Double formatter: round to two decimal places
          final DecimalFormat df = new DecimalFormat("#.##");

          // Update values: old value from InfluxDB + new value from Matomo
          final int plays = Integer.parseInt(itemPOJO.getString("nb_plays")) +
                  Integer.parseInt(itemSeg.getString("nb_plays"));

          final int sum = Integer.parseInt(itemPOJO.getString("sum_plays")) +
                  Integer.parseInt(itemSeg.getString("sum_plays"));

          final double rate = (double) plays / (double) sum;

          // Update JSONObjects with new values
          itemPOJO.put("nb_plays", String.valueOf(plays));
          itemPOJO.put("sum_plays", String.valueOf(sum));
          itemPOJO.put("play_rate", df.format(rate));
        }

        // Set updated JSONArray into POJO
        segmentsPointList.get(0).setSegments(arrayPOJO.toString());
        // Push updated POJO to InfluxDB. InfluxDB does not support updates natively.
        // Instead, points with the same tags and timestamp are overwritten
        mapper.save(segmentsPointList.get(0));

        // Since an update was performed, the item can be evicted from stream
        return Flowable.empty();

      } catch (final JSONException e) {
        final String error = String.format("Present segments: %s%n Query segments: %s%n Episodes: ",
                seg.getSegments(),
                segmentsPointList.get(0).getSegments());
        throw new ParsingJsonSyntaxException(error);
      }
    } else {
      // If no point in InfluxDB exists yet, return new point from Segments
      return Flowable.just(seg.toPoint());
    }
  }

  public void addToBatch(final Point p) {
    // TEST TEST TEST TEST TEST
    count++;
    this.batch.point(p);
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
    // TEST TEST TEST TEST TEST
    System.out.println("Count of points in batch: " + this.count);
    this.count = 0;

    final String rp = this.batch.getRetentionPolicy();
    final String db = this.batch.getDatabase();
    //this.batch = null;
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