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
'use strict';
/*
 * Controller for the job UI dialog.
 */
angular.module('hopsWorksApp')
    .controller('ExperimentCtrl', ['$scope', '$timeout', 'growl', '$location', 'MembersService', 'UserService', 'ModalService', 'ProjectService', 'ExperimentService', 'TensorBoardService', '$interval',
        '$routeParams', '$route', '$sce', '$window',
        function($scope, $timeout, growl, $location, MembersService, UserService, ModalService, ProjectService, ExperimentService, TensorBoardService, $interval,
            $routeParams, $route, $sce, $window) {

            var self = this;

            self.pageSize = 5;
            self.currentPage = 1;
            self.totalItems = 0;

            self.sortType = 'start';
            self.orderBy = 'desc';
            self.reverse = true;

            self.projectId = $routeParams.projectID;

            self.memberSelected = {};

            self.experiments = []

            self.loading = false;
            self.loadingText = "";

            self.experimentsFilter = "";

            self.query = "";

            self.membersList = [];
            self.members = [];
            self.userEmail = "";

            self.experimentsToDate = new Date();
            self.experimentsToDate.setMinutes(self.experimentsToDate.getMinutes() + 60*24);
            self.experimentsFromDate = new Date();
            self.experimentsFromDate.setMinutes(self.experimentsToDate.getMinutes() - 60*24*30);

            var startLoading = function(label) {
                self.loading = true;
                self.loadingText = label;
            };
            var stopLoading = function() {
                self.loading = false;
                self.loadingText = "";
            };

            self.order = function () {
                if (self.reverse) {
                    self.orderBy = "desc";
                } else {
                    self.orderBy = "asc";
                }
            };

            self.sortBy = function(type) {
                if(self.sortType !== type) {
                    self.reverse = true;
                } else {
                    self.reverse = !self.reverse; //if true make it false and vice versa
                }
                self.sortType = type;
                self.order();
                self.getAll();
            };

            self.deleteExperiment = function (id) {
                startLoading("Deleting Experiment...");
                ExperimentService.deleteExperiment(self.projectId, id).then(
                    function(success) {
                          stopLoading();
                          for(var i = 0; self.experiments.length > i; i++) {
                            if(self.experiments[i].id === id) {
                               self.experiments.splice(i, 1);
                               return;
                            }
                          }
                    },
                    function(error) {
                        stopLoading();
                        if (typeof error.data.usrMsg !== 'undefined') {
                            growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 8000});
                        } else {
                            growl.error("", {title: error.data.errorMsg, ttl: 8000});
                        }
                    });

            };

            self.viewExperiment = function (experiment) {
                ModalService.viewExperimentInfo('lg', self.projectId, experiment).then(
                function (success) {
                    self.getAll();
                }, function (error) {
                    self.getAll();
                });
            };

            self.buildQuery = function() {
                var offset = self.pageSize * (self.currentPage - 1);
                self.query = "";
                if(self.experimentsFilter !== "") {
                    self.query = '?filter_by=name:' + self.experimentsFilter + "&filter_by=date_start_lt:" + self.experimentsToDate.toISOString().replace('Z','')
                        + "&filter_by=date_start_gt:" + self.experimentsFromDate.toISOString().replace('Z','');
                } else {
                    self.query = '?filter_by=date_start_lt:' + self.experimentsToDate.toISOString().replace('Z','')
                        + "&filter_by=date_start_gt:" + self.experimentsFromDate.toISOString().replace('Z','');
                }
                if(self.memberSelected.name !== 'All Members') {
                    self.query = self.query + '&filter_by=user:' + self.memberSelected.uid;
                }
                self.query = self.query + '&sort_by=' + self.sortType + ':' + self.orderBy + '&offset=' + offset + '&limit=' + self.pageSize;
            };

            self.getAll = function() {
                self.buildQuery();
                startLoading("Fetching Experiments...");
                ExperimentService.getAll(self.projectId, self.query).then(
                    function(success) {
                        stopLoading();
                        self.experiments = success.data.items;
                        self.totalItems = success.data.count;
                    },
                    function(error) {
                        stopLoading();
                        if (typeof error.data.usrMsg !== 'undefined') {
                            growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 8000});
                        } else {
                            growl.error("", {title: error.data.errorMsg, ttl: 8000});
                        }
                    });
            };

            /**
             * Helper function for redirecting to another project page
             *
             * @param serviceName project page
             */
            self.goToExperiment = function (experiment_id) {
                $location.path('project/' + self.projectId + '/datasets/Experiments/' + experiment_id);
            };

            self.goToModel = function (model) {
                var modelSplit = model.split('_')
                $location.path('project/' + self.projectId + '/datasets/Models/' + modelSplit[0] + '/' + modelSplit[1]);
            };

            self.init = function () {
              UserService.profile().then(
                function (success) {
                  self.userEmail = success.data.email;
                  self.getMembers();
                },
                function (error) {
                    if (typeof error.data.usrMsg !== 'undefined') {
                        growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 8000});
                    } else {
                        growl.error("", {title: error.data.errorMsg, ttl: 8000});
                    }
                });
            };

            self.getMembers = function () {
              MembersService.query({id: self.projectId}).$promise.then(
                function (success) {
                  self.members = success;
                  if(self.members.length > 0) {
                    //Get current user team role
                    self.members.forEach(function (member) {
                        if(member.user.email !== 'serving@hopsworks.se') {
                            self.membersList.push({'name': member.user.fname + ' ' + member.user.lname, 'uid': member.user.uid, 'email': member.user.email});
                        }
                    });

                    if(self.membersList.length > 1) {
                        self.membersList.push({'name': 'All Members'})
                    }

                    for(var i = 0; i < self.membersList.length; i++) {
                        if(self.membersList[i].email === self.userEmail) {
                            self.memberSelected = self.membersList[i];
                            break;
                        }
                    }
                  }
                  self.getAll();
                },
                function (error) {
                    if (typeof error.data.usrMsg !== 'undefined') {
                        growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 8000});
                    } else {
                        growl.error("", {title: error.data.errorMsg, ttl: 8000});
                    }
                });
            };

            self.init();

            self.getNewPage = function() {
                self.offset = self.pageSize * (self.currentPage - 1);
                self.getAll();
            }
        }
    ]);