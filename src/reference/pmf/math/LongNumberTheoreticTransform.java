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

        LongModMath.MontgomeryContext montgomery = LongModMath.montgomeryContext(prime.modulus());
        long[] fa = Arrays.copyOf(left, nttLength);
        long[] fb = Arrays.copyOf(right, nttLength);
        toMontgomery(fa, left.length, montgomery);
        toMontgomery(fb, right.length, montgomery);
        transformInMontgomery(fa, false, prime, montgomery);
        transformInMontgomery(fb, false, prime, montgomery);

        for (int i = 0; i < nttLength; i++) {
            fa[i] = montgomery.multiply(fa[i], fb[i]);
        }

        transformInMontgomery(fa, true, prime, montgomery);
        fromMontgomery(fa, resultLength, montgomery);
        return Arrays.copyOf(fa, resultLength);
    }

    /** Performs one in-place forward or inverse NTT. */
    public void transform(long[] values, boolean invert, LongNttPrime prime) {
        LongModMath.MontgomeryContext montgomery = LongModMath.montgomeryContext(prime.modulus());
        toMontgomery(values, values.length, montgomery);
        transformInMontgomery(values, invert, prime, montgomery);
        fromMontgomery(values, values.length, montgomery);
    }

    private void transformInMontgomery(long[] values, boolean invert, LongNttPrime prime,
                                       LongModMath.MontgomeryContext montgomery) {
        int length = values.length;
        bitReverse(values);

        for (int blockLength = 2; blockLength <= length; blockLength <<= 1) {
            long root = principalRoot(blockLength, prime, invert, montgomery);

            for (int start = 0; start < length; start += blockLength) {
                long currentRoot = montgomery.one();
                int half = blockLength >>> 1;
                for (int offset = 0; offset < half; offset++) {
                    long even = values[start + offset];
                    long odd = montgomery.multiply(
                            values[start + half + offset],
                            currentRoot);

                    values[start + offset] = LongModMath.add(even, odd, prime.modulus());
                    values[start + half + offset] = LongModMath.subtract(even, odd, prime.modulus());
                    currentRoot = montgomery.multiply(currentRoot, root);
                }
            }
        }

        if (invert) {
            long inverseLength = montgomery.toMontgomery(LongModMath.inverse(length, prime.modulus()));
            for (int i = 0; i < length; i++) {
                values[i] = montgomery.multiply(values[i], inverseLength);
            }
        }
    }

    private long principalRoot(int blockLength, LongNttPrime prime, boolean invert,
                               LongModMath.MontgomeryContext montgomery) {
        int blockPower = Integer.numberOfTrailingZeros(blockLength);
        long exponent = 1L << (prime.maxPowerOfTwo() - blockPower);
        long root = LongModMath.pow(prime.powerOfTwoRoot(), exponent, prime.modulus());
        if (invert) {
            root = LongModMath.inverse(root, prime.modulus());
        }
        return montgomery.toMontgomery(root);
    }

    private void toMontgomery(long[] values, int activeLength, LongModMath.MontgomeryContext montgomery) {
        for (int i = 0; i < activeLength; i++) {
            values[i] = montgomery.toMontgomery(values[i]);
        }
    }

    private void fromMontgomery(long[] values, int activeLength, LongModMath.MontgomeryContext montgomery) {
        for (int i = 0; i < activeLength; i++) {
            values[i] = montgomery.fromMontgomery(values[i]);
        }
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