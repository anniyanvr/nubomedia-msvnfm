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

package org.openbaton.autoscaling.core.decision;

import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.NfvoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.Set;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DecisionEngine {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private ConfigurableApplicationContext context;

  private ExecutionManagement executionManagement;

  @Autowired private NFVORequestor nfvoRequestor;

  @Autowired private NfvoProperties nfvoProperties;

  @PostConstruct
  public void init() throws SDKException {
    this.executionManagement = context.getBean(ExecutionManagement.class);
  }

  public void sendDecision(
      String nsr_id, String vnfr_id, Set<ScalingAction> actions, long cooldown) {
    log.debug("Sending decision to Executor " + new Date().getTime());
    log.info("[DECISION_MAKER] DECIDED_ABOUT_ACTIONS " + new Date().getTime());
    executionManagement.executeActions(nsr_id, vnfr_id, actions, cooldown);
    log.info("Sent decision to Executor " + new Date().getTime());
  }

  public Status getStatus(String nsr_id, String vnfr_id) {
    log.debug("Check Status of VNFR with id: " + vnfr_id);
    VirtualNetworkFunctionRecord vnfr = null;
    try {
      vnfr =
          nfvoRequestor
              .getNetworkServiceRecordAgent()
              .getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
    } catch (SDKException e) {
      log.warn(e.getMessage(), e);
      return Status.NULL;
    }
    if (vnfr == null || vnfr.getStatus() == null) {
      return Status.NULL;
    }
    return vnfr.getStatus();
  }

  public VirtualNetworkFunctionRecord getVNFR(String nsr_id, String vnfr_id) throws SDKException {
    try {
      VirtualNetworkFunctionRecord vnfr =
          nfvoRequestor
              .getNetworkServiceRecordAgent()
              .getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
      return vnfr;
    } catch (SDKException e) {
      log.error(e.getMessage(), e);
      throw e;
    }
  }
}
