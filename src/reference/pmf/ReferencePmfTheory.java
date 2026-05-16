package reference.pmf;

import reference.pmf.math.MultiModCrtConvolution;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Model-level PMF/count generation for the reference PMF tool. */
final class ReferencePmfTheory {

    private static final long NAIVE_CONVOLUTION_WORK_THRESHOLD = 8_192L;

    private static final List<DenseDistribution> MERGE_SORT_EXACT_CACHE = new ArrayList<>();
    private static final List<BigInteger[]> BINOMIAL_ROWS = new ArrayList<>();
    private static final Map<Long, DenseDistribution> MERGE_STEP_CACHE = new HashMap<>();
    private static final QuickSortExactEngine QUICK_SORT_EXACT_ENGINE = new QuickSortExactEngine();

    static {
        MERGE_SORT_EXACT_CACHE.add(DenseDistribution.singlePoint(0L));
        MERGE_SORT_EXACT_CACHE.add(DenseDistribution.singlePoint(0L));
        BINOMIAL_ROWS.add(new BigInteger[]{BigInteger.ONE});
    }

    private ReferencePmfTheory() {
    }

    /**
     * Generates direct PMF sources for one input size.
     *
     * <p>Bubble sort always stays exact. First/last-pivot quick sort stay exact
     * up to MAX_EXACT_QUICKSORT_PMF_N; above that they switch to the sampled-large
     * recurrence model. This keeps a model-level PMF available for large plots
     * without pretending that the result is exact combinatorial data.</p>
     */
    static List<OutputDistribution> analysePmfForN(int n, List<AlgorithmSpec> specs,
                                                   ReferencePmfConfig config) {
        List<OutputDistribution> outputs = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        BigInteger permutationCount = ReferencePmfSupport.factorial(n);
        Map<Long, BigInteger> sharedQuickSortExactCounts = null;
        Map<Long, BigInteger> sharedQuickSortSampledCounts = null;
        BigInteger sharedQuickSortSampleTotal = null;
        boolean announcedForcedQuickSortExact = false;

        for (AlgorithmSpec spec : specs) {
            boolean forceQuickSortExact = config.forceExactQuickSortPmf
                    && spec.sharedQuickSortTheory
                    && spec.pmfCounterFactory != null;
            if (spec.supportsExactPmf(n) || forceQuickSortExact) {
                Map<Long, BigInteger> counts;
                if (spec.sharedQuickSortTheory) {
                    if (sharedQuickSortExactCounts == null) {
                        if (forceQuickSortExact && n > spec.maxExactPmfN && !announcedForcedQuickSortExact) {
                            System.out.println("  forcing exact textbook quick-sort PMF above safe cutoff n="
                                    + spec.maxExactPmfN + "...");
                            announcedForcedQuickSortExact = true;
                        }
                        sharedQuickSortExactCounts = countQuickSortFirstComparisonsTheory(n);
                    }
                    counts = sharedQuickSortExactCounts;
                } else {
                    counts = spec.pmfCounterFactory.create(n);
                }
                validateExactCounts(spec.name, counts, permutationCount);
                outputs.add(new OutputDistribution(
                        spec.name,
                        counts,
                        permutationCount,
                        0,
                        "pmf",
                        spec.exactPmfModel));
                continue;
            }

            if (config.exactPmfOnly) {
                if (config.forceExactQuickSortPmf && spec.sharedQuickSortTheory && spec.pmfCounterFactory != null) {
                    skipped.add(spec.name + " (exact forced but unavailable)");
                    continue;
                }
                skipped.add(spec.name + " (exact <= " + spec.maxExactPmfN + ", requested n=" + n + ")");
                continue;
            }

            if (spec.sharedQuickSortTheory && spec.supportsSampledPmf()) {
                if (sharedQuickSortSampledCounts == null) {
                    System.out.println("  sampling textbook quick-sort PMF with "
                            + config.theorySamples + " recurrence draws...");
                    sharedQuickSortSampledCounts = spec.sampledPmfCounterFactory.create(n, config.theorySamples);
                    sharedQuickSortSampleTotal = ReferencePmfSupport.sumOccurrences(sharedQuickSortSampledCounts);
                }
                outputs.add(new OutputDistribution(
                        spec.name,
                        sharedQuickSortSampledCounts,
                        sharedQuickSortSampleTotal,
                        0,
                        "pmf",
                        spec.sampledPmfModel));
                continue;
            }

            if (spec.supportsSampledPmf()) {
                System.out.println("  sampling " + spec.name + " PMF with "
                        + config.theorySamples + " recurrence draws...");
                Map<Long, BigInteger> sampledCounts = spec.sampledPmfCounterFactory.create(n, config.theorySamples);
                outputs.add(new OutputDistribution(
                        spec.name,
                        sampledCounts,
                        ReferencePmfSupport.sumOccurrences(sampledCounts),
                        0,
                        "pmf",
                        spec.sampledPmfModel));
                continue;
            }

            skipped.add(spec.name);
        }

        if (!skipped.isEmpty()) {
            System.out.println("  PMF formula unavailable for: " + String.join(", ", skipped));
        }
        return outputs;
    }

