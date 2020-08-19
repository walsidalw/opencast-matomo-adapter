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

import com.google.common.cache.Cache;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.Objects;

import io.reactivex.Flowable;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Various utility functions for Opencast
 */
public final class OpencastUtils {
  private OpencastUtils() {
  }

  /**
   * Request metadata for the episode and return the corresponding series ID
   *
   * @param logger Logger to use
   * @param client Opencast HTTP client instance to use
   * @param orgaId The episode's organization
   * @param eventId The event ID
   * @return Either a singleton <code>Flowable</code> with the resulting series ID, or an empty <code>Flowable</code>
   */
  public static Flowable<String> seriesForEvent(final Logger logger, final OpencastClient client,
                                                 final String orgaId, final String eventId) {
    final Cache<String, String> cache = client.getCache();
    // Check if cache exists and then check, if the eventId is already stored
    final String cachedId = cache != null ? cache.getIfPresent(eventId) : null;
    // If the eventId already has an entry with a corresponding seriesId, return seriesId
    if (cachedId != null)
      return Flowable.just(cachedId);

    logger.info("Retrieving series and start date for organization \"{}\", episode \"{}\"...", orgaId, eventId);

    // Request event information from Opencast
    return client
            .getEventRequest(orgaId, eventId)
            .concatMap(body -> OpencastUtils.checkResponseCode(logger, body, orgaId, eventId))
            .map(OpencastUtils::seriesForEventJson)
            // If available, store the seriesId in the cache
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
}
