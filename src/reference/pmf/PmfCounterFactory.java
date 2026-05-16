package reference.pmf;

import java.math.BigInteger;
import java.util.Map;

/** Creates one exact combinatorial PMF/count distribution. */
@FunctionalInterface
interface PmfCounterFactory {
    Map<Long, BigInteger> create(int n);
}