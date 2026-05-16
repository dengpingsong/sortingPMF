package reference.pmf;

import cpt204.project.io.CsvReader;
import cpt204.project.model.Location;
import cpt204.project.sort.SortingAlgorithm;
import cpt204.project.util.ComparisonCountingComparator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Implementation-level simulation services for the reference PMF tool. */
final class ReferencePmfSimulation {

    private ReferencePmfSimulation() {
    }

    /** Chooses exact enumeration or sampled simulation depending on n. */
    static List<OutputDistribution> analyseSimulationForN(int n, List<AlgorithmSpec> specs,
                                                          ReferencePmfConfig config) {
        if (n <= ReferencePmfSupport.MAX_EXACT_SIMULATION_N || config.forceExactSimulation) {
            if (n > ReferencePmfSupport.MAX_EXACT_SIMULATION_N) {
                System.out.println("  forcing exact enumeration above n="
                        + ReferencePmfSupport.MAX_EXACT_SIMULATION_N + "; this may be extremely slow...");
            }
            return analyseExactSimulationForN(n, specs);
        }

        System.out.println("  using sampled simulation with " + config.simulationSamples
                + " random permutations...");
        return analyseSampledSimulationForN(n, specs, config.simulationSamples);
    }

    /** Computes where the real coursework datasets fall under each algorithm. */
    static List<DatasetObservation> analyseCurrentDatasets(List<AlgorithmSpec> specs) {
        List<DatasetObservation> observations = new ArrayList<>();

        for (int index = 0; index < ReferencePmfSupport.DATASET_PATHS.length; index++) {
            String datasetLabel = ReferencePmfSupport.DATASET_LABELS[index];
            List<Location> original = CsvReader.readLocations(ReferencePmfSupport.DATASET_PATHS[index]);
            String orderProfile = classifyOrderProfile(original);

            for (AlgorithmSpec spec : specs) {
                observations.add(observeDataset(spec, datasetLabel, original, orderProfile));
            }
        }

        return observations;
    }

    /** Enumerates every permutation exactly; intended only for very small n. */
    private static List<OutputDistribution> analyseExactSimulationForN(int n,
                                                                       List<AlgorithmSpec> specs) {
        List<DistributionAccumulator> accumulators = new ArrayList<>();
        for (AlgorithmSpec spec : specs) {
            accumulators.add(new DistributionAccumulator(spec));
        }

        Location[] pool = ReferencePmfSupport.createLocationPool(n);
        int[] permutation = ReferencePmfSupport.createInitialPermutation(n);
        long[] permutationOrdinal = {0L};

        enumeratePermutations(permutation, 0, current -> {
            long ordinal = permutationOrdinal[0]++;
            for (DistributionAccumulator accumulator : accumulators) {
                for (int trial = 0; trial < accumulator.spec.trialsPerPermutation; trial++) {
                    List<Location> input = ReferencePmfSupport.materialisePermutation(current, pool);
                    accumulator.comparator.reset();

                    SortingAlgorithm sorter = accumulator.spec.randomised
                            ? accumulator.spec.factory.create(
                            ReferencePmfSupport.mixSeed(n, ordinal, trial, accumulator.spec.name))
                            : accumulator.deterministicSorter;

                    sorter.sort(input, accumulator.comparator);
                    long comparisons = accumulator.comparator.getComparisonCount();
                    accumulator.counts.merge(comparisons, BigInteger.ONE, BigInteger::add);
                }
            }
        });

        BigInteger permutationCount = ReferencePmfSupport.factorial(n);
        List<OutputDistribution> outputs = new ArrayList<>();
        for (DistributionAccumulator accumulator : accumulators) {
            BigInteger totalSamples = permutationCount.multiply(
                    BigInteger.valueOf(accumulator.spec.trialsPerPermutation));
            outputs.add(new OutputDistribution(
                    accumulator.spec.name,
                    accumulator.counts,
                    totalSamples,
                    accumulator.spec.trialsPerPermutation,
                    "simulation",
                    accumulator.spec.simulationModel));
        }
        return outputs;
    }

    /** Samples random permutations but still executes the real project sorting code. */
    private static List<OutputDistribution> analyseSampledSimulationForN(int n,
                                                                         List<AlgorithmSpec> specs,
                                                                         int simulationSamples) {
        Location[] pool = ReferencePmfSupport.createLocationPool(n);
        List<int[]> sampledPermutations = ReferencePmfSupport.createSampledPermutations(n, simulationSamples);
        int poolSize = Math.min(specs.size(), Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        try {
            List<CompletableFuture<OutputDistribution>> futures = new ArrayList<>();
            for (AlgorithmSpec spec : specs) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> sampleDistributionForSpec(spec, n, pool, sampledPermutations),
                        executor));
            }

