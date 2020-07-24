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

import com.google.common.cache.Cache;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.reactivex.Flowable;
import io.reactivex.annotations.NonNull;
import io.reactivex.internal.operators.maybe.MaybeMergeArray;
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
   * @return Either a series ID or <code>Optional.empty()</code>
   */
  @SuppressWarnings("unchecked")
  private static Optional<OpencastDataPair> dataPairForEventJson(final String eventJson) {
    /*
    TODO: Use only one JSON handler GSON/JSONObject
     */
    try {
      final Map<String, Object> m = new Gson().fromJson(eventJson, Map.class);
      if (m != null) {
        final Object isPartOf = m.get("is_part_of");
        final Object start = m.get("start");
        if ((isPartOf instanceof String) && (start instanceof String)) {
          final String startDate = ((String) start).substring(0, 10);
          return Optional.of(new OpencastDataPair((String) isPartOf, startDate));
        }
      }
      return Optional.empty();
    } catch (final JsonSyntaxException e) {
      throw new ParsingJsonSyntaxException(eventJson);
    }
  }

  /**
   * Request metadata for the episode and return the corresponding series ID
   *
   * @param logger            Logger to use
   * @param client            Opencast HTTP client instance to use
   * @param organization      The episode's organization
   * @param episodeId         The episode ID
   * @return Either a singleton <code>Flowable</code> with the resulting series ID, or an empty <code>Flowable</code>
   */
  private static Flowable<OpencastDataPair> dataPairForEvent(
          final Logger logger,
          final OpencastClient client,
          final String organization,
          final String episodeId,
          // TEST TEST TEST TEST
          final Collection<String> count) {

    final Cache<String, OpencastDataPair> cache = client.getCache();

    // Check if cache exists and then check, if the eventId is already stored
    final OpencastDataPair cachedId = cache != null ? cache.getIfPresent(episodeId) : null;

    // If the eventId already has an entry with a corresponding seriesId, return seriesId
    if (cachedId != null) {
      // TEST TEST TEST TEST
      count.add("val");
      return Flowable.just(cachedId);
    }

    logger.info("Retrieving series and start date for organization \"{}\", episode \"{}\"...", organization, episodeId);

    // Request event information from Opencast. If available, store the seriesId in the cache
    return client
            .getEventRequest(organization, episodeId)
            .concatMap(body -> OpencastUtils.checkResponseCode(logger, body, organization, episodeId))
            .map(OpencastUtils::dataPairForEventJson)
            .concatMap(dataPair -> {
              if (dataPair.isPresent()) {
                if (cache != null)
                  cache.put(episodeId, dataPair.get());
                return Flowable.just(dataPair.get());
              }
              return Flowable.empty();
            });
  }

  /**
   * Parses the video URL to find the episodeId. Currently the most common URL-types, coming from the
   * Theodul and Paella player are supported. Live Streams do not contain an episodeId.
   *
   * @param label Sub-URL of the video
   * @return The eventID parsed from the URL
   */
  private static String getEventJson(final String label) {

    if (!label.contains("engage") && !label.contains("static"))
      return null;

    final String sub = label.substring(1, 7);

    if (sub.equals("engage")) {
      return label.substring(label.lastIndexOf("?id=") + 4, label.lastIndexOf("?id=") + 40);
    } else if (sub.equals("static")) {
      return label.substring(label.lastIndexOf("yer/") + 4, label.lastIndexOf("yer/") + 40);
    }
    return null;
  }

  /**
   * Filter out invalid HTTP requests
   *
   * @param x The HTTP response we got
   * @param logger Logger for errors
   * @return An empty <code>Flowable</code> if it's an invalid HTTP response, or a singleton <code>Flowable</code> containing the body as a string
   */
  private static Flowable<String> checkResponseCode(
          final Logger logger,
          final Response<? extends ResponseBody> x,
          final String organization,
          final String episodeId) {
    final boolean correctResponse = x.code() / 200 == 1;
    if (!correctResponse) {
      if (x.code() == 404) {
        logger.info("OCHTTPWARNING, episode {}, organization {}: code, {}", x.code(), episodeId, organization);
        return Flowable.empty();
      }
      logger.error("OCHTTPERROR, episode {}, organization {}: code, {}", x.code(), episodeId, organization);
      return Flowable.error(new InvalidOpencastResponse(x.code()));
    } else {
      logger.debug("OCHTTPSUCCESS, episode {}, organization {}", episodeId, organization);
      return Flowable.fromCallable(() -> Objects.requireNonNull(x.body()).string());
    }
  }

  /**
   * Extracts eventId and statistical data from a JSON Object received from Matomo.
   * Subsequently, the Opencast Event API is called for relevant series information (seriesID).
   * Finally, all required data is saved and returned within an Impression Object.
   *
   * @param time Date for which the data is requested
   * @param logger Logger from the main method
   * @param client Opencast client used for the event API request
   * @param json JSON object representing one video and its statistics
   * @return Completed Impression, ready to be converted to a InfluxDB point
   */
  public static Flowable<Impression> makeImpression(
          @SuppressWarnings("SameParameterValue") final Logger logger,
          final OpencastClient client,
          final JSONObject json,
          final OffsetDateTime time,
          // TEST TEST TEST TEST
          final Collection<String> count) {

    try {
      // Extract data from JSON
      final String label = json.getString("label");
      final String episodeId = getEventJson(label);

      // If the JSON label doesnt fit the pattern (e.g. Live Streams), the entry is evicted
      if (episodeId == null)
        return Flowable.empty();

      // Parse the remaining important data
      final int plays = json.getInt("nb_plays");
      final int visits = json.getInt("nb_unique_visitors_impressions");
      final int finishes = json.getInt("nb_finishes");

      // Create new Impression Flowable with series data from Opencast
      return dataPairForEvent(logger, client, "org", episodeId, count)
              .flatMap(dataPair -> Flowable.just(new Impression(episodeId, "mh_default_org",
                      dataPair.getSeriesId(), dataPair.getStartDate(), plays, visits, finishes, time)));

    } catch (final JSONException e) {
      throw new ParsingJsonSyntaxException(json.toString());
    }
  }

  /**
   * Checks all emitted Impressions for duplicates. Duplicates are merged into one Impression.
   *
   * @param newImpression Newly emitted Impression
   */
  @NonNull
  public static ArrayList<Impression> filterImpressions(final ArrayList<Impression> oldList,
                                                        final Impression newImpression) {

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
}
