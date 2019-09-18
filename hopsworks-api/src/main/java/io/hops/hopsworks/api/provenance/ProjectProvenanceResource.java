/*
 * Changes to this file committed after and not including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Changes to this file committed before and including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.hops.hopsworks.api.provenance;

import io.hops.hopsworks.api.filter.AllowedProjectRoles;
import io.hops.hopsworks.api.filter.Audience;
import io.hops.hopsworks.api.provenance.v2.ProvFileOpsBeanParam;
import io.hops.hopsworks.api.provenance.v2.ProvFileStateBeanParam;
import io.hops.hopsworks.api.util.Pagination;
import io.hops.hopsworks.common.dao.dataset.Dataset;
import io.hops.hopsworks.common.dao.dataset.DatasetFacade;
import io.hops.hopsworks.common.dao.hdfs.inode.Inode;
import io.hops.hopsworks.common.dao.hdfs.inode.InodeFacade;
import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.hdfs.Utils;
import io.hops.hopsworks.common.provenance.AppFootprintType;
import io.hops.hopsworks.common.provenance.ProvDatasetState;
import io.hops.hopsworks.common.provenance.ProvenanceController;
import io.hops.hopsworks.common.provenance.v2.xml.ArchiveDTO;
import io.hops.hopsworks.common.provenance.v2.xml.FileOpDTO;
import io.hops.hopsworks.common.provenance.v2.xml.SimpleResult;
import io.hops.hopsworks.common.provenance.v2.ProvFileOpsParamBuilder;
import io.hops.hopsworks.common.provenance.v2.ProvFileStateParamBuilder;
import io.hops.hopsworks.exceptions.GenericException;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.jwt.annotation.JWTRequired;
import io.hops.hopsworks.restutils.RESTCodes;
import io.swagger.annotations.Api;
import org.apache.hadoop.fs.XAttrSetFlag;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RequestScoped
@TransactionAttribute(TransactionAttributeType.NEVER)
@Api(value = "Project Provenance Service", description = "Project Provenance Service")
public class ProjectProvenanceResource {

  private static final Logger logger = Logger.getLogger(ProjectProvenanceResource.class.getName());

  @EJB
  private ProvenanceController provenanceCtrl;
  @EJB
  private ProjectFacade projectFacade;
  @EJB
  private InodeFacade inodeFacade;
  @EJB
  private DatasetFacade datasetFacade;
  @EJB
  private DistributedFsService dfs;
  
  private Project project;

  public void setProjectId(Integer projectId) {
    this.project = projectFacade.find(projectId);
  }
  
  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getProvenanceStatus()
    throws GenericException {
    Inode.MetaStatus status = provenanceCtrl.getProjectProvenanceStatus(project);
    return Response.ok().entity(new SimpleResult(status.name())).build();
  }
  
  @POST
  @Path("/status/{status}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response changeProvenanceStatus(
    @PathParam("status") Inode.MetaStatus status)
    throws GenericException {
    provenanceCtrl.changeProjectProvenanceStatus(project, status);
    return Response.ok().build();
  }
  
  @GET
  @Path("/content")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response content() {
    GenericEntity<List<ProvDatasetState>> result
      = new GenericEntity<List<ProvDatasetState>>(provenanceCtrl.getDatasetsProvenanceStatus(project)) {};
    return Response.ok().entity(result).build();
  }
  
  @GET
  @Path("file/ops/size")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getFileOpsSize() throws ServiceException, GenericException {
    ProvFileOpsParamBuilder paramBuilder = new ProvFileOpsParamBuilder()
      .withProjectInodeId(project.getInode().getId());
    return ProvenanceResourceHelper.getFileOps(provenanceCtrl, paramBuilder,
      FileOpsCompactionType.NONE, FileStructReturnType.COUNT);
  }
  
  @GET
  @Path("file/ops/cleanupsize")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getFileOpsCleanupSize() throws ServiceException, GenericException {
    FileOpDTO.Count result = provenanceCtrl.cleanupSize(project.getInode().getId());
    return Response.ok().entity(result).build();
  }
  
  @GET
  @Path("file/ops")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getFileOps(
    @BeanParam ProvFileOpsBeanParam params,
    @BeanParam Pagination pagination,
    @Context HttpServletRequest req) throws ServiceException, GenericException {
    ProvFileOpsParamBuilder paramBuilder = new ProvFileOpsParamBuilder()
      .withProjectInodeId(project.getInode().getId())
      .withQueryParamFilterBy(params.getFileOpsFilterBy())
      .withQueryParamSortBy(params.getFileOpsSortBy())
      .withQueryParamExpansions(params.getExpansions())
      .withQueryParamAppExpansionFilter(params.getAppExpansionParams())
      .withAggregations(params.getAggregations())
      .withPagination(pagination.getOffset(), pagination.getLimit());
    logger.log(Level.INFO, "Local content path:{0} file state params:{1} ",
      new Object[]{req.getRequestURL().toString(), params});
    return ProvenanceResourceHelper.getFileOps(provenanceCtrl, paramBuilder,
      params.getOpsCompaction(), params.getReturnType());
  }
  
  @GET
  @Path("file/{inodeId}/ops")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getFileOps(
    @PathParam("inodeId") Long fileInodeId,
    @BeanParam ProvFileOpsBeanParam params,
    @BeanParam Pagination pagination,
    @Context HttpServletRequest req) throws ServiceException, GenericException {
    ProvFileOpsParamBuilder paramBuilder = new ProvFileOpsParamBuilder()
      .withProjectInodeId(project.getInode().getId())
      .withFileInodeId(fileInodeId)
      .withQueryParamFilterBy(params.getFileOpsFilterBy())
      .withQueryParamSortBy(params.getFileOpsSortBy())
      .withQueryParamExpansions(params.getExpansions())
      .withQueryParamAppExpansionFilter(params.getAppExpansionParams())
      .withAggregations(params.getAggregations())
      .withPagination(pagination.getOffset(), pagination.getLimit());
    logger.log(Level.INFO, "Local content path:{0} file state params:{1} ",
      new Object[]{req.getRequestURL().toString(), params});
    return ProvenanceResourceHelper.getFileOps(provenanceCtrl, paramBuilder, params.getOpsCompaction(),
      params.getReturnType());
  }
  
  @GET
  @Path("file/state/size")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getFileStatesSize() throws ServiceException, GenericException {
    ProvFileStateParamBuilder paramBuilder = new ProvFileStateParamBuilder()
      .withProjectInodeId(project.getInode().getId());
    return ProvenanceResourceHelper.getFileStates(provenanceCtrl, paramBuilder,FileStructReturnType.COUNT);
  }
  
  @GET
  @Path("file/state")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getFileStates(
    @BeanParam ProvFileStateBeanParam params,
    @BeanParam Pagination pagination,
    @Context HttpServletRequest req) throws ServiceException, GenericException {
    ProvFileStateParamBuilder paramBuilder = new ProvFileStateParamBuilder()
      .withProjectInodeId(project.getInode().getId())
      .withQueryParamFileStateFilterBy(params.getFileStateFilterBy())
      .withQueryParamFileStateSortBy(params.getFileStateSortBy())
      .withQueryParamExactXAttr(params.getExactXAttrParams())
      .withQueryParamLikeXAttr(params.getLikeXAttrParams())
      .withQueryParamXAttrSortBy(params.getXattrSortBy())
      .withQueryParamExpansions(params.getExpansions())
      .withQueryParamAppExpansionFilter(params.getAppExpansionParams())
      .withPagination(pagination.getOffset(), pagination.getLimit());
    logger.log(Level.INFO, "Local content path:{0} file state params:{1} ",
      new Object[]{req.getRequestURL().toString(), params});
    return ProvenanceResourceHelper.getFileStates(provenanceCtrl, paramBuilder, params.getReturnType());
  }
  
  @GET
  @Path("file/{inodeId}/state")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getFileStates(
    @PathParam("inodeId") Long fileInodeId,
    @BeanParam ProvFileStateBeanParam params,
    @BeanParam Pagination pagination,
    @Context HttpServletRequest req) throws GenericException, ServiceException {
    ProvFileStateParamBuilder paramBuilder = new ProvFileStateParamBuilder()
      .withProjectInodeId(project.getInode().getId())
      .withFileInodeId(fileInodeId)
      .withQueryParamFileStateFilterBy(params.getFileStateFilterBy())
      .withQueryParamFileStateSortBy(params.getFileStateSortBy())
      .withQueryParamExactXAttr(params.getExactXAttrParams())
      .withQueryParamLikeXAttr(params.getLikeXAttrParams())
      .withQueryParamXAttrSortBy(params.getXattrSortBy())
      .withQueryParamExpansions(params.getExpansions())
      .withQueryParamAppExpansionFilter(params.getAppExpansionParams())
      .withPagination(pagination.getOffset(), pagination.getLimit());
    logger.log(Level.INFO, "Local content path:{0} file state params:{1} ",
      new Object[]{req.getRequestURL().toString(), params});
    return ProvenanceResourceHelper.getFileStates(provenanceCtrl, paramBuilder, params.getReturnType());
  }
  
  
  @GET
  @Path("app/{appId}/footprint/{type}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response appProvenance(
    @PathParam("appId") String appId,
    @PathParam("type") @DefaultValue("ALL") AppFootprintType footprintType,
    @BeanParam ProvFileOpsBeanParam params,
    @BeanParam Pagination pagination,
    @Context HttpServletRequest req) throws ServiceException, GenericException {
    ProvFileOpsParamBuilder paramBuilder = new ProvFileOpsParamBuilder()
      .withProjectInodeId(project.getInode().getId())
      .withAppId(appId)
      .withQueryParamFilterBy(params.getFileOpsFilterBy())
      .withQueryParamSortBy(params.getFileOpsSortBy())
      .withAggregations(params.getAggregations())
      .withPagination(pagination.getOffset(), pagination.getLimit());
    logger.log(Level.INFO, "Local content path:{0} file state params:{1} ",
      new Object[]{req.getRequestURL().toString(), params});
    
    return ProvenanceResourceHelper.getAppFootprint(provenanceCtrl, paramBuilder, footprintType,
      params.getReturnType());
  }
  
  @POST
  @Path("testXAttr/{inodeId}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response testXAttr(
    @PathParam("inodeId") Long inodeId) throws GenericException {
    Inode inode = inodeFacade.findById(inodeId);
    Dataset dataset = datasetFacade.findByProjectAndInode(project, inode);
    Inode inode2 = inode;
    if(dataset == null) {
      inode = inodeFacade.findById(inode.getInodePK().getParentId());
    }
    dataset = datasetFacade.findByProjectAndInode(project, inode);
    if(dataset == null) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_STATE, Level.INFO,
        "not dataset or child of dataset");
    }
    DistributedFileSystemOps dfso = dfs.getDfsOps();
    String path = Utils.getDatasetPath(project.getName(), dataset.getName());
    if(!inode2.equals(inode)) {
      path = path + "/" + inode2.getInodePK().getName();
    }
    createTestXAttr(dfso, path);
    updateTestXAttr(dfso, path);
    deleteTestXAttr(dfso, path);
    createTestXAttr(dfso, path);
    deleteTestXAttr(dfso, path);
    return Response.ok().build();
  }
  
  @DELETE
  @Path("file/{inodeId}/ops/cleanup")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response testArchival(
    @PathParam("inodeId") Long inodeId)
    throws ServiceException, GenericException {
    ArchiveDTO.Round result = provenanceCtrl.provCleanupFilePrefix(inodeId);
    return Response.ok().entity(result).build();
  }
  
  @DELETE
  @Path("file/{inodeId}/ops/cleanup/upto/{timestamp}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API}, allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response testArchival(
    @PathParam("inodeId") Long inodeId,
    @PathParam("timestamp") Long timestamp)
    throws ServiceException, GenericException {
    ArchiveDTO.Round result = provenanceCtrl.provCleanupFilePrefix(inodeId, timestamp);
    return Response.ok().entity(result).build();
  }
  
  private void createTestXAttr(DistributedFileSystemOps dfso, String datasetPath) throws GenericException {
    EnumSet<XAttrSetFlag> flags = EnumSet.noneOf(XAttrSetFlag.class);
    flags.add(XAttrSetFlag.CREATE);
    xattrOp(dfso, datasetPath, flags);
  }
  
  private void updateTestXAttr(DistributedFileSystemOps dfso, String datasetPath) throws GenericException {
    EnumSet<XAttrSetFlag> flags = EnumSet.noneOf(XAttrSetFlag.class);
    flags.add(XAttrSetFlag.REPLACE);
    xattrOp(dfso, datasetPath, flags);
  }
  
  private void deleteTestXAttr(DistributedFileSystemOps dfso, String datasetPath) throws GenericException {
    try {
      dfso.removeXAttr(datasetPath, "provenance.test");
    } catch (IOException e) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_STATE, Level.INFO,
        "xattrs persistance exception");
    }
  }
  
  private void xattrOp(DistributedFileSystemOps dfso, String datasetPath, EnumSet<XAttrSetFlag> flags)
    throws GenericException {
    try {
      dfso.setXAttr(datasetPath, "provenance.test", new byte[]{1}, flags);
    } catch (IOException e) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_STATE, Level.INFO,
        "xattrs persistance exception");
    }
  }
  
  public enum FileStructReturnType {
    LIST,
    MIN_TREE,
    FULL_TREE,
    COUNT,
    ARTIFACTS;
  }
  public enum FileOpsCompactionType {
    NONE,
    FILE_COMPACT,
    FILE_SUMMARY
  }
}
