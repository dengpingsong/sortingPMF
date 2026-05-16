package reference.pmf.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Exact polynomial convolution via adaptive 63-bit NTT primes plus CRT reconstruction. */
public final class AdaptiveLongMultiModCrtConvolution {

    private final List<LongNttPrime> primes;
    private final LongNumberTheoreticTransform transform;
    private final int workerCount;

    public AdaptiveLongMultiModCrtConvolution(List<LongNttPrime> primes) {
        this(primes, Runtime.getRuntime().availableProcessors());
    }

    /** Builds one long-prime convolution engine with an explicit worker budget. */
    public AdaptiveLongMultiModCrtConvolution(List<LongNttPrime> primes, int workerCount) {
        if (primes.isEmpty()) {
            throw new IllegalArgumentException("At least one long NTT prime is required.");
        }
        this.primes = List.copyOf(primes);
        this.transform = new LongNumberTheoreticTransform();
        this.workerCount = Math.max(1, workerCount);
    }

    /** Selects enough 63-bit primes and convolves two non-negative exact coefficient arrays. */
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
        List<LongNttPrime> primes = AdaptiveLongNttPrimeGenerator.selectPrimes(resultLength, coefficientBound);
        return new AdaptiveLongMultiModCrtConvolution(primes, workerCount).convolve(left, right);
    }

    /** Returns the product of all configured 63-bit moduli. */
    public BigInteger combinedModulus() {
        BigInteger product = BigInteger.ONE;
        for (LongNttPrime prime : primes) {
            product = product.multiply(prime.modulusAsBigInteger());
        }
        return product;
    }

    /** Convolves two non-negative exact coefficient arrays exactly. */
    public BigInteger[] convolve(BigInteger[] left, BigInteger[] right) {
        if (left.length == 0 || right.length == 0) {
            return new BigInteger[0];
        }

        int resultLength = left.length + right.length - 1;
        int parallelism = Math.min(workerCount, Math.max(primes.size(), 1));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);

        try {
            long[][] residues = computeResidues(left, right, executor);
            BigInteger[] result = new BigInteger[resultLength];
            reconstructCoefficients(residues, result, executor);
            return result;
        } finally {
            executor.shutdown();
        }
    }

    private long[][] computeResidues(BigInteger[] left, BigInteger[] right, ExecutorService executor) {
        List<CompletableFuture<long[]>> futures = new ArrayList<>();
        for (LongNttPrime prime : primes) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                long[] leftMod = reduceCoefficients(left, prime.modulus());
                long[] rightMod = reduceCoefficients(right, prime.modulus());
                return transform.convolve(leftMod, rightMod, prime);
            }, executor));
        }

        long[][] residues = new long[primes.size()][];
        for (int primeIndex = 0; primeIndex < futures.size(); primeIndex++) {
            residues[primeIndex] = futures.get(primeIndex).join();
        }
        return residues;
    }

    private void reconstructCoefficients(long[][] residues, BigInteger[] result, ExecutorService executor) {
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

    private void reconstructRange(long[][] residues, BigInteger[] result, int from, int to) {
        for (int coefficientIndex = from; coefficientIndex < to; coefficientIndex++) {
            long[] coefficientResidues = new long[primes.size()];
            for (int primeIndex = 0; primeIndex < primes.size(); primeIndex++) {
                coefficientResidues[primeIndex] = residues[primeIndex][coefficientIndex];
            }
            result[coefficientIndex] = reconstruct(coefficientResidues);
        }
    }

    private long[] reduceCoefficients(BigInteger[] coefficients, long modulus) {
        long[] reduced = new long[coefficients.length];
        BigInteger bigModulus = BigInteger.valueOf(modulus);
        for (int index = 0; index < coefficients.length; index++) {
            reduced[index] = coefficients[index].mod(bigModulus).longValueExact();
        }
        return reduced;
    }

    private BigInteger reconstruct(long[] residues) {
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
}