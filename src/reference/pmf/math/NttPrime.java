package reference.pmf.math;

import java.math.BigInteger;

/** Metadata for one NTT-friendly prime modulus. */
public final class NttPrime {
    private final int modulus;
    private final int primitiveRoot;
    private final int maxPowerOfTwo;

    public NttPrime(int modulus, int primitiveRoot, int maxPowerOfTwo) {
        this.modulus = modulus;
        this.primitiveRoot = primitiveRoot;
        this.maxPowerOfTwo = maxPowerOfTwo;
    }

    public int modulus() {
        return modulus;
    }

    public int primitiveRoot() {
        return primitiveRoot;
    }

    public int maxPowerOfTwo() {
        return maxPowerOfTwo;
    }

    /** Returns whether this modulus supports an NTT of the requested power-of-two length. */
    public boolean supportsLength(int length) {
        return length > 0
                && Integer.bitCount(length) == 1
                && Integer.numberOfTrailingZeros(length) <= maxPowerOfTwo;
    }

    public BigInteger modulusAsBigInteger() {
        return BigInteger.valueOf(modulus);
    }
}