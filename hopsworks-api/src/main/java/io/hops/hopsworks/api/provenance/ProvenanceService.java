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

import com.google.common.base.Strings;
import io.hops.hopsworks.api.filter.AllowedProjectRoles;
import io.hops.hopsworks.api.filter.Audience;
import io.hops.hopsworks.api.filter.NoCacheResponse;
import io.hops.hopsworks.common.elastic.ElasticController;
import io.hops.hopsworks.common.provenance.AppProvenanceHit;
import io.hops.hopsworks.common.provenance.FProvMLAssetHit;
import io.hops.hopsworks.common.provenance.FileProvenanceHit;
import io.hops.hopsworks.common.provenance.Provenance;
import io.hops.hopsworks.exceptions.GenericException;
import io.hops.hopsworks.exceptions.ProjectException;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.jwt.annotation.JWTRequired;
import io.hops.hopsworks.restutils.RESTCodes;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/provenance")
@Stateless
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api(value = "Provenance Service",
  description = "Provenance Service")
@TransactionAttribute(TransactionAttributeType.NEVER)
public class ProvenanceService {

  private static final Logger logger = Logger.getLogger(ProvenanceService.class.getName());

  @EJB
  private NoCacheResponse noCacheResponse;
  @EJB
  private ElasticController elasticController;

  @GET
  @Path("fileEntriesBy/field/{field}/value/{value}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response fileProvenance(@PathParam("field") FileProvenanceField field,
    @PathParam("value") String value, @Context HttpServletRequest req)
    throws ServiceException {

    if (Strings.isNullOrEmpty(value) || value == null) {
      throw new IllegalArgumentException("Value was not provided");
    }

    logger.log(Level.INFO, "Local content path {0}", req.getRequestURL().toString());

    GenericEntity<List<FileProvenanceHit>> searchResults;
    switch (field) {
      case FILE_INODE_ID:
        long fileInodeId;
        try {
          fileInodeId = Long.valueOf(value);
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException(
            FileProvenanceField.FILE_INODE_ID.toString() + "expected value: integer", ex);
        }
        searchResults = new GenericEntity<List<FileProvenanceHit>>(
          elasticController.fileProvenanceByFileInodeId(fileInodeId)) {
        };
        break;

      case PROJECT_INODE_ID:
        long projectInodeId;
        try {
          projectInodeId = Long.valueOf(value);
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException(
            FileProvenanceField.PROJECT_INODE_ID.toString() + "expected value: long", ex);
        }
        searchResults = new GenericEntity<List<FileProvenanceHit>>(
          elasticController.fileProvenanceByProjectInodeId(projectInodeId)) {
        };
        break;
      case DATASET_INODE_ID:
        long datasetInodeId;
        try {
          datasetInodeId = Long.valueOf(value);
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException(
            FileProvenanceField.DATASET_INODE_ID.toString() + "expected value: long", ex);
        }
        searchResults = new GenericEntity<List<FileProvenanceHit>>(
          elasticController.fileProvenanceByDatasetInodeId(datasetInodeId)) {
        };
        break;
      case USER_ID:
        int userId;
        try {
          userId = Integer.valueOf(value);
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException(
            FileProvenanceField.USER_ID.toString() + "expected value: int", ex);
        }
        searchResults = new GenericEntity<List<FileProvenanceHit>>(
          elasticController.fileProvenanceByUserId(userId)) {
        };
        break;
      case APP_ID:
        searchResults = new GenericEntity<List<FileProvenanceHit>>(
          elasticController.fileProvenanceByAppId(value)) {
        };
        break;
      case FILE_INODE_NAME:
        searchResults = new GenericEntity<List<FileProvenanceHit>>(
          elasticController.fileProvenanceByInodeName(value)) {
        };
        break;
      default:
        throw new IllegalArgumentException("Unmanaged field:" + field);
    }

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(searchResults).build();
  }

