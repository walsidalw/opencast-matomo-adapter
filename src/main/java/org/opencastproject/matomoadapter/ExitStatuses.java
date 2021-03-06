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

/**
 * Defines all possible <code>System.exit</code> statuses
 */
public final class ExitStatuses {
  /**
   * Invalid command line parameters were given
   */
  public static final int INVALID_COMMAND_LINE_ARGS = 1;
  /**
   * Invalid InfluxDB config was given
   */
  public static final int INVALID_INFLUXDB_CONFIG = 2;
  /**
   * There was an error configuring one (maybe the only) Opencast client
   */
  public static final int CLIENT_CONFIGURATION_ERROR = 3;
  /**
   * InfluxDB raised a run-time error (connection lost, or something)
   */
  public static final int INFLUXDB_RUNTIME_ERROR = 4;
  /**
   * The JSON file returned by the external API wasn't valid
   */
  public static final int JSON_SYNTAX_ERROR = 5;
  /**
   * Some random exception flew by us
   */
  public static final int UNKNOWN = 6;
  /**
   * The configuration file given was not found
   */
  public static final int CONFIG_FILE_NOT_FOUND = 7;
  /**
   * There was some syntax error with the configuration file given
   */
  public static final int CONFIG_FILE_PARSE_ERROR = 8;
  /**
   * There was an error with file reading/writing
   */
  public static final int FILE_HANDLING_ERROR = 9;
  /**
   * A log configuration file was given, but contained an error
   */
  public static final int LOG_FILE_CONFIGURATION_ERROR = 10;

  private ExitStatuses() {
  }
}
