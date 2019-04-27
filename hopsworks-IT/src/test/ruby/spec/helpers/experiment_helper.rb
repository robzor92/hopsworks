=begin
 This file is part of Hopsworks
 Copyright (C) 2018, Logical Clocks AB. All rights reserved

 Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 the GNU Affero General Public License as published by the Free Software Foundation,
 either version 3 of the License, or (at your option) any later version.

 Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 PURPOSE.  See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/>.
=end
module ExperimentHelper

  def create_experiment_job(project, job_name)

    # need to enable python for conversion .ipynb to .py works
    get "#{ENV['HOPSWORKS_API']}/project/#{project[:id]}/python/environments"
    if response.code == 404
       post "#{ENV['HOPSWORKS_API']}/project/#{project[:id]}/python/environments/3.6?action=create&pythonKernelEnable=true"
       expect_status(201)
    end

    job_conf = {
      "type":"sparkJobConfiguration",
      "appName":"#{job_name}",
      "amQueue":"default",
      "amMemory":1024,
      "amVCores":1,
      "jobType":"PYSPARK",
      "appPath":"hdfs:///Projects/#{project[:projectname]}/Jupyter/Experiment/TensorFlow/minimal_mnist_classifier_on_hops.ipynb",
      "mainClass":"org.apache.spark.deploy.PythonRunner",
      "spark.executor.instances":1,
      "spark.executor.cores":1,
      "spark.executor.memory":1500,
      "spark.executor.gpus":0,
      "spark.dynamicAllocation.enabled": true,
      "spark.dynamicAllocation.minExecutors":1,
      "spark.dynamicAllocation.maxExecutors":1,
      "spark.dynamicAllocation.initialExecutors":1
    }

    put "#{ENV['HOPSWORKS_API']}/project/#{project[:id]}/jobs/#{job_name}", job_conf
    expect_status(201)
  end

  def wait_for_experiment(timeout=480)
    start = Time.now
    x = yield
    until x
      if Time.now - start > timeout
        raise "Timed out waiting for Job to finish. Timeout #{timeout} sec"
      end
      sleep(5)
      x = yield
    end
  end

  def run_experiment_blocking(job_name)
    start_execution(@project[:id], job_name)
    execution_id = json_body[:id]
    expect_status(201)
    wait_for_execution do
      get_execution(@project[:id], job_name, execution_id)
      (json_body[:state].eql? "FINISHED") && (FileProv.where("project_name": @project["inode_name"]).empty?)
    end
  end

  def get_experiments(project_id, query)
    get "#{ENV['HOPSWORKS_API']}/project/#{project_id}/experiments#{query}"
  end

  def get_experiment(project_id, ml_id, query)
    get "#{ENV['HOPSWORKS_API']}/project/#{project_id}/experiments/#{ml_id}/#{query}"
  end

  def delete_experiment(project_id, ml_id)
    delete "#{ENV['HOPSWORKS_API']}/project/#{project_id}/experiments/#{ml_id}"
  end
end
