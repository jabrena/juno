package io.github.jabrena.juno.api.net.ledger;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FactorizerTest {
    private final long[] primes = new long[FactorProtocol.MAX_FACTORS];
    private final int[] exponents = new int[FactorProtocol.MAX_FACTORS];
    private final long[] stack = new long[Factorizer.STACK_SIZE];

    @Test
    void factorsSmallAndStructuredNumbers() {
        assertFactors(2, "2");
        assertFactors(360, "2^3 x 3^2 x 5");
        assertFactors(1L << 61, "2^61");
        assertFactors(999_983L * 999_983L, "999983^2");
        assertFactors(1_000_003L * 1_000_033L * 1_000_037L, "1000003 x 1000033 x 1000037");
        // The largest prime below 2^31 squared, and a semiprime of two ~31-bit primes just below 2^62.
        assertFactors(2_147_483_647L * 2_147_483_647L, "2147483647^2");
        assertFactors(2_147_483_629L * 2_147_483_587L, "2147483587 x 2147483629");
        // Product of the first 15 primes: the most distinct factors a number below 2^62 can have.
        assertFactors(614_889_782_588_491_410L,
                "2 x 3 x 5 x 7 x 11 x 13 x 17 x 19 x 23 x 29 x 31 x 37 x 41 x 43 x 47");
    }

    @Test
    void factorsRandomNumbersOfEverySizeTheLeaderCanHandOut() {
        SplittableRandom random = new SplittableRandom(42);
        for (int bits = 2; bits <= 62; bits++) {
            for (int i = 0; i < 20; i++) {
                long n = (random.nextLong() >>> (64 - bits)) | (1L << (bits - 1));
                int count = Factorizer.factorize(n, primes, exponents, stack);

                assertThat(Factorizer.verify(n, primes, exponents, count)).as("%d", n).isTrue();
                for (int f = 0; f < count; f++) {
                    assertThat(BigInteger.valueOf(primes[f]).isProbablePrime(50)).as("%d of %d", primes[f], n).isTrue();
                }
            }
        }
    }

    @Test
    void rejectsNumbersOutsideTheSupportedRange() {
        assertThat(Factorizer.factorize(1, primes, exponents, stack)).isEqualTo(-1);
        assertThat(Factorizer.factorize(Factorizer.LIMIT, primes, exponents, stack)).isEqualTo(-1);
    }

    @Test
    void primalityAgreesWithBigInteger() {
        SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 2000; i++) {
            long n = random.nextLong(Factorizer.LIMIT);
            assertThat(Factorizer.isPrime(n)).as("%d", n).isEqualTo(BigInteger.valueOf(n).isProbablePrime(50));
        }
        // Strong pseudoprimes to several small bases.
        assertThat(Factorizer.isPrime(3_215_031_751L)).isFalse();
        assertThat(Factorizer.isPrime(3_825_123_056_546_413_051L)).isFalse();
    }

    @Test
    void mulModMatchesBigIntegerAboveThirtyOneBits() {
        SplittableRandom random = new SplittableRandom(3);
        for (int i = 0; i < 2000; i++) {
            long m = random.nextLong(1L << 31, Factorizer.LIMIT);
            long a = random.nextLong(m);
            long b = random.nextLong(m);
            long expected = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).mod(BigInteger.valueOf(m)).longValue();
            assertThat(Factorizer.mulMod(a, b, m)).isEqualTo(expected);
        }
    }

    @Test
    void verifyRejectsWrongIncompleteAndUnorderedAnswers() {
        assertThat(verify(12, new long[] {2, 3}, new int[] {2, 1})).isTrue();
        assertThat(verify(12, new long[] {2, 3}, new int[] {1, 1})).isFalse();
        assertThat(verify(12, new long[] {4, 3}, new int[] {1, 1})).isFalse();
        assertThat(verify(12, new long[] {3, 2}, new int[] {1, 2})).isFalse();
        assertThat(verify(12, new long[] {2, 3}, new int[] {2, 0})).isFalse();
        assertThat(verify(12, new long[] {12}, new int[] {1})).isFalse();
        assertThat(verify(1L << 61, new long[] {2}, new int[] {120})).isFalse();
    }

    private static boolean verify(long n, long[] primes, int[] exponents) {
        return Factorizer.verify(n, primes, exponents, primes.length);
    }

    private void assertFactors(long n, String expected) {
        int count = Factorizer.factorize(n, primes, exponents, stack);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(i == 0 ? "" : " x ").append(primes[i]).append(exponents[i] > 1 ? "^" + exponents[i] : "");
        }
        assertThat(text.toString()).isEqualTo(expected);
        assertThat(Factorizer.verify(n, primes, exponents, count)).isTrue();
    }
}
