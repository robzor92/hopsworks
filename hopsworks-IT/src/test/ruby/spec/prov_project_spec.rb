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

require 'pp'

describe "On #{ENV['OS']}" do
  before :all do
    $stdout.sync = true
    with_valid_session
    @project1_name = "prov_proj_#{short_random_id}"
    @project2_name = "prov_proj_#{short_random_id}"
    @app1_id = "application_#{short_random_id}_0001"
    @app2_id = "application_#{short_random_id}_0001"
    @app3_id = "application_#{short_random_id}_0001"
    @experiment_app1_name1 = "#{@app1_id}_1"
    @experiment_app2_name1 = "#{@app2_id}_1"
    @experiment_app3_name1 = "#{@app3_id}_1"
    @experiment_app1_name2 = "#{@app1_id}_2"
    @not_experiment_name = "not_experiment"
    @model1_name = "model_a"
    @model2_name = "model_b"
    @model_version1 = "1"
    @model_version2 = "2"
    pp "create project: #{@project1_name}"
    @project1 = create_project_by_name(@project1_name)
    pp "create project: #{@project2_name}"
    @project2 = create_project_by_name(@project2_name)
  end

  after :all do 
    pp "delete projects"
    delete_project(@project1)
    delete_project(@project2)
  end
  
  describe 'provenance tests - experiments' do
    describe 'simple experiments' do
      it "create experiments" do
        prov_create_experiment(@project1, @experiment_app1_name1)
        prov_create_experiment(@project1, @experiment_app2_name1)
        prov_create_experiment(@project2, @experiment_app3_name1)
      end

      it "check experiments" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", false)
        expect(result1.length).to eq 2
        prov_check_experiment1(result1, prov_experiment_id(@experiment_app1_name1))
        prov_check_experiment1(result1, prov_experiment_id(@experiment_app2_name1))

        result2 = get_ml_asset_in_project(@project2, "EXPERIMENT", false)
        expect(result2.length).to eq 1
        prov_check_experiment1(result2, prov_experiment_id(@experiment_app3_name1))
        
        result3 = get_ml_asset_by_id(@project1, "EXPERIMENT", prov_experiment_id(@experiment_app1_name1), false, 200)
      end

      it "delete experiments" do
        prov_delete_experiment(@project1, @experiment_app1_name1)
        prov_delete_experiment(@project1, @experiment_app2_name1)
        prov_delete_experiment(@project2, @experiment_app3_name1)
      end
      
      it "check experiments" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", false)
        expect(result1.length).to eq 0

        result2 = get_ml_asset_in_project(@project2, "EXPERIMENT", false)
        expect(result2.length).to eq 0
        
        result3 = get_ml_asset_by_id(@project1, "EXPERIMENT", prov_experiment_id(@experiment_app1_name1), false, 404)
      end
    end

    describe 'experiment with xattr' do
      it "stop epipe" do
        execute_remotely @hostname, "sudo systemctl stop epipe"
      end

      it "create experiment with xattr" do
        prov_create_experiment(@project1, @experiment_app1_name1)
        experimentRecord = FileProv.where("project_name": @project1["inode_name"], "i_name": @experiment_app1_name1)
        expect(experimentRecord.length).to eq 1
        prov_add_xattr(experimentRecord[0], "xattr_key_1", "xattr_value_1", "XATTR_ADD", 1)
        prov_add_xattr(experimentRecord[0], "xattr_key_2", "xattr_value_2", "XATTR_ADD", 2)
      end

      it "restart epipe" do
        execute_remotely @hostname, "sudo systemctl restart epipe"
      end

      it "check experiment" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", false)
        expect(result1.length).to eq 1
        xattrs = Hash.new
        xattrs["xattr_key_1"] = "xattr_value_1"
        xattrs["xattr_key_2"] = "xattr_value_2"
        prov_check_experiment2(result1, prov_experiment_id(@experiment_app1_name1), xattrs)
      end

      it "delete experiments" do
        prov_delete_experiment(@project1, @experiment_app1_name1)
      end

      it "check experiments" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", false)
        expect(result1.length).to eq 0
      end
    end

    describe 'experiment with xattr add, update and delete' do
      it "stop epipe" do
        execute_remotely @hostname, "sudo systemctl stop epipe"
      end

      it "create experiment with xattr" do
        prov_create_experiment(@project1, @experiment_app1_name1)
        experimentRecord = FileProv.where("project_name": @project1["inode_name"], "i_name": @experiment_app1_name1)
        expect(experimentRecord.length).to eq 1
        prov_add_xattr(experimentRecord[0], "xattr_key_1", "xattr_value_1", "XATTR_ADD", 1)
        prov_add_xattr(experimentRecord[0], "xattr_key_2", "xattr_value_2", "XATTR_ADD", 2)
        prov_add_xattr(experimentRecord[0], "xattr_key_3", "xattr_value_3", "XATTR_ADD", 3)
        prov_add_xattr(experimentRecord[0], "xattr_key_1", "xattr_value_1_updated", "XATTR_UPDATE", 4)
        prov_add_xattr(experimentRecord[0], "xattr_key_2", "", "XATTR_DELETE", 5)
      end

      it "restart epipe" do
        execute_remotely @hostname, "sudo systemctl restart epipe"
      end

      it "check experiment" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", false)
        expect(result1.length).to eq 1
        xattrs = Hash.new
        xattrs["xattr_key_1"] = "xattr_value_1_updated"
        xattrs["xattr_key_3"] = "xattr_value_3"
        prov_check_experiment2(result1, prov_experiment_id(@experiment_app1_name1), xattrs)
      end

      it "delete experiments" do
        prov_delete_experiment(@project1, @experiment_app1_name1)
      end

      it "check experiments" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", false)
        expect(result1.length).to eq 0
      end
    end

    describe 'experiment with app states' do
      it "stop epipe" do
        execute_remotely @hostname, "sudo systemctl stop epipe"
      end

      it "create experiment with app states" do
        prov_create_experiment(@project1, @experiment_app1_name1)
        prov_create_experiment(@project1, @experiment_app1_name2)
        prov_create_experiment(@project1, @experiment_app2_name1)
        experiment_record = FileProv.where("project_name": @project1["inode_name"], "i_name": @experiment_app1_name1)
        user_name = experiment_record[0]["io_user_name"]
        expect(experiment_record.length).to eq 1
        prov_add_app_states1(@app1_id, user_name)
        prov_add_app_states2(@app2_id, user_name)
      end

      it "restart epipe" do
        execute_remotely @hostname, "sudo systemctl restart epipe"
      end

      it "check experiment" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", true)
        expect(result1.length).to eq 3
        prov_check_experiment3(result1, prov_experiment_id(@experiment_app1_name1), "RUNNING")
        prov_check_experiment3(result1, prov_experiment_id(@experiment_app1_name2), "RUNNING")
        prov_check_experiment3(result1, prov_experiment_id(@experiment_app2_name1), "FINISHED")
      end

      it "delete experiments" do
        prov_delete_experiment(@project1, @experiment_app1_name1)
        prov_delete_experiment(@project1, @experiment_app1_name2)
        prov_delete_experiment(@project1, @experiment_app2_name1)
      end

      it "check experiments" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "EXPERIMENT", true)
        expect(result1.length).to eq 0
      end
    end

    describe 'not experiment in Experiments' do
      it "create not experiment dir" do
        prov_create_experiment(@project1, @not_experiment_name)
      end
      it "check not experiment" do 
        prov_wait_for_epipe() 
        result = get_ml_asset_in_project(@project1, "EXPERIMENT", false)
        expect(result.length).to eq 0
      end

      it "delete not experiment" do
        prov_delete_experiment(@project1, @not_experiment_name)
      end
    end
  end

  describe 'provenance tests - models' do
    
    describe 'simple models' do
      it "create models" do
        prov_create_model(@project1, @model1_name)
        prov_create_model_version(@project1, @model1_name, @model_version1)
        prov_create_model_version(@project1, @model1_name, @model_version2)
        prov_create_model(@project1, @model2_name)
        prov_create_model_version(@project1, @model2_name, @model_version1)
        prov_create_model(@project2, @model1_name)
        prov_create_model_version(@project2, @model1_name, @model_version1)
      end

      it "check models" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "MODEL", false)
        expect(result1.length).to eq 3
        prov_check_model(result1, prov_model_id(@model1_name, @model_version1), Hash.new)
        prov_check_model(result1, prov_model_id(@model1_name, @model_version2), Hash.new)
        prov_check_model(result1, prov_model_id(@model2_name, @model_version1), Hash.new)

        result2 = get_ml_asset_in_project(@project2, "MODEL", false)
        expect(result2.length).to eq 1
        prov_check_model(result2, prov_model_id(@model1_name, @model_version1), Hash.new)
        
        result3 = get_ml_asset_by_id(@project1, "MODEL", prov_model_id(@model1_name, @model_version2), false, 200)
      end

      it "delete models" do
        prov_delete_model(@project1, @model1_name)
        prov_delete_model(@project1, @model2_name)
        prov_delete_model(@project2, @model1_name)
      end
      
      it "check models" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "MODEL", false)
        expect(result1.length).to eq 0

        result2 = get_ml_asset_in_project(@project2, "MODEL", false)
        expect(result2.length).to eq 0
        
        result3 = get_ml_asset_by_id(@project1, "MODEL", prov_model_id(@model1_name, @model_version2), false, 404)
      end
    end
    describe 'model with xattr' do
      it "stop epipe" do
        execute_remotely @hostname, "sudo systemctl stop epipe"
      end

      it "create model with xattr" do
        prov_create_model(@project1, @model1_name)
        prov_create_model_version(@project1, @model1_name, @model_version1)
        modelRecord = FileProv.where("project_name": @project1["inode_name"], "i_parent_name": @model1_name, "i_name": @model_version1)
        expect(modelRecord.length).to eq 1
        prov_add_xattr(modelRecord[0], "xattr_key_1", "xattr_value_1", "XATTR_ADD", 1)
        prov_add_xattr(modelRecord[0], "xattr_key_2", "xattr_value_2", "XATTR_ADD", 2)
      end

      it "restart epipe" do
        execute_remotely @hostname, "sudo systemctl restart epipe"
      end

      it "check model" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "MODEL", false)
        expect(result1.length).to eq 1
        xattrs = Hash.new
        xattrs["xattr_key_1"] = "xattr_value_1"
        xattrs["xattr_key_2"] = "xattr_value_2"
        prov_check_model(result1, prov_model_id(@model1_name, @model_version1), xattrs)
      end

      it "delete model" do
        prov_delete_model(@project1, @model1_name)
      end

      it "check models" do 
        prov_wait_for_epipe() 
        result1 = get_ml_asset_in_project(@project1, "MODEL", false)
        expect(result1.length).to eq 0
      end
    end
  end
end