    /** Exact PMF/counts for optimised bubble sort under the uniform model. */
    static Map<Long, BigInteger> countOptimisedBubbleComparisonsTheory(int n) {
        Map<Long, BigInteger> counts = new TreeMap<>();
        if (n <= 1) {
            counts.put(0L, BigInteger.ONE);
            return counts;
        }

        for (int passes = 1; passes < n; passes++) {
            long comparisons = comparisonCountFromPasses(n, passes);
            BigInteger occurrences = exactPassCountOccurrencesTheory(n, passes);
            counts.merge(comparisons, occurrences, BigInteger::add);
        }
        return counts;
    }

    /** Exact PMF/counts for non-optimised bubble sort under the uniform model. */
    static Map<Long, BigInteger> countNonOptimisedBubbleComparisonsTheory(int n) {
        Map<Long, BigInteger> counts = new TreeMap<>();
        counts.put(nonOptimisedBubbleComparisonCount(n), ReferencePmfSupport.factorial(n));
        return counts;
    }

    /**
     * Exact recursive PMF/counts for the project merge-sort implementation.
     *
     * <p>The split sizes are deterministic because the implementation always
     * uses {@code mid = n / 2}. The only randomness under the uniform
     * permutation model comes from which global ranks land in the left half,
     * which is equivalent to a uniform binary interleaving during each merge.
     * This method caches every size up to {@code n} so range generation does not
     * recompute smaller merge-sort PMFs from scratch.</p>
     */
    static synchronized Map<Long, BigInteger> countMergeSortComparisonsTheory(int n) {
        ensureMergeSortExactCache(n);
        return MERGE_SORT_EXACT_CACHE.get(n).toMap();
    }

    /**
     * Exact textbook first-pivot quick-sort counts for modest n.
     *
     * <p>The current implementation delegates to a dedicated quick-sort exact
     * engine so large-n work can evolve independently of the rest of the PMF
     * theory helpers.</p>
     */
    static synchronized Map<Long, BigInteger> countQuickSortFirstComparisonsTheory(int n) {
        return QUICK_SORT_EXACT_ENGINE.countFirstPivotComparisons(n);
    }

