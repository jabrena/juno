package io.github.jabrena.juno.api.net.ledger;

/**
 * Allocation-free integer factorization for numbers below 2^62: trial division by small odd
 * numbers, then Pollard's rho (Brent's variant) on whatever is left, with a deterministic
 * Miller-Rabin test deciding when a part is prime.
 *
 * <p>Juno has no 128-bit multiply, so {@link #mulMod} falls back to add-and-double once the
 * modulus exceeds 31 bits. Keeping every value below 2^62 guarantees that the additions it
 * performs never overflow a {@code long}.
 *
 * <p>The same code {@link #verify verifies} a worker's answer on the leader. Checking a
 * factorization (multiply it back, test each part for primality) is far cheaper than finding it,
 * which is what makes it useful work to put in a ledger.
 */
final class Factorizer {
    /** Exclusive upper bound for every number this class accepts. */
    static final long LIMIT = 1L << 62;
    /** Scratch {@code long[]} size {@link #factorize} needs: one entry per prime factor counted with multiplicity. */
    static final int STACK_SIZE = 64;

    private static final int TRIAL_LIMIT = 1000;
    private static final int RHO_BATCH = 64;
    private static final long SMALL_MODULUS = 1L << 31;

    /** Deterministic Miller-Rabin bases for every 64-bit integer. */
    private static final int[] BASES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37};

    private Factorizer() {
    }

    /**
     * Writes {@code n}'s prime factorization, primes ascending, into {@code primes}/{@code exponents}
     * (each with room for {@link FactorProtocol#MAX_FACTORS} entries) and returns how many distinct
     * primes it has, or {@code -1} when {@code n} is outside {@code [2, LIMIT)}. {@code stack} is
     * caller-owned scratch of {@link #STACK_SIZE} entries.
     */
    static int factorize(long n, long[] primes, int[] exponents, long[] stack) {
        if (n < 2 || n >= LIMIT) {
            return -1;
        }
        int count = 0;
        long rest = n;
        long divisor = 2;
        while (divisor < TRIAL_LIMIT && divisor * divisor <= rest) {
            if (rest % divisor == 0) {
                int exponent = 0;
                while (rest % divisor == 0) {
                    rest = rest / divisor;
                    exponent++;
                }
                primes[count] = divisor;
                exponents[count] = exponent;
                count++;
            }
            divisor = divisor == 2 ? 3 : divisor + 2;
        }

        int top = 0;
        if (rest > 1) {
            stack[top] = rest;
            top++;
        }
        while (top > 0) {
            top--;
            long part = stack[top];
            // Trial division removed every factor below TRIAL_LIMIT, so a part below its square is prime.
            if (part < (long) TRIAL_LIMIT * TRIAL_LIMIT || isPrime(part)) {
                count = add(part, primes, exponents, count);
            } else {
                long factor = rho(part);
                stack[top] = factor;
                stack[top + 1] = part / factor;
                top = top + 2;
            }
        }
        return count;
    }

    /**
     * Whether {@code primes}/{@code exponents} is exactly {@code n}'s prime factorization, listed
     * with strictly ascending primes.
     */
    static boolean verify(long n, long[] primes, int[] exponents, int count) {
        if (n < 2 || n >= LIMIT || count < 1 || count > FactorProtocol.MAX_FACTORS) {
            return false;
        }
        long product = 1;
        long previous = 1;
        for (int i = 0; i < count; i++) {
            long prime = primes[i];
            int exponent = exponents[i];
            if (prime <= previous || exponent < 1 || !isPrime(prime)) {
                return false;
            }
            for (int e = 0; e < exponent; e++) {
                if (product > n / prime) {
                    return false;
                }
                product = product * prime;
            }
            previous = prime;
        }
        return product == n;
    }

    static boolean isPrime(long n) {
        if (n < 2) {
            return false;
        }
        for (int i = 0; i < BASES.length; i++) {
            long prime = BASES[i];
            if (n % prime == 0) {
                return n == prime;
            }
        }
        long odd = n - 1;
        int twos = 0;
        while ((odd & 1) == 0) {
            odd = odd >>> 1;
            twos++;
        }
        for (int i = 0; i < BASES.length; i++) {
            if (!probablePrime(BASES[i], odd, twos, n)) {
                return false;
            }
        }
        return true;
    }

    /** {@code a * b mod m} for {@code 0 <= a, b < m < LIMIT}, without overflow. */
    static long mulMod(long a, long b, long m) {
        if (m <= SMALL_MODULUS) {
            return a * b % m;
        }
        long result = 0;
        long addend = a;
        long bits = b;
        while (bits != 0) {
            if ((bits & 1) != 0) {
                result = result + addend;
                if (result >= m) {
                    result = result - m;
                }
            }
            addend = addend + addend;
            if (addend >= m) {
                addend = addend - m;
            }
            bits = bits >>> 1;
        }
        return result;
    }

    static long powMod(long base, long exponent, long m) {
        long result = 1;
        long square = base % m;
        long bits = exponent;
        while (bits != 0) {
            if ((bits & 1) != 0) {
                result = mulMod(result, square, m);
            }
            square = mulMod(square, square, m);
            bits = bits >>> 1;
        }
        return result;
    }

    static long gcd(long a, long b) {
        long x = a;
        long y = b;
        while (y != 0) {
            long remainder = x % y;
            x = y;
            y = remainder;
        }
        return x;
    }

    private static boolean probablePrime(long base, long odd, int twos, long n) {
        long x = powMod(base, odd, n);
        if (x == 1 || x == n - 1) {
            return true;
        }
        for (int i = 1; i < twos; i++) {
            x = mulMod(x, x, n);
            if (x == n - 1) {
                return true;
            }
        }
        return false;
    }

    /** A non-trivial factor of the odd composite {@code n}. */
    private static long rho(long n) {
        long c = 1;
        while (true) {
            long factor = brent(n, c);
            if (factor != n) {
                return factor;
            }
            c++;
        }
    }

    /** Brent's cycle search with {@code f(x) = x^2 + c}; returns {@code n} itself when this {@code c} fails. */
    private static long brent(long n, long c) {
        long y = 2;
        long x = 2;
        long saved = 2;
        long product = 1;
        long g = 1;
        int range = 1;
        while (g == 1) {
            x = y;
            for (int i = 0; i < range; i++) {
                y = next(y, c, n);
            }
            int done = 0;
            while (done < range && g == 1) {
                saved = y;
                int steps = Math.min(RHO_BATCH, range - done);
                for (int i = 0; i < steps; i++) {
                    y = next(y, c, n);
                    product = mulMod(product, Math.abs(x - y), n);
                }
                g = gcd(product, n);
                done = done + RHO_BATCH;
            }
            range = range * 2;
        }
        if (g == n) {
            // The batch overshot (or hit product 0); replay it one step at a time.
            g = 1;
            while (g == 1) {
                saved = next(saved, c, n);
                g = gcd(Math.abs(x - saved), n);
            }
        }
        return g;
    }

    private static long next(long y, long c, long n) {
        return (mulMod(y, y, n) + c) % n;
    }

    private static int add(long prime, long[] primes, int[] exponents, int count) {
        int at = 0;
        while (at < count && primes[at] < prime) {
            at++;
        }
        if (at < count && primes[at] == prime) {
            exponents[at] = exponents[at] + 1;
            return count;
        }
        for (int i = count; i > at; i--) {
            primes[i] = primes[i - 1];
            exponents[i] = exponents[i - 1];
        }
        primes[at] = prime;
        exponents[at] = 1;
        return count + 1;
    }
}
