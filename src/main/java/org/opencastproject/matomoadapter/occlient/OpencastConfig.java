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

package org.opencastproject.matomoadapter.occlient;

import java.time.Duration;

/**
 * Represents all fields for Opencast's External API configuration (immutable)
 */
public final class OpencastConfig {
  private final String uri;
  private final String user;
  private final String password;
  private final String orgaId;
  private final int cacheSize;
  private final Duration cacheDuration;
  private final int rate;
  private final int timeout;

  public OpencastConfig(
          final String uri,
          final String user,
          final String password,
          final String orgaId,
          final int cacheSize,
          final Duration cacheDuration,
          final int rate,
          final int timeout) {
    this.uri = uri;
    this.user = user;
    this.password = password;
    this.orgaId = orgaId;
    this.cacheSize = cacheSize;
    this.cacheDuration = cacheDuration;
    this.rate = rate;
    this.timeout = timeout;
  }

  String getUri() {
    return this.uri;
  }

  String getUser() {
    return this.user;
  }

  String getPassword() {
    return this.password;
  }

  String getOrgaId() { return this.orgaId; }

  int getCacheSize() {
    return this.cacheSize;
  }

  Duration getCacheDuration() {
    return this.cacheDuration;
  }

  int getRate() { return this.rate; }

  int getTimeout() { return this.timeout; }
}
