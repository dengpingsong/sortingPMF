package reference.pmf.math;

import java.util.Arrays;

/** Iterative radix-2 NTT for positive signed-64-bit prime fields. */
public final class LongNumberTheoreticTransform {

    /** Convolves two modular coefficient arrays under one 63-bit NTT-friendly prime. */
    public long[] convolve(long[] left, long[] right, LongNttPrime prime) {
        if (left.length == 0 || right.length == 0) {
            return new long[0];
        }

        int resultLength = left.length + right.length - 1;
        int nttLength = 1;
        while (nttLength < resultLength) {
            nttLength <<= 1;
        }
        if (!prime.supportsLength(nttLength)) {
            throw new IllegalArgumentException(
                    "Prime modulus " + prime.modulus() + " does not support NTT length " + nttLength);
        }

        long[] fa = Arrays.copyOf(left, nttLength);
        long[] fb = Arrays.copyOf(right, nttLength);
        transform(fa, false, prime);
        transform(fb, false, prime);

        for (int i = 0; i < nttLength; i++) {
            fa[i] = LongModMath.multiply(fa[i], fb[i], prime.modulus());
        }

        transform(fa, true, prime);
        return Arrays.copyOf(fa, resultLength);
    }

    /** Performs one in-place forward or inverse NTT. */
    public void transform(long[] values, boolean invert, LongNttPrime prime) {
        int length = values.length;
        bitReverse(values);

        for (int blockLength = 2; blockLength <= length; blockLength <<= 1) {
            long root = principalRoot(blockLength, prime);
            if (invert) {
                root = LongModMath.inverse(root, prime.modulus());
            }

            for (int start = 0; start < length; start += blockLength) {
                long currentRoot = 1L;
                int half = blockLength >>> 1;
                for (int offset = 0; offset < half; offset++) {
                    long even = values[start + offset];
                    long odd = LongModMath.multiply(
                            values[start + half + offset],
                            currentRoot,
                            prime.modulus());

                    values[start + offset] = LongModMath.add(even, odd, prime.modulus());
                    values[start + half + offset] = LongModMath.subtract(even, odd, prime.modulus());
                    currentRoot = LongModMath.multiply(currentRoot, root, prime.modulus());
                }
            }
        }

        if (invert) {
            long inverseLength = LongModMath.inverse(length, prime.modulus());
            for (int i = 0; i < length; i++) {
                values[i] = LongModMath.multiply(values[i], inverseLength, prime.modulus());
            }
        }
    }

    private long principalRoot(int blockLength, LongNttPrime prime) {
        int blockPower = Integer.numberOfTrailingZeros(blockLength);
        long exponent = 1L << (prime.maxPowerOfTwo() - blockPower);
        return LongModMath.pow(prime.powerOfTwoRoot(), exponent, prime.modulus());
    }

    private void bitReverse(long[] values) {
        int n = values.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >>> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>>= 1;
            }
            j ^= bit;
            if (i < j) {
                long temp = values[i];
                values[i] = values[j];
                values[j] = temp;
            }
        }
    }
}