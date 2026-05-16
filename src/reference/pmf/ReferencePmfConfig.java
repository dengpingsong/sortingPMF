package reference.pmf;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** CLI/runtime configuration for the reference PMF tool. */
final class ReferencePmfConfig {
    final int minN;
    final int maxN;
    final int randomTrials;
    final int simulationSamples;
    final int theorySamples;
    final String outputPath;
    final boolean forceExactSimulation;
    final boolean runSimulation;
    final boolean runPmf;
    final boolean exactPmfOnly;
    final boolean forceExactQuickSortPmf;

    private ReferencePmfConfig(int minN, int maxN, int randomTrials, int simulationSamples,
                               int theorySamples, String outputPath,
                               boolean forceExactSimulation,
                               boolean runSimulation, boolean runPmf,
                               boolean exactPmfOnly,
                               boolean forceExactQuickSortPmf) {
        this.minN = minN;
        this.maxN = maxN;
        this.randomTrials = randomTrials;
        this.simulationSamples = simulationSamples;
        this.theorySamples = theorySamples;
        this.outputPath = outputPath;
        this.forceExactSimulation = forceExactSimulation;
        this.runSimulation = runSimulation;
        this.runPmf = runPmf;
        this.exactPmfOnly = exactPmfOnly;
        this.forceExactQuickSortPmf = forceExactQuickSortPmf;
    }

