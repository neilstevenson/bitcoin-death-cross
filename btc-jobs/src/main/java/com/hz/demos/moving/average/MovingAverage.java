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

import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.function.Functions;
import com.hazelcast.jet.aggregate.AggregateOperation;
import com.hazelcast.jet.aggregate.AggregateOperation2;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.Tuple3;
import com.hazelcast.jet.pipeline.JournalInitialPosition;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StageWithKeyAndWindow;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.jet.pipeline.StreamStageWithKey;
import com.hazelcast.jet.pipeline.WindowDefinition;
import com.hz.demos.MyConstants;
import com.hz.demos.PriceUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * <p>Creates a processing pipeline to calculate moving
 * averages and detect when they cross.
 * </p>
 * <p>The processing looks like this:
 * </p>
 * <pre>
 *                                +---------+
 *                                |priceFeed|
 *                                +---------+
 *                                    |
 *           /------------------------+----------------------\ 
 *           |                        |                      | 
 *     +---------------+        +-----------+          +-------------+
 *     |averageOf1-noop|        |averageOf50|          |averageOf200 |
 *     +---------------+        +-----------+          +-------------+
 *       |            |              |      |               |       | 
 * +---------------+  |    +------------+   |      +-------------+  |
 * |mapSink-Current|  |    |mapSink-50Pt|   |      |mapSink-200Pt|  |
 * +---------------+  |    +------------+   |      +-------------+  |
 *                    |                     |                       |
 *                +-------+         +----------+          +-----------+
 *                |logSink|         |streamOf50|          |streamof200|
 *                +-------+         +----------+          +-----------+
 *                                       |                     |
 *                                        \                    / 
 *                                         +------------------+
 *                                         |      joined      |
 *                                         +------------------+
 *                                                   |
 *                                             +-----------+
 *                                             | fullTrios |
 *                                             +-----------+
 *                                                   |
 *                                          +------------------+
 *                                          | consecutiveTrios |
 *                                          +------------------+
 *                                                   |
 *                                          +------------------+
 *                                          |   crossEmitter   |
 *                                          +------------------+
 *                                                   |
 *                                          +------------------+
 *                                          | topicSink-alert  |
 *                                          +------------------+
 * </pre>
 * <p>What each step does is as follows:
 * </p>
 * <ol>
 * <li><p><b>priceFeed</b> : {@code btc-data-feed} writes Bitcoin / US Dollar
 * prices into an {@code com.hazelcast.core.IMap IMap}. Each new price
 * replaces the previous, so what is stored in Hazelcast in the
 * "{@code prices_feed}" map is the current price.
 * </p>
 * <p>This map is configured with an event journal, to keep track of the
 * changes to the content.Each time the "{@code BTCUSD}" entry
 * is updated in the map, Hazelcast stores the new value and
 * produces an event on this infinite stream.
 * </p>
 * <p>Each price in the input has a date, but we ignore this date on
 * the input and regard the stream as having no timestamps as we
 * don't need this timestamp yet and when we derive a value from
 * this input it will have a different timestamp.
 * </li>
 * <li><p><b>averageOf50</b> : Produce a moving average of the last
 * <em>50</em> prices. Each input is added to a ring-buffer of size
 * 50. Once the ring-buffer is full, add the 50 prices up, divide
 * by 50 and produce the first output item. Thereafter each item
 * of input results in one item of output.
 * </p>
 * <p>If the ring-buffer was size 3, the first item of output
 * would be the average of input items 1, 2 and 3. The second item
 * of output would be the average of items 2, 3 and 4.
 * <p>
 * </li>
 * <li><p><b>averageOf200</b> : This is the same as the "{@code averageOf50}"
 * stage, except the parameter is 200 to produce the moving average of
 * the last 200 prices.
 * </p>
 * </li>
 * <li><p><b>averageOf1-noop</b> : Calculate the moving average of
 * using a ring-buffer size of 1. Mathematically this does nothing
 * (a "{@code no-op}"), the average of one number is that number. We
 * use it here as a convenience to reformat the raw input into the
 * same format as produced by the "{@code averageOf50}" and
 * "{@code averageOf200}" streams so we can treat all three the
 * same later on.
 * </p>
 * </li>
 * <li><p><b>logSink</b> : <i>[Optional] : </i> Write the output
 * of the "{@code averageOf1-noop}" stage to the logs. This
 * is just so we can see what is happening with the prices, to
 * aid understanding. You need to download the logs to see it.
 * </p>
 * </li>
 * <li><p><b>mapSink-50Pt</b> : Save the "{@link averageOf50}"
 * stream to an {@link com.hazelcast.map.IMap IMap} named
 * "{@code BTCUSD}". Each value replaces the previous
 * in the map, so this map holds the most recent 50-point average.
 * The web app can use a map listener to observe these changes.
 * </p>
 * </li>
 * <li><p><b>mapSink-200Pt</b> : Save the stream from
 * "{@code averageOf200}" into the same map as we save the
 * "{@code averageOf50}". Again the web app can use a map
 * listener to follow the changes.
 * </p>
 * </li>
 * <li><p><b>mapSink-Current</b> : Save the stream of unaltered
 * prices into the map named "{@code BTCUSD}".</p>
 * <p>So map "{@code price_average}" holds the latest values for the
 * 50-point average, 200-point average and unaltered original
 * price conveniently together. The unaltered price came from
 * and still exists in another map "{@code price_feed}" so
 * this is minor waste of space to duplicate it.
 * </p>
 * </li>
 * <li><p><b>streamOf50</b> : prepare the "{@code averageOf50}"
 * stream for joining.
 * </p>
 * <p>Firstly, add a timestamp. For the 50-point average we
 * could take the date of the first of the 50-points, the last
 * of the 50-points or the middle. All would have a sense to
 * them. We take the date of the last of the 50-points.
 * </p>
 * <p>Apply a window to this stream so Jet will control the release
 * of values to the next stage. We chose a tumbling window as
 * we don't want the next stage to see each average more than
 * once. We advance the window in 1-day units, and since we have
 * 1 average per day, this means our window contains at most one
 * value.
 * </p>
 * </li>
 * <li><p><b>streamOf200</b> : prepare the "{@code averageOf200}"
 * stream for joining by adding a timestamp to join on. Again,
 * use the end date of the average range as the timestamp.
 * </p>
 * </li>
 * <li><p><b>joined</b> : The join logic is easy! One input is
 * the 50-point average and the other is the 200-point average.
 * Each input has a timestamp and Jet arranges to provide us the
 * values one at a time.
 * </p>
 * <p>So the join boils down to creating a trio of values.
 * One of the trio is the price from the 50-point average input.
 * Another of the trio is the price from 200-point average input.
 * The last of the trio is the date, which is the same on both
 * 50-point and 200-point, but we take the 50-point one.
 * </p>
 * </li>
 * <li><p><b>fullTrios</b> : The preceding join puts of the
 * combination of the 50-point and 200-point for a specific date.
 * However, the 50-point begins 50 days into the input, so has
 * values for 150 dates before the 200-point begins. So the
 * first 150 outputs of the join only have the 50-point value
 * and no 200-point value. We don't care for these incomplete
 * records so filter them out.
 * </p>
 * <li><p><b>consecutiveTrios</b> : This stage produces one
 * output for a consecutive pair of input trios. In other words,
 * a trio of date, 50-point and 200-point, combined with another
 * trio of the same for the next date.
 * </p>
 * <li><p><b>crossEmitter</b> : Look for a cross on the 50-point
 * and 200-point averages.
 * </p>
 * <p>The cross definition is easy. From one day to the next the
 * 50-point has to move from a higher value than the 200-point
 * to being a lower value. <u>Or</u> from one day to the next the
 * 50-point has move from being a lower value than the 200-point
 * to being a higher value.
 * </p>
 * </li>
 * <li><p><b>topicSink-alert</b> : The "{@code crossEmitter}" stage
 * only produces output if a cross is detected. If anything gets to
 * this stage, send it to a {@link com.hazelcast.topic.ITopic ITopic}
 * so that any process with a listener subscribed to the topic is aware.
 * </p>
 * </li>
 * </ol>
 */
