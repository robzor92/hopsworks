package io.hops.hopsworks.api.experiments.results;

import io.hops.hopsworks.common.api.ResourceRequest;
import io.hops.hopsworks.common.dao.AbstractFacade;
import io.hops.hopsworks.common.dao.project.Project;

import io.hops.hopsworks.common.experiments.ExperimentConfigurationConverter;
import io.hops.hopsworks.common.experiments.dto.results.ExperimentResult;
import io.hops.hopsworks.common.experiments.dto.results.ExperimentResultSummaryDTO;
import io.hops.hopsworks.common.experiments.dto.results.ExperimentResultsDTO;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.hdfs.Utils;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.exceptions.ExperimentsException;
import io.hops.hopsworks.restutils.RESTCodes;
import org.apache.hadoop.fs.Path;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class ExperimentResultsBuilder {

  private static final Logger LOGGER = Logger.getLogger(ExperimentResultsBuilder.class.getName());

  @EJB
  private DistributedFsService dfs;
  @EJB
  private ExperimentConfigurationConverter experimentConfigurationConverter;

  public ExperimentResultSummaryDTO uri(ExperimentResultSummaryDTO dto, UriInfo uriInfo, Project project, String mlId) {
    dto.setHref(uriInfo.getBaseUriBuilder().path(ResourceRequest.Name.PROJECT.toString().toLowerCase())
        .path(Integer.toString(project.getId()))
        .path(ResourceRequest.Name.EXPERIMENTS.toString().toLowerCase())
        .path(mlId)
        .path(ResourceRequest.Name.RESULTS.toString().toLowerCase())
        .build());
    return dto;
  }

  public ExperimentResultSummaryDTO expand(ExperimentResultSummaryDTO dto, ResourceRequest resourceRequest) {
    if (resourceRequest != null && resourceRequest.contains(ResourceRequest.Name.RESULTS)) {
      dto.setExpand(true);
    }
    return dto;
  }

  public ExperimentResultSummaryDTO build(UriInfo uriInfo, ResourceRequest resourceRequest, Project project,
                                          String mlId)
      throws ExperimentsException {
    ExperimentResultSummaryDTO dto = new ExperimentResultSummaryDTO();
    uri(dto, uriInfo, project, mlId);
    expand(dto, resourceRequest);
    dto.setCount(0l);
    if (dto.isExpand()) {
      DistributedFileSystemOps dfso = null;
      try {
        dfso = dfs.getDfsOps();
        String summaryPath = Utils.getProjectPath(project.getName()) + Settings.HOPS_EXPERIMENTS_DATASET + "/"
            + mlId + "/.summary";
        if (dfso.exists(summaryPath)) {
          String summaryJson = dfso.cat(new Path(summaryPath));
          ExperimentResultsDTO[] results = experimentConfigurationConverter
              .unmarshalResults(summaryJson).getResults();
          if (results != null) {
            dto.setCount((long) results.length);
            results = apply(results, resourceRequest);
            dto.setResults(results);
          }
        }
      } catch (Exception e) {
        throw new ExperimentsException(RESTCodes.ExperimentsErrorCode.RESULTS_RETRIEVAL_ERROR, Level.SEVERE,
            "Unable to get results for experiment", e.getMessage(), e);
      } finally {
        if (dfso != null) {
          dfs.closeDfsClient(dfso);
        }
      }
    }
    return dto;
  }

  private ExperimentResultsDTO[] apply(ExperimentResultsDTO[] dto, ResourceRequest resourceRequest) {

    if (dto == null || dto.length == 1) {
      return dto;
    }

    Integer limit = resourceRequest.getLimit();

    if (limit == null) {
      limit = Integer.MAX_VALUE;
    }

    Integer offset = resourceRequest.getOffset();

    if (offset == null) {
      offset = 0;
    }

    AbstractFacade.SortBy sortByKey = resourceRequest.getSort().iterator().next();

    String sortKey = sortByKey.getValue();

    if(sortByKey != null) {
      if(sortByKey.getParam().getValue().compareToIgnoreCase("ASC") == 0) {
        Arrays.sort(dto, new OptKeyComparator(sortKey));
      } else if(sortByKey.getParam().getValue().compareToIgnoreCase("DESC") == 0) {
        Arrays.sort(dto, Collections.reverseOrder(new OptKeyComparator(sortKey)));
      }
    }

    ArrayList<ExperimentResultsDTO> results = new ArrayList<>();

    if (dto.length > 0) {
      for (int i = 0; offset + i < (offset + limit) && (offset + i) < dto.length; i++) {
        results.add(dto[offset + i]);
      }
    } else {
      return dto;
    }

    return results.toArray(new ExperimentResultsDTO[results.size()]);
  }

  public static class OptKeyComparator implements Comparator {
    private String sortKey;

    OptKeyComparator(String sortKey) {
      this.sortKey = sortKey;
    }

    @Override
    public int compare(Object experimentA, Object experimentB) {
      ExperimentResultsDTO firstExperiment = (ExperimentResultsDTO) experimentA;
      ExperimentResultsDTO secondExperiment = (ExperimentResultsDTO) experimentB;
      return getSortValue(firstExperiment, sortKey)
          .compareTo(getSortValue(secondExperiment, sortKey));
    }

    private Double getSortValue(ExperimentResultsDTO experiment, String sortKey) {
      for (ExperimentResult metric : experiment.getHyperparameters()) {
        if (metric.getKey().compareTo(sortKey) == 0) {
          return Double.parseDouble(metric.getValue());
        }
      }
      for (ExperimentResult metric : experiment.getMetrics()) {
        if (metric.getKey().compareTo(sortKey) == 0) {
          return Double.parseDouble(metric.getValue());
        }
      }
      return 0.0;
    }
  }

  protected enum Filters {
    METRIC
  }
}