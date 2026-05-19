package reference.pmf.math;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Modular arithmetic helpers for positive signed-64-bit prime moduli. */
public final class LongModMath {

    private static final Map<Long, MontgomeryContext> MONTGOMERY_CONTEXTS = new ConcurrentHashMap<>();

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
        return montgomeryContext(modulus).multiplyStandard(left, right);
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

    static MontgomeryContext montgomeryContext(long modulus) {
        if (modulus <= 0 || (modulus & 1L) == 0L) {
            throw new IllegalArgumentException("Require a positive odd modulus for Montgomery reduction.");
        }
        return MONTGOMERY_CONTEXTS.computeIfAbsent(modulus, MontgomeryContext::new);
    }

    private static long montgomeryNegativeInverse(long modulus) {
        long inverse = 1L;
        for (int iteration = 0; iteration < 6; iteration++) {
            inverse *= 2L - modulus * inverse;
        }
        return -inverse;
    }

    private static long unsignedMultiplyHigh(long left, long right) {
        long high = Math.multiplyHigh(left, right);
        high += (left >> 63) & right;
        high += (right >> 63) & left;
        return high;
    }

    static final class MontgomeryContext {
        private final long modulus;
        private final long negativeInverse;
        private final long rSquared;
        private final long oneMontgomery;

        MontgomeryContext(long modulus) {
            this.modulus = modulus;
            this.negativeInverse = montgomeryNegativeInverse(modulus);
            this.rSquared = BigInteger.ONE.shiftLeft(128)
                    .mod(BigInteger.valueOf(modulus))
                    .longValueExact();
            this.oneMontgomery = montgomeryMultiply(1L, rSquared);
        }

        long one() {
            return oneMontgomery;
        }

        long toMontgomery(long value) {
            long normalised = normalize(value);
            if (normalised == 0L) {
                return 0L;
            }
            return montgomeryMultiply(normalised, rSquared);
        }

        long fromMontgomery(long value) {
            return montgomeryMultiply(value, 1L);
        }

        long multiply(long leftMontgomery, long rightMontgomery) {
            return montgomeryMultiply(leftMontgomery, rightMontgomery);
        }

        long multiplyStandard(long left, long right) {
            if (left == 0L || right == 0L) {
                return 0L;
            }
            long leftMontgomery = toMontgomery(left);
            long rightMontgomery = toMontgomery(right);
            return fromMontgomery(multiply(leftMontgomery, rightMontgomery));
        }

        private long normalize(long value) {
            if (value < 0L) {
                throw new IllegalArgumentException("Require non-negative residues for Montgomery arithmetic.");
            }
            if (Long.compareUnsigned(value, modulus) >= 0) {
                return Long.remainderUnsigned(value, modulus);
            }
            return value;
        }

        private long montgomeryMultiply(long left, long right) {
            long lowProduct = left * right;
            long highProduct = Math.multiplyHigh(left, right);
            long factor = lowProduct * negativeInverse;
            long factorTimesModulusLow = factor * modulus;
            long factorTimesModulusHigh = unsignedMultiplyHigh(factor, modulus);
            long mergedLow = lowProduct + factorTimesModulusLow;
            long carry = Long.compareUnsigned(mergedLow, lowProduct) < 0 ? 1L : 0L;
            long candidate = highProduct + factorTimesModulusHigh + carry;
            if (Long.compareUnsigned(candidate, modulus) >= 0) {
                candidate -= modulus;
            }
            return candidate;
        }
    }
}