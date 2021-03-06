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

package org.openbaton.vnfm.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

/**
 * Created by mpa on 25.01.16.
 */
@Service
@ConfigurationProperties(prefix = "vnfm")
@PropertySource("classpath:application.properties")
public class VnfmProperties {

  private Rabbitmq rabbitmq;

  private Server server;

  private Management management;

  public Rabbitmq getRabbitmq() {
    return rabbitmq;
  }

  public void setRabbitmq(Rabbitmq rabbitmq) {
    this.rabbitmq = rabbitmq;
  }

  public Server getServer() {
    return server;
  }

  public void setServer(Server server) {
    this.server = server;
  }

  public Management getManagement() {
    return management;
  }

  public void setManagement(Management management) {
    this.management = management;
  }

  @Override
  public String toString() {
    return "VnfmProperties{"
        + "rabbitmq="
        + rabbitmq
        + ", server="
        + server
        + ", management="
        + management
        + '}';
  }

  public static class Rabbitmq {
    private String brokerIp;
    private Management management;
    private boolean autodelete;
    private boolean durable;
    private boolean exclusive;
    private int minConcurrency;
    private int maxConcurrency;

    public String getBrokerIp() {
      return brokerIp;
    }

    public void setBrokerIp(String brokerIp) {
      this.brokerIp = brokerIp;
    }

    public Management getManagement() {
      return management;
    }

    public void setManagement(Management management) {
      this.management = management;
    }

    public boolean isAutodelete() {
      return autodelete;
    }

    public void setAutodelete(boolean autodelete) {
      this.autodelete = autodelete;
    }

    public boolean isDurable() {
      return durable;
    }

    public void setDurable(boolean durable) {
      this.durable = durable;
    }

    public boolean isExclusive() {
      return exclusive;
    }

    public void setExclusive(boolean exclusive) {
      this.exclusive = exclusive;
    }

    public int getMinConcurrency() {
      return minConcurrency;
    }

    public void setMinConcurrency(int minConcurrency) {
      this.minConcurrency = minConcurrency;
    }

    public int getMaxConcurrency() {
      return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
      this.maxConcurrency = maxConcurrency;
    }

    @Override
    public String toString() {
      return "RabbitMQ{"
          + "brokerIp='"
          + brokerIp
          + '\''
          + ", management="
          + management
          + ", autodelete="
          + autodelete
          + ", durable="
          + durable
          + ", exclusive="
          + exclusive
          + ", minConcurrency="
          + minConcurrency
          + ", maxConcurrency="
          + maxConcurrency
          + '}';
    }
  }

  public static class Server {
    private String port;

    public String getPort() {
      return port;
    }

    public void setPort(String port) {
      this.port = port;
    }

    @Override
    public String toString() {
      return "Server{" + "port='" + port + '\'' + '}';
    }
  }

  public static class Management {
    private String port;

    public String getPort() {
      return port;
    }

    public void setPort(String port) {
      this.port = port;
    }

    @Override
    public String toString() {
      return "Management{" + "port='" + port + '\'' + '}';
    }
  }
}
