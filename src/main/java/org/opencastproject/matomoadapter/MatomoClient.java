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

import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import devcsrj.okhttp3.logging.HttpLoggingInterceptor;
import io.reactivex.Flowable;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

/**
 * Manages Opencast's External API endpoint
 */
public final class MatomoClient {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MatomoClient.class);

  private static final String FILTER_PATTERN = "^[1-9]\\d*$";
  private static final String SHOW_COL = "label,nb_plays,nb_unique_visitors_impressions,nb_finishes";

  private final MatomoConfig matomoConfig;
  private final OkHttpClient client;

  /**
   * Create the client.
   *
   * @param matomoConfig Matomo configuration
   */
  public MatomoClient(final MatomoConfig matomoConfig) {
    this.matomoConfig = matomoConfig;
    // Initialize HTTP client for Matomo network requests
    final Interceptor interceptor = new HttpLoggingInterceptor();
    final OkHttpClient.Builder b = new OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(500, TimeUnit.SECONDS)
            .readTimeout(500, TimeUnit.SECONDS)
            .writeTimeout(500, TimeUnit.SECONDS);
    // Add rate limiter in case network traffic needs to be throttled
    this.client = matomoConfig.getRate() != 0 ?
            b.addInterceptor(new LimitInterceptor(matomoConfig.getRate())).build() :
            b.build();
  }

  /**
   * Create a separate endpoint (meaning HTTP interface) for each organization
   *
   * @return A retrofit interface to be used to make HTTP calls
   */
  private MatomoExternalAPI getClient() {
    final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(this.matomoConfig.getUri())
            .client(this.client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build();
    return retrofit.create(MatomoExternalAPI.class);
  }

  /**
   * Send a HTTP GET request to the Matomo MediaAnalytics.getVideoResources API. If given idSubtable is null,
   * the expected response is a JSONArray containing all relevant statistical data for every episode played at
   * least once on the specified date.
   * If an idSubtable is provided, the response should contain video segments information for given date and
   * idSubtable.
   *
   * @param date Date for which statistics are requested
   * @param idSubtable Unique identifier of a resource on given date. If null, return all viewed episodes
   * @return Raw response to the request (JSONArray/String)
   */
  public Flowable<Response<ResponseBody>> getResourcesRequest(final String date, final String idSubtable) {

    final String idSite = this.matomoConfig.getSiteId();
    final String token = this.matomoConfig.getToken();

    // If no idSubtable was passed, it is assumed, that a list of all played episodes is requested
    if(idSubtable == null) {
      LOGGER.debug("MATOMOREQUESTSTART, method: getVideoResources, date: {}", date);
      return getClient().getResources(idSite, token, date, "1",
              FILTER_PATTERN, "nb_plays", SHOW_COL, "");
    }
    // Otherwise, request video segments information for given date and idSubtable
    LOGGER.debug("MATOMOREQUESTSTART, method: getVideoSegments, date: {}, idSubtable: {}", date, idSubtable);
    return getClient().getResources(idSite, token, date, idSubtable,
            "", "", "", "media_segments");
  }
}
