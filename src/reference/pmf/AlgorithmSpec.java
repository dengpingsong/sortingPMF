package reference.pmf;

/** Immutable per-algorithm generation metadata. */
final class AlgorithmSpec {
    final String name;
    final AlgorithmFactory factory;
    final int trialsPerPermutation;
    final boolean randomised;
    final String simulationModel;
    final String exactPmfModel;
    final String sampledPmfModel;
    final PmfCounterFactory pmfCounterFactory;
    final SampledPmfCounterFactory sampledPmfCounterFactory;
    final int maxExactPmfN;
    final boolean sharedQuickSortTheory;

    AlgorithmSpec(String name, AlgorithmFactory factory,
                  int trialsPerPermutation, boolean randomised,
                  String simulationModel, String exactPmfModel,
                  String sampledPmfModel, PmfCounterFactory pmfCounterFactory,
                  SampledPmfCounterFactory sampledPmfCounterFactory,
                  int maxExactPmfN, boolean sharedQuickSortTheory) {
        this.name = name;
        this.factory = factory;
        this.trialsPerPermutation = trialsPerPermutation;
        this.randomised = randomised;
        this.simulationModel = simulationModel;
        this.exactPmfModel = exactPmfModel;
        this.sampledPmfModel = sampledPmfModel;
        this.pmfCounterFactory = pmfCounterFactory;
        this.sampledPmfCounterFactory = sampledPmfCounterFactory;
        this.maxExactPmfN = maxExactPmfN;
        this.sharedQuickSortTheory = sharedQuickSortTheory;
    }

    boolean supportsExactPmf(int n) {
        return pmfCounterFactory != null && n <= maxExactPmfN;
    }

    boolean supportsSampledPmf() {
        return sampledPmfModel != null && sampledPmfCounterFactory != null;
    }
}