public class MovingAverage {

	/**
	 * <p>Break the input stream into daily chunks. As
	 * we have daily prices, each will contain one item.
	 * </p>
	 */
	private static final WindowDefinition ONE_DAY_WINDOW =
    		WindowDefinition.tumbling(TimeUnit.DAYS.toMillis(1));

	
	/**
	 * <p>Our input data is sorted (by us) so we don't need
	 * to allow for late, out-of-sequence items.
	 * </p>
	 */
	private static final long ZERO_LAG = 0;

    
    /**
     * <p>Build the pipeline definition, to send to all JVMs for execution.
     * </p>
     */
	public static Pipeline build() {
		Pipeline pipeline = Pipeline.create();

		/** <p> Create a single feed of prices from changes
		 * to an {@link com.hazelcast.core.IMap IMap}.</p> 
		 */
		StreamStageWithKey<Entry<String, HazelcastJsonValue>, String> priceFeed = 
				MovingAverage.buildPriceFeed(pipeline);

		/** <p>Create three different moving averages from
		 * these prices.</p>
		 */
		StreamStage<Entry<String, HazelcastJsonValue>> averageOf1 =
				MovingAverage.buildAverageOfCount(1, priceFeed);
		StreamStage<Entry<String, HazelcastJsonValue>> averageOf50 =
				MovingAverage.buildAverageOfCount(50, priceFeed);
		StreamStage<Entry<String, HazelcastJsonValue>> averageOf200 =
				MovingAverage.buildAverageOfCount(200, priceFeed);

		/** <p>Create a time-stamped stream from the 50-point averages.
		 * Add a window that advances it 1 day, and therefore 1 data point,
		 * at a time.
		 * </p>
		 */
		StageWithKeyAndWindow<Entry<String, HazelcastJsonValue>, String> timestampedWindowedOf50 =
				MovingAverage.buildKeyedTimestamped(averageOf50, 50)
				.window(ONE_DAY_WINDOW);
				
		/** <p>Create a time-stamped stream from the 200-point averages.
		 * </p>
		 */
		StreamStageWithKey<Entry<String, HazelcastJsonValue>, String> timestampedOf200 =
				MovingAverage.buildKeyedTimestamped(averageOf200, 200);
        
		/** <p>Join the time-stamped 50-point and 200-point streams by
		 * the timestamp. Produces a trio of the date, and the
		 * price from each input for the matching date.
		 * </p>
		 */
		StreamStage<KeyedWindowResult<String, Tuple3<LocalDate, BigDecimal, BigDecimal>>>
			joined50point200point
			= MovingAverage.join(timestampedWindowedOf50,timestampedOf200);

		/** <p>Remove trios where the 200-point is missing, which will be the
		 * first 150 of them.
		 * </p>
		 */
		StreamStage<KeyedWindowResult<String, Tuple3<LocalDate, BigDecimal, BigDecimal>>>
			actual50point200point
			= joined50point200point
				.filterStateful(Object::new, 
				(__, item) -> {
					return (item.getValue().f2() != null);
				})
				.setName("fullTrios");

		/** <p>Form into consecutive pairs for cross detection
		 * </p> 
		 */
		StreamStage<Tuple3<
		        String,
				Tuple3<LocalDate, BigDecimal, BigDecimal>,
				Tuple3<LocalDate, BigDecimal, BigDecimal>
		 	>>
			consecutivePair50point200point
			= actual50point200point
				.mapStateful(
						() -> new Object[1]
						,(
							Object[] tuple3Wrapper, 
							KeyedWindowResult<String, Tuple3<LocalDate, BigDecimal, BigDecimal>> current
							) -> {
								@SuppressWarnings("unchecked")
								Tuple3<LocalDate, BigDecimal, BigDecimal> previous
									= (Tuple3<LocalDate, BigDecimal, BigDecimal>) tuple3Wrapper[0];
								
								Tuple3<
									String,
									Tuple3<LocalDate, BigDecimal, BigDecimal>,
									Tuple3<LocalDate, BigDecimal, BigDecimal>
							 	> result = Tuple3.tuple3(current.getKey(), previous, current.getValue());

								tuple3Wrapper[0] = current.getValue();
								return result;
				})
				.filterStateful(Object::new, 
						(__, tuple2) -> tuple2.f1() != null)
				.setName("consecutiveTrios");

		/** <p>Finally, the <b>Business Logic!</b></p>
		 * <p>Compare the trio of date, 50-point and 200-point in the
		 * stream to the previous, looking for price inflection.
		 * </p>
		 */
		StreamStage<SimpleImmutableEntry<Tuple3<LocalDate, String, String>, Tuple2<BigDecimal, BigDecimal>>> alerts =
			consecutivePair50point200point
			.mapStateful(CrossDetector::new,
				(crossDetector, tuple2) -> crossDetector.consider(tuple2))
			.filter(item -> item != null);

		/** <p>If there is anything produced by the {@link CrossEmitter}
		 * dump it to a {@link com.hazelcast.core.ITopic ITopic}.
		 * What comes out is a {@code Map.Entry} so we could easily dump it to
		 *  an {@link com.hazelcast.core.IMap IMap} instead
		 * (or as well) and use a map listener.
		 * </p>
		 */
		alerts
		.writeTo(Sinks.reliableTopic(MyConstants.RELIABLE_TOPIC_ALERTS));
		
		/** <p><i>Optional: </i>Log the current price to help understanding.
		 * </p>
		 */
		averageOf1
			.writeTo(Sinks.logger())
			.setName("logSink");
		
		/** <p>Save the latest for each average to an
		 * {@link com.hazelcast.core.IMap IMap} for browsing.</p>
		 */
		averageOf1
			.map(oldEntry -> {
				HazelcastJsonValue key = PriceUtils.makeKey(oldEntry.getKey(), MyConstants.TYPE_CURRENT);
				return new SimpleImmutableEntry<>(key, oldEntry.getValue());
			})
			.writeTo(Sinks.map(MyConstants.IMAP_PRICE_AVERAGE))
			.setName("mapSink-" + MyConstants.TYPE_CURRENT);
		averageOf50
			.map(oldEntry -> {
				HazelcastJsonValue key = PriceUtils.makeKey(oldEntry.getKey(), MyConstants.TYPE_50_POINT);
				return new SimpleImmutableEntry<>(key, oldEntry.getValue());
			})
			.writeTo(Sinks.map(MyConstants.IMAP_PRICE_AVERAGE))
			.setName("mapSink-" + MyConstants.TYPE_50_POINT);
		averageOf200
			.map(oldEntry -> {
				HazelcastJsonValue key = PriceUtils.makeKey(oldEntry.getKey(), MyConstants.TYPE_200_POINT);
				return new SimpleImmutableEntry<>(key, oldEntry.getValue());
			})
			.writeTo(Sinks.map(MyConstants.IMAP_PRICE_AVERAGE))
			.setName("mapSink-" + MyConstants.TYPE_200_POINT);

		return pipeline;
	}


