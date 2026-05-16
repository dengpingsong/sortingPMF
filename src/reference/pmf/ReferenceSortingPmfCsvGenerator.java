package reference.pmf;

/** Compatibility entry point for the standalone reference PMF tool. */
public final class ReferenceSortingPmfCsvGenerator {

    private ReferenceSortingPmfCsvGenerator() {
    }

    /** Delegates to the refactored PMF application layer. */
    public static void main(String[] args) {
        ReferencePmfApplication.run(args);
    }
}