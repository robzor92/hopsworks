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

            self.sortType = 'name';
            self.orderBy = 'desc';
            self.reverse = true;

            $scope.modelSortType = {};
            $scope.modelReverse = {};

            self.inModalView = false;

            self.projectId = $routeParams.projectID;

            self.memberSelected = {};

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

            self.parameterHeaders = {};
            self.metricHeaders = {};
            self.allHeaders = {};
            self.modelData = {};
            self.models = [];
            self.modelDataMaxValue = {};
            self.selectedModel = {};
            self.modelCounter = {};

            self.mostRecent = {};

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

            $scope.sortBy = function(sortType, modelName) {
                $scope.modelReverse[modelName] = ($scope.modelSortType[modelName] === sortType) ? !$scope.modelReverse[modelName] : false;
                $scope.modelSortType[modelName] = sortType;
            };

            self.sortBy = function(type) {
                if(self.sortType !== type) {
                    self.reverse = true;
                } else {
                    self.reverse = !self.reverse; //if true make it false and vice versa
                }
                self.sortType = type;
                self.order();
            };

            self.buildQuery = function() {
                self.query = "?";
                if(self.modelsFilter !== "") {
                    self.query = self.query + 'filter_by=name_like:' + self.modelsFilter;
                }
            };

            self.getAll = function(loadingText) {
                if(loadingText) {
                    startLoading(loadingText);
                }
                self.buildQuery();
                self.updating = true;
                ModelService.getAll(self.projectId, self.query).then(
                    function(success) {
                        if(loadingText) {
                            stopLoading();
                        }
                        self.updating = false;
                        self.parameterHeaders = {};
                        self.metricHeaders = {};
                        self.allHeaders = {};
                        self.modelData = {};
                        self.models = [];
                        self.modelDataMaxValue = {};
                        self.mostRecent = {};
                        self.selectedModel = {};
                        self.groupModelsByName(success.data.items);
                        self.initSortType();
                        self.initTable();
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

            self.initSortType = function() {
                for(var i = 0; i < self.models.length; i++) {
                    $scope.modelSortType[self.models[i].name] = 'Created';
                    $scope.modelReverse[self.models[i].name] = true;
                }
            };

            self.groupModelsByName = function(models) {
                for(var i = 0; i < models.length; i++) {
                    self.selectedModel[models[i].name] = false;
                    if(self.isModelInList(models[i].name)) {
                        self.addToList(models[i].name, models[i]);
                    } else {
                        self.models.push({'name': models[i].name, 'versions': [models[i]]});
                    }
                }

                for(var i = 0; i < self.models.length; i++) {
                    self.modelCounter[self.models[i].name] = self.models[i].versions.length;
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

            self.getWidth = function(modelName, index, cell) {
                if(cell==="") {
                    return 0;
                } else {
                    return (cell/self.modelDataMaxValue[modelName][self.allHeaders[modelName][index]])*100;
                }
            };

            self.initTable = function() {
                for(var i = 0; i < self.models.length; i++) {
                    var model = self.models[i];
                    var modelName = model.name;
                    var parameters_set = new Set();
                    var metrics_set = new Set();
                    for(var x = 0; x < model.versions.length; x++) {
                        if(model.versions[x].parameters) {
                            var parameters = model.versions[x].parameters;
                            for(var y = 0; y < parameters.length; y++) {
                                parameters_set.add(Object.keys(parameters[y])[0]);
                            }
                        }
                        if(model.versions[x].metrics) {
                            var metrics = model.versions[x].metrics;
                            for(var z = 0; z < metrics.length; z++) {
                                metrics_set.add(Object.keys(metrics[z])[0]);
                            }
                        }
                    }
                    self.parameterHeaders[modelName] = Array.from(parameters_set);
                    self.metricHeaders[modelName] = Array.from(metrics_set);
                    self.allHeaders[modelName] = ['Version', 'Created', 'User'];
                    self.allHeaders[modelName] = self.allHeaders[modelName].concat(Array.from(parameters_set).concat(Array.from(metrics_set)));
                }

                for(var z = 0; z < self.models.length; z++) {
                    var versions = self.models[z].versions;

                    for(var y = 0; y < versions.length; y++) {
                        var modelName = versions[y].name

                        // Put model version
                        var tmp = {};
                        tmp['Version'] = Number(versions[y].version);
                        tmp['Created'] = versions[y].created;
                        tmp['User'] = versions[y].userFullName;

                        if(versions[y].parameters) {
                            for (var i = 0; i < self.parameterHeaders[modelName].length; i++) {
                              var parameters = versions[y].parameters;
                              var found = false;
                              for (var x = 0; x < versions[y].parameters.length; x++) {
                                var key = Object.keys(versions[y].parameters[x])[0];
                               if(self.parameterHeaders[modelName][i] === key) {
                                found = true;
                                var value = versions[y].parameters[x][key];
                                tmp[key] = value;
                                self.processMaxValue(versions[y].name, key, value)
                               }
                              }
                              if(!found) {
                                tmp[self.parameterHeaders[modelName][i]] = "";
                              }
                            }
                        } else {
                            for (var i = 0; i < self.parameterHeaders[modelName].length; i++) {
                                tmp[self.parameterHeaders[modelName][i]] = "";
                            }
                        }

                        if(versions[y].metrics) {
                            for (var i = 0; i < self.metricHeaders[modelName].length; i++) {
                              var metrics = versions[y].metrics;
                              var found = false;
                              for (var x = 0; x < versions[y].metrics.length; x++) {
                                var key = Object.keys(versions[y].metrics[x])[0];
                               if(self.metricHeaders[modelName][i] === key) {
                                found = true;
                                var value = versions[y].metrics[x][key];
                                tmp[key] = value;
                                self.processMaxValue(versions[y].name, key, value)
                               }
                              }
                              if(!found) {
                                tmp[self.metricHeaders[modelName][i]] = "";
                              }
                            }
                        } else {
                            for (var i = 0; i < self.metricHeaders[modelName].length; i++) {
                                tmp[self.metricHeaders[modelName][i]] = "";
                            }
                        }

                        self.processMostRecent(versions[y]);

                        if (modelName in self.modelData) {
                            self.modelData[modelName].push(tmp);
                        } else {
                            self.modelData[versions[y].name] = [tmp];
                        }
                    }
                }
            };

            self.processMaxValue = function(model_name, key, value) {
                if(!self.modelDataMaxValue[model_name]) {
                    self.modelDataMaxValue[model_name] = {};
                    self.modelDataMaxValue[model_name][key] = value;
                } else if(!self.modelDataMaxValue[model_name][key]) {
                    self.modelDataMaxValue[model_name][key] = value;
                } else if(value > self.modelDataMaxValue[model_name][key]) {
                    self.modelDataMaxValue[model_name][key] = value;
                }
            };

            self.processMostRecent = function(version) {
                version.created = new Date(version.created);
                if(!self.mostRecent[version.name]) {
                    self.mostRecent[version.name] = version;
                } else {
                    var currentMostRecent = new Date(self.mostRecent[version.name].created);
                    var tmpMostRecent = new Date(version.created);
                    if(currentMostRecent.getTime() < tmpMostRecent.getTime()) {
                        self.mostRecent[version.name] = version;
                    }
                }
            }

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

                    self.membersList.push({'name': 'All Members'});
                    self.memberSelected = {'name': 'All Members'};
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