	/**
	 * <p>The "{@code btc-data-feed}" job writes
	 * the current price of Bitcoin into an
	 * {@link com.hazelcast.map.IMap IMap}. This
	 * {@link com.hazelcast.map.IMap IMap} is defined with a
	 * {@link com.hazelcast.map.impl.journal.MapEventJournal MapEventJournal}
	 * that allows Jet to track the history of changes. Use this as a
	 * source to stream in.
	 * <p>
	 * <p>Don't bother yet with timestamps, they are added in later
	 * in the pipeline.
	 * </p>
	 * <p>Group (route) all events based on the key, which will be
	 * "{@code BTCUSD}". However many Jet nodes are running, only
	 * one will handle "{@code BTCUSD}". 
	 * </p>
	 *
	 * @param pipeline Will be empty
	 * @return The first stage of the pipeline
	 */
	protected static StreamStageWithKey<Entry<String, HazelcastJsonValue>, String> 
		buildPriceFeed(Pipeline pipeline) {

		return pipeline.readFrom(
				Sources.<String, HazelcastJsonValue>mapJournal(
					MyConstants.IMAP_PRICE_FEED,
					JournalInitialPosition.START_FROM_OLDEST)
				)
				.withoutTimestamps()
				.setName("priceFeed")
				.groupingKey(Functions.entryKey());
	}


