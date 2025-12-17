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


package com.oltpbenchmark;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

/**
 * Distribution statistics with optimized percentile calculation.
 *
 * Performance optimizations:
 * - QuickSelect algorithm for O(N) average case percentile finding
 * - Single pass for mean and standard deviation
 * - Avoids full array sort O(N log N) when possible
 */
public class DistributionStatistics {
    private static final Logger LOG = Logger.getLogger(DistributionStatistics.class);

    private static final double[] PERCENTILES = { 0.0, 0.25, 0.5, 0.75, 0.9,
            0.95, 0.99, 1.0 };

    private static final int MINIMUM = 0;
    private static final int PERCENTILE_25TH = 1;
    private static final int MEDIAN = 2;
    private static final int PERCENTILE_75TH = 3;
    private static final int PERCENTILE_90TH = 4;
    private static final int PERCENTILE_95TH = 5;
    private static final int PERCENTILE_99TH = 6;
    private static final int MAXIMUM = 7;

    // Threshold below which to use full sort (small arrays are faster with sort)
    private static final int QUICKSELECT_THRESHOLD = 10000;

    private final int count;
    private final long[] percentiles;
    private final double average;
    private final double standardDeviation;

    // Thread-local Random for QuickSelect pivot selection
    private static final ThreadLocal<Random> RANDOM = ThreadLocal.withInitial(Random::new);

	public DistributionStatistics(int count, long[] percentiles,
			double average, double standardDeviation) {
		assert count > 0;
		assert percentiles.length == PERCENTILES.length;
		this.count = count;
		this.percentiles = Arrays.copyOfRange(percentiles, 0,
				PERCENTILES.length);
		this.average = average;
		this.standardDeviation = standardDeviation;
	}

    /**
     * Computes distribution statistics over values.
     * Optimized version using QuickSelect for large arrays.
     * WARNING: This will modify the input array.
     */
    public static DistributionStatistics computeStatistics(int[] values) {
        if (values.length == 0) {
            LOG.warn("cannot compute statistics for an empty list");
            long[] percentiles = new long[PERCENTILES.length];
            Arrays.fill(percentiles, -1);
            return new DistributionStatistics(0, percentiles, -1, -1);
        }

        // Single pass for sum (for average calculation)
        double sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < values.length; ++i) {
            sum += values[i];
            if (values[i] < min) min = values[i];
            if (values[i] > max) max = values[i];
        }
        double average = sum / values.length;

        // Single pass for standard deviation
        double sumDiffsSquared = 0;
        for (int i = 0; i < values.length; ++i) {
            double v = values[i] - average;
            sumDiffsSquared += v * v;
        }
        double standardDeviation = 0;
        if (values.length > 1) {
            standardDeviation = Math.sqrt(sumDiffsSquared / (values.length - 1));
        }

        long[] percentiles = new long[PERCENTILES.length];

        // For small arrays, use full sort (cache-friendly and low overhead)
        // For large arrays, use QuickSelect for better performance
        if (values.length <= QUICKSELECT_THRESHOLD) {
            // Small array: full sort is efficient
            Arrays.sort(values);
            for (int i = 0; i < percentiles.length; ++i) {
                int index = (int) (PERCENTILES[i] * values.length);
                if (index == values.length) index = values.length - 1;
                percentiles[i] = values[index];
            }
        } else {
            // Large array: use min/max directly and QuickSelect for middle percentiles
            percentiles[MINIMUM] = min;
            percentiles[MAXIMUM] = max;

            // QuickSelect for the remaining percentiles
            for (int i = 1; i < percentiles.length - 1; ++i) {
                int index = (int) (PERCENTILES[i] * values.length);
                if (index == values.length) index = values.length - 1;
                percentiles[i] = quickSelect(values, 0, values.length - 1, index);
            }
        }

        return new DistributionStatistics(values.length, percentiles, average, standardDeviation);
    }

    /**
     * QuickSelect algorithm to find the k-th smallest element.
     * Average case O(N), worst case O(N^2) but randomized pivot makes worst case very unlikely.
     */
    private static int quickSelect(int[] arr, int left, int right, int k) {
        if (left == right) {
            return arr[left];
        }

        Random rand = RANDOM.get();
        int pivotIndex = left + rand.nextInt(right - left + 1);
        pivotIndex = partition(arr, left, right, pivotIndex);

        if (k == pivotIndex) {
            return arr[k];
        } else if (k < pivotIndex) {
            return quickSelect(arr, left, pivotIndex - 1, k);
        } else {
            return quickSelect(arr, pivotIndex + 1, right, k);
        }
    }

    /**
     * Partition function for QuickSelect
     */
    private static int partition(int[] arr, int left, int right, int pivotIndex) {
        int pivotValue = arr[pivotIndex];
        // Move pivot to end
        swap(arr, pivotIndex, right);
        int storeIndex = left;

        for (int i = left; i < right; i++) {
            if (arr[i] < pivotValue) {
                swap(arr, storeIndex, i);
                storeIndex++;
            }
        }
        // Move pivot to its final place
        swap(arr, storeIndex, right);
        return storeIndex;
    }

    /**
     * Swap two elements in array
     */
    private static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

	public int getCount() {
		return count;
	}

	public double getAverage() {
		return average;
	}

	public double getStandardDeviation() {
		return standardDeviation;
	}

	public double getMinimum() {
		return percentiles[MINIMUM];
	}

	public double get25thPercentile() {
		return percentiles[PERCENTILE_25TH];
	}

	public double getMedian() {
		return percentiles[MEDIAN];
	}

	public double get75thPercentile() {
		return percentiles[PERCENTILE_75TH];
	}

	public double get90thPercentile() {
		return percentiles[PERCENTILE_90TH];
	}

	public double get95thPercentile() {
		return percentiles[PERCENTILE_95TH];
	}

	public double get99thPercentile() {
		return percentiles[PERCENTILE_99TH];
	}

	public double getMaximum() {
		return percentiles[MAXIMUM];
	}

	@Override
	public String toString() {
		// convert times to ms
		return "[min=" + getMinimum() / 1e6 + ", " + "25th="
				+ get25thPercentile() / 1e6 + ", " + "median="
				+ getMedian() / 1e6 + ", " + "avg=" + getAverage() / 1e6 + ", "
				+ "75th=" + get75thPercentile() / 1e6 + ", " + "90th="
				+ get90thPercentile() / 1e6 + ", " + "95th="
				+ get95thPercentile() / 1e6 + ", " + "99th="
				+ get99thPercentile() / 1e6 + ", " + "max=" + getMaximum()
				/ 1e6 + "]";
	}

	public Map<String, Double> toMap() {
		Map<String, Double> distMap = new LinkedHashMap<String, Double>();
		distMap.put("Minimum Latency (milliseconds)", getMinimum() / 1e3);
		distMap.put("25th Percentile Latency (milliseconds)", get25thPercentile() / 1e3);
		distMap.put("Median Latency (milliseconds)", getMedian() / 1e3);
		distMap.put("Average Latency (milliseconds)", getAverage() / 1e3);
		distMap.put("75th Percentile Latency (milliseconds)", get75thPercentile() / 1e3);
		distMap.put("90th Percentile Latency (milliseconds)", get90thPercentile() / 1e3);
		distMap.put("95th Percentile Latency (milliseconds)", get95thPercentile() / 1e3);
		distMap.put("99th Percentile Latency (milliseconds)", get99thPercentile() / 1e3);
		distMap.put("Maximum Latency (milliseconds)", getMaximum() / 1e3);
		return distMap;
	}
}
