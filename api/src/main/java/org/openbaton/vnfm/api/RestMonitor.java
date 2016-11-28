/*
 *
 *  * (C) Copyright 2016 NUBOMEDIA (http://www.nubomedia.eu)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.vnfm.api;

import org.openbaton.exceptions.NotFoundException;
import org.openbaton.vnfm.catalogue.Application;
import org.openbaton.vnfm.catalogue.MediaServer;
import org.openbaton.vnfm.core.ApplicationManagement;
import org.openbaton.vnfm.core.MediaServerManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/monitor/{hostname}")
public class RestMonitor {

  //	TODO add log prints
  private Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private ApplicationManagement applicationManagement;

  @Autowired private MediaServerManagement mediaServerManagement;

  /**
   * Returns the consumed capacity of the requested MediaServer
   *
   * @param hostName : hostName of the MediaServer
   * @return consumed_capacity: Consumed Capacity of the MediaServer
   */
  @RequestMapping(value = "CONSUMED_CAPACITY", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.OK)
  public String get(@PathVariable("hostname") String hostName) throws NotFoundException {
    MediaServer mediaServer = mediaServerManagement.queryByHostName(hostName);
    if (mediaServer == null)
      throw new NotFoundException("MediaServer with name " + hostName + " not found.");
    return Double.toString(mediaServer.getUsedPoints());
  }

  /**
   * Returns the elapsed heartbeat time of each Application running on a specific VNFR
   *
   * @param vnfrId : ID of VNFR
   */
  @RequestMapping(value = "HEARTBEAT_ELAPSED", method = RequestMethod.GET)
  @ResponseStatus(HttpStatus.OK)
  public Map<String, String> delete(@PathVariable("hostname") String vnfrId)
      throws NotFoundException {
    Iterable<Application> applications = applicationManagement.queryByVnfrId(vnfrId);
    Map<String, String> elapsedHeartbeats = new HashMap<String, String>();
    for (Application application : applications) {
      elapsedHeartbeats.put(
          application.getId(),
          Long.toString((new Date().getTime() - application.getHeartbeat().getTime())));
    }
    return elapsedHeartbeats;
  }
}
