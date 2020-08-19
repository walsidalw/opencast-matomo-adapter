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

import org.opencastproject.matomoadapter.influxdbclient.SegmentsImpression;
import org.opencastproject.matomoadapter.influxdbclient.ViewImpression;
import org.opencastproject.matomoadapter.matclient.MatomoClient;
import org.opencastproject.matomoadapter.matclient.MatomoUtils;
import org.opencastproject.matomoadapter.occlient.OpencastClient;
import org.opencastproject.matomoadapter.occlient.OpencastUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;

import io.reactivex.Flowable;
import io.reactivex.annotations.NonNull;

public final class ImpressionHandler {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private ImpressionHandler() {
  }

  /**
   * Extracts eventId and statistical data from a JSON Object received from Matomo.
   * Subsequently, the Opencast Event API is called for relevant series information (seriesID).
   * Finally, all required data is stored and returned within a ViewImpression Object.
   *
   * @param date Date for which the data is requested
   * @param ocClient Opencast client used for the event API request
   * @param json JSON object representing one video and its statistics
   * @return Completed Impression, ready to be converted to a InfluxDB point
   */
  public static Flowable<ViewImpression> createViewImpression(final OpencastClient ocClient, final JSONObject json,
                                                              final OffsetDateTime date) {
    try {
      // Extract eventId from JSON
      final String label = json.getString("label");
      final String eventId = getEventJson(label);

      // If the JSON label doesnt fit the pattern (e.g. Live Streams), the entry is evicted
      if (eventId.isEmpty())
        return Flowable.empty();

      final String orgaId = ocClient.getOrgaId();
      // Parse the remaining important data from JSON
      final int plays = json.getInt("nb_plays");
      final int visits = json.getInt("nb_unique_visitors_impressions");
      final int finishes = json.getInt("nb_finishes");
      final ArrayList<String> idSubtables = new ArrayList<>();
      idSubtables.add(json.getString("idsubdatatable"));

      // Create new ViewImpression with series data from Opencast
      return OpencastUtils.seriesForEvent(LOGGER, ocClient, orgaId, eventId)
              .flatMap(series -> Flowable.just(new ViewImpression(eventId, orgaId,
                      series, plays, visits, finishes, date.toInstant(), idSubtables)));

    } catch (final JSONException e) {
      throw new ParsingJsonSyntaxException(json.toString());
    }
  }

  /**
   * Prompts an API call and converts the response to a SegmentsImpression. If no segment data is
   * available for the given impression/episode, empty Flowable is returned.
   *
   * @param matClient Matomo client instanceold
   * @param viewImpression Contains all necessary episode information
   * @param date Date of request
   * @return Returns Flowable containing either one or none SegmentsImpressions
   */
  public static Flowable<SegmentsImpression> createSegmentsImpression(final MatomoClient matClient,
                                                                      final ViewImpression viewImpression,
                                                                      final OffsetDateTime date) {
    final JSONArray seed = new JSONArray();

    return Flowable.just(viewImpression.getSubtables()).flatMapIterable(imp -> imp)
            // For each subtable request segment data
            .flatMap(subtable -> MatomoUtils.getSegments(LOGGER, matClient, date, subtable))
            // Reduce all subtable responses to one data set
            .reduce(seed, Utils::combineSegmentJson)
            .toFlowable()
            // Create SegmentsImpression
            .flatMap(json -> Flowable.just(new SegmentsImpression(
                    viewImpression.getEventId(), viewImpression.getOrgaId(), json, date.toInstant())));
  }

  /**
   * Parses the video URL to find the eventId. Currently the most common URL-types, coming from the
   * Theodul and Paella player are supported. Live Streams do not contain an eventId.
   *
   * @param label Sub-URL of the video
   * @return The eventID parsed from the URL
   */
  private static String getEventJson(final String label) {
    if (!label.contains("engage") && !label.contains("static"))
      return "";

    final String sub = label.substring(1, 7);

    if (sub.equals("engage")) {
      return label.substring(label.lastIndexOf("?id=") + 4, label.lastIndexOf("?id=") + 40);
    } else if (sub.equals("static")) {
      return label.substring(label.lastIndexOf("yer/") + 4, label.lastIndexOf("yer/") + 40);
    }
    return "";
  }

  /**
   * Checks all emitted ViewImpressions for duplicates. Duplicates are merged into one impression.
   *
   * @param newViewImpression Newly emitted Impression
   */
  @NonNull
  public static ArrayList<ViewImpression> reduceViewImpressions(final ArrayList<ViewImpression> oldList,
                                                            final ViewImpression newViewImpression) {
    final String eventId = newViewImpression.getEventId();
    // If the list already contains an Impression with the same eventId as the new Impression, merge
    // both into one Impression
    if (oldList.contains(newViewImpression)) {

      final ViewImpression old = oldList.get(oldList.indexOf(newViewImpression));
      // Merge stats of old and new Impression
      final int plays = old.getPlays() + newViewImpression.getPlays();
      final int visitors = old.getVisitors() + newViewImpression.getVisitors();
      final int finishes = old.getFinishes() + newViewImpression.getFinishes();
      final ArrayList<String> idSubtables = old.getSubtables();
      idSubtables.addAll(newViewImpression.getSubtables());
      // Create now ViewImpression with merged stats, since ViewImpressions are immutable
      final ViewImpression combined = new ViewImpression(eventId, old.getOrgaId(), old.getSeriesId(), plays,
              visitors, finishes, old.getDate(), idSubtables);
      // Replace old impression with new, combined ViewImpression
      oldList.set(oldList.indexOf(newViewImpression), combined);

    } else {
      // Else just add the ViewImpression to the list
      oldList.add(newViewImpression);
    }
    return oldList;
  }
}
