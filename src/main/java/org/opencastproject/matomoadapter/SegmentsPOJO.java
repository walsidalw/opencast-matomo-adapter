package org.opencastproject.matomoadapter;

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Model class (POJO) for InfluxDB mapper
 */
@SuppressWarnings("InstanceVariableMayNotBeInitialized")
@Measurement(name = "segments_daily", timeUnit = TimeUnit.SECONDS)
public class SegmentsPOJO {

  @Column(name = "time")
  private Instant time;

  @Column(name = "organizationId", tag = true)
  private String organizationId;

  @Column(name = "episodeId", tag = true)
  private String episodeId;

  @Column(name = "segments")
  private String segments;

  public Instant getTime() { return this.time; }

  public String getSegments() { return this.segments; }
}
