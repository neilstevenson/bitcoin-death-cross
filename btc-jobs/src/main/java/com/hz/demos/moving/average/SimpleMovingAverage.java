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

package com.hz.demos.moving.average;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.jet.core.AbstractProcessor;
import com.hz.demos.PriceUtils;

/**
 * <p>Calculate the simple average of a some points
 * arriving.
 * </p>
 * <p>"<i>Simple</i>" here refers to the usual way
 * of adding up all the points and dividing by the
 * count. So the input "{@code 1,2,3,4,5}" should
 * give the answer "{@code 3}".
 * </p>
 * <p>Other ways exist. For example repeating the
 * later numbers to reflect their recentness makes
 * their values more important. So input 
 * "{@code 1,2,3,4,5}" might be calculated as
 * the sum of "{@code 1,2,3,4,4,5,5}" divided by 
 * 7 giving "{@code 3.43}".
 * </p>
 * <p><b>NOTE:</b> This implementation is based on
 * the assumption that the input stream is strictly
 * consecutive. Item 1 arrives, then item 2, then
 * item 3, etc. There can be no gaps or out of sequence
 * items, or the wrong output will occur. If that is
 * possible, it needs better logic. This is just an
 * example after all, we can make this assumption
 * as the data used is pre-set and sorted.
 * </p>
 */
public class SimpleMovingAverage
	extends AbstractProcessor {

	private final BigDecimal[] rates;
	private final BigDecimal size;
	private int current;
	
	public SimpleMovingAverage(int i) {
		this.rates = new BigDecimal[i];
		this.size = new BigDecimal(i);
	}

	/**
	 * <p>Accumulate input in a ringbuffer, implemented
	 * using an array.
	 * </p>
	 * <p>As the array is filling, no output is produced
	 * but we return "{@code true}" so Jet knows all is
	 * good with input processing.
	 * </p>
	 * <p>Once the array is full, for each number of input
	 * we place in the appropriate part of the array,
	 * calculate the average and try to output. Potentially
	 * the output queue is full. It's not guaranteed that
	 * there is room for our output to be accepted, so cater
     * for this.
	 * </p>
	 */
	@Override
	protected boolean tryProcess(int ordinal, Object item) {
		@SuppressWarnings("unchecked")
		Entry<String, HazelcastJsonValue> entry = (Entry<String, HazelcastJsonValue>) item;
		HazelcastJsonValue value = entry.getValue();
		
		// Store the value
		this.rates[this.current] = PriceUtils.getRate(value);

		// Determine the next slot
		int next;
		if (this.current == (this.rates.length - 1)) {
			next = 0;
		} else {
			next = this.current + 1;
		}
		
		// Try to output an average, if we have enough stored input
		if (this.rates[next]==null) {
			this.current = next;
			return true;
		} else {
			HazelcastJsonValue average = PriceUtils.makePrice(PriceUtils.getDate(value), this.calculateAverage());
			
			Entry<String, HazelcastJsonValue> result
				= new SimpleImmutableEntry<>(entry.getKey(), average);
			
			// If we can output, advance the next write location
			boolean emit = super.tryEmit(result);
			if (emit) {
				this.current = next;
			}
			return emit;
		}
	}
	
	/**
	 * <p>Add all the points up and divide by how many
	 * points there are.
	 * </p>
	 * 
	 * @return
	 */
	private BigDecimal calculateAverage() {
		BigDecimal sum = BigDecimal.ZERO;
		
		for (int i = 0 ; i < this.rates.length ; i ++) {
			sum = sum.add(this.rates[i]);
		}
		
		// Two decimal places
		BigDecimal average = sum.divide(size, 2, RoundingMode.HALF_UP);
		
		return average;
	}

}
