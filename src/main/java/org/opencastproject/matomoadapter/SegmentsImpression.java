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

import org.influxdb.dto.Point;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * An Impression is an object containing all necessary metadata to write to the InfluxDB (immutable)
 */
public final class SegmentsImpression {
  private final String episodeId;
  private final String organizationId;
  private final String segments;
  private final Instant date;

  public SegmentsImpression (
          final String episodeId,
          final String organizationId,
          final String segments,
          final OffsetDateTime date,
          // TEST TEST TEST
          final ConcurrentLinkedQueue<String> cou) {
    this.episodeId = episodeId;
    this.organizationId = organizationId;
    this.segments = segments;
    this.date = date.toInstant();

    // TEST TEST TEST TEST
    cou.add("te");
    System.out.println("Size WIP: " + cou.size());
  }

  public SegmentsImpression (final SegmentsImpression seg, final Instant time) {
    this.episodeId = seg.getEpisodeId();
    this.organizationId = seg.getOrganizationId();
    this.segments = seg.getSegments();
    this.date = time;
  }

  /**
   * Convert this impression into an InfluxDB point
   * @return The InfluxDB point
   */
  public Point toPoint() {
    return Point
            .measurement("segments_daily")
            .time(this.date.getEpochSecond(), TimeUnit.SECONDS)
            .addField("segments", this.segments)
            .tag("organizationId", this.organizationId)
            .tag("episodeId", this.episodeId)
            .build();
  }

  public String getEpisodeId() {
    return this.episodeId;
  }

  public String getSegments() { return this.segments; }

  public String getOrganizationId() { return this.organizationId; }
}
