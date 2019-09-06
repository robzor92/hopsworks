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

/*jshint undef: false, unused: false, indent: 2*/
/*global angular: false */

'use strict';

angular.module('hopsWorksApp')
  .controller('ProvenanceCtrl', ['ProjectService', '$routeParams', '$location', 'growl',
    function (ProjectService,  $routeParams, $location, growl) {

      var self = this;
      self.metaStatus = [
        {id:'DISABLED', label: 'No provenance'},
        {id: 'META_ENABLED', label: 'Meta Enabled - Searchable'},
        {id: 'PROVENANCE_ENABLED', label: 'Provenance Enabled - Operations and states are searchable'},
      ]
      self.projectId = $routeParams.projectID;
      self.pGetStatusWorking = false;
      self.dGetStatusWorking = false;
      self.setStatusWorking = false;
      self.projectProvenanceEnabled = false;
      self.provStateSize = 0;
      self.provOpsSize = 0;
      self.provCleanupSize = 0;

      self.getProjectProvenanceStatus = function () {
        self.pGetStatusWorking = true;
        ProjectService.getProjectProvenanceStatus({id: self.projectId})
          .$promise.then(
            function (response) {
              self.projectProvenanceEnabled = (response.result.value === "PROVENANCE_ENABLED");
              self.pGetStatusWorking = false;
            },
            function (error) {
              if (typeof error.data.usrMsg !== 'undefined') {
                growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 5000});
              } else {
                growl.error("", {title: error.data.errorMsg, ttl: 5000});
              }
              self.pGetStatusWorking = false;
            });
      };

      self.getProjectProvenanceStatus();

      self.setProvenanceStatus = function () {
        self.setStatusWorking = true;
        var provenanceStatus;
        if(self.projectProvenanceEnabled === true) {
          provenanceStatus = "PROVENANCE_ENABLED";
        } else {
          provenanceStatus = "DISABLED";
        }
        ProjectService.setProjectProvenanceStatus({id: self.projectId, provenanceStatus: provenanceStatus})
          .$promise.then(
            function (response) {
              self.setStatusWorking = false;
              self.getDatasetsProvenanceStatus();
            },
            function (error) {
              if (typeof error.data.usrMsg !== 'undefined') {
                growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 5000});
              } else {
                growl.error("", {title: error.data.errorMsg, ttl: 5000});
              }
              self.setStatusWorking = false;
            });
      };

      self.getDatasetsProvenanceStatus = function() {
        self.dGetStatusWorking = true;
        ProjectService.getDatasetsProvenanceStatus({id: self.projectId})
          .$promise.then(
          function (response) {
            self.datasetProvenanceStatus = response;
            self.dGetStatusWorking = false;
          },
          function (error) {
            if (typeof error.data.usrMsg !== 'undefined') {
              growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 5000});
            } else {
              growl.error("", {title: error.data.errorMsg, ttl: 5000});
            }
            self.dGetStatusWorking = false;
          });
      }

      self.getDatasetsProvenanceStatus();

      self.isWorking = function() {
        return self.pGetStatusWorking || self.dGetStatusWorking || self.setStatusWorking;
      }

      self.getSize = function() {
        ProjectService.provStates({id: self.projectId})
          .$promise.then(
          function (response) {
            self.provStatesSize = response.result.value;
          },
          function (error) {
            if (typeof error.data.usrMsg !== 'undefined') {
              growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 5000});
            } else {
              growl.error("", {title: error.data.errorMsg, ttl: 5000});
            }
          });
        ProjectService.provOps({id: self.projectId})
          .$promise.then(
          function (response) {
            self.provOpsSize = response.count;
          },
          function (error) {
            if (typeof error.data.usrMsg !== 'undefined') {
              growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 5000});
            } else {
              growl.error("", {title: error.data.errorMsg, ttl: 5000});
            }
          });
        ProjectService.provCleanup({id: self.projectId})
          .$promise.then(
          function (response) {
            self.provCleanupSize = response.count;
          },
          function (error) {
            if (typeof error.data.usrMsg !== 'undefined') {
              growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 5000});
            } else {
              growl.error("", {title: error.data.errorMsg, ttl: 5000});
            }
          });
      }
      self.getSize();
    }]);