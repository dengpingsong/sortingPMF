package reference.pmf;

import cpt204.project.model.Location;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Shared constants and small utilities for the reference PMF tool. */
final class ReferencePmfSupport {
    static final int MAX_EXACT_SIMULATION_N = 9;
    static final int MAX_EXACT_QUICKSORT_PMF_N = 144;
    static final int MAX_EXACT_MERGE_SORT_PMF_N = 256;
    static final int DEFAULT_RANDOM_TRIALS = 16;
    static final int DEFAULT_SIMULATION_SAMPLES = 2_000;
    static final int DEFAULT_THEORY_SAMPLES = 25_000;
    static final String DEFAULT_OUTPUT_PATH = "reference/sortingPmf/generated/sorting_pmf.csv";
    static final Comparator<Location> SYNTHETIC_COMPARATOR = Comparator.naturalOrder();
    static final String[] DATASET_PATHS = {
        "data/candidates_A.csv",
        "data/candidates_B.csv",
        "data/candidates_C.csv"
    };
    static final String[] DATASET_LABELS = {"A", "B", "C"};

    private ReferencePmfSupport() {
    }

    /** Ensures that the parent directory of one output path exists. */
    static void ensureParentDirectory(String outputPath) throws IOException {
        File outputFile = new File(outputPath);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Failed to create directory: " + parent);
        }
    }

    /** Derives the sidecar path by inserting _dataset_observations. */
    static String deriveDatasetObservationPath(String outputPath) {
        int extensionIndex = outputPath.lastIndexOf('.');
        int separatorIndex = Math.max(outputPath.lastIndexOf('/'), outputPath.lastIndexOf(File.separatorChar));

        if (extensionIndex > separatorIndex) {
            return outputPath.substring(0, extensionIndex)
                    + "_dataset_observations"
                    + outputPath.substring(extensionIndex);
        }
        return outputPath + "_dataset_observations.csv";
    }

    /** Escapes one CSV field when commas or quotes are present. */
    static String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** Formats observed comparison counts for CSV output. */
    static String formatObservedComparisons(double observedComparisons) {
        long rounded = Math.round(observedComparisons);
        if (Math.abs(observedComparisons - rounded) < 1e-9) {
            return Long.toString(rounded);
        }
        return String.format(java.util.Locale.US, "%.6f", observedComparisons);
    }

    /** Converts one exact count into a probability for CSV output. */
    static double probability(BigInteger occurrences, BigInteger totalSamples) {
        if (totalSamples.signum() == 0) {
            return 0.0;
        }
        return new BigDecimal(occurrences)
                .divide(new BigDecimal(totalSamples), MathContext.DECIMAL64)
                .doubleValue();
    }

    /** Computes n!. */
    static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    /** Sums every frequency in one count histogram. */
    static BigInteger sumOccurrences(Map<Long, BigInteger> counts) {
        BigInteger total = BigInteger.ZERO;
        for (BigInteger value : counts.values()) {
            total = total.add(value);
        }
        return total;
    }

    /** Deterministic seed mixer used across simulation and sampled-large PMFs. */
    static long mixSeed(int n, long permutationOrdinal, int trial, String algorithmName) {
        long seed = 1469598103934665603L;
        seed ^= n;
        seed *= 1099511628211L;
        seed ^= permutationOrdinal;
        seed *= 1099511628211L;
        seed ^= trial;
        seed *= 1099511628211L;
        seed ^= algorithmName.hashCode();
        return seed;
    }

    /** Adds every count from source into target. */
    static void mergeCounts(Map<Long, BigInteger> target, Map<Long, BigInteger> source) {
        for (Map.Entry<Long, BigInteger> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), BigInteger::add);
        }
    }

    /** Builds a synthetic rank-labelled input pool for permutation generation. */
    static Location[] createLocationPool(int n) {
        Location[] pool = new Location[n + 1];
        for (int rank = 1; rank <= n; rank++) {
            pool[rank] = new Location("P" + rank, n - rank);
        }
        return pool;
    }

    /** Materialises one permutation of the synthetic rank pool. */
    static List<Location> materialisePermutation(int[] permutation, Location[] pool) {
        List<Location> list = new ArrayList<>(permutation.length);
        for (int value : permutation) {
            list.add(pool[value]);
        }
        return list;
    }

    /** Returns the identity permutation 1..n. */
    static int[] createInitialPermutation(int n) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i + 1;
        }
        return permutation;
    }

    /** Generates sampled random permutations for large-n simulation. */
    static List<int[]> createSampledPermutations(int n, int samples) {
        List<int[]> sampledPermutations = new ArrayList<>(samples);
        Random random = new Random(mixSeed(n, samples, 0, "reference_sampled_permutations"));

        for (int sampleIndex = 0; sampleIndex < samples; sampleIndex++) {
            int[] permutation = createInitialPermutation(n);
            shufflePermutation(permutation, random);
            sampledPermutations.add(permutation);
        }

        return sampledPermutations;
    }

    /** Fisher-Yates shuffle for one permutation array. */
    static void shufflePermutation(int[] permutation, Random random) {
        for (int i = permutation.length - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            swap(permutation, i, swapIndex);
        }
    }

    /** Swaps two positions in one permutation array. */
    static void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    /** Describes sampled simulation sources for CSV metadata. */
    static String sampledSimulationModel(AlgorithmSpec spec) {
        if (spec.randomised) {
            return "uniform_random_permutation_samples_x_random_pivot_samples_simulation";
        }
        return "uniform_random_permutation_samples_simulation";
    }
}