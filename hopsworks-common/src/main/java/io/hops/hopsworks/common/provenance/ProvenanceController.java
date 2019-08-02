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
package io.hops.hopsworks.common.provenance;

import io.hops.hopsworks.common.dao.dataset.Dataset;
import io.hops.hopsworks.common.dao.hdfs.inode.Inode;
import io.hops.hopsworks.common.dao.hdfs.inode.InodeFacade;
import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.elastic.ElasticController;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.hdfs.Utils;
import io.hops.hopsworks.exceptions.GenericException;
import io.hops.hopsworks.exceptions.ProjectException;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.restutils.RESTCodes;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

@Stateless
public class ProvenanceController {
  @EJB
  private ElasticController elasticCtrl;
  @EJB
  private InodeFacade inodes;
  @EJB
  private ProjectFacade projectFacade;
  @EJB
  private DistributedFsService dfs;
  
  public static final String PROJECT_PROVENANCE_STATUS_XATTR_NAME = "provenance.meta_status";
  
  public boolean isProjectProvenanceEnabled(Project project) throws GenericException {
    return getProjectProvenanceStatus(project).equals(Inode.MetaStatus.PROVENANCE_ENABLED);
  }
  
  public boolean isProjectProvenanceEnabled(Project project, DistributedFileSystemOps dfso) throws GenericException {
    return getProjectProvenanceStatus(project, dfso).equals(Inode.MetaStatus.PROVENANCE_ENABLED);
  }
  
  public Inode.MetaStatus getProjectProvenanceStatus(Project project) throws GenericException {
    DistributedFileSystemOps dfso = dfs.getDfsOps();
    try {
      return getProjectProvenanceStatus(project, dfso);
    } finally {
      if (dfso != null) {
        dfso.close();
      }
    }
  }
  
