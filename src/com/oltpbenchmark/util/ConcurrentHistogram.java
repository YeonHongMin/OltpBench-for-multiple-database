/******************************************************************************
 *  Copyright 2015 by OLTPBenchmark Project                                   *
 *                                                                            *
 *  Licensed under the Apache License, Version 2.0 (the "License");           *
 *  you may not use this file except in compliance with the License.          *
 *  You may obtain a copy of the License at                                   *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 *  Unless required by applicable law or agreed to in writing, software       *
 *  distributed under the License is distributed on an "AS IS" BASIS,         *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 *  See the License for the specific language governing permissions and       *
 *  limitations under the License.                                            *
 ******************************************************************************/

package com.oltpbenchmark.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * A high-performance, lock-free histogram implementation using ConcurrentHashMap
 * and LongAdder for thread-safe counting without synchronization overhead.
 *
 * This class is optimized for high-throughput scenarios where many threads
 * need to increment counters simultaneously.
 *
 * @param <X> The type of values being counted
 */
public class ConcurrentHistogram<X> {

    private final ConcurrentHashMap<X, LongAdder> histogram;
    private final LongAdder totalSamples;

    /**
     * Default constructor with initial capacity of 16
     */
    public ConcurrentHistogram() {
        this(16);
    }

    /**
     * Constructor with specified initial capacity
     * @param initialCapacity initial capacity for the underlying map
     */
    public ConcurrentHistogram(int initialCapacity) {
        this.histogram = new ConcurrentHashMap<>(initialCapacity);
        this.totalSamples = new LongAdder();
    }

    /**
     * Increment the count for a value by 1
     * This is a lock-free operation
     * @param value the value to increment
     */
    public void put(X value) {
        put(value, 1);
    }

    /**
     * Increment the count for a value by the specified amount
     * This is a lock-free operation
     * @param value the value to increment
     * @param count the amount to increment by
     */
    public void put(X value, long count) {
        if (value == null) return;

        LongAdder adder = histogram.computeIfAbsent(value, k -> new LongAdder());
        adder.add(count);
        totalSamples.add(count);
    }

    /**
     * Add all entries from another ConcurrentHistogram
     * @param other the histogram to merge
     */
    public void putHistogram(ConcurrentHistogram<X> other) {
        if (other == null) return;

        other.histogram.forEach((key, adder) -> {
            long count = adder.sum();
            if (count > 0) {
                put(key, count);
            }
        });
    }

    /**
     * Add all entries from a traditional Histogram
     * @param other the histogram to merge
     */
    public void putHistogram(Histogram<X> other) {
        if (other == null) return;

        for (X key : other.values()) {
            Integer count = other.get(key);
            if (count != null && count > 0) {
                put(key, count);
            }
        }
    }

    /**
     * Get the count for a specific value
     * @param value the value to look up
     * @return the count, or 0 if not present
     */
    public long get(X value) {
        LongAdder adder = histogram.get(value);
        return (adder != null) ? adder.sum() : 0;
    }

    /**
     * Get the count for a specific value, with a default value
     * @param value the value to look up
     * @param defaultValue the value to return if not present
     * @return the count, or defaultValue if not present
     */
    public long get(X value, long defaultValue) {
        LongAdder adder = histogram.get(value);
        return (adder != null) ? adder.sum() : defaultValue;
    }

    /**
     * Check if the histogram contains a specific value
     * @param value the value to check
     * @return true if present
     */
    public boolean contains(X value) {
        return histogram.containsKey(value);
    }

    /**
     * Get the total number of samples added to the histogram
     * @return total sample count
     */
    public long getSampleCount() {
        return totalSamples.sum();
    }

    /**
     * Get the number of unique values in the histogram
     * @return number of unique values
     */
    public int getValueCount() {
        return histogram.size();
    }

    /**
     * Get all values in the histogram
     * @return set of values
     */
    public Set<X> values() {
        return histogram.keySet();
    }

    /**
     * Check if the histogram is empty
     * @return true if empty
     */
    public boolean isEmpty() {
        return histogram.isEmpty();
    }

    /**
     * Clear all entries from the histogram
     */
    public void clear() {
        histogram.clear();
        totalSamples.reset();
    }

    /**
     * Convert to a traditional Histogram for compatibility
     * @return a new Histogram with the same data
     */
    public Histogram<X> toHistogram() {
        Histogram<X> result = new Histogram<>();
        histogram.forEach((key, adder) -> {
            long count = adder.sum();
            if (count > 0) {
                result.put(key, (int) Math.min(count, Integer.MAX_VALUE));
            }
        });
        return result;
    }

    /**
     * Get a snapshot of counts as a Map
     * @return map of values to counts
     */
    public Map<X, Long> getSnapshot() {
        ConcurrentHashMap<X, Long> snapshot = new ConcurrentHashMap<>();
        histogram.forEach((key, adder) -> {
            snapshot.put(key, adder.sum());
        });
        return snapshot;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ConcurrentHistogram[");
        boolean first = true;
        for (Map.Entry<X, LongAdder> entry : histogram.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=").append(entry.getValue().sum());
            first = false;
        }
        sb.append("] (total=").append(totalSamples.sum()).append(")");
        return sb.toString();
    }
}
