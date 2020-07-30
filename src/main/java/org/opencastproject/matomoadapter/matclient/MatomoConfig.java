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

/**
 * Represents all fields for Matomo's External API configuration (immutable)
 */
public final class MatomoConfig {
  private final String uri;
  private final String siteId;
  private final String token;
  private final int rate;
  private final int timeout;

  public MatomoConfig(
          final String uri,
          final String siteId,
          final String token,
          final int rate,
          final int timeout) {
    this.uri = uri;
    this.siteId = siteId;
    this.token = token;
    this.rate = rate;
    this.timeout = timeout;
  }

  public String getUri() {
    return this.uri;
  }

  public String getSiteId() {
    return this.siteId;
  }

  public String getToken() {
    return this.token;
  }

  public int getRate() {
    return this.rate;
  }

  public int getTimeout() { return this.timeout; }
}
