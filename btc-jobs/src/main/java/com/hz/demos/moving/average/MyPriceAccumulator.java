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

import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.jet.datamodel.Tuple3;
import com.hz.demos.PriceUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Merge two price streams, 50-point on the left
 * and 200-point on the right. As the 50-point starts
 * first it will always exist, for the first 150
 * observations the right stream (200-point) will have
 * no matching value.
 * </p>
 */
@Data
@Slf4j
public class MyPriceAccumulator {
	
	private LocalDate date;
	private BigDecimal left;
	private BigDecimal right;

	/**
	 * <p>Apply a new data from the 50-point stream into
	 * the current accumulator.
	 * </p>
	 *
	 * @param arg0 The 50-point stream
	 * @return A trio combining 50-point and 200-point
	 */
	public MyPriceAccumulator setLeft(HazelcastJsonValue arg0) {
		this.left = PriceUtils.getRate(arg0);
		if (this.date != null) {
			if (!this.date.isEqual(PriceUtils.getDate(arg0))) {
				log.error("Date clash: {} but setLeft({})", this, arg0);
			}
		} else {
			this.date = PriceUtils.getDate(arg0);
		}
		return this;
	}

	/**
	 * <p>Apply a new data from the 200-point stream into
	 * the current accumulator.
	 * </p>
	 *
	 * @param arg0 The 200-point stream
	 * @return A trio combining 50-point and 200-point
	 */
	public MyPriceAccumulator setRight(HazelcastJsonValue arg0) {
		this.right = PriceUtils.getRate(arg0);
		if (this.date != null) {
			if (!this.date.isEqual(PriceUtils.getDate(arg0))) {
				log.error("Date clash: {} but setRight({})", this, arg0);
			}
		} else {
			this.date = PriceUtils.getDate(arg0);
		}
		return this;
	}

	/**
	 * <p>Merge two accumulators together, eg. if accumulated on
	 * another CPU. We do not expect the incoming side to overwrite
	 * any data, so report error if so.
	 * </p>
	 *
	 * @param that The other accumulator
	 * @return A trio combining 50-point and 200-point
	 */
	public MyPriceAccumulator combine(MyPriceAccumulator that) {
		if (this.date == null) {
			this.date = that.getDate();
		} else {
			if (that.getDate() != null && !this.getDate().equals(that.getDate())) {
				log.error("Date clash: {} but combine with ({})", this, that);
			}
		}
		if (this.left == null) {
			this.left = that.getLeft();
		} else {
			if (that.getLeft() != null && !this.getLeft().equals(that.getLeft())) {
				log.error("Left clash: {} but combine with ({})", this, that);
			}
		}
		if (this.right == null) {
			this.right = that.getRight();
		} else {
			if (that.getLeft() != null && !this.getRight().equals(that.getRight())) {
				log.error("Right clash: {} but combine with ({})", this, that);
			}
		}
		return this;
	}

	public Tuple3<LocalDate, BigDecimal, BigDecimal> result() {
		return Tuple3.tuple3(this.date, this.left, this.right);
	}
	
}
