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

package org.opencastproject.matomoadapter.matclient;

import org.opencastproject.matomoadapter.InvalidHttpResponseException;
import org.opencastproject.matomoadapter.ParsingJsonSyntaxException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Objects;

import io.reactivex.Flowable;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Provides utility functions for Matomo API requests.
 */
public final class MatomoUtils {

  private MatomoUtils() {
  }

  /**
   * Invoke a request to the Matomo MediaAnalytics.getVideoResources API for view statistics.
   *
   * @param logger Logger for info/error logging
   * @param client Matomo client instance
   * @param date Date of request
   * @return Returns Flowable with JSONObjects containing episode statistics
   */
  public static Flowable<JSONObject> getViewed(final Logger logger, final MatomoClient client,
                                               final OffsetDateTime date) {
    logger.info("Retrieving viewed episodes for date: {}", date);

    return getResources(logger, client, date, null, null)
            // Convert response body to JSONObjects List
            .map(MatomoUtils::getViewedJson)
            // Emit each item from List
            .flatMapIterable(json -> json);
  }

  /**
   * Invoke a request to the Matomo MediaAnalytics.getVideoResources API for segment statistics.
   *
   * @param logger Logger for info/error logging
   * @param client Matomo client instance
   * @param date Date of request
   * @param idSubtable Unique identifier for a player-episode pair on given date
   * @return Returns Flowable with Strings containing segment statistics
   */
  public static Flowable<String> getSegments(final Logger logger, final MatomoClient client, final OffsetDateTime date,
          final String idSubtable) {
    return getResources(logger, client, date, idSubtable, "media_segments");
  }

  /**
   * Invoke a request to the Matomo MediaAnalytics.getVideoResources API
   * for given dimension and idSubtable. Filter out empty responses.
   *
   * @param logger Logger for info/error logging
   * @param client Matomo client instance
   * @param idSubtable Unique identifier for a player-episode pair on given date
   * @param date Date of request
   * @param dimension Secondary dimension for request
   * @return Returns Flowable with Strings containing response body
   */
  private static Flowable<String> getResources(final Logger logger, final MatomoClient client, final OffsetDateTime date,
                                               final String idSubtable, final String dimension) {
    // Convert OffsetDateTime to fitting format: YYYY-MM-DD
    final String reqDate = date.toLocalDate().toString();
    return client
            .getResourcesRequest(reqDate, idSubtable, dimension)
            // Check, if response code is correct
            .concatMap(body -> MatomoUtils.checkResponseCode(logger, body))
            // Filter out all empty responses
            .filter(x -> x.length() > 2);
  }

  /**
   * Convert response JSON String to ArrayList.
   *
   * @param json Response body from Matomo API request
   * @return ArrayList with separate JSONObjects containing statistics for each episode
   */
  private static ArrayList<JSONObject> getViewedJson(final String json) {
    try {
      final JSONArray jArray = new JSONArray(json);
      final ArrayList<JSONObject> list = new ArrayList<>();
      if (jArray.length() != 0) {
        for (int i = 0; i < jArray.length(); i++) {
          list.add(jArray.getJSONObject(i));
        }
      }
      return list;
    } catch (final JSONException e) {
      throw new ParsingJsonSyntaxException(json);
    }
  }

  /**
   * Filter out invalid HTTP responses.
   *
   * @param x The HTTP response we got
   * @param logger Logger for errors
   * @return An empty <code>Flowable</code> if it's an invalid HTTP response, or a singleton <code>Flowable</code>
   *         containing the body as a string
   */
  private static Flowable<String> checkResponseCode(final Logger logger, final Response<? extends ResponseBody> x) {
    final boolean correctResponse = x.code() / 200 == 1;
    if (!correctResponse) {
      logger.error("MATOMOHTTPERROR: code: {}", x.code());
    } else {
      logger.debug("MATOMOHTTPSUCCESS");
    }
    return correctResponse ?
            Flowable.fromCallable(() -> Objects.requireNonNull(x.body()).string()) :
            Flowable.error(new InvalidHttpResponseException("Matomo HTTP error, code: " + x.code()));
  }
}
