package reference.pmf;

import reference.pmf.math.AdaptiveLongMultiModCrtConvolution;
import reference.pmf.math.MultiModCrtConvolution;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dedicated exact first-pivot quick-sort PMF engine.
 *
 * <p>This class intentionally owns the quick-sort-specific exact DP so the
 * rest of {@link ReferencePmfTheory} can stay stable while a future large-n
 * solver replaces this dense legacy engine.</p>
 */
final class QuickSortExactEngine {

    private static final long NAIVE_CONVOLUTION_WORK_THRESHOLD = 8_192L;

    private final List<ExactDistribution> exactCache = new ArrayList<>();
    private final List<BigInteger[]> binomialRows = new ArrayList<>();

    QuickSortExactEngine() {
        exactCache.add(ExactDistribution.singlePoint(0L));
        exactCache.add(ExactDistribution.singlePoint(0L));
        binomialRows.add(new BigInteger[]{BigInteger.ONE});
    }

    /** Returns exact first-pivot quick-sort counts for one input size. */
    synchronized Map<Long, BigInteger> countFirstPivotComparisons(int n) {
        ensureExactCache(n);
        return exactCache.get(n).toMap();
    }

    private void ensureExactCache(int n) {
        if (n < exactCache.size()) {
            return;
        }

        ensureBinomialRows(Math.max(0, n - 1));
        for (int size = exactCache.size(); size <= n; size++) {
            int leftBalanced = (size - 1) / 2;
            int rightBalanced = size - 1 - leftBalanced;
            long minComparisons = size - 1L
                    + exactCache.get(leftBalanced).minComparisons
                    + exactCache.get(rightBalanced).minComparisons;
            long maxComparisons = size - 1L + exactCache.get(size - 1).maxComparisons();
            BigInteger[] counts = createZeroArray((int) (maxComparisons - minComparisons + 1L));
            BigInteger[] binomialRow = buildBinomialRow(size - 1);
            int maxLeftSize = (size - 1) / 2;

            for (int leftSize = 0; leftSize <= maxLeftSize; leftSize++) {
                int rightSize = size - 1 - leftSize;
                ExactDistribution childDistribution = convolve(
                        exactCache.get(leftSize),
                        exactCache.get(rightSize));
                BigInteger weight = binomialRow[leftSize];
                if (leftSize != rightSize) {
                    weight = weight.shiftLeft(1);
                }

                accumulateScaled(
                        counts,
                        minComparisons,
                        size - 1L + childDistribution.minComparisons,
                        childDistribution.counts,
                        weight);
            }

            exactCache.add(new ExactDistribution(minComparisons, counts, ReferencePmfSupport.factorial(size)));
        }
    }

    private BigInteger[] buildBinomialRow(int n) {
        ensureBinomialRows(n);
        return binomialRows.get(n);
    }

    private void ensureBinomialRows(int maxRow) {
        for (int rowIndex = binomialRows.size(); rowIndex <= maxRow; rowIndex++) {
            BigInteger[] previousRow = binomialRows.get(rowIndex - 1);
            BigInteger[] row = createZeroArray(rowIndex + 1);
            row[0] = BigInteger.ONE;
            row[rowIndex] = BigInteger.ONE;
            for (int column = 1; column < rowIndex; column++) {
                row[column] = previousRow[column - 1].add(previousRow[column]);
            }
            binomialRows.add(row);
        }
    }

    private ExactDistribution convolve(ExactDistribution left, ExactDistribution right) {
        BigInteger totalOccurrences = left.totalOccurrences.multiply(right.totalOccurrences);
        BigInteger[] counts;
        long naiveWork = (long) left.counts.length * right.counts.length;

        if (naiveWork <= NAIVE_CONVOLUTION_WORK_THRESHOLD) {
            counts = convolveNaively(left.counts, right.counts);
        } else {
            counts = convolveExactly(left.counts, right.counts, totalOccurrences);
        }

        return new ExactDistribution(left.minComparisons + right.minComparisons, counts, totalOccurrences);
    }

    private BigInteger[] convolveExactly(BigInteger[] left, BigInteger[] right, BigInteger coefficientBound) {
        try {
            return MultiModCrtConvolution.convolveExact(
                    left,
                    right,
                    coefficientBound,
                    Runtime.getRuntime().availableProcessors());
        } catch (IllegalStateException ex) {
            return AdaptiveLongMultiModCrtConvolution.convolveExact(
                    left,
                    right,
                    coefficientBound,
                    Runtime.getRuntime().availableProcessors());
        }
    }

    private static BigInteger[] convolveNaively(BigInteger[] left, BigInteger[] right) {
        BigInteger[] counts = createZeroArray(left.length + right.length - 1);

        for (int leftIndex = 0; leftIndex < left.length; leftIndex++) {
            BigInteger leftCount = left[leftIndex];
            if (leftCount.signum() == 0) {
                continue;
            }
            for (int rightIndex = 0; rightIndex < right.length; rightIndex++) {
                BigInteger rightCount = right[rightIndex];
                if (rightCount.signum() == 0) {
                    continue;
                }
                int combinedIndex = leftIndex + rightIndex;
                counts[combinedIndex] = counts[combinedIndex].add(leftCount.multiply(rightCount));
            }
        }

        return counts;
    }

    private static void accumulateScaled(BigInteger[] target, long targetMinComparisons,
                                         long sourceMinComparisons, BigInteger[] sourceCounts,
                                         BigInteger weight) {
        int startIndex = (int) (sourceMinComparisons - targetMinComparisons);
        for (int index = 0; index < sourceCounts.length; index++) {
            BigInteger sourceCount = sourceCounts[index];
            if (sourceCount.signum() == 0) {
                continue;
            }
            int targetIndex = startIndex + index;
            target[targetIndex] = target[targetIndex].add(sourceCount.multiply(weight));
        }
    }

    private static BigInteger[] createZeroArray(int length) {
        BigInteger[] counts = new BigInteger[length];
        for (int index = 0; index < length; index++) {
            counts[index] = BigInteger.ZERO;
        }
        return counts;
    }

    private static final class ExactDistribution {
        final long minComparisons;
        final BigInteger[] counts;
        final BigInteger totalOccurrences;

        ExactDistribution(long minComparisons, BigInteger[] counts, BigInteger totalOccurrences) {
            this.minComparisons = minComparisons;
            this.counts = counts;
            this.totalOccurrences = totalOccurrences;
        }

        static ExactDistribution singlePoint(long comparisons) {
            return new ExactDistribution(comparisons, new BigInteger[]{BigInteger.ONE}, BigInteger.ONE);
        }

        long maxComparisons() {
            return minComparisons + counts.length - 1L;
        }

        Map<Long, BigInteger> toMap() {
            Map<Long, BigInteger> output = new TreeMap<>();
            for (int index = 0; index < counts.length; index++) {
                if (counts[index].signum() != 0) {
                    output.put(minComparisons + index, counts[index]);
                }
            }
            return output;
        }
    }
}