    /** Parses CLI flags and optional positional n/range arguments. */
    static ReferencePmfConfig parse(String[] args) {
        List<String> positional = new ArrayList<>();
        String outputPath = ReferencePmfSupport.DEFAULT_OUTPUT_PATH;
        int randomTrials = ReferencePmfSupport.DEFAULT_RANDOM_TRIALS;
        int simulationSamples = ReferencePmfSupport.DEFAULT_SIMULATION_SAMPLES;
        int theorySamples = ReferencePmfSupport.DEFAULT_THEORY_SAMPLES;
        boolean forceExactSimulation = false;
        boolean runSimulation = true;
        boolean runPmf = false;
        boolean exactPmfOnly = false;
        boolean forceExactQuickSortPmf = false;

        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsageAndExit();
            } else if ("--force".equals(arg)) {
                forceExactSimulation = true;
            } else if ("--exact-pmf-only".equals(arg)) {
                exactPmfOnly = true;
            } else if ("--force-exact-quicksort-pmf".equals(arg)
                    || "--force-exact-pmf".equals(arg)
                    || "--forces".equals(arg)) {
                forceExactQuickSortPmf = true;
            } else if ("--simulate".equals(arg)) {
                runSimulation = true;
            } else if ("--no-simulate".equals(arg)) {
                runSimulation = false;
            } else if ("--pmf".equals(arg)) {
                runPmf = true;
            } else if ("--no-pmf".equals(arg)) {
                runPmf = false;
            } else if ("--simulate-only".equals(arg)) {
                runSimulation = true;
                runPmf = false;
            } else if ("--pmf-only".equals(arg)) {
                runSimulation = false;
                runPmf = true;
            } else if (arg.startsWith("--output=")) {
                outputPath = arg.substring("--output=".length());
            } else if (arg.startsWith("--random-trials=")) {
                randomTrials = Integer.parseInt(arg.substring("--random-trials=".length()));
            } else if (arg.startsWith("--simulation-samples=")) {
                simulationSamples = Integer.parseInt(arg.substring("--simulation-samples=".length()));
            } else if (arg.startsWith("--theory-samples=")) {
                theorySamples = Integer.parseInt(arg.substring("--theory-samples=".length()));
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException(
                        "Unknown option: " + arg
                                + ". Use --force-exact-quicksort-pmf (alias: --force-exact-pmf, --forces)"
                                + " to force exact quick-sort PMFs, or --help to list all options.");
            } else {
                positional.add(arg);
            }
        }

        validate(randomTrials, simulationSamples, theorySamples, runSimulation, runPmf);

        if (positional.isEmpty()) {
            return promptConfig(outputPath, randomTrials, simulationSamples,
                    theorySamples, forceExactSimulation, runSimulation, runPmf,
                    exactPmfOnly, forceExactQuickSortPmf);
        }
        if (positional.size() > 2) {
            throw new IllegalArgumentException("Expected either one n or a minN maxN range.");
        }

        int minN = Integer.parseInt(positional.get(0));
        int maxN = positional.size() == 2 ? Integer.parseInt(positional.get(1)) : minN;
        if (minN < 1 || maxN < minN) {
            throw new IllegalArgumentException("Require 1 <= minN <= maxN.");
        }

        return new ReferencePmfConfig(minN, maxN, randomTrials, simulationSamples,
                theorySamples, outputPath, forceExactSimulation, runSimulation, runPmf,
                exactPmfOnly, forceExactQuickSortPmf);
    }

    /** Prints CLI help for the reference PMF tool. */
    private static void printUsageAndExit() {
        System.out.println("Usage:");
        System.out.println("  java -cp build/classes:reference/sortingPmf/build/classes \\");
        System.out.println("      reference.pmf.ReferenceSortingPmfCsvGenerator <n>");
        System.out.println("  java -cp build/classes:reference/sortingPmf/build/classes \\");
        System.out.println("      reference.pmf.ReferenceSortingPmfCsvGenerator <minN> <maxN>");
        System.out.println("Options:");
        System.out.println("  --output=<path>           CSV output path (default: "
                + ReferencePmfSupport.DEFAULT_OUTPUT_PATH + ")");
        System.out.println("  --random-trials=<k>       Trials per permutation for random-pivot quick sort");
        System.out.println("  --simulation-samples=<k>  Random permutations used when n > "
                + ReferencePmfSupport.MAX_EXACT_SIMULATION_N + " without --force");
        System.out.println("  --theory-samples=<k>      Samples for large-n textbook quick-sort PMFs");
        System.out.println("  --pmf                     Enable direct PMF/formula output for supported algorithms");
        System.out.println("  --exact-pmf-only          Skip sampled PMFs and keep only exact PMF outputs");
        System.out.println("  --force-exact-quicksort-pmf"
            + "  Force first/last-pivot quick-sort PMFs to use the exact engine above the safe cutoff");
        System.out.println("  --force-exact-pmf         Alias of --force-exact-quicksort-pmf");
        System.out.println("  --forces                  Short alias of --force-exact-quicksort-pmf");
        System.out.println("  --no-simulate             Disable sorting-based simulation/enumeration output");
        System.out.println("  --simulate-only           Run only the simulation/enumeration path");
        System.out.println("  --pmf-only                Run only the direct PMF/formula path");
        System.out.println("  --force                   Allow exact enumeration above n="
                + ReferencePmfSupport.MAX_EXACT_SIMULATION_N);
        System.exit(0);
    }

    /** Interactive fallback used when no positional n/range is provided. */
    @SuppressWarnings("resource")
    private static ReferencePmfConfig promptConfig(String outputPath, int randomTrials,
                                                   int simulationSamples, int theorySamples,
                                                   boolean forceExactSimulation,
                                                   boolean runSimulation,
                                                   boolean runPmf,
                                                   boolean exactPmfOnly,
                                                   boolean forceExactQuickSortPmf) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter min n: ");
        int minN = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("Enter max n: ");
        int maxN = Integer.parseInt(scanner.nextLine().trim());
        if (minN < 1 || maxN < minN) {
            throw new IllegalArgumentException("Require 1 <= minN <= maxN.");
        }

        return new ReferencePmfConfig(minN, maxN, randomTrials, simulationSamples,
            theorySamples, outputPath, forceExactSimulation, runSimulation, runPmf,
            exactPmfOnly, forceExactQuickSortPmf);
    }

    /** Checks the cheap scalar CLI invariants. */
    private static void validate(int randomTrials, int simulationSamples, int theorySamples,
                                 boolean runSimulation, boolean runPmf) {
        if (randomTrials < 1) {
            throw new IllegalArgumentException("randomTrials must be >= 1");
        }
        if (simulationSamples < 1) {
            throw new IllegalArgumentException("simulationSamples must be >= 1");
        }
        if (theorySamples < 1) {
            throw new IllegalArgumentException("theorySamples must be >= 1");
        }
        if (!runSimulation && !runPmf) {
            throw new IllegalArgumentException("Enable at least one source: simulation or pmf.");
        }
    }
}