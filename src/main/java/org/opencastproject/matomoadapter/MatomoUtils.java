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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.reactivex.Flowable;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Various utility functions for Matomo API requests.
 */
public final class MatomoUtils {

  private MatomoUtils() {
  }

  /**
   * Convert response JSON String to ArrayList.
   *
   * @param json Response body from Matomo API request
   * @return ArrayList with separate JSONObjects containing statistics for each episode
   */
  private static ArrayList<JSONObject> getResourcesJson(final String json) {
    try {
      final JSONArray jArray = new JSONArray(json);
      final ArrayList<JSONObject> list = new ArrayList<>();
      if (jArray.length() != 0) {
        for (int i = 0; i < jArray.length(); i++) {
          list.add(jArray.getJSONObject(i));
        }
      }
      Main.testT(list.size());
      return list;
    } catch (final JSONException e) {
      throw new ParsingJsonSyntaxException(json);
    }
  }

  /**
   * Invoke a request to the Matomo MediaAnalytics.getVideoResources API.
   *
   * @param logger Logger Object from Main
   * @param client Matomo client instance
   * @param idSite Site ID from config file
   * @param token Auth token for Matomo API from config file
   * @param date Date for request
   * @return Returns Flowable with JSONObjects containing episode statistics
   */
  public static Flowable<JSONObject> getResources(
          final Logger logger,
          final MatomoClient client,
          final String idSite,
          final String token,
          final String date) {
    logger.info("Retrieving resources");
    return client
            .getResourcesRequest(idSite, token, date)
            .concatMap(body -> MatomoUtils.checkResponseCode(logger, body))
            .map(MatomoUtils::getResourcesJson)
            .flatMapIterable(json -> json);
  }

  /**
   * Invoke a request to the Matomo MediaAnalytics.getVideoTitles API and filter out empty responses.
   *
   * @param logger Logger Object from Main
   * @param client Matomo client instance
   * @param idSite Site ID from config file
   * @param token Auth token for Matomo API from config file
   * @param episodeID Episode for which segment information shall be retrieved
   * @param date Date for request
   * @return Returns Flowable with Strings containing segment statistics
   */
  private static Flowable<String> getSegments(
          final Logger logger,
          final MatomoClient client,
          final String idSite,
          final String token,
          final String episodeID,
          final String date) {
    logger.info("Retrieving segments");
    return client
            .getSegmentsRequest(idSite, token, episodeID, date)
            .concatMap(body -> MatomoUtils.checkResponseCode(logger, body))
            .filter(x -> x.length() > 2);
  }

  public static Flowable<Segments> makeSegmentsImpression(
          final Logger logger,
          final MatomoClient client,
          final String episodeID,
          final String idSite,
          final String token,
          final String date,
          final OffsetDateTime time) {

    return getSegments(logger, client, idSite, token, episodeID, date)
            .flatMap(json -> Flowable.just(new Segments(episodeID, "mh_default", json, time)));
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
          final Response<? extends ResponseBody> x) {
    final boolean correctResponse = x.code() / 200 == 1;
    if (!correctResponse) {
      logger.error("MATHTTPERROR: code: {}", x.code());
    } else {
      logger.debug("MATHTTPSUCCESS");
    }
    return correctResponse ?
            Flowable.fromCallable(() -> Objects.requireNonNull(x.body()).string()) :
            Flowable.error(new InvalidMatomoResponse(x.code()));
  }
}
