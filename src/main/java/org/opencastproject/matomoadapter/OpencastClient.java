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

  private static final String ORGANIZATION = "{organization}";

  private final OpencastConfig opencastConfig;
  private final OkHttpClient client;

  /**
   * Create the client
   *
   * @param opencastConfig Opencast configuration
   */
  public OpencastClient(final OpencastConfig opencastConfig) {
    this.opencastConfig = opencastConfig;
    final Interceptor interceptor = new HttpLoggingInterceptor();
    this.client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
  }

  private boolean hostHasPlaceholder() {
    return this.opencastConfig.getUri().contains(ORGANIZATION);
  }

  private OpencastExternalAPI getClient(final String organization) {
    final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(this.opencastConfig.getUri())
            .client(this.client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build();
    return retrofit.create(OpencastExternalAPI.class);
  }

  public Flowable<Response<ResponseBody>> getEventRequest(final String organization, final String episodeId) {
    LOGGER.debug("OCREQUESTSTART, episode {}, organization {}", episodeId, organization);
    return getClient(organization).getEvent(episodeId, getAuthHeader());
  }

  private String getAuthHeader() {
    return basicAuthHeader(this.opencastConfig.getUser(), this.opencastConfig.getPassword());
  }

  private static String basicAuthHeader(final String user, final String pw) {
    final String userAndPass = user + ":" + pw;
    final String userAndPassBase64 = Base64.getEncoder().encodeToString(userAndPass.getBytes(StandardCharsets.UTF_8));
    return "Basic " + userAndPassBase64;
  }
}