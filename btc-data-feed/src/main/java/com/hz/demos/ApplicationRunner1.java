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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.hazelcast.config.EventJournalConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;

/**
 * <p>First step of processing, "{@code @Order(1)}"
 * </p>
 * <p>Define mappings for the maps created so that they can be queried
 * from Management Center and elsewhere.
 * </p>
 */
@Configuration
@Order(value = 1)
public class ApplicationRunner1 implements CommandLineRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRunner1.class);

	@Autowired
	private HazelcastInstance hazelcastInstance;

	@Override
	public void run(String... args) throws Exception {
		// Add journal to the map, 10000 per partition
		int capacity = 271 * 10_000;
		EventJournalConfig eventJournalConfig = new EventJournalConfig().setEnabled(true).setCapacity(capacity);
		
        MapConfig priceFeedMapConfig = new MapConfig(MyConstants.IMAP_PRICE_FEED);
        priceFeedMapConfig.setEventJournalConfig(eventJournalConfig);
        try {
        	// So can run twice safely
        	this.hazelcastInstance.getConfig().addMapConfig(priceFeedMapConfig);
            LOGGER.info("Added '{}'", priceFeedMapConfig);
        } catch (Exception e) {
        	String message = String.format("Error adding map config for '%s': %s",
        			priceFeedMapConfig.getName(), e.getMessage());
        	LOGGER.error(message);
        }

        // Define what we may query
		this.addMappings();

		// Ensure all maps visible on Management Center, force create
		MyConstants.IMAPS.forEach(this.hazelcastInstance::getMap);
	}
	
	/**
	 * <p>"{@code DATE}" for {@link java.time.LocalDate}.</p>
	 * <p>"{@code DECIMAL}" for {@link java.math.BigDecimal}.</p>
	 */
	private void addMappings() {
		String priceAverageMapping = "CREATE OR REPLACE MAPPING \"" + MyConstants.IMAP_PRICE_AVERAGE + "\""
				+ " ("
				+ "  \"" + MyConstants.JSON_FIELD_PAIR + "\" VARCHAR EXTERNAL NAME \"__key." + MyConstants.JSON_FIELD_PAIR + "\","
				+ "  \"" + MyConstants.JSON_FIELD_TYPE + "\" VARCHAR EXTERNAL NAME \"__key." + MyConstants.JSON_FIELD_TYPE + "\","
				+ "  \"" + MyConstants.JSON_FIELD_DATE + "\" DATE,"
				+ "  \"" + MyConstants.JSON_FIELD_RATE + "\" DECIMAL"
				+ " )"
				+ " TYPE IMap "
				+ " OPTIONS ( "
				+ " 'keyFormat' = 'json-flat',"
				+ " 'valueFormat' = 'json-flat'"
				+ " )";

		String priceFeedMapping = "CREATE OR REPLACE MAPPING \"" + MyConstants.IMAP_PRICE_FEED + "\""
				+ " ("
				+ "  \"__key\" VARCHAR,"
				+ "  \"" + MyConstants.JSON_FIELD_DATE + "\" DATE,"
				+ "  \"" + MyConstants.JSON_FIELD_RATE + "\" DECIMAL"
				+ " )"
				+ " TYPE IMap "
				+ " OPTIONS ( "
				+ " 'keyFormat' = 'java',"
				+ " 'keyJavaClass' = '" + String.class.getName() + "',"
				+ " 'valueFormat' = 'json-flat'"
				+ " )";
		
		List.of(priceAverageMapping, priceFeedMapping)
			.forEach(this.hazelcastInstance.getSql()::execute);
	}

}
