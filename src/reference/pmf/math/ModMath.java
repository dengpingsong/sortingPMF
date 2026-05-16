package reference.pmf.math;

import java.math.BigInteger;

/** Small modular-arithmetic helpers for int-sized NTT primes. */
public final class ModMath {

    private ModMath() {
    }

    public static int add(int left, int right, int modulus) {
        return toInt(reduce(BigInteger.valueOf(left).add(BigInteger.valueOf(right)), modulus));
    }

    public static int subtract(int left, int right, int modulus) {
        return toInt(reduce(BigInteger.valueOf(left).subtract(BigInteger.valueOf(right)), modulus));
    }

    public static int multiply(int left, int right, int modulus) {
        return toInt(reduce(BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)), modulus));
    }

    public static int pow(int value, long exponent, int modulus) {
        BigInteger reducedBase = reduce(BigInteger.valueOf(value), modulus);
        BigInteger result = reducedBase.modPow(BigInteger.valueOf(exponent), BigInteger.valueOf(modulus));
        return toInt(result);
    }

    public static int inverse(int value, int modulus) {
        BigInteger reducedValue = reduce(BigInteger.valueOf(value), modulus);
        return toInt(reducedValue.modInverse(BigInteger.valueOf(modulus)));
    }

    private static BigInteger reduce(BigInteger value, int modulus) {
        return value.mod(BigInteger.valueOf(modulus));
    }

    private static int toInt(BigInteger value) {
        return value.intValueExact();
    }
}