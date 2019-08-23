package io.hops.hopsworks.common.experiments.dto.results;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ExperimentResult {

  private String key;

  private String value;

  public ExperimentResult() {
    //Needed for JAXB
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}