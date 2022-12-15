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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.map.IMap;

/**
 * <p>Second and last step of processing, "{@code @Order(2)}"
 * </p>
 * <p>Write a record to the current prices map, for each line in the file, with
 * a delay from line to line. Although the prices are downloaded, this simulates
 * a live feed.
 * </p>
 * <p>Currently just BTC/USD pair, but could add others so use parallel threading
 * approach.
 * </p>
 */
@Configuration
@Order(value = 2)
public class ApplicationRunner2 implements CommandLineRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRunner2.class);

	@Autowired
	private FileUtils fileUtils;
	@Autowired
	private HazelcastInstance hazelcastInstance;

	@Override
	public void run(String... args) throws Exception {
		List<Resource> resources = this.fileUtils.getResources();
		List<ApplicationRunner2Callable> tasks = new ArrayList<>();
		for (int i = 0; i < resources.size(); i++) {
			tasks.add(new ApplicationRunner2Callable(this.hazelcastInstance, resources.get(i)));
		}
		
		// One thread executor for each input file
		ExecutorService executorService = Executors.newFixedThreadPool(tasks.size());
		
		Map<String, Integer> results = executorService.invokeAll(tasks)
				.stream()
				.map(future -> {
					try {
						return future.get();
					} catch (Exception e) {
						e.printStackTrace();
					}
					return null;
				})
				.filter(future -> future != null)
				.collect(Collectors.toMap(Tuple2::f0, Tuple2::f1));

		new TreeMap<>(results).entrySet().forEach(entry -> {
			LOGGER.info("'{}', wrote '{}'", entry.getKey(), entry.getValue());
		});
		
		LOGGER.info("Shutting down Hazelcast client");
		this.hazelcastInstance.shutdown();
	}

	/**
	 * <p>A thread to read a file and apply each line of input.
	 * </p>
	 */
	public class ApplicationRunner2Callable implements Callable<Tuple2<String, Integer>> {

		private final HazelcastInstance hazelcastInstance;
		private final Resource resource;

		ApplicationRunner2Callable(HazelcastInstance arg0, Resource arg1) {
			this.hazelcastInstance = arg0;
			this.resource = arg1;
		}
		
		@Override
		public Tuple2<String, Integer> call() throws Exception {
			int count = 0;
			String key = this.resource.getFilename().split("\\.")[0];
            String line;
            IMap<String, HazelcastJsonValue> priceFeedMap = this.hazelcastInstance.getMap(MyConstants.IMAP_PRICE_FEED);
	        try (BufferedReader bufferedReader =
	                new BufferedReader(
	                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
	        	while ((line = bufferedReader.readLine()) != null) {
	            	// Date, rate
	            	String[] tokens = line.split(",");

	                try {
	                	HazelcastJsonValue value = PriceUtils.makePrice(tokens[0], tokens[1]);
	                			
	                	priceFeedMap.set(key, value);
	                	
	                	if (count % MyConstants.LOG_EVERY == 0) {
	                		String message = String.format("Write %6d for '%s' '%s'", count, key, value);
	                		LOGGER.info(message);
	                	}

	                	// Simulate the prices arriving periodically
	                	TimeUnit.MILLISECONDS.sleep(MyConstants.DATA_FEED_WRITE_INTERVAL_MS);

	                } catch (Exception e) {
                		String message = String.format("Line %6d for '%s'", count, key);
	                	LOGGER.error(message, e);
	                	break;
	                }
	                
	            	count++;
	            }
	        }
	        
	        return Tuple2.tuple2(key, count);
		}
		
	}

}
