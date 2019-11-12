package io.hops.hopsworks.common.jupyter;

import io.hops.hopsworks.common.jobs.configuration.JobConfiguration;
import io.hops.hopsworks.common.jobs.configuration.JobType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DockerJobConfiguration extends JobConfiguration {

  public DockerJobConfiguration(){}

  @XmlElement
  private int cores = 1;

  @XmlElement
  private int memory = 1024;

  public int getCores() {
    return cores;
  }

  public void setCores(int cores) {
    this.cores = cores;
  }

  public int getMemory() {
    return memory;
  }

  public void setMemory(int memory) {
    this.memory = memory;
  }

  @Override
  @XmlElement(name="jobType")
  public JobType getJobType() {
    return JobType.DOCKER;
  }

  @Override
  @XmlElement(name="jobTypeName")
  public String getJobTypeName() {
    return JobType.DOCKER.getName();
  }
}
