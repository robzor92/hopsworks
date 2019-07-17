package io.hops.hopsworks.common.experiments;

import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.user.Users;
import io.hops.hopsworks.common.experiments.dto.ExperimentDescription;
import io.hops.hopsworks.common.experiments.dto.ExperimentDTO;
import io.hops.hopsworks.common.experiments.dto.ExperimentResult;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.hdfs.Utils;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.exceptions.DatasetException;
import io.hops.hopsworks.restutils.RESTCodes;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.XAttrSetFlag;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.MarshallerProperties;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class ExperimentsController {

  private static final Logger LOGGER = Logger.getLogger(ExperimentsController.class.getName());

  @EJB
  private DistributedFsService dfs;

  public void publish(String id, Project project, Users user, ExperimentDescription experimentDescription,
                      ExperimentDTO.XAttrSetFlag xAttrSetFlag)
      throws DatasetException {

    String realName = user.getFname() + " " + user.getLname();
    experimentDescription.setUserFullName(realName);

    String experimentPath = Utils.getProjectPath(project.getName()) + Settings.HOPS_EXPERIMENTS_DATASET + "/" + id;

    DistributedFileSystemOps dfso = null;
    try {
      JAXBContext sparkJAXBContext = JAXBContextFactory.createContext(new Class[] {ExperimentDescription.class},
          null);
      Marshaller marshaller = sparkJAXBContext.createMarshaller();
      marshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, false);
      marshaller.setProperty(MarshallerProperties.MEDIA_TYPE, MediaType.APPLICATION_JSON);
      StringWriter sw = new StringWriter();
      marshaller.marshal(experimentDescription, sw);
      byte[] experiment = sw.toString().getBytes(StandardCharsets.UTF_8);
      dfso = dfs.getDfsOps();

      EnumSet<XAttrSetFlag> flags = EnumSet.noneOf(XAttrSetFlag.class);

      LOGGER.log(Level.SEVERE, "attaching xattr " + xAttrSetFlag.name());

      if(experimentDescription.getResults() == null) {
        LOGGER.log(Level.SEVERE, "its null");
      } else {
        ExperimentResult[] res = experimentDescription.getResults()[0].getMetrics();
        for(ExperimentResult res2: res) {
          LOGGER.log(Level.SEVERE, res2.getKey() + " " + res2.getValue());
        }
      }

      flags.add(XAttrSetFlag.valueOf(xAttrSetFlag.name()));

      dfso.setXAttr(experimentPath, "provenance.config", experiment, flags);
    } catch(IOException | JAXBException ex) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.ATTACH_XATTR_ERROR, Level.SEVERE,
          "path: " + experimentPath, ex.getMessage(), ex);
    } finally {
      if (dfso != null) {
        dfs.closeDfsClient(dfso);
      }
    }
  }

  public void delete(String id, Project project, String hdfsUser) {
    boolean success = false;
    DistributedFileSystemOps dfso = null;
    try {
      dfso = dfs.getDfsOps(hdfsUser);
      String experimentPath = Utils.getProjectPath(project.getName()) + Settings.HOPS_EXPERIMENTS_DATASET + "/" + id;
      Path path = new Path(experimentPath);
      success = dfso.rm(path, true);
    } catch (IOException ioe) {

    }
    if(!success) {

    }
  }
}
