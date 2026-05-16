package reference.pmf.math;

import java.util.Arrays;

/**
 * Iterative radix-2 NTT for int-sized prime fields.
 *
 * <p>This class is deliberately independent of the PMF generator so that a
 * future exact large-n engine can reuse it without depending on the rest of the
 * application-layer code.</p>
 */
public final class NumberTheoreticTransform {

    /** Convolves two modular coefficient arrays under one NTT-friendly prime. */
    public int[] convolve(int[] left, int[] right, NttPrime prime) {
        if (left.length == 0 || right.length == 0) {
            return new int[0];
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

        int[] fa = Arrays.copyOf(left, nttLength);
        int[] fb = Arrays.copyOf(right, nttLength);
        transform(fa, false, prime);
        transform(fb, false, prime);

        for (int i = 0; i < nttLength; i++) {
            fa[i] = ModMath.multiply(fa[i], fb[i], prime.modulus());
        }

        transform(fa, true, prime);
        return Arrays.copyOf(fa, resultLength);
    }

    /** Performs one in-place forward or inverse NTT. */
    public void transform(int[] values, boolean invert, NttPrime prime) {
        int length = values.length;
        bitReverse(values);

        for (int blockLength = 2; blockLength <= length; blockLength <<= 1) {
            int root = ModMath.pow(
                    prime.primitiveRoot(),
                    (prime.modulus() - 1L) / blockLength,
                    prime.modulus());
            if (invert) {
                root = ModMath.inverse(root, prime.modulus());
            }

            for (int start = 0; start < length; start += blockLength) {
                int currentRoot = 1;
                int half = blockLength >>> 1;
                for (int offset = 0; offset < half; offset++) {
                    int even = values[start + offset];
                    int odd = ModMath.multiply(values[start + half + offset], currentRoot, prime.modulus());

                    values[start + offset] = ModMath.add(even, odd, prime.modulus());
                    values[start + half + offset] = ModMath.subtract(even, odd, prime.modulus());
                    currentRoot = ModMath.multiply(currentRoot, root, prime.modulus());
                }
            }
        }

        if (invert) {
            int inverseLength = ModMath.inverse(length, prime.modulus());
            for (int i = 0; i < length; i++) {
                values[i] = ModMath.multiply(values[i], inverseLength, prime.modulus());
            }
        }
    }

    private void bitReverse(int[] values) {
        int n = values.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >>> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>>= 1;
            }
            j ^= bit;
            if (i < j) {
                int temp = values[i];
                values[i] = values[j];
                values[j] = temp;
            }
        }
    }
}