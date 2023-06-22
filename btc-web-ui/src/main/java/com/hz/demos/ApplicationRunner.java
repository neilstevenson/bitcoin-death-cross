/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hz.demos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.core.JobStatus;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hz.demos.moving.average.MovingAverage;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>Launches jobs and then mainly does nothing as it's the Web UI.
 * We launch them here for timing reasons, so they start at same time
 * as Web UI, if started too far before much of the data change won't
 * be seen.
 * </p>
 */
@Configuration
@Slf4j
public class ApplicationRunner {

    @Autowired
    private MyWebSocketBridgeListener myWebSocketBridgeListener;
	@Autowired
	private HazelcastInstance hazelcastInstance;
	private List<String> loggedJobNames = new ArrayList<>();

	@Bean
	CommandLineRunner commandLineRunner() {
		return args -> {
			log.info("---");

			// myWebSocketBridgeListener listeners for map/topic events to send to React
			this.hazelcastInstance
				.getMap(MyConstants.IMAP_PRICE_AVERAGE)
				.addEntryListener(this.myWebSocketBridgeListener, true);
			this.hazelcastInstance
				.getReliableTopic(MyConstants.RELIABLE_TOPIC_ALERTS)
				.addMessageListener(this.myWebSocketBridgeListener);

			this.launchMovingAverage();
			log.info("---");
			while(true) {
				TimeUnit.SECONDS.sleep(15L);
				this.logAnyJobIssues();
			}
		};
	}
	
	/**
	 * <p>Calculates the moving average and saves to maps
	 * </p>
	 */
	private void launchMovingAverage() {
		Pipeline pipeline = MovingAverage.build();
		JobConfig jobConfig = new JobConfig();
		jobConfig.setName(MovingAverage.class.getSimpleName()
				+ System.currentTimeMillis()//FIXME temp name so can resubmit for faster testing, but duplicate alerts
				);
		jobConfig.addPackage(MovingAverage.class.getPackageName());
		jobConfig.setMetricsEnabled(true);
		
		this.checkedSubmit(pipeline, jobConfig);
	}

	/**
	 * <p>Check all jobs for problems. May have failed if badly coded
	 * or cancelled from Management Center. Only log once.
	 * </p>
	 */
	private void logAnyJobIssues() {
		int sizeBefore = this.loggedJobNames.size();
		
		Set<String> jobNames = this.hazelcastInstance.getJet().getJobs()
				.stream()
				.filter(job -> job.getName() != null && !job.getName().isBlank())
				.map(Job::getName)
				.collect(Collectors.toCollection(TreeSet::new));
		
		for (String jobName : jobNames) {
			if (!this.loggedJobNames.contains(jobName)) {
				try {
					Job job = this.hazelcastInstance.getJet().getJob(jobName);
					JobStatus jobStatus = job.getStatus();
					if (jobStatus != JobStatus.RUNNING) {
						log.info("Job '{}', state '{}'", jobName, jobStatus);
						this.loggedJobNames.add(jobName);
					}
				} catch (Exception e) {
					// Job unlikely to have been deleted but could happen
					log.error("logAnyRunningJobs():" + jobName, e);
				}
			}
		}

		if (sizeBefore != this.loggedJobNames.size()) {
			log.info("---");
		}
	}
	
	/**
	 * <p>Safety submit, if the client is run twice, don't launch jobs a second time.
	 * </p>
	 *
	 * @param pipeline
	 * @param jobConfig
	 */
	private void checkedSubmit(Pipeline pipeline, JobConfig jobConfig) {
		log.info("Submitting '{}'", jobConfig.getName());
		long now = System.currentTimeMillis();
		Collection<Job> jobs = this.hazelcastInstance.getJet().getJobs();

		for (Job job : jobs) {
        	if (jobConfig.getName().equals(job.getName())) {
        		log.error("Not submitting '{}', job already exists '{}' with status: {}", jobConfig.getName(), job, job.getStatus());
        		return;
        	}
        };
        
        Job job = this.hazelcastInstance.getJet().newJobIfAbsent(pipeline, jobConfig);
        try {
        	long then = job.getSubmissionTime();
        	// Allow 5 seconds in case clocks not exactly in sync, submission time is serverside
            if (then < now - TimeUnit.SECONDS.toMillis(5L)) {
        		log.info("Not submitting '{}', already running since {}, status {}", job, new Date(then), job.getStatus());
            } else {
            	// Give it time to start
            	TimeUnit.SECONDS.sleep(1L);
        		log.info("Submitted '{}', status {}", job, job.getStatus());
            }
        } catch (Exception e) {
        	log.error("checkSubmit(): " + jobConfig.getName(), e);
        }
	}

}
