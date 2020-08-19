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

import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Model class (POJO) for InfluxDB mapper. Measurement name can not be changed dynamically. If you wish to
 * store segments data under another measurement, change the following @Measurement annotation.
 */
@SuppressWarnings("InstanceVariableMayNotBeInitialized")
@Measurement(name = "segments_daily", timeUnit = TimeUnit.SECONDS)
public class SegmentsPOJO {

  @Column(name = "time")
  private Instant time;

  @Column(name = "eventId", tag = true)
  private String eventId;

  @Column(name = "organizationId", tag = true)
  private String organizationId;

  @Column(name = "segments")
  private String segments;

  public Instant getTime() { return this.time; }

  public String getSegments() { return this.segments; }
}
