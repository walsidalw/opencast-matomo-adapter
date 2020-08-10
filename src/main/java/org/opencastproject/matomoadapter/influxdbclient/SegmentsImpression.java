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

package org.opencastproject.matomoadapter.influxdbclient;

import org.influxdb.dto.Point;
import org.json.JSONArray;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * An Impression is an object containing all necessary metadata to write to the InfluxDB (immutable)
 */
public final class SegmentsImpression {
  private final String eventId;
  private final String orgaId;
  private final JSONArray segments;
  private final Instant date;

  public SegmentsImpression(
          final String eventId,
          final String orgaId,
          final JSONArray segments,
          final Instant date) {
    this.eventId = eventId;
    this.orgaId = orgaId;
    this.segments = segments;
    this.date = date;
  }

  /**
   * Convert this impression into an InfluxDB point
   * @return The InfluxDB point
   */
  public Point toPoint() {
    return Point
            .measurement("segments_daily")
            .time(this.date.getEpochSecond(), TimeUnit.SECONDS)
            .addField("segments", this.segments.toString())
            .tag("organizationId", this.orgaId)
            .tag("eventId", this.eventId)
            .build();
  }

  public String getEventId() {
    return this.eventId;
  }

  public JSONArray getSegments() { return this.segments; }

  public String getOrgaId() { return this.orgaId; }
}
