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

import io.reactivex.Flowable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for the external API of Matomo
 */
@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface MatomoExternalAPI {
  @GET("/?module=API&method=MediaAnalytics.getVideoResources&period=day&format=json&filter_limit="
          + "-1&idSubtable=1&filter_pattern=^[1-9]\\d*$&filter_column=nb_plays&showColumns="
          + "label,nb_plays,nb_unique_visitors_impressions,nb_finishes")
  Flowable<Response<ResponseBody>> getResources(
          @Query("idSite") String idSite,
          @Query("token_auth") String token,
          @Query("date") String date);

  @GET("/?module=API&method=MediaAnalytics.getVideoTitles&period=range&format=json&idSubtable=1"
          + "&filter_limit=-1&secondaryDimension=media_segments")
  Flowable<Response<ResponseBody>> getSegments(
          @Query("idSite") String idSite,
          @Query("token_auth") String token,
          @Query("segment") String source,
          @Query("date") String period);
}