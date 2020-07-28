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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * An Impression is an object containing all necessary metadata to write to the InfluxDB (immutable)
 */
public final class Impression {
  private final String episodeId;
  private final String organizationId;
  private final String seriesId;
  private final String startDate;
  private final int plays;
  private final int visitors;
  private final int finishes;
  private final OffsetDateTime date;

  public Impression(
          final String episodeId,
          final String organizationId,
          final String seriesId,
          final String startDate,
          final int plays,
          final int visitors,
          final int finishes,
          final OffsetDateTime date) {


    String ep;
    String ser;

    switch (episodeId) {
      case "a8fc7191-e1da-486a-a244-f7b2ec9f2312":
        // Kunst 1
        ep = "724525f2-c44c-41e5-8697-e7c3bf9e1012";
        ser = "9bced1af-3f86-425a-a16b-8db01d9475ff";
        break;
      case "2678470a-519f-43f5-bf4e-8781ccc36ff6":
        // Kunst 2
        ep = "8a24880e-7fe9-44c6-8a89-198896338db0";
        ser = "9bced1af-3f86-425a-a16b-8db01d9475ff";
        break;
      case "c667ea18-3239-479b-a940-3300607503eb":
        // Kunst 3
        ep = "e10018de-6eae-441c-bc64-122913c94849";
        ser = "9bced1af-3f86-425a-a16b-8db01d9475ff";
        break;
      case "34b45126-856b-48de-9d04-1786488b426e":
        // Mathe 1
        ep = "76d27944-0e1b-4ca2-aaa4-9136d91c9e6d";
        ser = "7728ad3a-1c91-4469-9306-1bd8b9d81a52";
        break;
      case "5044dcb8-3a04-4a80-b761-bcfc51c9876c":
        ep = "4282faa6-cd29-4b13-8953-a70f9965cd90";
        ser = "d59843a1-c466-455d-9084-95014314e5e9";
        break;
      case "504fecf7-87ce-4609-8fcc-46d81b0c0b4c":
        ep = "4993bfa2-8109-46f7-ae49-60af1084fafe";
        ser = "d59843a1-c466-455d-9084-95014314e5e9";
        break;
      default:
        ep = episodeId;
        ser = seriesId;
    }


    this.episodeId = ep;
    this.organizationId = organizationId;
    this.seriesId = ser;
    this.startDate = startDate;
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
            .tag("seriesId", this.seriesId)
            //.tag("seriesId", "2c2b6898-a1af-4b97-8b86-d72ad26d34dc")
            .tag("organizationId", this.organizationId)
            //.tag("organizationId", this.organizationId)
            .tag("episodeId", this.episodeId)
            //.tag("episodeId", "6d26d029-9238-4b6e-aa26-97983b88f614")
            .build();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    final Impression impression = (Impression) o;
    return this.organizationId.equals(impression.organizationId) && this.episodeId.equals(impression.episodeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.organizationId, this.episodeId);
  }

  public String getEpisodeId() { return this.episodeId; }

  public String getOrganizationId() { return this.organizationId; }

  public String getSeriesId() { return this.seriesId; }

  public String getStartDate() { return this.startDate; }

  public int getPlays() { return this.plays; }

  public int getVisitors() { return this.visitors; }

  public int getFinishes() { return this.finishes; }

  public OffsetDateTime getDate() { return this.date; }
}