	/**
	 * <p>Create a moving average of the last <i>n</i> prices,
	 * where the embedded timestamp is the date of the last price.
	 * </p>
	 * <p>The averaging here is the mathematical usual. If there
	 * are 50 prices, sum up 50 from the input and divide by
	 * 50. There is no weighting given to more recent prices,
	 * all are treated the same.
	 * </p>
	 * <p>As a special case, we may take the average of <i>n==1</i>.
	 * This is a "{@code no-op}", the input and the output are
	 * essentially the same. But we use it here to do re-formatting.
	 * </p>
	 *
	 * @param count How many consecutive prices to average
	 * @param priceFeed From the previous pipeline stage 
	 * @return The moving average
	 */
	protected static StreamStage<Entry<String, HazelcastJsonValue>>
		buildAverageOfCount(int count, StreamStageWithKey<Entry<String, HazelcastJsonValue>, String> priceFeed) {
		
		String stageName = ( count == 1 ? 
							"averageOf1-noop" :
							"averageOf" + count );
			
		return priceFeed.customTransform(stageName, 
				() -> new SimpleMovingAverage(count)
		);
	}
	
	/**
	 * <p>Take a stream of prices that happen to be a derived (average)
	 * stream of prices, and create a timestamp on that stream. Based the
	 * timestamp on the last date of the average.
	 * </p>
	 *
	 * @param averageOfSomething Average of 50 points or the average of 200 points
	 * @param count For building the stage name
	 * @return A time-stamped stream on this input
	 */
	private static StreamStageWithKey<Entry<String, HazelcastJsonValue>, String> 
		buildKeyedTimestamped(StreamStage<Entry<String, HazelcastJsonValue>> averageOfSomething, int count) {
		
		return averageOfSomething
				.setLocalParallelism(1)
				.addTimestamps(PriceUtils::getTimestamp, ZERO_LAG)
				.setName("streamOf" + count)
				.groupingKey(e -> e.getKey())
				;
	}
	
