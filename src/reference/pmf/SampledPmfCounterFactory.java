package reference.pmf;

import java.math.BigInteger;
import java.util.Map;

/** Creates one sampled model-level PMF/count distribution. */
@FunctionalInterface
interface SampledPmfCounterFactory {
    Map<Long, BigInteger> create(int n, int sampleCount);
}