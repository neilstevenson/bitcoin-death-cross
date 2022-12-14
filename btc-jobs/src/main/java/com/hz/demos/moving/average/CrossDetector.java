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
import java.time.LocalDate;
import java.util.AbstractMap.SimpleImmutableEntry;

import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.Tuple3;

/**
 * <p>Compare one pair of prices with the immediately preceding
 * pair of prices.
 * </p>
 * <p>The "{@code left}" field is the 50-point moving average and
 * the "{@code right}" is the 200-point.
 * </p>
 * <p>If they cross, one going up while the other is down, produce
 * some output. Otherwise keep silent.
 * </p>
 * <p><b>Note: </b> As the 50-point moving average starts 150 days
 * before the 200-point moving average the first 150 pairs of
 * prices are incomplete, missing the 200-point "{@code right}"
 * value. We discard those too, but we could just as easily
 * filter them out in the pipeline itself before passing to this
 * routine, and this would be fractionally more efficient.
 * </p>
 * <p>Since the days start from 2017-01-01, the first 50-point is
 * on 2017-02-17, and the first 200-point on 2017-07-19.
 * </p>
 */
public class CrossDetector {
			
	public SimpleImmutableEntry<Tuple3<LocalDate, String, String>, Tuple2<BigDecimal, BigDecimal>>
		consider(
				Tuple3<
				    String,
					Tuple3<LocalDate, BigDecimal, BigDecimal>,
					Tuple3<LocalDate, BigDecimal, BigDecimal>
			 	> trio) {
		
		String key = trio.f0();
		Tuple3<LocalDate, BigDecimal, BigDecimal> priceBefore = trio.f1();
		Tuple3<LocalDate, BigDecimal, BigDecimal> priceAfter = trio.f2();
		
		BigDecimal yesterday50point = priceBefore.f1();
		BigDecimal yesterday200point = priceBefore.f2();
		BigDecimal today50point = priceAfter.f1();
		BigDecimal today200point = priceAfter.f2();

		// Business logic, do they cross ?
		String trend = null;
		if (yesterday50point.compareTo(yesterday200point) < 0) {
			if (today50point.compareTo(today200point) > 0) {
				trend = "Upward";
			}
		}
		if (yesterday50point.compareTo(yesterday200point) > 0) {
			if (today50point.compareTo(today200point) < 0) {
				trend = "Downward";
			}
		}
		
		if (trend != null) {
			Tuple3<LocalDate, String, String> resultKey
				= Tuple3.tuple3(priceAfter.f0(), key, trend);
			
			Tuple2<BigDecimal, BigDecimal> resultValue
				= Tuple2.tuple2(today50point, today200point);

			return new SimpleImmutableEntry<>(resultKey, resultValue);
		} else {
			return null;
		}
	}
 
}
