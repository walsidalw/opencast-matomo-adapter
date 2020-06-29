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

//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;

import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

  /*
   * TODO: Figure out how to use cache to store responds
   */
  public static final class CacheKey {
    private final String organizationId;
    private final String episodeId;

    public CacheKey(final String organizationId, final String episodeId) {
      this.organizationId = organizationId;
      this.episodeId = episodeId;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      final CacheKey cacheKey = (CacheKey) o;
      return this.organizationId.equals(cacheKey.organizationId) && this.episodeId.equals(cacheKey.episodeId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.organizationId, this.episodeId);
    }
  }

  private final MatomoConfig matomoConfig;
  private final OkHttpClient client;

  /**
   * Create the client
   *
   * @param matomoConfig Matomo configuration
   */
  public MatomoClient(final MatomoConfig matomoConfig) {
    this.matomoConfig = matomoConfig;
    final Interceptor interceptor = new HttpLoggingInterceptor();
    this.client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
  }

  private String getRawAddress() {
    return this.matomoConfig.getUri();
  }

  /**
   * Create a separate endpoint (meaning HTTP interface) for each organization
   *
   * @return A retrofit interface to be used to make HTTP calls
   */
  private MatomoExternalAPI getClient() {
    final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(getRawAddress())
            .client(this.client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build();
    return retrofit.create(MatomoExternalAPI.class);
  }

  /**
   * Request episode metadata from Opencast
   *
   * @param organization Organization (tenant) for the episode
   * @param episodeId    The episode's ID (usually a UUID)
   * @return A <code>Flowable</code> with the response body
   */
  public Flowable<Response<ResponseBody>> getRequest(final String method, final String token,
                                                     final String idSite, final String format) {
    final Flowable<Response<ResponseBody>> requestUncached = getRequestUncached(method, token, idSite, format);
    return requestUncached;
  }

  private Flowable<Response<ResponseBody>> getRequestUncached(final String method, final String token,
                                                              final String idSite, final String format) {
    LOGGER.debug("MATOMOREQUESTSTART, method {}", method);
    return getClient().getDetails(method, token, idSite, format);
  }
}
