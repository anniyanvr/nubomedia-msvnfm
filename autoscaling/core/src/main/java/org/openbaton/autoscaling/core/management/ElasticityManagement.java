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

package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.AutoScalingProperties;
import org.openbaton.vnfm.configuration.NfvoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
@ContextConfiguration(
  loader = AnnotationConfigContextLoader.class,
  classes = {ASBeanConfiguration.class}
)
public class ElasticityManagement {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private DetectionManagement detectionManagment;

  @Autowired private DecisionManagement decisionManagement;

  @Autowired private ExecutionManagement executionManagement;

  @Autowired private PoolManagement poolManagement;

  @Autowired private NFVORequestor nfvoRequestor;

  private List<String> subscriptionIds;

  @Autowired private NfvoProperties nfvoProperties;

  @Autowired private AutoScalingProperties autoScalingProperties;

  @PostConstruct
  public void init() throws SDKException {
    subscriptionIds = new ArrayList<>();
  }

  @PreDestroy
  private void exit() throws SDKException {}

  public void activate(String nsr_id) throws NotFoundException, VimException, SDKException {
    log.debug("Activating Elasticity for NSR with id: " + nsr_id);
    if (autoScalingProperties.getPool().isActivate()) {
      log.debug("Activating pool mechanism");
      poolManagement.activate(nsr_id);
    } else {
      log.debug("pool mechanism is disabled");
    }
    detectionManagment.start(nsr_id);
    log.info("Activated Elasticity for NSR with id: " + nsr_id);
  }

  public void activate(String nsr_id, String vnfr_id)
      throws NotFoundException, VimException, SDKException {
    log.debug("Activating Elasticity for NSR with id: " + nsr_id);
    if (autoScalingProperties.getPool().isActivate()) {
      log.debug("Activating pool mechanism");
      poolManagement.activate(nsr_id, vnfr_id);
    } else {
      log.debug("pool mechanism is disabled");
    }
    detectionManagment.start(nsr_id, vnfr_id);
    log.info("Activated Elasticity for NSR with id: " + nsr_id);
  }

  public void deactivate(String nsr_id) {
    log.info("Deactivating Elasticity for NSR with id: " + nsr_id);
    if (autoScalingProperties.getPool().isActivate()) {
      try {
        poolManagement.deactivate(nsr_id);
      } catch (NotFoundException e) {
        log.warn(e.getMessage());
        if (log.isDebugEnabled()) {
          log.error(e.getMessage(), e);
        }
      } catch (VimException e) {
        log.warn(e.getMessage());
        if (log.isDebugEnabled()) {
          log.error(e.getMessage(), e);
        }
      }
    }
    try {
      detectionManagment.stop(nsr_id);
    } catch (NotFoundException e) {
      log.warn(e.getMessage());
      if (log.isDebugEnabled()) {
        log.error(e.getMessage(), e);
      }
    }
    decisionManagement.stop(nsr_id);
    executionManagement.stop(nsr_id);
    log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
  }

  @Async
  public Future<Boolean> deactivate(String nsr_id, String vnfr_id) {
    log.info("Deactivating Elasticity for NSR with id: " + nsr_id);
    Set<Future<Boolean>> pendingTasks = new HashSet<>();
    if (autoScalingProperties.getPool().isActivate()) {
      try {
        pendingTasks.add(poolManagement.deactivate(nsr_id, vnfr_id));
      } catch (NotFoundException e) {
        log.warn(e.getMessage());
        if (log.isDebugEnabled()) {
          log.error(e.getMessage(), e);
        }
      } catch (VimException e) {
        log.warn(e.getMessage());
        if (log.isDebugEnabled()) {
          log.error(e.getMessage(), e);
        }
      }
    }
    try {
      pendingTasks.add(detectionManagment.stop(nsr_id, vnfr_id));
    } catch (NotFoundException e) {
      log.error(e.getMessage(), e);
    }
    pendingTasks.add(decisionManagement.stop(nsr_id, vnfr_id));
    pendingTasks.add(executionManagement.stop(nsr_id, vnfr_id));
    for (Future<Boolean> pendingTask : pendingTasks) {
      try {
        pendingTask.get(60, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        if (log.isDebugEnabled()) {
          log.error(e.getMessage(), e);
        }
      } catch (ExecutionException e) {
        if (log.isDebugEnabled()) {
          log.error(e.getMessage(), e);
        }
      } catch (TimeoutException e) {
        if (log.isDebugEnabled()) {
          log.error(e.getMessage(), e);
        }
      }
    }
    log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
    return new AsyncResult<>(true);
  }

  private void subscribe(Action action) throws SDKException {
    log.debug("Subscribing to all NSR Events with Action " + action);
    EventEndpoint eventEndpoint = new EventEndpoint();
    eventEndpoint.setName("Subscription:" + action);
    eventEndpoint.setEndpoint("http://localhost:9999/event/" + action);
    eventEndpoint.setEvent(action);
    eventEndpoint.setType(EndpointType.REST);
    this.subscriptionIds.add(nfvoRequestor.getEventAgent().create(eventEndpoint).getId());
  }

  private void unsubscribe() throws SDKException {
    for (String subscriptionId : subscriptionIds) {
      nfvoRequestor.getEventAgent().delete(subscriptionId);
    }
  }

  private void waitForNfvo() {
    if (!Utils.isNfvoStarted(nfvoProperties.getIp(), nfvoProperties.getPort())) {
      log.error("After 150 sec the Nfvo is not started yet. Is there an error?");
      System.exit(1); // 1 stands for the error in running nfvo TODO define error codes (doing)
    }
  }
}
