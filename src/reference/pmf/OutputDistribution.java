package reference.pmf;

import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;

/** Holds one output distribution before CSV serialisation. */
final class OutputDistribution {
    final String algorithmName;
    final Map<Long, BigInteger> counts;
    final BigInteger totalSamples;
    final int randomTrialsPerPermutation;
    final String source;
    final String model;

    OutputDistribution(String algorithmName, Map<Long, BigInteger> counts,
                       BigInteger totalSamples, int randomTrialsPerPermutation,
                       String source, String model) {
        this.algorithmName = algorithmName;
        this.counts = new TreeMap<>(counts);
        this.totalSamples = totalSamples;
        this.randomTrialsPerPermutation = randomTrialsPerPermutation;
        this.source = source;
        this.model = model;
    }
}