  public Inode.MetaStatus getProjectProvenanceStatus(Project project, DistributedFileSystemOps dfso)
    throws GenericException {
    String projectPath = Utils.getProjectPath(project.getName());
    try {
      byte[] bVal = dfso.getXAttr(projectPath, PROJECT_PROVENANCE_STATUS_XATTR_NAME);
      Inode.MetaStatus status;
      if(bVal == null) {
        status = Inode.MetaStatus.DISABLED;
      } else {
        status = Inode.MetaStatus.valueOf(new String(bVal));
      }
      return status;
    } catch (IOException e) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_STATE, Level.INFO,
        "project provenance xattr persistance exception");
    }
  }
  
  public void changeProjectProvenanceStatus(Project project, Inode.MetaStatus newStatus) throws GenericException {
    String projectPath = Utils.getProjectPath(project.getName());
    DistributedFileSystemOps dfso = dfs.getDfsOps();
    try {
      byte[] bVal = dfso.getXAttr(projectPath, PROJECT_PROVENANCE_STATUS_XATTR_NAME);
      Inode.MetaStatus previousStatus;
      if(bVal == null) {
        previousStatus = Inode.MetaStatus.DISABLED;
      } else {
        previousStatus = Inode.MetaStatus.valueOf(new String(bVal));
      }
      
      if(newStatus.equals(previousStatus)) {
        return;
      }
      if(Inode.MetaStatus.DISABLED.equals(previousStatus)) {
        upgradeDatasetsMetaStatus(project, dfso);
        dfso.insertXAttr(projectPath, PROJECT_PROVENANCE_STATUS_XATTR_NAME, newStatus.name().getBytes());
      } else {
        downgradeDatasetsMetaStatus(project, dfso);
        dfso.removeXAttr(projectPath, PROJECT_PROVENANCE_STATUS_XATTR_NAME);
      }
    } catch (IOException e) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_STATE, Level.INFO,
        "project provenance xattr persistance exception");
    } finally {
      if (dfso != null) {
        dfso.close();
      }
    }
  }
  
  private void upgradeDatasetsMetaStatus(Project project, DistributedFileSystemOps dfso) throws GenericException {
    try {
      for (Dataset ds : project.getDatasetCollection()) {
        if(isHive(ds) || isFeatureStore(ds)) {
          //TODO - bug?
          continue;
        }
        if (Inode.MetaStatus.META_ENABLED.equals(ds.getInode().getMetaStatus())) {
          String datasetPath = Utils.getDatasetPath(project.getName(), ds.getName());
          dfso.setProvenanceEnabled(datasetPath);
        }
      }
    } catch (IOException e) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_STATE, Level.INFO,
        "dataset provenance persistance exception");
    }
  }
  
  private void downgradeDatasetsMetaStatus(Project project, DistributedFileSystemOps dfso) throws GenericException {
    try {
      for (Dataset ds : project.getDatasetCollection()) {
        if(isHive(ds) || isFeatureStore(ds)) {
          //TODO - bug?
          continue;
        }
        if (Inode.MetaStatus.PROVENANCE_ENABLED.equals(ds.getInode().getMetaStatus())) {
          String datasetPath = Utils.getDatasetPath(project.getName(), ds.getName());
          dfso.setMetaEnabled(datasetPath);
        }
      }
    } catch (IOException e) {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_STATE, Level.INFO,
        "dataset provenance persistance exception");
    }
  }
  
  private boolean isHive(Dataset ds) {
    String hiveDB = ds.getProject().getName() + ".db";
    return hiveDB.equals(ds.getName());
  }
  
  private boolean isFeatureStore(Dataset ds) {
    String hiveDB = ds.getProject().getName() + "_featurestore.db";
    return hiveDB.equals(ds.getName());
  }
  
  public List<ProvDatasetState> getDatasetsProvenanceStatus(Project project) {
    List<ProvDatasetState> result = new ArrayList<>();
    for (Dataset ds : project.getDatasetCollection()) {
      ProvDatasetState dsState = new ProvDatasetState(ds.getName(), ds.getInode().getId(),
        ds.getInode().getMetaStatus());
      result.add(dsState);
    }
    return result;
  }
  
  public List<ProvFileStateHit> provFileState(ProvFileStateListParamBuilder params)
    throws GenericException, ServiceException, ProjectException {
    if(params.getMlId() != null || params.getInodeId() != null) {
      return elasticCtrl.provFileState(params.fileParams(), params.mlAssetParams(), params.appDetails());
    } else {
      return provFileState(params.fileDetails(), params.mlAssetDetails(), params.appDetails());
    }
  }
  
  public List<ProvFileStateHit> provFileState(ProvFileDetailsQueryParams fileDetails,
    ProvMLAssetDetailsQueryParams mlAssetDetails, ProvFileAppDetailsQueryParams appDetails)
    throws ServiceException, ProjectException {
    List<ProvFileStateHit> fileStates = elasticCtrl.provFileState(fileDetails, mlAssetDetails, appDetails);
    Map<Long, String> cachedPaths = new HashMap<>();
    if(fileDetails.withFullPath) {
      for(ProvFileStateHit fileOp : fileStates) {
        String filePath = cachedPaths.get(fileOp.getInodeId());
        if(filePath == null) {
          Inode fileInode = inodes.findById(fileOp.getInodeId());
          if(fileInode == null) {
            filePath = "noInode";
          } else {
            filePath = "withInode";
          }
          cachedPaths.put(fileOp.getInodeId(), filePath);
        }
        fileOp.setFullPath(filePath);
      }
    }
    return fileStates;
  }
  
  public Long provFileStateCount(ProvFileStateListParamBuilder params)
    throws GenericException, ProjectException, ServiceException {
    return provFileStateCount(params.fileDetails(), params.mlAssetDetails(), params.appDetails());
  }
  
  public Long provFileStateCount(ProvFileDetailsQueryParams fileDetails,
    ProvMLAssetDetailsQueryParams mlAssetDetails, ProvFileAppDetailsQueryParams appDetails)
    throws GenericException, ProjectException, ServiceException {
    return elasticCtrl.provFileStateCount(fileDetails, mlAssetDetails, appDetails);
  }
  
  public List<ProvFileOpHit> provFileOps(ProvFileOpListParamBuilder params) throws ServiceException, ProjectException {
    return provFileOps(params.getProjectId(), params.getInodeId(), params.getAppId(), params.isWithFullPath());
  }
  
  public List<ProvFileOpHit> provFileOps(Integer projectId, Long inodeId, String appId, boolean withFullPath)
    throws ServiceException, ProjectException {
    List<ProvFileOpHit> fileOps = elasticCtrl.provFileOps(getProjectInodeId(projectId), inodeId, appId, new String[0]);
    Map<Long, String> cachedPaths = new HashMap<>();
    if(withFullPath) {
      for(ProvFileOpHit fileOp : fileOps) {
        String filePath = cachedPaths.get(fileOp.getInodeId());
        if(filePath == null) {
          Inode fileInode = inodes.findById(fileOp.getInodeId());
          if(fileInode == null) {
            filePath = "noInode";
          } else {
            filePath = "withInode";
          }
          cachedPaths.put(fileOp.getInodeId(), filePath);
        }
        fileOp.setFullPath(filePath);
      }
    }
    return fileOps;
  }
  
  public List<ProvFileHit> provAppFootprint(Integer projectId, String appId, ProvAppFootprintType footprintType)
    throws ServiceException, ProjectException {
    String inodeOperations[];
    switch(footprintType) {
      case ALL:
        inodeOperations = new String[0];
        break;
      case INPUT:
        inodeOperations = new String[]{"CREATE", "ACCESS_DATA"};
        break;
      case OUTPUT:
        inodeOperations = new String[]{"CREATE", "MODIFY_DATA", "DELETE"};
        break;
      case OUTPUT_ADDED:
      case TMP:
      case REMOVED:
        inodeOperations = new String[]{"CREATE", "DELETE"};
        break;
      default:
        throw new IllegalArgumentException("footprint type:" + footprintType + " not managed");
    }
    
    List<ProvFileOpHit> fileOps
      = elasticCtrl.provFileOps(getProjectInodeId(projectId),null, appId, inodeOperations);
    Map<Long, ProvFileHit> files = new HashMap<>();
    Set<Long> filesAccessed = new HashSet<>();
    Set<Long> filesCreated = new HashSet<>();
    Set<Long> filesModified = new HashSet<>();
    Set<Long> filesDeleted = new HashSet<>();
    for(ProvFileOpHit fileOp : fileOps) {
      files.put(fileOp.getInodeId(), new ProvFileHit(fileOp.getInodeId(), fileOp.getInodeName()));
      switch(fileOp.getInodeOperation()) {
        case "CREATE":
          filesCreated.add(fileOp.getInodeId());
          break;
        case "DELETE":
          filesDeleted.add(fileOp.getInodeId());
          break;
        case "ACCESS_DATA":
          filesAccessed.add(fileOp.getInodeId());
          break;
        case "MODIFY_DATA":
          filesModified.add(fileOp.getInodeId());
          break;
        default:
      }
    }
    //filter files based on footprintTypes
    switch(footprintType) {
      case ALL:
        //nothing - return all results
        break;
      case INPUT: {
        //files read - that existed before app (not created by app)
        Set<Long> aux = new HashSet<>(filesAccessed);
        aux.removeAll(filesCreated);
        files.keySet().retainAll(aux);
      } break;
      case OUTPUT: {
        //files created or modified, but not deleted
        Set<Long> aux = new HashSet<>(filesCreated);
        aux.addAll(filesModified);
        aux.removeAll(filesDeleted);
        files.keySet().retainAll(aux);
      } break;
      case OUTPUT_ADDED: {
        //files created but not deleted
        Set<Long> aux = new HashSet<>(filesCreated);
        aux.removeAll(filesDeleted);
        files.keySet().retainAll(aux);
      } break;
      case TMP: {
        //files created and deleted
        Set<Long> aux = new HashSet<>(filesCreated);
        aux.retainAll(filesDeleted);
        files.keySet().retainAll(aux);
      } break;
      case REMOVED: {
        //files not created and deleted
        Set<Long> aux = new HashSet<>(filesDeleted);
        aux.removeAll(filesCreated);
        files.keySet().retainAll(aux);
      } break;
      default:
        //continue;
    }
    return new LinkedList<>(files.values());
  }
  
  private Long getProjectInodeId(Integer projectId) throws ProjectException {
    if(projectId == null) {
      return null;
    }
    Project project = projectFacade.find(projectId);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.INFO,
        "projectId:" + projectId);
    }
    return project.getInode().getId();
  }
  
}
