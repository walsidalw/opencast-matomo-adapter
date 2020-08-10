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

import io.reactivex.Flowable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for the external API of Matomo
 */
@FunctionalInterface
public interface MatomoExternalAPI {
  @GET("/?module=API&method=MediaAnalytics.getVideoResources&period=day&format=json&filter_limit=-1")
  Flowable<Response<ResponseBody>> getResources(
          @Query("idSite") String idSite,
          @Query("token_auth") String token,
          @Query("date") String date,
          @Query("idSubtable") String idSubtable,
          @Query("filter_pattern") String filterPat,
          @Query("filter_column") String filterCol,
          @Query("showColumns") String showCol,
          @Query("secondaryDimension") String dimension);
}
