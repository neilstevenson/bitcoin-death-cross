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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.Tuple3;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;

import org.json.JSONObject;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>Listen for alerts and map updates and pass on to websocket.
 * </p>
 */
@Slf4j
@Component
public class MyWebSocketBridgeListener implements 
	EntryAddedListener<HazelcastJsonValue, HazelcastJsonValue>,
	EntryUpdatedListener<HazelcastJsonValue, HazelcastJsonValue>,
	MessageListener<Object> {

    private static final String DESTINATION_ALERTS =
            "/" + MyConstants.WEBSOCKET_FEED_PREFIX
            + "/" + MyConstants.WEBSOCKET_ALERTS_SUFFIX;
    private static final String DESTINATION_DATA =
            "/" + MyConstants.WEBSOCKET_FEED_PREFIX
            + "/" + MyConstants.WEBSOCKET_DATA_SUFFIX;
    
    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;
    
    public MyWebSocketBridgeListener() {
    	log.debug("Alerts destination: {}", DESTINATION_ALERTS);
    	log.debug("Data destination: {}", DESTINATION_DATA);
	}
    
	@Override
	public void entryAdded(EntryEvent<HazelcastJsonValue, HazelcastJsonValue> arg0) {
		this.handle(arg0);
	}
	@Override
	public void entryUpdated(EntryEvent<HazelcastJsonValue, HazelcastJsonValue> arg0) {
		this.handle(arg0);
	}

	/**
	 * <p>First insert and later updates all sent the same.
	 * </p>
	 * @param arg0
	 */
	private void handle(EntryEvent<HazelcastJsonValue, HazelcastJsonValue> arg0) {
		JSONObject key = new JSONObject(arg0.getKey().toString());
		JSONObject value = new JSONObject(arg0.getValue().toString());

        long now = System.currentTimeMillis();
        
		String pair = key.getString(MyConstants.JSON_FIELD_PAIR);
		String type = key.getString(MyConstants.JSON_FIELD_TYPE).replaceAll(" ", "_");
		LocalDate date = LocalDate.parse(value.getString(MyConstants.JSON_FIELD_DATE));
		BigDecimal rate =  value.getBigDecimal(MyConstants.JSON_FIELD_RATE);
		
		StringBuilder stringBuilder = new StringBuilder()
				.append("{ \"").append(MyConstants.JSON_FIELD_NOW).append("\": \"").append(now).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_DATE).append("\": \"").append(date).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_PAIR).append("\": \"").append(pair).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_RATE).append("\": ").append(rate)
				.append(", \"").append(MyConstants.JSON_FIELD_TYPE).append("\": \"").append(type).append("\"")
				.append("}");
		
		String result = stringBuilder.toString();
        log.trace("Data to websocket '{}'", result);
        this.simpMessagingTemplate.convertAndSend(DESTINATION_DATA, result);
	}

	/**
	 * <p>Alerts from topic
	 * </p>
	 */
	@Override
	public void onMessage(Message<Object> arg0) {
		@SuppressWarnings("unchecked")
		Entry<Tuple3<LocalDate, String, String>, Tuple2<BigDecimal, BigDecimal>> payload =
				(Entry<Tuple3<LocalDate, String, String>, Tuple2<BigDecimal, BigDecimal>>) arg0.getMessageObject();
		
		
        long now = System.currentTimeMillis();
        
		LocalDate date = payload.getKey().f0();
		String pair = payload.getKey().f1();
		String trend = payload.getKey().f2();
		BigDecimal averageOf50 = payload.getValue().f0();
		BigDecimal averageOf200 = payload.getValue().f1();

		StringBuilder stringBuilder = new StringBuilder()
				.append("{ \"").append(MyConstants.JSON_FIELD_NOW).append("\": \"").append(now).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_DATE).append("\": \"").append(date).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_PAIR).append("\": \"").append(pair).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_TREND).append("\": \"").append(trend).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_50_POINT).append("\": \"").append(averageOf50).append("\"")
				.append(", \"").append(MyConstants.JSON_FIELD_2000_POINT).append("\": \"").append(averageOf200).append("\"")
				.append("}");
		
		String result = stringBuilder.toString();
        log.info("Alert to websocket '{}'", result);
        this.simpMessagingTemplate.convertAndSend(DESTINATION_ALERTS, result);
	}

}
