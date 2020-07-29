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

import org.influxdb.dto.Point;
import org.json.JSONArray;
import org.json.JSONException;

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
  public static JSONArray combineSegmentJson(final JSONArray old, @NonNull final String json) {
    // If the new json array is empty, just return the unchanged JSONArray object
    if (json.length() > 2) {
      try {
        final JSONArray freshJson = new JSONArray(json);
        // If the old JSONArray is empty, just return the new JSONArray from string json
        if (old.length() == 0)
          return freshJson;
        // The longer JSONArray is always stored
        final JSONArray longer = old.length() > freshJson.length() ? old : freshJson;
        final JSONArray shorter = old.length() > freshJson.length() ? freshJson : old;
        final DecimalFormat df = new DecimalFormat("#.##");
        // The sum doesn't change
        final int sum = Integer.parseInt(old.getJSONObject(0).getString("sum_plays")) +
                Integer.parseInt(freshJson.getJSONObject(0).getString("sum_plays"));
        // Update values for segments
        for (int i = 0; i < longer.length(); i++) {
          // If the shorter arrays length is reached, add 0
          final int playsShort = i < shorter.length() ?
                  Integer.parseInt(shorter.getJSONObject(i).getString("nb_plays")) : 0;
          final int plays = Integer.parseInt(longer.getJSONObject(i).getString("nb_plays")) + playsShort;
          final double rate = (double) plays / (double) sum;

          // Update JSONObject with new values
          longer.getJSONObject(i).put("nb_plays", String.valueOf(plays));
          longer.getJSONObject(i).put("sum_plays", String.valueOf(sum));
          longer.getJSONObject(i).put("play_rate", df.format(rate));
        }
        return longer;
      } catch (final JSONException e) {
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
    final String episodeId = seg.getEpisodeId();

    final String queryString = "SELECT * FROM %s.%s.segments_daily WHERE episodeId='" + episodeId + "'";
    final List<SegmentsPOJO> segPojoList = influxPro.mapPojo(queryString, SegmentsPOJO.class);
    final JSONArray segJson = seg.getSegments();

    // If the given SegmentsImpression doesnt contain segment data, evict item from steam
    if (segJson.length() == 0)
      return Flowable.empty();

    // If an entry of segments for this episode exists
    if (!segPojoList.isEmpty()) {
      // Unification of old segments data from DB and new data
      final JSONArray combo = Utils.combineSegmentJson(segJson, segPojoList.get(0).getSegments());

      // In order to overwrite an entry, the new point needs to have the same timestamp and tags.
      // Implication: "new" updates will always be written with the oldest timestamp of the episode.
      final Instant date = segPojoList.get(0).getTime();

      return Flowable.just(new SegmentsImpression(seg.getEpisodeId(), seg.getOrganizationId(), combo, date).toPoint());
    }
    // If no point in InfluxDB exists yet, return new point from SegmentsImpression
    return Flowable.just(seg.toPoint());
  }
}