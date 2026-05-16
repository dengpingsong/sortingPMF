package reference.pmf.math;

import java.math.BigInteger;

/** Modular arithmetic helpers for positive signed-64-bit prime moduli. */
public final class LongModMath {

    private LongModMath() {
    }

    public static long add(long left, long right, long modulus) {
        if (left < 0 || right < 0 || modulus <= 0) {
            throw new IllegalArgumentException("Require non-negative residues and positive modulus.");
        }
        if (left >= modulus - right) {
            return left - (modulus - right);
        }
        return left + right;
    }

    public static long subtract(long left, long right, long modulus) {
        if (left < 0 || right < 0 || modulus <= 0) {
            throw new IllegalArgumentException("Require non-negative residues and positive modulus.");
        }
        if (left >= right) {
            return left - right;
        }
        return modulus - (right - left);
    }

    public static long multiply(long left, long right, long modulus) {
        return BigInteger.valueOf(left)
                .multiply(BigInteger.valueOf(right))
                .mod(BigInteger.valueOf(modulus))
                .longValueExact();
    }

    public static long pow(long value, long exponent, long modulus) {
        return BigInteger.valueOf(value)
                .modPow(BigInteger.valueOf(exponent), BigInteger.valueOf(modulus))
                .longValueExact();
    }

    public static long inverse(long value, long modulus) {
        return BigInteger.valueOf(value)
                .modInverse(BigInteger.valueOf(modulus))
                .longValueExact();
    }
}