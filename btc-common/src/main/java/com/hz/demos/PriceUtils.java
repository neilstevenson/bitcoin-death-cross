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
import java.time.ZoneOffset;
import java.util.Map.Entry;
import java.util.Objects;

import com.hazelcast.core.HazelcastJsonValue;
import org.json.JSONObject;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>Utility functions for the application as a whole.
 * </p>
 */
@Slf4j
public class PriceUtils {

    /**
     * <p>Build JSON representation of price from typed arguments
     * </p>
     *
     * @param date
     * @param rate
     * @return
     */
    public static HazelcastJsonValue makePrice(LocalDate date, BigDecimal rate) {
    	StringBuilder stringBuilder = new StringBuilder()
    			.append("{")
    			.append(" \"").append(MyConstants.JSON_FIELD_DATE).append("\" : \"").append(date).append("\"")
    			.append(",")
    			.append(" \"").append(MyConstants.JSON_FIELD_RATE).append("\" : ").append(rate)
    			.append("}");
    	
    	return new HazelcastJsonValue(stringBuilder.toString());
    }

    /**
     * <p>Convenience mechanism to call {@link #makePrice(LocalDate, BigDecimal)}
     * when arguments presented as strings
     * </p>
     *
     * @param dateStr
     * @param rateStr
     * @return
     */
    public static HazelcastJsonValue makePrice(String dateStr, String rateStr) {
    	LocalDate date = LocalDate.parse(dateStr);
    	BigDecimal rate = new BigDecimal(rateStr);
    	return makePrice(date, rate);
    }

    /**
     * <p>Shouldn't fail if input constructed using {@link #makePrice(LocalDate, BigDecimal)}
     * </p>
     * @param value
     * @return
     */
	public static LocalDate getDate(HazelcastJsonValue value) {
    	try {
    		JSONObject jsonObject = new JSONObject(value.toString());
    		String field = jsonObject.getString(MyConstants.JSON_FIELD_DATE);
    		return LocalDate.parse(field);
    	} catch (Exception e) {
    		log.error("getDate(): " + Objects.toString(value), e);
    		return null;
    	}
	}

    /**
     * <p>Shouldn't fail if input constructed using {@link #makePrice(LocalDate, BigDecimal)}
     * </p>
     * @param value
     * @return
     */
	public static BigDecimal getRate(HazelcastJsonValue value) {
    	try {
    		JSONObject jsonObject = new JSONObject(value.toString());
    		long field = jsonObject.getLong(MyConstants.JSON_FIELD_RATE);
    		return new BigDecimal(field);
    	} catch (Exception e) {
    		log.error("getRate(): " + Objects.toString(value), e);
    		return null;
    	}
	}

	/**
     * <p>Shouldn't fail if input constructed using {@link #makePrice(LocalDate, BigDecimal)}
     * </p>
	 * @param entry
	 * @return
	 */
    public static long getTimestamp(Entry<String, HazelcastJsonValue> entry) {
    	try {
    		JSONObject jsonObject = new JSONObject(entry.getValue().toString());
    		String field = jsonObject.getString(MyConstants.JSON_FIELD_DATE);
    		LocalDate date = LocalDate.parse(field);
    		return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    	} catch (Exception e) {
    		log.error("getTimestamp(): " + Objects.toString(entry), e);
    		return 0L;
    	}
    } 

    /**
     * <p>Form compound key
     * </p>
     *
     * @param pair
     * @param type
     * @return
     */
    public static HazelcastJsonValue makeKey(String pair, String type) {
    	StringBuilder stringBuilder = new StringBuilder();
    	
    	stringBuilder
    	.append("{")
    	.append(" \"").append(MyConstants.JSON_FIELD_PAIR).append("\" : \"").append(pair).append("\"")
    	.append(",")
    	.append(" \"").append(MyConstants.JSON_FIELD_TYPE).append("\" : \"").append(type).append("\"")
    	.append("}");

    	return new HazelcastJsonValue(stringBuilder.toString());
    }

    
}