	/**
	 * <p>Define an {@link AggregateOperation2} that can be used to join
	 * two streams.
	 * </p>
	 * <p>The method signature has four parts as follows:
	 * </p>
	 * <ol>
	 * <li><p><code>Entry&lt;String, Price&gt;</code> : The left input
	 * stream, known here as accumulator 0. This is a stream of averaged
	 * prices that happen to be 50-point. As 50-point starts before 200-point
	 * this stream won't be empty.
	 * </p>
	 * <li><p><code>Entry&lt;String, Price&gt;</code> : The right input
	 * stream, known here as accumulator 1. This is a stream of averaged
	 * prices on the 200-point method. As this starts 150 days after the
	 * 50-point, the first 150 items on this side will be missing.
	 * </p>
	 * <li><p><code>MyPriceAccumulator</code> : A class that combines
	 * the left and right inputs, {@link MyPriceAccumulator}. This
	 * does nothing more than capture the left and right values from
	 * the input.
	 * </p>
	 * <li><p><code>Tuple3&lt;LocalDate, BigDecimal, BigDecimal&gt;</code> :
	 * the output from the aggregation, a trio ({@link Tuple3}) of the date
	 * of the matching pair, the value from the left input (50-point) and
	 * from the right input (200-point).
	 * </p>
	 * </ol>
	 *
	 * @return An {@link AggregateOperation2} operation, combines two streams
	 */
	private static AggregateOperation2<
				Entry<String, HazelcastJsonValue>, 
				Entry<String, HazelcastJsonValue>,
				MyPriceAccumulator, 
				Tuple3<LocalDate, BigDecimal, BigDecimal>> 
		buildAggregateOperation() {

		return AggregateOperation
				.withCreate(MyPriceAccumulator::new)
			     .<Entry<String, HazelcastJsonValue>>andAccumulate0(
			    		 (MyPriceAccumulator myPriceAccumulator, Entry<String, HazelcastJsonValue> entry)
			    		 -> myPriceAccumulator.setLeft(entry.getValue())
			    		 )
			     .<Entry<String, HazelcastJsonValue>>andAccumulate1(
			    		 (MyPriceAccumulator myPriceAccumulator, Entry<String, HazelcastJsonValue> entry)
			    		 -> myPriceAccumulator.setRight(entry.getValue())
			    		 )
			     .andCombine(
			    		 (MyPriceAccumulator left, MyPriceAccumulator right)
			    		 -> left.combine(right)
			    		 )
			     .andExportFinish(MyPriceAccumulator::result);
	}

	
	/**
	 * <p>Join the 50-point and 200-point streams, matching on the date
	 * for each. 
	 * </p>
	 * <p>For the 50-point stream, if there is a value for "{@code 2017-12-31}"
	 * this represents the average of 50 dates from "{@code 2017-11-16}" to
	 * "{@code 2017-12-31}".
	 * </p>
	 * <p>For the 200-point stream, if there is a value for "{@code 2017-12-31}"
	 * this represents the average of 200 dates from "{@code 2017-06-15}" to
	 * "{@code 2017-12-31}".
	 * </p>
	 * <p>Since the left stream (50-point) starts 150 days before the right
	 * stream (200-point), the first 150 output records will be incomplete.
	 * We could suppress them here, but instead we let the next stage do
	 * this.
	 * </p>
	 * 
	 * @param windowOf50 The current price on the 50-point average stream
	 * @param windowOf200 The current price on the 200-point average stream
	 * @return A trio of current from 50-point and 200-point and the date
	 */
	private static StreamStage<KeyedWindowResult<String, Tuple3<LocalDate, BigDecimal, BigDecimal>>> join(
			StageWithKeyAndWindow<Entry<String, HazelcastJsonValue>, String> windowOf50,
			StreamStageWithKey<Entry<String, HazelcastJsonValue>, String> windowOf200) {

		// How to join
		AggregateOperation2<Entry<String, HazelcastJsonValue>, Entry<String, HazelcastJsonValue>, 
			MyPriceAccumulator, Tuple3<LocalDate, BigDecimal, BigDecimal>> 
			myAggregateOperation = MovingAverage.buildAggregateOperation();
		
		// Do the join
		return windowOf50
					.aggregate2(windowOf200, myAggregateOperation)
					.setName("joined")
					;
	}
	
}
