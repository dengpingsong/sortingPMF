package reference.pmf.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptive generator for positive signed-64-bit NTT-friendly primes.
 *
 * <p>The generator searches primes of the form {@code oddMultiplier * 2^p + 1}
 * and keeps extending the list until the CRT modulus product exceeds the caller
 * supplied exact coefficient bound. This avoids hard-coding a fixed prime list
 * when future exact PMF work needs larger coverage.</p>
 */
public final class AdaptiveLongNttPrimeGenerator {

    private static final long[] MILLER_RABIN_BASES = {
            2L, 325L, 9_375L, 28_178L, 450_775L, 9_780_504L, 1_795_265_022L
    };

    private static final Map<Integer, SearchState> SEARCH_STATES = new HashMap<>();

    private AdaptiveLongNttPrimeGenerator() {
    }

    /** Selects enough 63-bit NTT primes for one transform length and coefficient bound. */
    public static List<LongNttPrime> selectPrimes(int resultLength, BigInteger coefficientBound) {
        return selectPrimesForPower(requiredPowerOfTwo(resultLength), coefficientBound);
    }

    /** Selects enough 63-bit NTT primes for one exact CRT bound at a fixed 2-power depth. */
    public static List<LongNttPrime> selectPrimesForPower(int requiredPowerOfTwo,
                                                          BigInteger coefficientBound) {
        if (requiredPowerOfTwo < 0 || requiredPowerOfTwo > 62) {
            throw new IllegalArgumentException("requiredPowerOfTwo must be in [0, 62]");
        }
        BigInteger positiveBound = coefficientBound.max(BigInteger.ONE);

        SearchState state;
        synchronized (SEARCH_STATES) {
            state = SEARCH_STATES.computeIfAbsent(requiredPowerOfTwo, SearchState::new);
        }

        synchronized (state) {
            return state.selectPrimes(positiveBound);
        }
    }

    /** Returns the minimum power p such that 2^p >= resultLength. */
    public static int requiredPowerOfTwo(int resultLength) {
        if (resultLength < 1) {
            throw new IllegalArgumentException("resultLength must be >= 1");
        }

        int nttLength = 1;
        int power = 0;
        while (nttLength < resultLength) {
            nttLength <<= 1;
            power++;
        }
        return power;
    }

    private static boolean isPrime(long value) {
        if (value < 2) {
            return false;
        }
        if ((value & 1L) == 0L) {
            return value == 2L;
        }
        if (value % 3L == 0L) {
            return value == 3L;
        }

        long d = value - 1L;
        int r = 0;
        while ((d & 1L) == 0L) {
            d >>= 1;
            r++;
        }

        for (long base : MILLER_RABIN_BASES) {
            if (base % value == 0L) {
                continue;
            }

            long witness = powMod(base, d, value);
            if (witness == 1L || witness == value - 1L) {
                continue;
            }

            boolean composite = true;
            for (int round = 1; round < r; round++) {
                witness = multiplyMod(witness, witness, value);
                if (witness == value - 1L) {
                    composite = false;
                    break;
                }
            }
            if (composite) {
                return false;
            }
        }
        return true;
    }

    private static long findPowerOfTwoRoot(long modulus, long oddMultiplier, int maxPowerOfTwo) {
        if (maxPowerOfTwo == 0) {
            return 1L;
        }

        long halfExponent = 1L << (maxPowerOfTwo - 1);
        for (long candidate = 2L; candidate < modulus; candidate++) {
            long root = powMod(candidate, oddMultiplier, modulus);
            if (root == 1L) {
                continue;
            }
            if (powMod(root, halfExponent, modulus) != 1L) {
                return root;
            }
        }

        throw new IllegalStateException("Failed to find 2^" + maxPowerOfTwo
                + " root for modulus " + modulus);
    }

    private static long powMod(long base, long exponent, long modulus) {
        return BigInteger.valueOf(base)
                .modPow(BigInteger.valueOf(exponent), BigInteger.valueOf(modulus))
                .longValueExact();
    }

    private static long multiplyMod(long left, long right, long modulus) {
        return BigInteger.valueOf(left)
                .multiply(BigInteger.valueOf(right))
                .mod(BigInteger.valueOf(modulus))
                .longValueExact();
    }

    private static final class SearchState {
        final int requiredPowerOfTwo;
        final List<LongNttPrime> primes;
        final List<BigInteger> prefixProducts;
        long nextOddMultiplier;

        SearchState(int requiredPowerOfTwo) {
            this.requiredPowerOfTwo = requiredPowerOfTwo;
            this.primes = new ArrayList<>();
            this.prefixProducts = new ArrayList<>();

            long maxOddMultiplier = (Long.MAX_VALUE - 1L) >> requiredPowerOfTwo;
            if ((maxOddMultiplier & 1L) == 0L) {
                maxOddMultiplier--;
            }
            this.nextOddMultiplier = Math.max(1L, maxOddMultiplier);
        }

        List<LongNttPrime> selectPrimes(BigInteger coefficientBound) {
            ensureCoverage(coefficientBound);
            int count = 0;
            while (prefixProducts.get(count).compareTo(coefficientBound) <= 0) {
                count++;
            }
            return new ArrayList<>(primes.subList(0, count + 1));
        }

        private void ensureCoverage(BigInteger coefficientBound) {
            while (prefixProducts.isEmpty()
                    || prefixProducts.get(prefixProducts.size() - 1).compareTo(coefficientBound) <= 0) {
                appendNextPrime();
            }
        }

        private void appendNextPrime() {
            for (long multiplier = nextOddMultiplier; multiplier > 0L; multiplier -= 2L) {
                long candidate = (multiplier << requiredPowerOfTwo) + 1L;
                if (candidate <= 1L || !isPrime(candidate)) {
                    continue;
                }

                long root = findPowerOfTwoRoot(candidate, multiplier, requiredPowerOfTwo);
                primes.add(new LongNttPrime(candidate, root, requiredPowerOfTwo));

                BigInteger newProduct = BigInteger.valueOf(candidate);
                if (!prefixProducts.isEmpty()) {
                    newProduct = prefixProducts.get(prefixProducts.size() - 1).multiply(newProduct);
                }
                prefixProducts.add(newProduct);
                nextOddMultiplier = multiplier - 2L;
                return;
            }

            throw new IllegalStateException(
                    "Unable to find enough signed-64-bit NTT primes for 2^" + requiredPowerOfTwo);
        }
    }
}