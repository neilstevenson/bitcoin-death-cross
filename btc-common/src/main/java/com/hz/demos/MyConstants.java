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

/**
 * <p>Constants shared across the application.
 * </p>
 */
public class MyConstants {
	// Prefix for maps, etc, so can use same demo in shared cluster. Acts as a namespace.
	public static final String PREFIX = "btc_";
	
	public static final String IMAP_PRICE_AVERAGE = PREFIX + "price_average";
	public static final String IMAP_PRICE_FEED = PREFIX + "price_feed";
	public static final List<String> IMAPS = List.of(
			IMAP_PRICE_AVERAGE, IMAP_PRICE_FEED
			);

	public static final String RELIABLE_TOPIC_ALERTS = PREFIX + "alerts";
	
    // Types of data output by Jet job
	public static final String TYPE_CURRENT = "Current";
	public static final String TYPE_50_POINT = "50 Point";
	public static final String TYPE_200_POINT = "200 Point";

	// JSON field names
	public static final String JSON_FIELD_DATE = "date";
	public static final String JSON_FIELD_PAIR = "pair";
	public static final String JSON_FIELD_NOW = "now";
	public static final String JSON_FIELD_RATE = "rate";
	public static final String JSON_FIELD_TREND = "trend";
	public static final String JSON_FIELD_TYPE = "type";
	public static final String JSON_FIELD_50_POINT = "average_" + TYPE_50_POINT.replaceAll(" ", "_");
	public static final String JSON_FIELD_2000_POINT = "average_" + TYPE_200_POINT.replaceAll(" ", "_");

	// How frequently to write prices, in milliseconds
	public static final long DATA_FEED_WRITE_INTERVAL_MS = 10L;
	
	// For diagnostics, log every n-th item
	public static final int LOG_EVERY = 100;
	
    // Web Sockets
    public static final String WEBSOCKET_ENDPOINT = "hazelcast";
    public static final String WEBSOCKET_FEED_PREFIX = "feed";
    public static final String WEBSOCKET_ALERTS_SUFFIX = "alerts";
    public static final String WEBSOCKET_DATA_SUFFIX = "data";

}
