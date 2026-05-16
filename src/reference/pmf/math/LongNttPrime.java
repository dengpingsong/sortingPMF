package reference.pmf.math;

import java.math.BigInteger;

/** Metadata for one positive signed-64-bit NTT-friendly prime modulus. */
public final class LongNttPrime {
    private final long modulus;
    private final long powerOfTwoRoot;
    private final int maxPowerOfTwo;

    public LongNttPrime(long modulus, long powerOfTwoRoot, int maxPowerOfTwo) {
        this.modulus = modulus;
        this.powerOfTwoRoot = powerOfTwoRoot;
        this.maxPowerOfTwo = maxPowerOfTwo;
    }

    public long modulus() {
        return modulus;
    }

    /**
     * Returns one primitive root of unity of exact order 2^maxPowerOfTwo.
     *
     * <p>This is sufficient for radix-2 NTT construction even though it is not
     * required to be a primitive generator of the full multiplicative group.</p>
     */
    public long powerOfTwoRoot() {
        return powerOfTwoRoot;
    }

    public int maxPowerOfTwo() {
        return maxPowerOfTwo;
    }

    /** Returns whether this modulus supports the requested power-of-two NTT length. */
    public boolean supportsLength(int length) {
        return length > 0
                && Integer.bitCount(length) == 1
                && Integer.numberOfTrailingZeros(length) <= maxPowerOfTwo;
    }

    public BigInteger modulusAsBigInteger() {
        return BigInteger.valueOf(modulus);
    }
}