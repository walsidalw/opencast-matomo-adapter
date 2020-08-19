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

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.InfluxDBIOException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.impl.InfluxDBMapper;

import java.util.List;

/**
 * Handles all processes relevant to InfluxDB
 */
public final class InfluxDBProcessor {
  private final org.slf4j.Logger logger;

  private BatchPoints batch;
  private final InfluxDBConfig config;
  private final InfluxDB influxDB;

  public InfluxDBProcessor(final InfluxDBConfig config, final org.slf4j.Logger logger) {
    this.logger = logger;
    this.influxDB = connect(config);
    this.config = config;
    this.batch = BatchPoints.database(config.getDb()).retentionPolicy(config.getRetentionPolicy()).build();
  }

  /**
   * Query InfluxDB and map the result to a given POJO (plain old Java object). Each point returned one POJO.
   *
   * @param query Requested DB query, must contain two placeholders for database and retention policy information
   * @param clazz POJO class which must be mapped to
   * @return List of objects mapped from query result
   */
  public <T> List<T> mapPojo(final String query, final Class<T> clazz) {
    final String q = String.format(query, this.config.getDb(), this.config.getRetentionPolicy());
    final InfluxDBMapper mapper = new InfluxDBMapper(this.influxDB);
    return mapper.query(new Query(q, this.config.getDb()), clazz);
  }

  /**
   * Add a point to the batch.
   *
   * @param p Point, that needs to be added to the batch.
   */
  public void addToBatch(final Point p) {
    this.batch.point(p);
  }

  /**
   * Push the whole batch to InfluxDB and reset it afterwards.
   */
  public void writeBatch() {
    try {
      final Pong pong = this.influxDB.ping();
      if (!pong.isGood()) {
        this.logger.error("INFLUXPINGERROR, not good");
      }
    } catch (final InfluxDBIOException e) {
      this.logger.error("INFLUXPINGERROR, {}", e.getMessage());
    }

    this.influxDB.write(this.batch);
    resetBatch();
  }

  /**
   * Reset the batch object.
   */
  private void resetBatch() {
    this.batch = BatchPoints.database(this.config.getDb()).retentionPolicy(this.config.getRetentionPolicy()).build();
  }

  /**
   * Connect and configure InfluxDB from a configuration
   *
   * @param config InfluxDB configuration
   * @return A connected InfluxDB instance
   */
  private static InfluxDB connect(final InfluxDBConfig config) {
    InfluxDB influxDB = null;
    try {
      influxDB = InfluxDBFactory.connect(config.getHost(), config.getUser(), config.getPassword());

      influxDB.setDatabase(config.getDb());
      influxDB.setRetentionPolicy(config.getRetentionPolicy());
      influxDB.enableBatch();
      if (config.getLogLevel().equals("debug")) {
        influxDB.setLogLevel(InfluxDB.LogLevel.FULL);
      } else {
        influxDB.setLogLevel(InfluxDB.LogLevel.BASIC);
      }
      return influxDB;
    } catch (final Exception e) {
      if (influxDB != null) {
        influxDB.close();
      }
      throw e;
    }
  }
}
