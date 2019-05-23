package io.hops.hopsworks.common.experiments.dto;

import io.hops.hopsworks.common.jobs.spark.SparkJobConfiguration;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;

/**
 * Contains configuration and other information about an experiment
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class ExperimentConfigurationDTO {

  @XmlElement
  private String appPath;

  @XmlElement
  private String name;

  @XmlElement
  private String description;

  @XmlElement
  private String user;

  @XmlElement
  private Date start;

  @XmlElement
  private Date finished;

  @XmlElement
  private String status;

  @XmlElement
  private String function;

  @XmlElement
  //ID to model, will be a separate entry in provenance (it is attached to /Models/mnist/1)
  private String model;

  @XmlElement
  private HyperparameterDTO[] hyperparameter;

  @XmlElement
  private MetricsDTO[] metrics;

  @XmlElement
  private SparkJobConfiguration sparkJobConfiguration;

  public String getAppPath() {
    return appPath;
  }

  public void setAppPath(String appPath) {
    this.appPath = appPath;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public Date getStart() {
    return start;
  }

  public void setStart(Date start) {
    this.start = start;
  }

  public Date getFinished() {
    return finished;
  }

  public void setFinished(Date finished) {
    this.finished = finished;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getFunction() {
    return function;
  }

  public void setFunction(String function) {
    this.function = function;
  }

  public SparkJobConfiguration getSparkJobConfiguration() {
    return sparkJobConfiguration;
  }

  public void setSparkJobConfiguration(SparkJobConfiguration sparkJobConfiguration) {
    this.sparkJobConfiguration = sparkJobConfiguration;
  }

  public HyperparameterDTO[] getHyperparameter() {
    return hyperparameter;
  }

  public void setHyperparameter(HyperparameterDTO[] hyperparameter) {
    this.hyperparameter = hyperparameter;
  }

  public MetricsDTO[] getMetrics() {
    return metrics;
  }

  public void setMetrics(MetricsDTO[] metrics) {
    this.metrics = metrics;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }
}
