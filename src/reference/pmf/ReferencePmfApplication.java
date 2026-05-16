package reference.pmf;

import cpt204.project.sort.BubbleSort;
import cpt204.project.sort.MergeSort;
import cpt204.project.sort.QuickSort;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import reference.pmf.math.MultiModCrtConvolution;

/**
 * Application layer for the standalone PMF/data generator.
 *
 * <p>This class owns orchestration only: CLI configuration, algorithm catalogue,
 * CSV writing, and branch coordination between simulation and direct PMF sources.
 * Mathematical kernels are delegated to ReferencePmfTheory, implementation-level
 * sampling to ReferencePmfSimulation, and exact convolution helpers live under
 * reference.pmf.math.</p>
 */
final class ReferencePmfApplication {

    private ReferencePmfApplication() {
    }

    /** Entry point used by the compatibility wrapper. */
    static void run(String[] args) {
        try {
            ReferencePmfConfig config = ReferencePmfConfig.parse(args);
            generateCsv(config);
        } catch (IllegalArgumentException ex) {
            System.err.println("Invalid arguments: " + ex.getMessage());
            System.err.println("Use --help to see available options.");
        } catch (IOException ex) {
            System.err.println("Failed to write CSV: " + ex.getMessage());
        }
    }

    /** Writes the main PMF CSV plus the dataset observation sidecar. */
    private static void generateCsv(ReferencePmfConfig config) throws IOException {
        List<AlgorithmSpec> specs = buildAlgorithmSpecs(config.randomTrials);
        String datasetObservationPath = ReferencePmfSupport.deriveDatasetObservationPath(config.outputPath);

        ReferencePmfSupport.ensureParentDirectory(config.outputPath);
        ReferencePmfSupport.ensureParentDirectory(datasetObservationPath);

        try (PrintWriter out = new PrintWriter(new FileWriter(config.outputPath));
             PrintWriter datasetOut = new PrintWriter(new FileWriter(datasetObservationPath))) {
            out.println("algorithm,n,comparisons,occurrences,totalSamples,pmf,randomTrialsPerPermutation,source,model");
            datasetOut.println("dataset,n,orderProfile,algorithm,observedComparisons,observationModel");

            for (int n = config.minN; n <= config.maxN; n++) {
                BigInteger permutationCount = ReferencePmfSupport.factorial(n);
                System.out.println("Analysing n=" + n + " (" + permutationCount + " permutations)...");
                List<OutputDistribution> outputs = analyseForN(n, specs, config);
                writeRows(out, n, outputs);
            }

            writeDatasetObservationRows(datasetOut, ReferencePmfSimulation.analyseCurrentDatasets(specs));
        }

        System.out.println("CSV written to " + config.outputPath);
        System.out.println("Dataset observation CSV written to " + datasetObservationPath);
        System.out.println("Exact convolution kernels ready: "
            + MultiModCrtConvolution.standardPrimes().size()
            + " preset 32-bit NTT primes plus adaptive signed-64-bit prime search.");
    }

    /** Runs simulation/PMF branches for one n, in parallel when both are enabled. */
    private static List<OutputDistribution> analyseForN(int n, List<AlgorithmSpec> specs,
                                                        ReferencePmfConfig config) {
        if (config.runSimulation && config.runPmf) {
            System.out.println("  running simulation and PMF formula in parallel...");
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CompletableFuture<List<OutputDistribution>> simulationFuture =
                        CompletableFuture.supplyAsync(
                                () -> ReferencePmfSimulation.analyseSimulationForN(n, specs, config), executor);
                CompletableFuture<List<OutputDistribution>> pmfFuture =
                        CompletableFuture.supplyAsync(
                                () -> ReferencePmfTheory.analysePmfForN(n, specs, config), executor);

                List<OutputDistribution> combined = new ArrayList<>();
                combined.addAll(simulationFuture.join());
                combined.addAll(pmfFuture.join());
                return combined;
            } finally {
                executor.shutdown();
            }
        }

