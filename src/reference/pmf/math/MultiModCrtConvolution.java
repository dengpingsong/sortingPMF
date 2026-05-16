package reference.pmf.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exact polynomial convolution via multiple NTT primes plus Chinese Remainder reconstruction.
 *
 * <p>This kernel is designed for non-negative integer coefficients. The caller
 * must provide enough moduli to cover the largest coefficient exactly. The class
 * is not wired into the current PMF generator yet, but it provides the exact
 * large-integer convolution layer needed by a future dense quick-sort count DP.</p>
 */
public final class MultiModCrtConvolution {

    private static final Map<Integer, PrimeSearchState> PRIME_SEARCH_STATES = new HashMap<>();

    private static final List<NttPrime> STANDARD_PRIMES = Arrays.asList(
            new NttPrime(998244353, 3, 23),
            new NttPrime(1004535809, 3, 21),
            new NttPrime(469762049, 3, 26),
            new NttPrime(167772161, 3, 25),
            new NttPrime(754974721, 11, 24),
            new NttPrime(1224736769, 3, 24)
    );

    private final List<NttPrime> primes;
    private final NumberTheoreticTransform transform;
    private final int workerCount;

    public MultiModCrtConvolution(List<NttPrime> primes) {
        this(primes, Runtime.getRuntime().availableProcessors());
    }

    /** Builds one convolution engine with an explicit worker budget. */
    public MultiModCrtConvolution(List<NttPrime> primes, int workerCount) {
        if (primes.isEmpty()) {
            throw new IllegalArgumentException("At least one NTT prime is required.");
        }
        this.primes = List.copyOf(primes);
        this.transform = new NumberTheoreticTransform();
        this.workerCount = Math.max(1, workerCount);
    }

    /** Returns a small catalogue of known NTT-friendly primes. */
    public static List<NttPrime> standardPrimes() {
        return STANDARD_PRIMES;
    }

    /**
     * Convolves two non-negative BigInteger coefficient arrays exactly.
     *
     * <p>The method selects enough NTT-friendly primes so that the combined CRT
     * modulus exceeds the maximum possible output coefficient. Since every output
     * coefficient is bounded above by {@code sum(left) * sum(right)}, this is a
     * sufficient exactness condition for non-negative inputs.</p>
     */
    public static BigInteger[] convolveExact(BigInteger[] left, BigInteger[] right) {
        return convolveExact(left, right, coefficientBound(left, right),
                Runtime.getRuntime().availableProcessors());
    }

    /** Exact convolution with an explicit coefficient bound and worker budget. */
    public static BigInteger[] convolveExact(BigInteger[] left, BigInteger[] right,
                                             BigInteger coefficientBound, int workerCount) {
        if (left.length == 0 || right.length == 0) {
            return new BigInteger[0];
        }

        int resultLength = left.length + right.length - 1;
        List<NttPrime> primes = selectExactPrimes(resultLength, coefficientBound);
        return new MultiModCrtConvolution(primes, workerCount).convolve(left, right);
    }

    /** Returns the product of all configured moduli. */
    public BigInteger combinedModulus() {
        BigInteger product = BigInteger.ONE;
        for (NttPrime prime : primes) {
            product = product.multiply(prime.modulusAsBigInteger());
        }
        return product;
    }

    /**
     * Convolves two non-negative BigInteger coefficient arrays exactly, provided
     * that the combined CRT modulus exceeds every true output coefficient.
     */
    public BigInteger[] convolve(BigInteger[] left, BigInteger[] right) {
        if (left.length == 0 || right.length == 0) {
            return new BigInteger[0];
        }

        int resultLength = left.length + right.length - 1;
        int parallelism = Math.min(workerCount, Math.max(primes.size(), 1));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);

