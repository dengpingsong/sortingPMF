package reference.pmf;

/** Captures how one real dataset sits relative to the generated PMFs. */
final class DatasetObservation {
    final String datasetLabel;
    final int n;
    final String orderProfile;
    final String algorithmName;
    final double observedComparisons;
    final String observationModel;

    DatasetObservation(String datasetLabel, int n, String orderProfile,
                       String algorithmName, double observedComparisons,
                       String observationModel) {
        this.datasetLabel = datasetLabel;
        this.n = n;
        this.orderProfile = orderProfile;
        this.algorithmName = algorithmName;
        this.observedComparisons = observedComparisons;
        this.observationModel = observationModel;
    }
}