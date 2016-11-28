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

package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.execution.task.CooldownTask;
import org.openbaton.autoscaling.core.execution.task.ExecutionTask;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.NfvoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.ErrorHandler;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ExecutionManagement {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  private ThreadPoolTaskScheduler taskScheduler;

  @Autowired private ExecutionEngine executionEngine;

  @Autowired private NFVORequestor nfvoRequestor;

  private ActionMonitor actionMonitor;

  @Autowired private NfvoProperties nfvoProperties;

  @PostConstruct
  public void init() throws SDKException {
    this.actionMonitor = new ActionMonitor();
    this.executionEngine.setActionMonitor(actionMonitor);
    this.taskScheduler = new ThreadPoolTaskScheduler();
    this.taskScheduler.setPoolSize(10);
    this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
    this.taskScheduler.setRemoveOnCancelPolicy(true);
    this.taskScheduler.setErrorHandler(
        new ErrorHandler() {
          protected Logger log = LoggerFactory.getLogger(this.getClass());

          @Override
          public void handleError(Throwable t) {
            log.error(t.getMessage(), t);
          }
        });
    this.taskScheduler.initialize();
  }

  public void executeActions(
      String nsr_id, String vnfr_id, Set<ScalingAction> actions, long cooldown) {
    log.info("[EXECUTOR] RECEIVED_ACTION " + new Date().getTime());
    log.info(
        "Processing execution request of ScalingActions: "
            + actions
            + " for VNFR with id: "
            + vnfr_id);
    if (actionMonitor.requestAction(vnfr_id, Action.SCALE)) {
      log.debug(
          "Creating new ExecutionTask of ScalingActions: "
              + actions
              + " for VNFR with id: "
              + vnfr_id);
      ExecutionTask executionTask =
          new ExecutionTask(nsr_id, vnfr_id, actions, cooldown, executionEngine, actionMonitor);
      taskScheduler.execute(executionTask);
    } else {
      if (actionMonitor.getAction(vnfr_id) == Action.SCALE) {
        log.debug(
            "Processing already an execution request for VNFR with id: "
                + vnfr_id
                + ". Cannot create another ExecutionTask for VNFR with id: "
                + vnfr_id);
      } else if (actionMonitor.getAction(vnfr_id) == Action.COOLDOWN) {
        log.debug(
            "Waiting for Cooldown for VNFR with id: "
                + vnfr_id
                + ". Cannot create another ExecutionTask for VNFR with id: "
                + vnfr_id);
      } else {
        log.warn(
            "Problem while starting ExecutionThread. Internal Status is: "
                + actionMonitor.getAction(vnfr_id));
      }
    }
  }

  public void executeCooldown(String nsr_id, String vnfr_id, long cooldown) {
    log.info("[EXECUTOR] START_COOLDOWN " + new Date().getTime());
    if (actionMonitor.isTerminating(vnfr_id)) {
      actionMonitor.finishedAction(vnfr_id, Action.TERMINATED);
      return;
    }
    log.info("Starting CooldownTask for VNFR with id: " + vnfr_id);
    if (actionMonitor.requestAction(vnfr_id, Action.COOLDOWN)) {
      log.debug("Creating new CooldownTask for VNFR with id: " + vnfr_id);
      CooldownTask cooldownTask =
          new CooldownTask(nsr_id, vnfr_id, cooldown, executionEngine, actionMonitor);
      taskScheduler.execute(cooldownTask);
    } else {
      if (actionMonitor.getAction(vnfr_id) == Action.COOLDOWN) {
        log.debug(
            "Waiting already for Cooldown for VNFR with id: "
                + vnfr_id
                + ". Cannot create another ExecutionTask for VNFR with id: "
                + vnfr_id);
      } else if (actionMonitor.getAction(vnfr_id) == Action.SCALE) {
        log.debug("VNFR with id: " + vnfr_id + " is still in Scaling.");
      } else {
        log.debug(actionMonitor.toString());
      }
    }
  }

  public void stop(String nsr_id) {
    log.debug("Stopping ExecutionTask for all VNFRs of NSR with id: " + nsr_id);
    NetworkServiceRecord nsr = null;
    try {
      nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
    } catch (SDKException e) {
      log.error(e.getMessage(), e);
    } catch (ClassNotFoundException e) {
      log.error(e.getMessage(), e);
    }
    if (nsr != null && nsr.getVnfr() != null) {
      for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
        stop(nsr_id, vnfr.getId());
      }
    }
    log.info("Stopped all ExecutionTasks for NSR with id: " + nsr_id);
  }

  @Async
  public Future<Boolean> stop(String nsr_id, String vnfr_id) {
    log.debug("Stopping ExecutionTask/CooldownTask for VNFR with id: " + vnfr_id);
    int i = 60;
    while (!actionMonitor.isTerminated(vnfr_id)
        && actionMonitor.getAction(vnfr_id) != Action.INACTIVE
        && i >= 0) {
      actionMonitor.terminate(vnfr_id);
      log.debug(
          "Waiting for finishing ExecutionTask/Cooldown for VNFR with id: "
              + vnfr_id
              + " ("
              + i
              + "s)");
      log.debug(actionMonitor.toString());
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        log.error(e.getMessage(), e);
      }
      i--;
      if (i <= 0) {
        actionMonitor.removeId(vnfr_id);
        log.error(
            "Were not able to wait until ExecutionTask finished for VNFR with id: " + vnfr_id);
        return new AsyncResult<>(false);
      }
    }
    actionMonitor.removeId(vnfr_id);
    log.info("Stopped ExecutionTask for VNFR with id: " + vnfr_id);
    return new AsyncResult<>(true);
  }
}
