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
import org.opencastproject.matomoadapter.influxdbclient.SegmentsImpression;
import org.opencastproject.matomoadapter.influxdbclient.SegmentsPOJO;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonSyntaxException;

import org.influxdb.dto.Point;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.annotations.NonNull;

public final class Utils {

  private Utils() {
  }

  /**
   * Combines two JSONArrays containing segment data. The fields nb_plays, sum_plays and
   * play_rate are unified in the process.
   *
   * Warning: If the JSONArrays have different lengths, the resulting arrays length is the
   * greater of both.
   *
   * @param old Old JSONArray with segment data
   * @param json New JSON string containing segment data
   * @return Unified JSONArray with updated values
   */
  @NonNull
  public static JsonArray combineSegmentJson(final JsonArray old, @NonNull final String json) {
    // If the new json array is empty, just return the unchanged JSONArray object
    if (json.length() > 2) {
      try {
        final JsonArray newJson = new Gson().fromJson(json, JsonArray.class);
        // If the old JSONArray is empty, just return the new JSONArray from string json
        if (old.size() == 0)
          return newJson;
        // The longer JSONArray is always stored
        final JsonArray longer = old.size() > newJson.size() ? old : newJson;
        final JsonArray shorter = old.size() > newJson.size() ? newJson : old;
        final DecimalFormat df = new DecimalFormat("#.##");
        // The sum doesn't change
        final int sum = old.get(0).getAsJsonObject().get("sum_plays").getAsInt() +
                newJson.get(0).getAsJsonObject().get("sum_plays").getAsInt();
        // Update values for segments
        for (int i = 0; i < longer.size(); i++) {
          // If the shorter arrays length is reached, add 0
          final int playsShort = i < shorter.size() ?
                  old.get(i).getAsJsonObject().get("nb_plays").getAsInt() : 0;
          final int plays = longer.get(i).getAsJsonObject().get("nb_plays").getAsInt() + playsShort;
          final double rate = (double) plays / (double) sum;

          // Update JSONObject with new values
          longer.get(i).getAsJsonObject().addProperty("nb_plays", String.valueOf(plays));
          longer.get(i).getAsJsonObject().addProperty("sum_plays", String.valueOf(sum));
          longer.get(i).getAsJsonObject().addProperty("play_rate", Double.parseDouble(df.format(rate)));
        }
        return longer;
      } catch (final JsonSyntaxException e) {
        throw new ParsingJsonSyntaxException(json);
      }
    }
    return old;
  }

  /**
   * Checks, if an entry of segments for an episode already exists in InfluxDB. If it does,
   * the entry is overwritten, otherwise, an InfluxDB point is created from Segments
   * object.
   * Update and delete are not natively supported on point basis by InfluxDB. Therefore, existing
   * points are overwritten.
   *
   * @param seg Segments objects generated from Matomo request
   * @param influxPro InfluxDB processor, which handles influxDB operations
   * @return Point from Segments
   */
  public static Flowable<Point> checkSegments(final SegmentsImpression seg, final InfluxDBProcessor influxPro) {

    final JsonArray segJson = seg.getSegments();
    // If the given SegmentsImpression doesnt contain segment data, evict item from stream
    if (segJson.size() == 0)
      return Flowable.empty();

    final String eventId = seg.getEventId();
    final String orgaId = seg.getOrgaId();
    // Prepare a query string for InfluxDB, leave two placeholders for DB and RP
    final String queryString = "SELECT * FROM %s.%s.segments_daily WHERE eventId='" + eventId + "' AND "
            + "organizationId='" + orgaId + "'";
    final List<SegmentsPOJO> segPojoList = influxPro.mapPojo(queryString, SegmentsPOJO.class);

    // If an entry of segments for this episode exists
    if (!segPojoList.isEmpty()) {
      // Unification of old segments data from DB and new data
      final JsonArray combo = Utils.combineSegmentJson(segJson, segPojoList.get(0).getSegments());

      // In order to overwrite an entry, the new point needs to have the same timestamp and tags.
      // Implication: "new" updates will always be written with the oldest timestamp of the episode.
      final Instant date = segPojoList.get(0).getTime();

      return Flowable.just(new SegmentsImpression(seg.getEventId(), seg.getOrgaId(), combo, date).toPoint());
    }
    // If no point in InfluxDB exists yet, return new point from SegmentsImpression
    return Flowable.just(seg.toPoint());
  }
}
