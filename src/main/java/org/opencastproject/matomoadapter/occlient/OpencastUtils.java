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

package org.opencastproject.matomoadapter.occlient;

import org.opencastproject.matomoadapter.InvalidHttpResponseException;
import org.opencastproject.matomoadapter.ParsingJsonSyntaxException;
import org.opencastproject.matomoadapter.influxdbclient.ViewImpression;

import com.google.common.cache.Cache;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Objects;

import io.reactivex.Flowable;
import io.reactivex.annotations.NonNull;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Various utility functions for Opencast
 */
public final class OpencastUtils {
  private OpencastUtils() {
  }

  /**
   * Parse JSON and extract series ID from the External API result
   *
   * @param eventJson The returned JSON as <code>String</code>
   * @return SeriesId, if it exists
   */
  private static String seriesForEventJson(final String eventJson) {
    try {
      return new JSONObject(eventJson).getString("is_part_of");
    } catch (final JSONException e) {
      throw new ParsingJsonSyntaxException(eventJson);
    }
  }

  /**
   * Request metadata for the episode and return the corresponding series ID
   *
   * @param logger Logger to use
   * @param client Opencast HTTP client instance to use
   * @param orgaId The episode's organization
   * @param eventId The episode ID
   * @return Either a singleton <code>Flowable</code> with the resulting series ID, or an empty <code>Flowable</code>
   */
  private static Flowable<String> seriesForEvent(final Logger logger, final OpencastClient client,
                                                 final String orgaId, final String eventId) {
    final Cache<String, String> cache = client.getCache();
    // Check if cache exists and then check, if the eventId is already stored
    final String cachedId = cache != null ? cache.getIfPresent(eventId) : null;
    // If the eventId already has an entry with a corresponding seriesId, return seriesId
    if (cachedId != null)
      return Flowable.just(cachedId);

    logger.info("Retrieving series and start date for organization \"{}\", episode \"{}\"...", orgaId, eventId);

    // Request event information from Opencast. If available, store the seriesId in the cache
    return client
            .getEventRequest(orgaId, eventId)
            .concatMap(body -> OpencastUtils.checkResponseCode(logger, body, orgaId, eventId))
            .map(OpencastUtils::seriesForEventJson)
            .concatMap(series -> {
              if (!series.isEmpty()) {
                if (cache != null)
                  cache.put(eventId, series);
                return Flowable.just(series);
              }
              return Flowable.empty();
            });
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
   * Filter out invalid HTTP requests
   *
   * @param x The HTTP response we got
   * @param logger Logger for errors
   * @return An empty <code>Flowable</code> if it's an invalid HTTP response, or a singleton <code>Flowable</code>
   *         containing the body as a string
   */
  private static Flowable<String> checkResponseCode(
          final Logger logger,
          final Response<? extends ResponseBody> x,
          final String orgaId,
          final String eventId) {
    final boolean correctResponse = x.code() / 200 == 1;
    if (!correctResponse) {
      if (x.code() == 404) {
        // If eventId could not be found, skip
        logger.info("OCHTTPWARNING, episode {}, organization {}: code, {}", eventId, orgaId, x.code());
        return Flowable.empty();
      }
      logger.error("OCHTTPERROR, episode {}, organization {}: code, {}", eventId, orgaId, x.code());
      return Flowable.error(new InvalidHttpResponseException("Opencast HTTP error, code: " + x.code()));
    } else {
      logger.debug("OCHTTPSUCCESS, episode {}, organization {}", eventId, orgaId);
      return Flowable.fromCallable(() -> Objects.requireNonNull(x.body()).string());
    }
  }

  /**
   * Extracts eventId and statistical data from a JSON Object received from Matomo.
   * Subsequently, the Opencast Event API is called for relevant series information (seriesID).
   * Finally, all required data is saved and returned within an Impression Object.
   *
   * @param date Date for which the data is requested
   * @param client Opencast client used for the event API request
   * @param json JSON object representing one video and its statistics
   * @return Completed Impression, ready to be converted to a InfluxDB point
   */
  public static Flowable<ViewImpression> makeImpression(final OpencastClient client, final JSONObject json,
                                                        final OffsetDateTime date) {
    try {
      // Extract data from JSON
      final String label = json.getString("label");
      final String eventId = getEventJson(label);

      // If the JSON label doesnt fit the pattern (e.g. Live Streams), the entry is evicted
      if (eventId.isEmpty())
        return Flowable.empty();

      final String orgaId = client.getOrgaId();
      final Logger logger = client.getLogger();
      // Parse the remaining important data
      final int plays = json.getInt("nb_plays");
      final int visits = json.getInt("nb_unique_visitors_impressions");
      final int finishes = json.getInt("nb_finishes");
      final ArrayList<String> idSubtables = new ArrayList<>();
      idSubtables.add(json.getString("idsubdatatable"));

      // Create new Impression Flowable with series data from Opencast
      return seriesForEvent(logger, client, orgaId, eventId)
              .flatMap(series -> Flowable.just(new ViewImpression(eventId, orgaId,
                      series, plays, visits, finishes, date.toInstant(), idSubtables)));

    } catch (final JSONException e) {
      throw new ParsingJsonSyntaxException(json.toString());
    }
  }

  /**
   * Checks all emitted Impressions for duplicates. Duplicates are merged into one Impression.
   *
   * @param newViewImpression Newly emitted Impression
   */
  @NonNull
  public static ArrayList<ViewImpression> filterImpressions(final ArrayList<ViewImpression> oldList,
                                                            final ViewImpression newViewImpression) {
    final String eventId = newViewImpression.getEventId();
    // If the list already contains an Impression with the same eventId as the new Impression, merge
    // both into one Impression.
    if (oldList.contains(newViewImpression)) {

      final ViewImpression old = oldList.get(oldList.indexOf(newViewImpression));
      // Merge stats of old and new Impression
      final int plays = old.getPlays() + newViewImpression.getPlays();
      final int visitors = old.getVisitors() + newViewImpression.getVisitors();
      final int finishes = old.getFinishes() + newViewImpression.getFinishes();
      final ArrayList<String> idSubtables = old.getSubtables();
      idSubtables.addAll(newViewImpression.getSubtables());

      final ViewImpression combined = new ViewImpression(eventId, old.getOrgaId(), old.getSeriesId(), plays,
              visitors, finishes, old.getDate(), idSubtables);

      oldList.set(oldList.indexOf(newViewImpression), combined);

    } else {
      oldList.add(newViewImpression);
    }
    return oldList;
  }
}