        try {
            int[][] residues = computeResidues(left, right, executor);
            BigInteger[] result = new BigInteger[resultLength];
            reconstructCoefficients(residues, result, executor);
            return result;
        } finally {
            executor.shutdown();
        }
    }

    /** Runs each prime-field convolution in parallel. */
    private int[][] computeResidues(BigInteger[] left, BigInteger[] right, ExecutorService executor) {
        List<CompletableFuture<int[]>> futures = new ArrayList<>();
        for (NttPrime prime : primes) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                int[] leftMod = reduceCoefficients(left, prime.modulus());
                int[] rightMod = reduceCoefficients(right, prime.modulus());
                return transform.convolve(leftMod, rightMod, prime);
            }, executor));
        }

        int[][] residues = new int[primes.size()][];
        for (int primeIndex = 0; primeIndex < futures.size(); primeIndex++) {
            residues[primeIndex] = futures.get(primeIndex).join();
        }
        return residues;
    }

    /** Reconstructs output coefficients in parallel over contiguous chunks. */
    private void reconstructCoefficients(int[][] residues, BigInteger[] result, ExecutorService executor) {
        int taskCount = Math.min(workerCount, result.length);
        if (taskCount <= 1) {
            reconstructRange(residues, result, 0, result.length);
            return;
        }

        int chunkSize = (result.length + taskCount - 1) / taskCount;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int start = 0; start < result.length; start += chunkSize) {
            int from = start;
            int to = Math.min(result.length, start + chunkSize);
            futures.add(CompletableFuture.runAsync(() -> reconstructRange(residues, result, from, to), executor));
        }

        for (CompletableFuture<Void> future : futures) {
            future.join();
        }
    }

    private void reconstructRange(int[][] residues, BigInteger[] result, int from, int to) {
        for (int coefficientIndex = from; coefficientIndex < to; coefficientIndex++) {
            int[] coefficientResidues = new int[primes.size()];
            for (int primeIndex = 0; primeIndex < primes.size(); primeIndex++) {
                coefficientResidues[primeIndex] = residues[primeIndex][coefficientIndex];
            }
            result[coefficientIndex] = reconstruct(coefficientResidues);
        }
    }

    private int[] reduceCoefficients(BigInteger[] coefficients, int modulus) {
        int[] reduced = new int[coefficients.length];
        BigInteger bigModulus = BigInteger.valueOf(modulus);
        for (int index = 0; index < coefficients.length; index++) {
            reduced[index] = coefficients[index].mod(bigModulus).intValue();
        }
        return reduced;
    }

    private BigInteger reconstruct(int[] residues) {
        BigInteger x = BigInteger.ZERO;
        BigInteger modulusProduct = BigInteger.ONE;

        for (int index = 0; index < primes.size(); index++) {
            BigInteger prime = primes.get(index).modulusAsBigInteger();
            BigInteger residue = BigInteger.valueOf(residues[index]);
            BigInteger xModPrime = x.mod(prime);
            BigInteger delta = residue.subtract(xModPrime).mod(prime);
            BigInteger inverse = modulusProduct.mod(prime).modInverse(prime);
            BigInteger step = delta.multiply(inverse).mod(prime);
            x = x.add(modulusProduct.multiply(step));
            modulusProduct = modulusProduct.multiply(prime);
        }

        return x;
    }

    private static BigInteger coefficientBound(BigInteger[] left, BigInteger[] right) {
        BigInteger leftSum = BigInteger.ZERO;
        for (BigInteger coefficient : left) {
            leftSum = leftSum.add(coefficient);
        }

        BigInteger rightSum = BigInteger.ZERO;
        for (BigInteger coefficient : right) {
            rightSum = rightSum.add(coefficient);
        }

        return leftSum.multiply(rightSum);
    }

    private static List<NttPrime> selectExactPrimes(int resultLength, BigInteger coefficientBound) {
        int requiredPowerOfTwo = requiredPowerOfTwo(resultLength);
        BigInteger positiveBound = coefficientBound.max(BigInteger.ONE);

        PrimeSearchState state;
        synchronized (PRIME_SEARCH_STATES) {
            state = PRIME_SEARCH_STATES.computeIfAbsent(requiredPowerOfTwo, PrimeSearchState::new);
        }

        synchronized (state) {
            return state.selectPrimes(positiveBound);
        }
    }

    private static int requiredPowerOfTwo(int resultLength) {
        int nttLength = 1;
        int power = 0;
        while (nttLength < resultLength) {
            nttLength <<= 1;
            power++;
        }
        return power;
    }

    private static int findPrimitiveRoot(int modulus, long oddMultiplier) {
        List<Integer> factors = new ArrayList<>();
        factors.add(2);

        long remaining = oddMultiplier;
        for (long divisor = 3; divisor * divisor <= remaining; divisor += 2) {
            if (remaining % divisor != 0) {
                continue;
            }
            factors.add((int) divisor);
            while (remaining % divisor == 0) {
                remaining /= divisor;
            }
        }
        if (remaining > 1) {
            factors.add((int) remaining);
        }

        int phi = modulus - 1;
        for (int candidate = 2; candidate < modulus; candidate++) {
            boolean primitive = true;
            for (int factor : factors) {
                if (ModMath.pow(candidate, phi / factor, modulus) == 1) {
                    primitive = false;
                    break;
                }
            }
            if (primitive) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to find primitive root for modulus " + modulus);
    }

    private static final class PrimeSearchState {
        final int requiredPowerOfTwo;
        final List<NttPrime> primes;
        final List<BigInteger> prefixProducts;
        long nextOddMultiplier;

        PrimeSearchState(int requiredPowerOfTwo) {
            this.requiredPowerOfTwo = requiredPowerOfTwo;
            this.primes = new ArrayList<>();
            this.prefixProducts = new ArrayList<>();

            long maxOddMultiplier = (Integer.MAX_VALUE - 1L) >> requiredPowerOfTwo;
            if ((maxOddMultiplier & 1L) == 0L) {
                maxOddMultiplier--;
            }
            this.nextOddMultiplier = maxOddMultiplier;
        }

        List<NttPrime> selectPrimes(BigInteger coefficientBound) {
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
            for (long multiplier = nextOddMultiplier; multiplier > 0; multiplier -= 2) {
                long candidate = (multiplier << requiredPowerOfTwo) + 1L;
                if (candidate > Integer.MAX_VALUE) {
                    continue;
                }
                if (!BigInteger.valueOf(candidate).isProbablePrime(30)) {
                    continue;
                }

                int modulus = (int) candidate;
                int primitiveRoot = findPrimitiveRoot(modulus, multiplier);
                primes.add(new NttPrime(modulus, primitiveRoot, requiredPowerOfTwo));

                BigInteger newProduct = BigInteger.valueOf(modulus);
                if (!prefixProducts.isEmpty()) {
                    newProduct = prefixProducts.get(prefixProducts.size() - 1).multiply(newProduct);
                }
                prefixProducts.add(newProduct);
                nextOddMultiplier = multiplier - 2;
                return;
            }

            throw new IllegalStateException(
                    "Unable to find enough NTT-friendly primes for 2^" + requiredPowerOfTwo + " transforms.");
        }
    }
}