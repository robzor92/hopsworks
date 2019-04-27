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
describe "On #{ENV['OS']}" do
  experiment_1 = "experiment_1"
  experiment_2 = "experiment_2"
  experiment_3 = "experiment_3"
  experiment_4 = "experiment_4"
  describe 'experiment' do
    context 'without authentication' do
      before :all do
        with_valid_project
        reset_session
      end
      it "should fail" do
        get_experiment(@project[:id], "app_id_4252123_1", nil)
        expect_json(errorCode: 200003)
        expect_status(401)
      end
    end
    context 'with authentication create, delete, get' do
      before :all do
        with_valid_tour_project("deep_learning")
      end
      after :all do
        clean_jobs(@project[:id])
      end
      it "should not find any experiments" do
        get_experiments(@project[:id], nil)
        expect_status(200)
        expect(json_body[:items]).to be nil
        expect(json_body[:count]).to eq 0
        expect(URI(json_body[:href]).path).to eq "#{ENV['HOPSWORKS_API']}/project/#{@project[:id]}/experiments"
      end
      it "should not find specific experiment" do
        get_experiment(@project[:id], "app_id_4254316623_1", nil)
        expect_status(404)
      end
      it "should run three experiments and check items, count and href" do
        create_experiment_job(@project, experiment_1)
        run_experiment_blocking(experiment_1)
        get_experiments(@project[:id], nil)
        expect_status(200)
        expect(json_body[:items].count).to eq 1
        expect(json_body[:count]).to eq 1

        create_experiment_job(@project, experiment_2)
        run_experiment_blocking(experiment_2)
        get_experiments(@project[:id], nil)
        expect_status(200)
        expect(json_body[:items].count).to eq 2
        expect(json_body[:count]).to eq 2

        create_experiment_job(@project, experiment_3)
        run_experiment_blocking(experiment_3)
        get_experiments(@project[:id], nil)
        expect_status(200)
        expect(json_body[:items].count).to eq 3
        expect(json_body[:count]).to eq 3

        json_body[:items].each {|experiment| expect(URI(experiment[:href]).path).to eq "#{ENV['HOPSWORKS_API']}/project/#{@project[:id]}/experiments/#{experiment[:id]}"}
      end
      it "should get all existing experiments" do
        get_experiments(@project[:id], nil)
        json_body[:items].each {|experiment|
        get_experiment(@project[:id], experiment[:id], nil)
        expect_status(200)
        }
      end
      it "should fail deleting non-existing experiment" do
        delete_experiment(@project[:id], 'app_id_incognito123')
        expect_status(404)
      end
      it "should delete single experiment using experiment API" do
        get_experiments(@project[:id], nil)
        ml_id = json_body[:items][0][:id]
        delete_experiment(@project[:id], ml_id)
        expect_status(204)
        wait_for_experiment do
          get_experiments(@project[:id], nil)
          json_body[:count].eql? 2
        end
      end
      it "should delete single experiment by deleting ml_id folder" do
        get_experiments(@project[:id], nil)
        ml_id = json_body[:items][0][:id]
        delete "#{ENV['HOPSWORKS_API']}/project/#{@project[:id]}/dataset/file/Experiments/#{ml_id}"
        expect_status(200)
        wait_for_experiment do
          get_experiments(@project[:id], nil)
          json_body[:count].eql? 1
        end
      end
      it "should delete experiment by deleting Experiments dataset" do
        delete "#{ENV['HOPSWORKS_API']}/project/#{@project[:id]}/dataset/Experiments"
        expect_status(200)
        wait_for_experiment do
          get_experiments(@project[:id], nil)
          json_body[:count].eql? 0
        end
      end
    end
  end
  describe 'experiments sort, filter, offset and limit' do
    context 'with authentication' do
      before :all do
        with_valid_tour_project("deep_learning")
        create_experiment_job(@project, experiment_1)
        run_experiment_blocking(experiment_1)

        create_experiment_job(@project, experiment_2)
        run_experiment_blocking(experiment_2)

        create_experiment_job(@project, experiment_3)
        run_experiment_blocking(experiment_3)
      end
      after :all do
        clean_jobs(@project[:id])
      end
      describe "Experiment sort" do
        it "should get all experiments sorted by name ascending" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted)
        end
        it "should get all experiments sorted by name descending" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?sort_by=name:desc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted)
        end
        it "should get all experiments sorted by date created ascending" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:start]}
          sorted = experiments.sort
          get_experiments(@project[:id], "?sort_by=start:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:start]}
          expect(sorted_res).to eq(sorted)
        end
        it "should get all experiments sorted by date created descending" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:start]}
          sorted = experiments.sort.reverse
          get_experiments(@project[:id], "?sort_by=start:desc")
          sorted_res = json_body[:items].map {|experiment| experiment[:start]}
          expect(sorted_res).to eq(sorted)
        end
        it "should get all experiments sorted by date created ascending" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:end]}
          sorted = experiments.sort
          get_experiments(@project[:id], "?sort_by=end:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:end]}
          expect(sorted_res).to eq(sorted)
        end
        it "should get all experiments sorted by date created descending" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:end]}
          sorted = experiments.sort.reverse
          get_experiments(@project[:id], "?sort_by=end:desc")
          sorted_res = json_body[:items].map {|experiment| experiment[:end]}
          expect(sorted_res).to eq(sorted)
        end
      end
      describe "Experiments limit, offset, sort" do
        it "should return limit=x experiments" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?limit=2&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted.take(2))
        end
        it "should return experiments starting from offset=y" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?offset=1&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted.drop(1))
        end
        it "should return limit=x jobs with offset=y" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?offset=1&limit=2&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted.drop(1).take(2))
        end
        it "should ignore if limit < 0" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?limit<0&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted)
        end
        it "should ignore if offset < 0" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?offset<0&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted)
        end
        it "should ignore if limit = 0" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?limit=0&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted)
        end
        it "should ignore if offset = 0" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?offset=0&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted)
        end
        it "should ignore if limit = 0 and offset = 0" do
          #sort in memory and compare with query
          get_experiments(@project[:id], nil)
          experiments = json_body[:items].map {|experiment| experiment[:name]}
          sorted = experiments.sort_by(&:downcase)
          get_experiments(@project[:id], "?limit=0&offset=0&sort_by=name:asc")
          sorted_res = json_body[:items].map {|experiment| experiment[:name]}
          expect(sorted_res).to eq(sorted)
        end
      end
      describe "Experiments filter" do
        it "should get 3 experiments with name like experiment" do
          get_experiments(@project[:id], "?filter_by=name:experiment")
          expect_status(200)
          expect(json_body[:items].count).to eq 3
        end
        it "should get 1 experiment with name like experiment_1" do
          get_experiments(@project[:id], "?filter_by=name:experiment_1")
          expect_status(200)
          expect(json_body[:items].count).to eq 1
        end
        it "should get 0 experiment with name like experiment_4" do
          get_experiments(@project[:id], "?filter_by=name:experiment_4")
          expect_status(200)
          expect(json_body[:items].count).to be nil
        end
        it "should get 3 experiments with state FINISHED experiment" do
          get_experiments(@project[:id], "?filter_by=state:FINISHED")
          expect_status(200)
          expect(json_body[:items].count).to eq 3
        end
        it "should get 0 experiments with state FAILED experiment" do
          get_experiments(@project[:id], "?filter_by=state:FAILED")
          expect_status(200)
          expect(json_body[:items].count).to be nil
        end
        it "should get 0 experiments with state RUNNING experiment" do
          get_experiments(@project[:id], "?filter_by=state:RUNNING")
          expect_status(200)
          expect(json_body[:items].count).to be nil
        end
        it "should get 3 experiments with state FINISHED and name like experiment_ experiment" do
          get_experiments(@project[:id], "?filter_by=state:FINISHED&filter_by=name:experiment_")
          expect_status(200)
          expect(json_body[:items].count).to eq 3
        end
      end
    end
  end
end