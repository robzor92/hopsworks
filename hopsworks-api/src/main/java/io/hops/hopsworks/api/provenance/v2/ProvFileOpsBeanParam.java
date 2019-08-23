/*
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
 */
package io.hops.hopsworks.api.provenance.v2;

import io.hops.hopsworks.api.provenance.ProjectProvenanceResource;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import java.util.Set;

public class ProvFileOpsBeanParam {
  @QueryParam("filter_by")
  @ApiParam(value = "ex. filter_by=fileName:file1",
    allowableValues = "filter_by=PROJECT_I_ID:id1, filter_by=INODE_ID:id1, " +
      "filter_by=FILE_NAME:file1, filter_by=FILE_NAME_LIKE:fil, " +
      "filter_by=FILE_OPERATION:CREATE/DELETE/ACCESS_DATA/MODIFY/DATA" +
      "filter_by=USER_ID:user1, filter_by=APP_ID:app1," +
      "filter_by=TIMESTAMP_LT:10030042, filter_by=TIMESTAMP_GT:10010042",
    allowMultiple = true)
  private Set<String> fileOpsFilter;
  
  @QueryParam("return_type")
  @DefaultValue("LIST")
  private ProjectProvenanceResource.FileStructReturnType returnType;
  
  @QueryParam("ops_compaction")
  @DefaultValue("NONE")
  private ProjectProvenanceResource.FileOpsCompactionType opsCompaction;
  
  public ProvFileOpsBeanParam(
    @QueryParam("filter_by") Set<String> fileOpsFilter,
    @QueryParam("return_type") @DefaultValue("LIST") ProjectProvenanceResource.FileStructReturnType returnType,
    @QueryParam("ops_compaction") @DefaultValue("NONE") ProjectProvenanceResource.FileOpsCompactionType opsCompaction) {
    
    this.fileOpsFilter = fileOpsFilter;
    this.returnType = returnType;
    this.opsCompaction = opsCompaction;
  }
  
  public Set<String> getFileOpsFilter() {
    return fileOpsFilter;
  }
  
  public void setFileOpsFilter(Set<String> fileOpsFilter) {
    this.fileOpsFilter = fileOpsFilter;
  }
  
  public ProjectProvenanceResource.FileStructReturnType getReturnType() {
    return returnType;
  }
  
  public void setReturnType(ProjectProvenanceResource.FileStructReturnType returnType) {
    this.returnType = returnType;
  }
  
  public ProjectProvenanceResource.FileOpsCompactionType getOpsCompaction() {
    return opsCompaction;
  }
  
  public void setOpsCompaction(ProjectProvenanceResource.FileOpsCompactionType opsCompaction) {
    this.opsCompaction = opsCompaction;
  }
  
  @Override
  public String toString() {
    return "ProvFileStateBeanParam{"
      + "file ops:" + fileOpsFilter.toString()
      + "ops compaction:" + opsCompaction
      + "return type:" + returnType
      + '}';
  }
}