            List<OutputDistribution> outputs = new ArrayList<>();
            for (CompletableFuture<OutputDistribution> future : futures) {
                outputs.add(future.join());
            }
            return outputs;
        } finally {
            executor.shutdown();
        }
    }

    /** Samples one simulation distribution by repeatedly calling the real sort implementation. */
    private static OutputDistribution sampleDistributionForSpec(AlgorithmSpec spec, int n,
                                                                Location[] pool,
                                                                List<int[]> sampledPermutations) {
        ComparisonCountingComparator<Location> comparator =
                new ComparisonCountingComparator<>(ReferencePmfSupport.SYNTHETIC_COMPARATOR);
        Map<Long, BigInteger> counts = new TreeMap<>();
        SortingAlgorithm deterministicSorter = spec.randomised ? null : spec.factory.create(0L);

        for (int sampleIndex = 0; sampleIndex < sampledPermutations.size(); sampleIndex++) {
            int[] permutation = sampledPermutations.get(sampleIndex);
            long ordinal = sampleIndex;

            for (int trial = 0; trial < spec.trialsPerPermutation; trial++) {
                List<Location> input = ReferencePmfSupport.materialisePermutation(permutation, pool);
                comparator.reset();

                SortingAlgorithm sorter = spec.randomised
                        ? spec.factory.create(ReferencePmfSupport.mixSeed(n, ordinal, trial, spec.name))
                        : deterministicSorter;

                sorter.sort(input, comparator);
                long comparisons = comparator.getComparisonCount();
                counts.merge(comparisons, BigInteger.ONE, BigInteger::add);
            }
        }

        BigInteger totalSamples = BigInteger.valueOf(sampledPermutations.size())
                .multiply(BigInteger.valueOf(spec.trialsPerPermutation));
        return new OutputDistribution(
                spec.name,
                counts,
                totalSamples,
                spec.trialsPerPermutation,
                "simulation",
                ReferencePmfSupport.sampledSimulationModel(spec));
    }

    /** Measures one real dataset against one algorithm. */
    private static DatasetObservation observeDataset(AlgorithmSpec spec, String datasetLabel,
                                                     List<Location> original,
                                                     String orderProfile) {
        ComparisonCountingComparator<Location> comparator =
                new ComparisonCountingComparator<>(ReferencePmfSupport.SYNTHETIC_COMPARATOR);

        if (spec.randomised) {
            long totalComparisons = 0L;
            for (int trial = 0; trial < spec.trialsPerPermutation; trial++) {
                List<Location> copy = new ArrayList<>(original);
                comparator.reset();
                SortingAlgorithm sorter = spec.factory.create(
                        ReferencePmfSupport.mixSeed(original.size(), datasetLabel.hashCode(), trial, spec.name));
                sorter.sort(copy, comparator);
                totalComparisons += comparator.getComparisonCount();
            }
            return new DatasetObservation(
                    datasetLabel,
                    original.size(),
                    orderProfile,
                    spec.name,
                    (double) totalComparisons / spec.trialsPerPermutation,
                    "current_dataset_random_trial_average");
        }

        List<Location> copy = new ArrayList<>(original);
        comparator.reset();
        spec.factory.create(0L).sort(copy, comparator);
        return new DatasetObservation(
                datasetLabel,
                original.size(),
                orderProfile,
                spec.name,
                comparator.getComparisonCount(),
                "current_dataset_exact_comparisons");
    }

    /** Produces a human-readable order profile for sidecar annotation. */
    private static String classifyOrderProfile(List<Location> input) {
        if (input.size() <= 1) {
            return "trivial";
        }

        boolean alreadySorted = true;
        boolean reverseSorted = true;
        boolean allEqualPriority = true;
        int descents = 0;

        for (int i = 0; i < input.size() - 1; i++) {
            Location current = input.get(i);
            Location next = input.get(i + 1);
            int cmp = ReferencePmfSupport.SYNTHETIC_COMPARATOR.compare(current, next);

            if (cmp > 0) {
                alreadySorted = false;
                descents++;
            }
            if (cmp < 0) {
                reverseSorted = false;
            }
            if (current.getPriorityScore() != next.getPriorityScore()) {
                allEqualPriority = false;
            }
        }

        if (alreadySorted && allEqualPriority) {
            return "already_sorted_equal_priority";
        }
        if (alreadySorted) {
            return "already_sorted";
        }
        if (reverseSorted) {
            return "reverse_sorted";
        }
        if (descents <= Math.max(1, input.size() / 20)) {
            return "mostly_sorted";
        }
        if (allEqualPriority) {
            return "equal_priority_mixed_order";
        }
        return "mixed_order";
    }

    /** Enumerates every permutation of one array in-place. */
    private static void enumeratePermutations(int[] permutation, int index,
                                              PermutationConsumer consumer) {
        if (index == permutation.length) {
            consumer.accept(permutation);
            return;
        }

        for (int i = index; i < permutation.length; i++) {
            ReferencePmfSupport.swap(permutation, index, i);
            enumeratePermutations(permutation, index + 1, consumer);
            ReferencePmfSupport.swap(permutation, index, i);
        }
    }

    /** Small helper to accumulate exact simulation counts. */
    private static final class DistributionAccumulator {
        final AlgorithmSpec spec;
        final ComparisonCountingComparator<Location> comparator;
        final Map<Long, BigInteger> counts;
        final SortingAlgorithm deterministicSorter;

        DistributionAccumulator(AlgorithmSpec spec) {
            this.spec = spec;
            this.comparator = new ComparisonCountingComparator<>(ReferencePmfSupport.SYNTHETIC_COMPARATOR);
            this.counts = new TreeMap<>();
            this.deterministicSorter = spec.randomised ? null : spec.factory.create(0L);
        }
    }

    @FunctionalInterface
    private interface PermutationConsumer {
        void accept(int[] permutation);
    }
}