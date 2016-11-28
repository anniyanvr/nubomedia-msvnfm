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

package org.openbaton.autoscaling.core.detection;

import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAlarm;
import org.openbaton.catalogue.mano.common.monitoring.ObjectSelection;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdDetails;
import org.openbaton.catalogue.mano.common.monitoring.ThresholdType;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.monitoring.interfaces.VirtualisedResourcesPerformanceManagement;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.AutoScalingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DetectionEngine {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private NFVORequestor nfvoRequestor;

  @Autowired private ConfigurableApplicationContext context;

  private VirtualisedResourcesPerformanceManagement monitor;

  //@Autowired
  private DetectionManagement detectionManagement;

  @Autowired private AutoScalingProperties autoScalingProperties;

  @PostConstruct
  public void init() {
    this.detectionManagement = context.getBean(DetectionManagement.class);
    this.monitor = new EmmMonitor(autoScalingProperties.getMonitor().getUrl());
    if (monitor == null) {
      log.warn("DetectionTask: Monitor was not found. Cannot start Autoscaling...");
    }
  }

  public List<Item> getRawMeasurementResults(
      VirtualNetworkFunctionRecord vnfr, String metric, String period) throws MonitoringException {
    ArrayList<Item> measurementResults = new ArrayList<Item>();
    ArrayList<String> hostnames = new ArrayList<String>();
    ArrayList<String> metrics = new ArrayList<String>();
    metrics.add(metric);
    log.debug(
        "Getting all measurement results for vnfr " + vnfr.getId() + " on metric " + metric + ".");
    for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
      for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
        hostnames.add(vnfcInstance.getHostname());
      }
    }
    log.trace(
        "Getting all measurement results for hostnames "
            + hostnames
            + " on metric "
            + metric
            + ".");
    measurementResults.addAll(monitor.queryPMJob(hostnames, metrics, period));
    log.debug(
        "Got all measurement results for vnfr "
            + vnfr.getId()
            + " on metric "
            + metric
            + " -> "
            + measurementResults
            + ".");
    return measurementResults;
  }

  public double calculateMeasurementResult(ScalingAlarm alarm, List<Item> measurementResults) {
    log.debug("Calculating final measurement result ...");
    double result;
    List<Double> consideredResults = new ArrayList<>();
    for (Item measurementResult : measurementResults) {
      consideredResults.add(Double.parseDouble(measurementResult.getValue()));
    }
    switch (alarm.getStatistic()) {
      case "avg":
        double sum = 0;
        for (Double consideredResult : consideredResults) {
          sum += consideredResult;
        }
        result = sum / measurementResults.size();
        break;
      case "min":
        result = Collections.min(consideredResults);
        break;
      case "max":
        result = Collections.max(consideredResults);
        break;
      default:
        result = -1;
        break;
    }
    return result;
  }

  public boolean checkThreshold(String comparisonOperator, double threshold, double result) {
    log.debug("Checking Threshold ...");
    switch (comparisonOperator) {
      case ">":
        if (result > threshold) {
          return true;
        }
        break;
      case ">=":
        if (result >= threshold) {
          return true;
        }
        break;
      case "<":
        if (result < threshold) {
          return true;
        }
        break;
      case "<=":
        if (result <= threshold) {
          return true;
        }
        break;
      case "=":
        if (result == threshold) {
          return true;
        }
        break;
      case "!=":
        if (result != threshold) {
          return true;
        }
        break;
      default:
        return false;
    }
    return false;
  }

  public void sendAlarm(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
    log.info("Alarm fired for VNFR with id: " + vnfr_id);
    detectionManagement.sendAlarm(nsr_id, vnfr_id, autoScalePolicy);
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

class EmmMonitor implements VirtualisedResourcesPerformanceManagement {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  private String monitorUrl;

  public EmmMonitor(String url) {
    this.monitorUrl = url;
  }

  @Override
  public String createPMJob(
      ObjectSelection resourceSelector,
      List<String> performanceMetric,
      List<String> performanceMetricGroup,
      Integer collectionPeriod,
      Integer reportingPeriod)
      throws MonitoringException {
    return null;
  }

  @Override
  public List<String> deletePMJob(List<String> itemIdsToDelete) throws MonitoringException {
    return null;
  }

  @Override
  public List<Item> queryPMJob(List<String> hostnames, List<String> metrics, String period)
      throws MonitoringException {
    log.debug(
        "Requesting measurement results for hosts: "
            + hostnames
            + " on metrics: "
            + metrics
            + " (period: "
            + period
            + ")");
    List<Item> items = new ArrayList<>();
    for (String metric : metrics) {
      for (String hostName : hostnames) {
        try {
          URL url = new URL("http://" + monitorUrl + "/monitor/" + hostName + "/" + metric);
          HttpURLConnection conn = (HttpURLConnection) url.openConnection();
          conn.setRequestMethod("GET");
          conn.setRequestProperty("Accept", "application/json");
          if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
          }
          BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
          String output;
          while ((output = br.readLine()) != null) {
            log.debug(
                "Measurement result for host "
                    + hostName
                    + " on metric "
                    + metric
                    + " is "
                    + output);
            Item item = new Item();
            item.setHostname(hostName);
            item.setHostId(hostName);
            item.setLastValue(output);
            item.setValue(output);
            item.setMetric(metric);
            items.add(item);
          }
          conn.disconnect();
        } catch (MalformedURLException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return items;
  }

  @Override
  public void subscribe() {}

  @Override
  public void notifyInfo() {}

  @Override
  public String createThreshold(
      ObjectSelection objectSelector,
      String performanceMetric,
      ThresholdType thresholdType,
      ThresholdDetails thresholdDetails)
      throws MonitoringException {
    return null;
  }

  @Override
  public List<String> deleteThreshold(List<String> thresholdIds) throws MonitoringException {
    return null;
  }

  @Override
  public void queryThreshold(String queryFilter) {}
}