        if (config.runSimulation) {
            return ReferencePmfSimulation.analyseSimulationForN(n, specs, config);
        }
        return ReferencePmfTheory.analysePmfForN(n, specs, config);
    }

    /** Builds the algorithm catalogue for both PMF and simulation passes. */
    private static List<AlgorithmSpec> buildAlgorithmSpecs(int randomTrials) {
        List<AlgorithmSpec> specs = new ArrayList<>();
        specs.add(new AlgorithmSpec(
                "Bubble Sort (opt)",
                seed -> new BubbleSort<>(true),
                1,
                false,
                "uniform_permutations_exact_simulation",
                "uniform_permutations_pmf_formula",
                null,
                ReferencePmfTheory::countOptimisedBubbleComparisonsTheory,
            null,
                Integer.MAX_VALUE,
                false));
        specs.add(new AlgorithmSpec(
                "Bubble Sort (non-opt)",
                seed -> new BubbleSort<>(false),
                1,
                false,
                "uniform_permutations_exact_simulation",
                "uniform_permutations_pmf_formula",
                null,
                ReferencePmfTheory::countNonOptimisedBubbleComparisonsTheory,
                null,
                Integer.MAX_VALUE,
                false));
        specs.add(new AlgorithmSpec(
                "Quick Sort (first)",
                seed -> new QuickSort<>(QuickSort.PivotStrategy.FIRST),
                1,
                false,
                "uniform_permutations_exact_simulation",
                "uniform_permutations_first_pivot_recursive_pmf_exact",
                "uniform_permutations_first_pivot_recursive_pmf_sampled",
                ReferencePmfTheory::countQuickSortFirstComparisonsTheory,
                ReferencePmfTheory::sampleTextbookQuickSortPmf,
                ReferencePmfSupport.MAX_EXACT_QUICKSORT_PMF_N,
                true));
        specs.add(new AlgorithmSpec(
                "Quick Sort (last)",
                seed -> new QuickSort<>(QuickSort.PivotStrategy.LAST),
                1,
                false,
                "uniform_permutations_exact_simulation",
                "uniform_permutations_last_pivot_via_reverse_symmetry_exact",
                "uniform_permutations_last_pivot_via_reverse_symmetry_sampled",
                ReferencePmfTheory::countQuickSortFirstComparisonsTheory,
                ReferencePmfTheory::sampleTextbookQuickSortPmf,
                ReferencePmfSupport.MAX_EXACT_QUICKSORT_PMF_N,
                true));
        specs.add(new AlgorithmSpec(
                "Quick Sort (random)",
                seed -> new QuickSort<>(QuickSort.PivotStrategy.RANDOM, new Random(seed)),
                randomTrials,
                true,
                "uniform_permutations_x_random_pivot_samples_simulation",
                null,
                null,
                null,
                null,
                0,
                false));
        specs.add(new AlgorithmSpec(
                "Merge Sort",
                seed -> new MergeSort(),
                1,
                false,
                "uniform_permutations_exact_simulation",
                "uniform_permutations_recursive_merge_pmf_exact",
                "uniform_permutations_recursive_merge_pmf_sampled",
                ReferencePmfTheory::countMergeSortComparisonsTheory,
                ReferencePmfTheory::sampleTextbookMergeSortPmf,
                ReferencePmfSupport.MAX_EXACT_MERGE_SORT_PMF_N,
                false));
        return specs;
    }

    /** Serialises logical distributions into the main CSV schema. */
    private static void writeRows(PrintWriter out, int n, List<OutputDistribution> outputs) {
        for (OutputDistribution output : outputs) {
            for (Map.Entry<Long, BigInteger> entry : output.counts.entrySet()) {
                long comparisons = entry.getKey();
                BigInteger occurrences = entry.getValue();
                double pmf = ReferencePmfSupport.probability(occurrences, output.totalSamples);

                out.printf(Locale.US,
                        "%s,%d,%d,%s,%s,%.12f,%d,%s,%s%n",
                        ReferencePmfSupport.csvEscape(output.algorithmName),
                        n,
                        comparisons,
                        occurrences.toString(),
                        output.totalSamples.toString(),
                        pmf,
                        output.randomTrialsPerPermutation,
                        output.source,
                        output.model);
            }
        }
    }

    /** Writes the dataset observation sidecar CSV. */
    private static void writeDatasetObservationRows(PrintWriter out,
                                                    List<DatasetObservation> observations) {
        for (DatasetObservation observation : observations) {
            out.printf(Locale.US,
                    "%s,%d,%s,%s,%s,%s%n",
                    observation.datasetLabel,
                    observation.n,
                    observation.orderProfile,
                    ReferencePmfSupport.csvEscape(observation.algorithmName),
                    ReferencePmfSupport.formatObservedComparisons(observation.observedComparisons),
                    observation.observationModel);
        }
    }
}