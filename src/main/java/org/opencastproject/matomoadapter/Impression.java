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

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/**
 * An Impression is a "resolved" RawImpression, containing all necessary metadata to write to the InfluxDB (immutable)
 */
public final class Impression {
  private final String episodeId;
  private final String organizationId;
  private final String seriesId;
  private final int plays;
  private final int visitors;
  private final int finishes;
  private final OffsetDateTime date;

  public Impression(
          final String episodeId,
          final String organizationId,
          final String seriesId,
          final int plays,
          final int visitors,
          final int finishes,
          final OffsetDateTime date) {
    this.episodeId = episodeId;
    this.organizationId = organizationId;
    this.seriesId = seriesId;
    this.plays = plays;
    this.visitors = visitors;
    this.finishes = finishes;
    this.date = date;
  }

  /**
   * Convert this impression into an InfluxDB point
   * @return The InfluxDB point
   */
  public Point toPoint() {
    return Point
            .measurement("impressions_daily")
            .time(this.date.toInstant().getEpochSecond(), TimeUnit.SECONDS)
            .addField("plays", this.plays)
            .addField("visitors", this.visitors)
            .addField("finishes", this.finishes)
            //.tag("seriesId", this.seriesId)
            .tag("seriesId", "2c2b6898-a1af-4b97-8b86-d72ad26d34dc")
            //.tag("organizationId", this.organizationId)
            .tag("organizationId", this.organizationId)
            //.tag("episodeId", this.episodeId)
            .tag("episodeId", "6d26d029-9238-4b6e-aa26-97983b88f614")
            .build();
  }
}
