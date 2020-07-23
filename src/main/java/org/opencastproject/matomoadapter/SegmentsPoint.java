package org.opencastproject.matomoadapter;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Measurement(name = "segments_daily", timeUnit = TimeUnit.SECONDS)
public class SegmentsPoint {

  @Column(name = "time")
  private Instant time;

  @Column(name = "organizationId", tag = true)
  private String organizationId;

  @Column(name = "episodeId", tag = true)
  private String episodeId;

  @Column(name = "segments")
  private String segments;

  public String getEpisodeId() { return this.episodeId; }

  public String getSegments() { return this.segments; }

  public void setEpisodeId(final String episodeId) { this.episodeId = episodeId; }

  public void setSegments(final String segments) { this.segments = segments; }

  //public void setTime(final long time) { this.time = time; }
}
