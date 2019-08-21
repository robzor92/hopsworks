package io.hops.hopsworks.api.experiments;

import com.google.common.base.Strings;
import io.hops.hopsworks.api.experiments.provenance.ExperimentFileProvenanceBuilder;
import io.hops.hopsworks.api.experiments.results.ExperimentResultsBuilder;
import io.hops.hopsworks.api.experiments.tensorboard.TensorBoardBuilder;
import io.hops.hopsworks.common.api.ResourceRequest;
import io.hops.hopsworks.common.dao.AbstractFacade;
import io.hops.hopsworks.common.dao.hdfsUser.HdfsUsers;
import io.hops.hopsworks.common.dao.hdfsUser.HdfsUsersFacade;
import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.dao.user.Users;
import io.hops.hopsworks.common.experiments.ExperimentConfigurationConverter;
import io.hops.hopsworks.common.experiments.dto.ExperimentDTO;
import io.hops.hopsworks.common.experiments.dto.ExperimentDescription;

import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.common.provenance.Provenance;

import io.hops.hopsworks.common.provenance.ProvenanceController;
import io.hops.hopsworks.common.provenance.v2.ProvFileStateParamBuilder;
import io.hops.hopsworks.common.provenance.v2.xml.FileState;
import io.hops.hopsworks.common.util.DateUtils;
import io.hops.hopsworks.exceptions.ExperimentsException;
import io.hops.hopsworks.exceptions.GenericException;
import io.hops.hopsworks.exceptions.InvalidQueryException;
import io.hops.hopsworks.exceptions.ServiceException;
import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONObject;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.UriInfo;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class ExperimentsBuilder {

  private static final Logger LOGGER = Logger.getLogger(ExperimentsBuilder.class.getName());

  @EJB
  private ProvenanceController provenanceController;
  @EJB
  private TensorBoardBuilder tensorBoardBuilder;
  @EJB
  private ExperimentFileProvenanceBuilder experimentFileProvenanceBuilder;
  @EJB
  private ExperimentResultsBuilder experimentResultsBuilder;
  @EJB
  private ExperimentConfigurationConverter experimentConfigurationConverter;
  @EJB
  private UserFacade userFacade;
  @EJB
  private HdfsUsersFacade hdfsUsersFacade;
  @EJB
  private HdfsUsersController hdfsUsersController;

  public ExperimentDTO uri(ExperimentDTO dto, UriInfo uriInfo, Project project) {
    dto.setHref(uriInfo.getBaseUriBuilder().path(ResourceRequest.Name.PROJECT.toString().toLowerCase())
        .path(Integer.toString(project.getId()))
        .path(ResourceRequest.Name.EXPERIMENTS.toString().toLowerCase())
        .build());
    return dto;
  }

  public ExperimentDTO uri(ExperimentDTO dto, UriInfo uriInfo, Project project, FileState fileProvenanceHit) {
    dto.setHref(uriInfo.getBaseUriBuilder()
        .path(ResourceRequest.Name.PROJECT.toString().toLowerCase())
        .path(Integer.toString(project.getId()))
        .path(ResourceRequest.Name.EXPERIMENTS.toString().toLowerCase())
        .path(fileProvenanceHit.getMlId())
        .build());
    return dto;
  }

  public ExperimentDTO expand(ExperimentDTO dto, ResourceRequest resourceRequest) {
    if (resourceRequest != null && resourceRequest.contains(ResourceRequest.Name.EXPERIMENTS)) {
      dto.setExpand(true);
    }
    return dto;
  }

  //Build collection
  public ExperimentDTO build(UriInfo uriInfo, ResourceRequest resourceRequest, Project project)
      throws ServiceException, GenericException, ExperimentsException {
    ExperimentDTO dto = new ExperimentDTO();
    uri(dto, uriInfo, project);
    expand(dto, resourceRequest);

    if(dto.isExpand()) {
      ProvFileStateParamBuilder provFilesParamBuilder = new ProvFileStateParamBuilder()
          .withProjectInodeId(project.getInode().getId())
          .withMlType(Provenance.MLType.EXPERIMENT.name())
          .withAppState();

      buildSortOrder(provFilesParamBuilder, resourceRequest.getSort());
      buildFilter(provFilesParamBuilder, resourceRequest.getFilter(), project);

      GenericEntity<Collection<FileState>> searchResults = new GenericEntity<Collection<FileState>>(
          provenanceController.provFileStateList(provFilesParamBuilder)) {
      };

      dto.setCount((long)searchResults.getEntity().size());

      for(FileState fileProvStateHit: searchResults.getEntity()) {
        ExperimentDTO experimentDTO = build(uriInfo, resourceRequest, project, fileProvStateHit);
        if(experimentDTO != null) {
          dto.addItem(experimentDTO);
        }
      }
    }
    return dto;
  }

  //Build specific
  public ExperimentDTO build(UriInfo uriInfo, ResourceRequest resourceRequest, Project project,
                             FileState fileProvenanceHit) throws ExperimentsException {

    ExperimentDTO experimentDTO = new ExperimentDTO();
    uri(experimentDTO, uriInfo, project, fileProvenanceHit);
    expand(experimentDTO, resourceRequest);

    if (experimentDTO.isExpand()) {
      if(fileProvenanceHit.getXattrs().containsKey("config")) {
        JSONObject config = new JSONObject(fileProvenanceHit.getXattrs().get("config"));

        ExperimentDescription experimentDescription =
            experimentConfigurationConverter.unmarshalDescription(config.toString());

        Long creationTime = fileProvenanceHit.getCreateTime();
        experimentDTO.setStarted(DateUtils.millis2LocalDateTime(creationTime).toString());
        Long finishTime = null;
        if (!Strings.isNullOrEmpty(experimentDescription.getDuration())) {
          finishTime = creationTime + Long.valueOf(experimentDescription.getDuration());
          experimentDTO.setFinished(DateUtils.millis2LocalDateTime(finishTime).toString());
        }

        if(experimentDescription.getState().equals(Provenance.AppState.RUNNING.name())) {
          experimentDTO.setState(fileProvenanceHit.getAppState().getCurrentState().name());
        } else {
          experimentDTO.setState(experimentDescription.getState());
        }

        if(fileProvenanceHit.getXattrs().containsKey("model")) {
          String model = fileProvenanceHit.getXattrs().get("model");
          experimentDTO.setModel(model);
        }

        experimentDTO.setId(fileProvenanceHit.getMlId());
        experimentDTO.setName(experimentDescription.getName());
        experimentDTO.setUserFullName(experimentDescription.getUserFullName());
        experimentDTO.setMetric(experimentDescription.getMetric());
        experimentDTO.setDescription(experimentDescription.getDescription());
        experimentDTO.setExperimentType(experimentDescription.getExperimentType());
        experimentDTO.setFunction(experimentDescription.getFunction());
        experimentDTO.setDirection(experimentDescription.getDirection());
        experimentDTO.setOptimizationKey(experimentDescription.getOptimizationKey());
        experimentDTO.setTensorboard(tensorBoardBuilder.build(uriInfo,
            resourceRequest.get(ResourceRequest.Name.TENSORBOARD), project, fileProvenanceHit.getMlId()));
        experimentDTO.setProvenance(experimentFileProvenanceBuilder.build(uriInfo,
            resourceRequest.get(ResourceRequest.Name.PROVENANCE), project, fileProvenanceHit.getMlId()));
        experimentDTO.setResults(experimentResultsBuilder.build(uriInfo,
            resourceRequest.get(ResourceRequest.Name.RESULTS), project, fileProvenanceHit.getMlId()));
      } else {
        return null;
      }
    }
    return experimentDTO;
  }

  private void buildFilter(ProvFileStateParamBuilder provFilesParamBuilder,
                                            Set<? extends AbstractFacade.FilterBy> filters, Project project) {
    if(filters != null) {
      for (AbstractFacade.FilterBy filterBy : filters) {
        if(filterBy.getParam().compareToIgnoreCase(Filters.NAME.name()) == 0) {
          HashMap<String, String> map = new HashMap<>();
          map.put("config.name", filterBy.getValue());
          provFilesParamBuilder.withXAttrsLike(map);
        } else if(filterBy.getParam().compareToIgnoreCase(Filters.DATE_START_LT.name()) == 0) {
          provFilesParamBuilder.createdBefore(getDate(filterBy.getField(), filterBy.getValue()).getTime());
        } else if(filterBy.getParam().compareToIgnoreCase(Filters.DATE_START_GT.name()) == 0) {
          provFilesParamBuilder.createdAfter(getDate(filterBy.getField(), filterBy.getValue()).getTime());
        } else if(filterBy.getParam().compareToIgnoreCase(Filters.USER.name()) == 0) {
          String userId = filterBy.getValue();
          Users user = userFacade.find(Integer.parseInt(userId));
          String hdfsUserStr = hdfsUsersController.getHdfsUserName(project, user);
          HdfsUsers hdfsUsers = hdfsUsersFacade.findByName(hdfsUserStr);
          provFilesParamBuilder.withUserId(hdfsUsers.getId().toString());
        }
      }
    }
  }

  private Date getDate(String field, String value) {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    try {
      return formatter.parse(value);
    } catch (ParseException e) {
      throw new InvalidQueryException(
          "Filter value for " + field + " needs to set valid format. Expected:yyyy-mm-dd hh:mm:ss but found: " + value);
    }
  }

  public void buildSortOrder(ProvFileStateParamBuilder provFilesParamBuilder, Set<? extends AbstractFacade.SortBy> sort)
  {
    if(sort != null) {
      for(AbstractFacade.SortBy sortBy: sort) {
        if(sortBy.getValue().compareToIgnoreCase(SortBy.NAME.name()) == 0) {
          provFilesParamBuilder.sortBy("config.name", SortOrder.valueOf(sortBy.getParam().getValue()));
        } else if(sortBy.getValue().compareToIgnoreCase(SortBy.METRIC.name()) == 0) {
          provFilesParamBuilder.sortBy("config.metric", SortOrder.valueOf(sortBy.getParam().getValue()));
        } else if(sortBy.getValue().compareToIgnoreCase(SortBy.USER.name()) == 0) {
          provFilesParamBuilder.sortBy("config.userFullName", SortOrder.valueOf(sortBy.getParam().getValue()));
        }else if(sortBy.getValue().compareToIgnoreCase(SortBy.START.name()) == 0) {
          provFilesParamBuilder.sortBy("create_timestamp", SortOrder.valueOf(sortBy.getParam().getValue()));
        }
      }
    }
  }

  protected enum SortBy {
    NAME,
    METRIC,
    USER,
    START,
    END,
    STATE;
  }

  protected enum Filters {
    NAME,
    DATE_START_LT,
    DATE_START_GT,
    USER
  }
}