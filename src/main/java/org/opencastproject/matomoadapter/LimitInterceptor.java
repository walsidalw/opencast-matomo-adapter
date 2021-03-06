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

import com.google.common.util.concurrent.RateLimiter;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * HTTP interceptor for rate limiting. RateLimiter is marked as beta.
 */
public class LimitInterceptor implements Interceptor {

  private final RateLimiter rateLimiter;

  public LimitInterceptor(final int rate) {
    this.rateLimiter = RateLimiter.create(rate);
  }

  @Override
  public Response intercept(final Chain chain) throws IOException {
    this.rateLimiter.acquire(1);
    return chain.proceed(chain.request());
  }
}
