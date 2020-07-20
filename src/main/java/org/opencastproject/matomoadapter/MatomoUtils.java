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

import java.util.ArrayList;
import java.util.Objects;

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
    try{
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
   * @param hour Server time; acts as time frame for the request (all views since)
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
      logger.error("Fehlerhafter code: {}", x.code());
    } else {
      logger.debug("OCHTTPSUCCESS");
    }
    return correctResponse ?
            Flowable.fromCallable(() -> Objects.requireNonNull(x.body()).string()) :
            Flowable.error(new InvalidMatomoResponse(x.code()));
  }
}
