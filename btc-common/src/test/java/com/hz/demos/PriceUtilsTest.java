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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.hazelcast.core.HazelcastJsonValue;

public class PriceUtilsTest {
	private static LocalDate TODAY = LocalDate.now();

    @Test
    public void comparePriceBuilders(TestInfo testInfo) {
    	String dateStr = TODAY.toString();
    	String rateStr = "123.45";
    	BigDecimal rate = new BigDecimal(rateStr);
    	
    	HazelcastJsonValue valueFromString = PriceUtils.makePrice(dateStr, rateStr);
    	HazelcastJsonValue valueFromParsed = PriceUtils.makePrice(TODAY, rate);
    	
    	assertEquals(valueFromParsed, valueFromString);
    }

    @Test
    public void testDateExtract(TestInfo testInfo) {
    	HazelcastJsonValue price = PriceUtils.makePrice(TODAY, BigDecimal.ZERO);
    	
    	LocalDate actual = PriceUtils.getDate(price);
    	
    	assertEquals(TODAY, actual);
    }

    @Test
    public void testRateExtract(TestInfo testInfo) {
    	HazelcastJsonValue price = PriceUtils.makePrice(TODAY, BigDecimal.ZERO);
    	
    	BigDecimal actual = PriceUtils.getRate(price);
    	
    	assertEquals(BigDecimal.ZERO, actual);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
    public void testTimestampStartOfDay(TestInfo testInfo) {
    	long now = System.currentTimeMillis();
    	long yesterday = now - (24 * 60 * 60 * 1000L);
    	
    	HazelcastJsonValue price = PriceUtils.makePrice(TODAY, BigDecimal.ZERO);
    	Entry entry = new SimpleImmutableEntry(null, price);
    	
    	long actual = PriceUtils.getTimestamp(entry);
    	
    	String message = String.format("now (%d) > actual (%d)", now, actual);
    	assertTrue(now > actual, message);
    	message = String.format("yesterday (%d) < actual (%d)", yesterday, actual);
    	assertTrue(yesterday < actual, message);
    }
}
