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
 * Controller for the Models service
 */
angular.module('hopsWorksApp')
    .controller('ModelCtrl', ['$scope', '$timeout', 'growl', '$location', 'MembersService', 'UserService', 'ModalService', 'ModelService', 'ProjectService', '$interval',
        '$routeParams', '$route', '$sce', '$window',
        function($scope, $timeout, growl, $location, MembersService, UserService, ModalService, ModelService, ProjectService, $interval,
            $routeParams, $route, $sce, $window) {

            var self = this;

            self.pageSize = 12;
            self.currentPage = 1;
            self.totalItems = 0;

            self.sortType = 'start';
            self.orderBy = 'desc';
            self.reverse = true;

            self.inModalView = false;

            self.projectId = $routeParams.projectID;

            self.memberSelected = {};

            self.models = []

            self.loading = false;
            self.loadingText = "";

            self.modelsFilter = "";

            self.query = "";

            self.membersList = [];
            self.members = [];
            self.userEmail = "";

            self.updating = false;

            self.modelsToDate = new Date();
            self.modelsToDate.setMinutes(self.modelsToDate.getMinutes() + 60*24);
            self.modelsFromDate = new Date();
            self.modelsFromDate.setMinutes(self.modelsFromDate.getMinutes() - 60*24*30);

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

            $scope.sortBy = function(sortType) {
                $scope.reverse = ($scope.sortType === sortType) ? !$scope.reverse : false;
                $scope.sortType = sortType;
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

            self.deleteModel = function (id) {
                startLoading("Deleting Model...");
                ModelService.deleteModel(self.projectId, id).then(
                    function(success) {
                          stopLoading();
                          for(var i = 0; self.models.length > i; i++) {
                            if(self.models[i].id === id) {
                               self.models.splice(i, 1);
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

            self.viewModel = function (model) {
                self.inModalView = true;
                ModalService.viewExperimentInfo('lg', self.projectId, model).then(
                function (success) {
                    self.inModalView = false;
                    self.getAll();
                }, function (error) {
                    self.inModalView = false;
                    self.getAll();
                });
            };

            self.viewMonitor = function (experiment) {
              if(experiment.jobName) {
                $location.path('project/' + self.projectId + '/jobMonitor-job/' + experiment.jobName);
              } else {
                $location.path('project/' + self.projectId + '/jobMonitor-app/' + experiment.appId + '/true/jupyter');
              }
            };

            self.buildQuery = function() {
                var offset = self.pageSize * (self.currentPage - 1);
                self.query = "";
                if(self.modelsFilter !== "") {
                    self.query = '?filter_by=name:' + self.modelsFilter + "&filter_by=date_start_lt:" + self.modelsToDate.toISOString().replace('Z','')
                        + "&filter_by=date_start_gt:" + self.modelsFromDate.toISOString().replace('Z','');
                } else {
                    self.query = '?filter_by=date_start_lt:' + self.modelsToDate.toISOString().replace('Z','')
                        + "&filter_by=date_start_gt:" + self.modelsFromDate.toISOString().replace('Z','');
                }
                if(self.memberSelected.name !== 'All Members') {
                    self.query = self.query + '&filter_by=user:' + self.memberSelected.uid;
                }
                self.query = self.query + '&sort_by=' + self.sortType + ':' + self.orderBy + '&offset=' + offset + '&limit=' + self.pageSize;
            };

            self.getAll = function(loadingText) {
                if(loadingText) {
                    startLoading(loadingText)
                }
                self.buildQuery();
                self.updating = true;
                ModelService.getAll(self.projectId, self.query).then(
                    function(success) {
                        if(loadingText) {
                            stopLoading();
                        }
                        self.updating = false;
                        self.models = [];
                        self.groupModelsByName(success.data.items);
                        self.totalItems = success.data.count;
                    },
                    function(error) {
                        if(loadingText) {
                            stopLoading();
                        }
                        self.updating = false;
                        if (typeof error.data.usrMsg !== 'undefined') {
                            growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 8000});
                        } else {
                            growl.error("", {title: error.data.errorMsg, ttl: 8000});
                        }
                    });
            };

            self.groupModelsByName = function(models) {
                for(var i = 0; i < models.length; i++) {
                    if(self.isModelInList(models[i].name)) {
                        self.addToList(models[i].name, models[i]);

                    } else {
                        self.models.push({'name': models[i].name, 'versions': [models[i]]});
                    }
                }
            };

            self.isModelInList = function(modelName) {
                for(var i = 0; i < self.models.length; i++) {
                    if(self.models[i].name === modelName) {
                    return true;
                    }
                }
                return false;
            };

            self.addToList = function(modelName, model) {
                for(var i = 0; i < self.models.length; i++) {
                    if(self.models[i].name === modelName) {
                    self.models[i].versions.push(model);
                }
                }
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


                    self.membersList.push({'name': 'All Members'})

                    for(var i = 0; i < self.membersList.length; i++) {
                        if(self.membersList[i].email === self.userEmail) {
                            self.memberSelected = self.membersList[i];
                            break;
                        }
                    }
                  }
                  self.getAll('Loading Models...');
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
                self.getAll();
            };
        }
    ]);