    /**
     * Sampled-large direct PMF for textbook first-pivot quick sort.
     *
     * <p>Each sample draws pivot ranks directly from the uniform recurrence,
     * producing exact histogram counts over the chosen sample budget. This is a
     * model-level PMF source and does not call the concrete quick-sort
     * implementation.</p>
     */
    static Map<Long, BigInteger> sampleTextbookQuickSortPmf(int n, int sampleCount) {
        int workerCount = Math.min(Math.max(1, Runtime.getRuntime().availableProcessors()), sampleCount);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        try {
            List<CompletableFuture<Map<Long, BigInteger>>> futures = new ArrayList<>();
            int baseSamples = sampleCount / workerCount;
            int remainder = sampleCount % workerCount;

            for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
                int samplesForWorker = baseSamples + (workerIndex < remainder ? 1 : 0);
                if (samplesForWorker == 0) {
                    continue;
                }

                final int worker = workerIndex;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    Random rng = new Random(ReferencePmfSupport.mixSeed(n, worker, samplesForWorker,
                            "reference_textbook_quicksort_pmf_samples"));
                    Map<Long, BigInteger> localCounts = new TreeMap<>();
                    for (int sampleIndex = 0; sampleIndex < samplesForWorker; sampleIndex++) {
                        long comparisons = simulateTextbookQuickSortComparisons(n, rng);
                        localCounts.merge(comparisons, BigInteger.ONE, BigInteger::add);
                    }
                    return localCounts;
                }, executor));
            }

            Map<Long, BigInteger> counts = new TreeMap<>();
            for (CompletableFuture<Map<Long, BigInteger>> future : futures) {
                ReferencePmfSupport.mergeCounts(counts, future.join());
            }
            return counts;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Sampled-large direct PMF for merge sort under the uniform permutation model.
     *
     * <p>Each sample draws the recursive merge structure directly instead of
     * calling the concrete {@code MergeSort} implementation. This keeps a PMF
     * source available once the exact recursive DP becomes too expensive.</p>
     */
    static Map<Long, BigInteger> sampleTextbookMergeSortPmf(int n, int sampleCount) {
        int workerCount = Math.min(Math.max(1, Runtime.getRuntime().availableProcessors()), sampleCount);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        try {
            List<CompletableFuture<Map<Long, BigInteger>>> futures = new ArrayList<>();
            int baseSamples = sampleCount / workerCount;
            int remainder = sampleCount % workerCount;

            for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
                int samplesForWorker = baseSamples + (workerIndex < remainder ? 1 : 0);
                if (samplesForWorker == 0) {
                    continue;
                }

                final int worker = workerIndex;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    Random rng = new Random(ReferencePmfSupport.mixSeed(n, worker, samplesForWorker,
                            "reference_textbook_mergesort_pmf_samples"));
                    Map<Long, BigInteger> localCounts = new TreeMap<>();
                    for (int sampleIndex = 0; sampleIndex < samplesForWorker; sampleIndex++) {
                        long comparisons = simulateTextbookMergeSortComparisons(n, rng);
                        localCounts.merge(comparisons, BigInteger.ONE, BigInteger::add);
                    }
                    return localCounts;
                }, executor));
            }

            Map<Long, BigInteger> counts = new TreeMap<>();
            for (CompletableFuture<Map<Long, BigInteger>> future : futures) {
                ReferencePmfSupport.mergeCounts(counts, future.join());
            }
            return counts;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * One draw from the textbook first-pivot recurrence.
     *
     * <p>Choosing the first pivot on a uniform random permutation is equivalent
     * to drawing a pivot rank uniformly from 1..size. The iterative stack avoids
     * deep recursion for unlucky samples.</p>
     */
    static long simulateTextbookQuickSortComparisons(int n, Random rng) {
        if (n <= 1) {
            return 0L;
        }

        int[] stack = new int[Math.max(2, n)];
        int top = 0;
        stack[top++] = n;
        long comparisons = 0L;

        while (top > 0) {
            int size = stack[--top];
            if (size <= 1) {
                continue;
            }

            comparisons += size - 1L;
            int leftSize = rng.nextInt(size);
            int rightSize = size - 1 - leftSize;

            if (leftSize > 1) {
                stack[top++] = leftSize;
            }
            if (rightSize > 1) {
                stack[top++] = rightSize;
            }
        }

        return comparisons;
    }

    /** One sampled draw from the recursive merge-sort comparison model. */
    static long simulateTextbookMergeSortComparisons(int n, Random rng) {
        if (n <= 1) {
            return 0L;
        }

        int leftSize = n / 2;
        int rightSize = n - leftSize;
        return simulateTextbookMergeSortComparisons(leftSize, rng)
                + simulateTextbookMergeSortComparisons(rightSize, rng)
                + sampleMergeComparisons(leftSize, rightSize, rng);
    }

    /** Ensures that an exact count distribution sums to n!. */
    private static void validateExactCounts(String algorithmName, Map<Long, BigInteger> counts,
                                            BigInteger permutationCount) {
        BigInteger totalOccurrences = ReferencePmfSupport.sumOccurrences(counts);
        if (!totalOccurrences.equals(permutationCount)) {
            throw new IllegalStateException(
                    "PMF counts for " + algorithmName + " do not sum to n!: "
                            + totalOccurrences + " != " + permutationCount);
        }
    }

    /** Comparison count function for optimised bubble sort. */
    private static long comparisonCountFromPasses(int n, int passes) {
        return (long) n * passes - (long) passes * (passes + 1) / 2;
    }

    /** Deterministic comparison count for non-optimised bubble sort. */
    private static long nonOptimisedBubbleComparisonCount(int n) {
        return (long) n * (n - 1) / 2;
    }

    /** Closed-form cumulative count for the optimised bubble-sort drop statistic. */
    private static BigInteger cumulativeMaxDropCount(int n, int dropLimit) {
        if (dropLimit < 0) {
            return BigInteger.ZERO;
        }
        if (n <= 1) {
            return BigInteger.ONE;
        }
        if (dropLimit >= n - 1) {
            return ReferencePmfSupport.factorial(n);
        }
        return ReferencePmfSupport.factorial(dropLimit)
                .multiply(BigInteger.valueOf(dropLimit + 1L).pow(n - dropLimit));
    }

    /** Exact occurrences of one optimised bubble-sort pass count. */
    private static BigInteger exactPassCountOccurrencesTheory(int n, int passes) {
        if (n <= 1) {
            return passes == 0 ? BigInteger.ONE : BigInteger.ZERO;
        }
        if (passes < 1 || passes > n - 1) {
            return BigInteger.ZERO;
        }
        if (passes == 1) {
            return cumulativeMaxDropCount(n, 0);
        }
        if (passes == n - 1) {
            return ReferencePmfSupport.factorial(n).subtract(cumulativeMaxDropCount(n, n - 3));
        }

        int drop = passes - 1;
        return cumulativeMaxDropCount(n, drop).subtract(cumulativeMaxDropCount(n, drop - 1));
    }

    private static void ensureMergeSortExactCache(int n) {
        if (n < MERGE_SORT_EXACT_CACHE.size()) {
            return;
        }

        ensureBinomialRows(n);
        for (int size = MERGE_SORT_EXACT_CACHE.size(); size <= n; size++) {
            int leftSize = size / 2;
            int rightSize = size - leftSize;
            DenseDistribution childDistribution = convolve(
                    MERGE_SORT_EXACT_CACHE.get(leftSize),
                    MERGE_SORT_EXACT_CACHE.get(rightSize));
            DenseDistribution mergeDistribution = mergeStepDistribution(leftSize, rightSize);
            MERGE_SORT_EXACT_CACHE.add(convolve(childDistribution, mergeDistribution));
        }
    }

    private static void ensureBinomialRows(int maxRow) {
        for (int rowIndex = BINOMIAL_ROWS.size(); rowIndex <= maxRow; rowIndex++) {
            BigInteger[] previousRow = BINOMIAL_ROWS.get(rowIndex - 1);
            BigInteger[] row = createZeroArray(rowIndex + 1);
            row[0] = BigInteger.ONE;
            row[rowIndex] = BigInteger.ONE;
            for (int column = 1; column < rowIndex; column++) {
                row[column] = previousRow[column - 1].add(previousRow[column]);
            }
            BINOMIAL_ROWS.add(row);
        }
    }

    private static DenseDistribution mergeStepDistribution(int leftSize, int rightSize) {
        if (leftSize == 0 || rightSize == 0) {
            return DenseDistribution.singlePoint(0L);
        }

        int smaller = Math.min(leftSize, rightSize);
        int larger = Math.max(leftSize, rightSize);
        long cacheKey = (((long) smaller) << 32) | (larger & 0xffffffffL);
        DenseDistribution cached = MERGE_STEP_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        BigInteger[] counts = createZeroArray(larger);
        for (int comparisons = smaller; comparisons <= leftSize + rightSize - 1; comparisons++) {
            BigInteger occurrences = binomial(comparisons - 1, leftSize - 1)
                    .add(binomial(comparisons - 1, rightSize - 1));
            counts[comparisons - smaller] = occurrences;
        }

        DenseDistribution distribution = new DenseDistribution(
            smaller,
            counts,
            binomial(leftSize + rightSize, leftSize));
        MERGE_STEP_CACHE.put(cacheKey, distribution);
        return distribution;
    }

    private static BigInteger binomial(int n, int k) {
        if (k < 0 || k > n) {
            return BigInteger.ZERO;
        }
        return BINOMIAL_ROWS.get(n)[k];
    }

    private static DenseDistribution convolve(DenseDistribution left, DenseDistribution right) {
        BigInteger totalOccurrences = left.totalOccurrences.multiply(right.totalOccurrences);
        BigInteger[] counts;
        long naiveWork = (long) left.counts.length * right.counts.length;

        if (naiveWork <= NAIVE_CONVOLUTION_WORK_THRESHOLD) {
            counts = convolveNaively(left.counts, right.counts);
        } else {
            counts = MultiModCrtConvolution.convolveExact(
                    left.counts,
                    right.counts,
                    totalOccurrences,
                    Runtime.getRuntime().availableProcessors());
        }

        return new DenseDistribution(left.minComparisons + right.minComparisons, counts, totalOccurrences);
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

    private static long sampleMergeComparisons(int leftSize, int rightSize, Random rng) {
        int leftRemaining = leftSize;
        int rightRemaining = rightSize;
        long comparisons = 0L;

        while (leftRemaining > 0 && rightRemaining > 0) {
            int totalRemaining = leftRemaining + rightRemaining;
            if (rng.nextInt(totalRemaining) < leftRemaining) {
                leftRemaining--;
            } else {
                rightRemaining--;
            }
            comparisons++;
        }

        return comparisons;
    }

    private static BigInteger[] createZeroArray(int length) {
        BigInteger[] counts = new BigInteger[length];
        for (int index = 0; index < length; index++) {
            counts[index] = BigInteger.ZERO;
        }
        return counts;
    }

    private static final class DenseDistribution {
        final long minComparisons;
        final BigInteger[] counts;
        final BigInteger totalOccurrences;

        DenseDistribution(long minComparisons, BigInteger[] counts, BigInteger totalOccurrences) {
            this.minComparisons = minComparisons;
            this.counts = counts;
            this.totalOccurrences = totalOccurrences;
        }

        static DenseDistribution singlePoint(long comparisons) {
            return new DenseDistribution(comparisons, new BigInteger[]{BigInteger.ONE}, BigInteger.ONE);
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