  @GET
  @Path("appEntriesBy/field/{field}/value/{value}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response appProvenance(@PathParam("field") AppProvenanceField field,
    @PathParam("value") String value, @Context HttpServletRequest req)
    throws ServiceException {

    if (Strings.isNullOrEmpty(value) || value == null) {
      throw new IllegalArgumentException("Value was not provided");
    }

    logger.log(Level.INFO, "Local content path {0}", req.getRequestURL().toString());

    GenericEntity<List<AppProvenanceHit>> searchResults;
    switch (field) {
      case APP_ID:
        searchResults = new GenericEntity<List<AppProvenanceHit>>(
          elasticController.appProvenanceByAppId(value)) {
        };
        break;
      case APP_STATE:
        searchResults = new GenericEntity<List<AppProvenanceHit>>(
          elasticController.appProvenanceByAppState(value)) {
        };
        break;
      case APP_NAME:
        searchResults = new GenericEntity<List<AppProvenanceHit>>(
          elasticController.appProvenanceByAppName(value)) {
        };
        break;
      case APP_USER:
        searchResults = new GenericEntity<List<AppProvenanceHit>>(
          elasticController.appProvenanceByAppUser(value)) {
        };
        break;
      default:
        throw new IllegalArgumentException("Unmanaged field:" + field);
    }

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(searchResults).build();
  }

  @GET
  @Path("mlType/{mlType}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getMLAssets(@PathParam("mlType") Provenance.MLType mlType,
    @DefaultValue("false") @QueryParam("withAppState") boolean withAppState, @Context HttpServletRequest req)
    throws ServiceException, GenericException {
    logger.log(Level.INFO, "Local content path {0}", req.getRequestURL().toString());
    if (mlType == null) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO,
        "ml asset type is not set");
    }
    GenericEntity<List<FProvMLAssetHit>> searchResults = new GenericEntity<List<FProvMLAssetHit>>(
      elasticController.fileProvenanceByMLType(mlType.toString(), withAppState)) {
    };

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(searchResults).build();
  }

  @GET
  @Path("mlType/{mlType}/project/{projectId}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getMLAssets(@PathParam("mlType") Provenance.MLType mlType, @PathParam("projectId") Integer projectId,
    @DefaultValue("false") @QueryParam("withAppState") boolean withAppState,
    @Context HttpServletRequest req) throws ServiceException, GenericException, ProjectException {
    logger.log(Level.INFO, "Local content path {0}", req.getRequestURL().toString());
    if (mlType == null) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO, "ml asset type is not set");
    }
    if (projectId == null) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO, "project id is not set");
    }
    GenericEntity<List<FProvMLAssetHit>> searchResults = new GenericEntity<List<FProvMLAssetHit>>(
      elasticController.fileProvenanceByMLType(mlType.toString(), projectId, withAppState)) {
    };

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(searchResults).build();
  }

  @GET
  @Path("mlType/{mlType}/project/{projectId}/mlId/{mlId}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTRequired(acceptedTokens = {Audience.API},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER"})
  public Response getMLAssets(@PathParam("mlType") Provenance.MLType mlType, @PathParam("projectId") Integer projectId,
    @PathParam("mlId") String mlId, @DefaultValue("false") @QueryParam("withAppState") boolean withAppState,
    @Context HttpServletRequest req) throws ServiceException, GenericException, ProjectException {
    logger.log(Level.INFO, "Local content path {0}", req.getRequestURL().toString());
    if (mlType == null) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO, "ml asset type is not set");
    }
    if (projectId == null) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO, "project id is not set");
    }
    if (mlId == null || mlId.equals("")) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO, "ml asset id is not set");
    }
    GenericEntity<List<FProvMLAssetHit>> searchResults = new GenericEntity<List<FProvMLAssetHit>>(
      elasticController.fileProvenanceByMLType(mlType.toString(), projectId, mlId, withAppState)) {
    };

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(searchResults).build();
  }

  public static enum FileProvenanceField {
    FILE_INODE_ID,
    PROJECT_INODE_ID,
    DATASET_INODE_ID,
    USER_ID,
    APP_ID,
    FILE_INODE_NAME;
  }

  public static enum AppProvenanceField {
    APP_ID,
    APP_STATE,
    APP_NAME,
    APP_USER;
  }
}
