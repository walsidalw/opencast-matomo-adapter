package org.opencastproject.matomoadapter;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;

@Measurement(name = "segments_daily")
public class SegmentsPoint {

  @Column(name = "time")
  private Instant time;

  @Column(name = "organizationId")
  private String organizationId;

  @Column(name = "episodeId")
  private String episodeId;

  @Column(name = "segments")
  private String segments;

  public String getEpisodeId() { return this.episodeId; }

  public String getSegments() { return this.segments; }

  public void setEpisodeId(final String episodeId) { this.episodeId = episodeId; }

  public void setSegments(final String segments) { this.segments = segments; }

  public void setTime(final Instant time) { this.time = time; }
}
