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

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Objects;

/**
 * An Impression is an object containing all necessary metadata to write to the InfluxDB (immutable)
 */
public final class ViewImpression {
  private final String eventId;
  private final String orgaId;
  private final String seriesId;
  private final int plays;
  private final int visitors;
  private final int finishes;
  private final Instant date;
  private final ArrayList<String> idSubtables;

  public ViewImpression(
          final String eventId,
          final String orgaId,
          final String seriesId,
          final int plays,
          final int visitors,
          final int finishes,
          final Instant date,
          final ArrayList<String> idSubtables) {
    this.eventId = eventId;
    this.orgaId = orgaId;
    this.seriesId = seriesId;
    this.plays = plays;
    this.visitors = visitors;
    this.finishes = finishes;
    this.date = date;
    this.idSubtables = idSubtables;
  }

  /**
   * Convert this impression into an InfluxDB point
   * @return The InfluxDB point
   */
  public Point toPoint() {
    return Point
            .measurement("impressions_daily")
            .time(this.date.getEpochSecond(), TimeUnit.SECONDS)
            .addField("plays", this.plays)
            .addField("visitors", this.visitors)
            .addField("finishes", this.finishes)
            .tag("seriesId", this.seriesId)
            .tag("organizationId", this.orgaId)
            .tag("eventId", this.eventId)
            .build();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    final ViewImpression viewImpression = (ViewImpression) o;
    return this.orgaId.equals(viewImpression.orgaId) && this.eventId.equals(viewImpression.eventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.orgaId, this.eventId);
  }

  public String getEventId() { return this.eventId; }

  public String getOrgaId() { return this.orgaId; }

  public String getSeriesId() { return this.seriesId; }

  public int getPlays() { return this.plays; }

  public int getVisitors() { return this.visitors; }

  public int getFinishes() { return this.finishes; }

  public Instant getDate() { return this.date; }

  public ArrayList<String> getSubtables() { return this.idSubtables; }
}
