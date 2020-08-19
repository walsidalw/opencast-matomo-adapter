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

import org.opencastproject.matomoadapter.LimitInterceptor;

import org.slf4j.Logger;

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
 * Manages Opencast's External API endpoint.
 */
public final class MatomoClient {
  // Filter pattern for view statistics requests. Filters out episodes with 0 views.
  private static final String FILTER_PATTERN = "^[1-9]\\d*$";
  // Filter out unnecessary columns to shave of some weight from responses
  private static final String SHOW_COL = "label,nb_plays,nb_unique_visitors_impressions,nb_finishes";

  private final Logger logger;
  private final MatomoConfig matomoConfig;
  private final OkHttpClient httpClient;
  private final MatomoExternalAPI apiClient;

  /**
   * Create the client.
   *
   * @param matomoConfig Matomo configuration
   */
  public MatomoClient(final MatomoConfig matomoConfig, final Logger logger) {
    this.logger = logger;
    this.matomoConfig = matomoConfig;
    // Initialize HTTP client for Matomo network requests
    final Interceptor interceptor = new HttpLoggingInterceptor();
    final OkHttpClient.Builder b = new OkHttpClient.Builder()
            .addInterceptor(interceptor)
            // Set timeouts
            .connectTimeout(matomoConfig.getTimeout(), TimeUnit.SECONDS)
            .readTimeout(matomoConfig.getTimeout(), TimeUnit.SECONDS)
            .writeTimeout(matomoConfig.getTimeout(), TimeUnit.SECONDS);
    // Add rate limiter in case network traffic needs to be throttled
    this.httpClient = matomoConfig.getRate() != 0 ?
            b.addInterceptor(new LimitInterceptor(matomoConfig.getRate())).build() :
            b.build();
    this.apiClient = getClient();
  }

  /**
   * Create a separate endpoint (meaning HTTP interface) for each organization.
   *
   * @return A retrofit interface to be used to make HTTP calls
   */
  private MatomoExternalAPI getClient() throws IllegalArgumentException {
    final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(this.matomoConfig.getUri())
            .client(this.httpClient)
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
   * @param date Date for which statistics are requested. Expected format: YYYY-MM-DD
   * @param idSubtable Unique identifier of a resource on given date. If null, return all viewed episodes
   * @param dimension Secondary dimension for request. Used in combination with idSubtable
   * @return Raw response to the request (JSONArray/String)
   */
  Flowable<Response<ResponseBody>> getResourcesRequest(final String date, final String idSubtable,
                                                       final String dimension) {
    final String idSite = this.matomoConfig.getSiteId();
    final String token = this.matomoConfig.getToken();

    // If no idSubtable was passed, it is assumed, that a list of all played episodes is requested
    if (idSubtable == null) {
      this.logger.debug("MATOMOREQUESTSTART, method: getVideoResources, date: {}", date);
      // If you wish to include episodes with 0 views, set FILTER_PATTERN to ""
      return this.apiClient.getResources(idSite, token, date, "1",
              FILTER_PATTERN, "nb_plays", SHOW_COL, "");
    }
    // Otherwise, request video statistics with given dimension, date and idSubtable
    this.logger.debug("MATOMOREQUESTSTART, method: getVideoResources ({}), date: {}, idSubtable: {}",
            dimension, date, idSubtable);
    return this.apiClient.getResources(idSite, token, date, idSubtable,
            "", "", "", dimension);
  }
}
