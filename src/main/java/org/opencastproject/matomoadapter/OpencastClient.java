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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

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
public final class OpencastClient {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(OpencastClient.class);

  private final OpencastConfig opencastConfig;
  private final OkHttpClient client;
  private final Cache<String, String> cache;

  /**
   * Create the client. If needed, additionally initialize a rate limiter and cache.
   *
   * @param opencastConfig Opencast configuration
   */
  public OpencastClient(final OpencastConfig opencastConfig) {
    this.opencastConfig = opencastConfig;
    // Initialize HTTP client for Opencast network requests
    final Interceptor interceptor = new HttpLoggingInterceptor();
    final OkHttpClient.Builder b = new OkHttpClient.Builder()
            .addInterceptor(interceptor)
            // Set timeouts
            .connectTimeout(opencastConfig.getTimeout(), TimeUnit.SECONDS)
            .readTimeout(opencastConfig.getTimeout(), TimeUnit.SECONDS)
            .writeTimeout(opencastConfig.getTimeout(), TimeUnit.SECONDS);
    // Add rate limiter in case network traffic needs to be throttled
    this.client = opencastConfig.getRate() != 0 ?
            b.addInterceptor(new LimitInterceptor(opencastConfig.getRate())).build() :
            b.build();
    // Initialize cache, if needed
    this.cache = !opencastConfig.getCacheDuration().isZero() && opencastConfig.getCacheSize() != 0 ?
            CacheBuilder.newBuilder()
                    .expireAfterAccess(opencastConfig.getCacheDuration())
                    .maximumSize(opencastConfig.getCacheSize())
                    .build() : null;
  }

  /**
   * Build retrofit Opencast API.
   *
   * @return Retrofit API
   */
  private OpencastExternalAPI getClient() {
    final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(this.opencastConfig.getUri())
            .client(this.client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build();
    return retrofit.create(OpencastExternalAPI.class);
  }

  /**
   * Send a HTTP GET request to the Opencast Events API. The response contains general episode data
   * like title, creation date and seriesId.
   *
   * @param organization Opencast organizationId
   * @param episodeId Opencast episode/eventId
   * @return Response from GET request
   */
  public Flowable<Response<ResponseBody>> getEventRequest(final String organization, final String episodeId) {
    LOGGER.debug("OCREQUESTSTART, episode {}, organization {}", episodeId, organization);
    return getClient().getEvent(episodeId, getAuthHeader());
  }

  private String getAuthHeader() {
    return Utils.basicAuthHeader(this.opencastConfig.getUser(), this.opencastConfig.getPassword());
  }

  public Cache<String, String> getCache() {
    return this.cache;
  }
}