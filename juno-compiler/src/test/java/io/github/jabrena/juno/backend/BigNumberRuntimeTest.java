package io.github.jabrena.juno.backend;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds the exact BigInteger/BigDecimal runtime text with the host C++ compiler and checks thousands of random
 * operations against the JDK classes (results, scales, rounding, text and the exception class). The shim's own
 * handle helpers are replaced by index-based ones so 64-bit hosts work.
 */
class BigNumberRuntimeTest {
    private static final int CASES = 6000;

    @TempDir
    Path directory;

    @Test
    void matchesTheJdkOnRandomOperations() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        Files.writeString(directory.resolve("runtime.inc"), BigNumberRuntime.helpers(1, 2, 3, false),
                StandardCharsets.UTF_8);
        Files.copy(Path.of("src/test/resources/bignumber/driver.cpp"), directory.resolve("driver.cpp"));
        Process build = new ProcessBuilder(compiler, "-std=c++17", "-O1", "-w", "-o", "driver", "driver.cpp")
                .directory(directory.toFile()).redirectErrorStream(true).start();
        String buildOutput = new String(build.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(build.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(build.exitValue()).as(buildOutput).isZero();

        Random random = new Random(20261007L);
        List<String> commands = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (int index = 0; index < CASES; index++) {
            Case generated = generate(random);
            commands.add(generated.command());
            expected.add(generated.result());
        }
        Files.write(directory.resolve("commands.txt"), commands);
        Process run = new ProcessBuilder(directory.resolve("driver").toString())
                .redirectInput(directory.resolve("commands.txt").toFile())
                .redirectOutput(directory.resolve("actual.txt").toFile()).start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        List<String> actual = Files.readAllLines(directory.resolve("actual.txt"));
        assertThat(actual).hasSameSizeAs(expected);
        for (int index = 0; index < CASES; index++) {
            assertThat(normalize(actual.get(index))).as(commands.get(index)).isEqualTo(normalize(expected.get(index)));
        }
    }

    /** Exception messages differ between JDK code paths, so only the class is compared. */
    private static String normalize(String line) {
        if (!line.startsWith("EXC ")) {
            return line;
        }
        String[] parts = line.split(" ", 3);
        return parts[0] + " " + parts[1];
    }

    private record Case(String command, String result) {
    }

    private static BigInteger randomBig(Random random) {
        int kind = random.nextInt(10);
        if (kind == 0) return BigInteger.ZERO;
        if (kind == 1) return random.nextBoolean() ? BigInteger.ONE : BigInteger.ONE.negate();
        if (kind == 2) {
            BigInteger power = BigInteger.ONE.shiftLeft(random.nextInt(130));
            return random.nextBoolean() ? power : power.subtract(BigInteger.ONE);
        }
        if (kind == 3 || kind == 4) {
            BigInteger edge = BigInteger.ONE.shiftLeft(32 * (1 + random.nextInt(40)))
                    .subtract(BigInteger.valueOf(random.nextInt(5)));
            if (kind == 4) {
                edge = edge.multiply(BigInteger.ONE.shiftLeft(32 * random.nextInt(4))).add(BigInteger.valueOf(random.nextInt(3)));
            }
            return random.nextBoolean() ? edge : edge.negate();
        }
        BigInteger value = new BigInteger(1 + random.nextInt(random.nextInt(4) == 0 ? 420 : 70), random);
        return random.nextBoolean() ? value : value.negate();
    }

    private static BigDecimal randomDecimal(Random random) {
        int scale = random.nextInt(9) == 0 ? random.nextInt(60) - 30 : random.nextInt(25) - 4;
        return new BigDecimal(randomBig(random), scale);
    }

    private static String show(BigDecimal value) {
        return value.toString() + "|" + value.scale();
    }

    private static String bits(double value) {
        return String.format("%016x", Double.doubleToLongBits(value));
    }

    private static Case generate(Random random) {
        String[] operations = {
            "bi_add", "bi_sub", "bi_mul", "bi_div", "bi_rem", "bi_mod", "bi_pow", "bi_sqrt", "bi_gcd", "bi_shl",
            "bi_shr", "bi_neg", "bi_abs", "bi_min", "bi_max", "bi_cmp", "bi_signum", "bi_bitlen", "bi_int",
            "bi_long", "bi_dbl", "bi_str", "bd_add", "bd_sub", "bd_mul", "bd_addmc", "bd_mulmc", "bd_div",
            "bd_divs", "bd_divm", "bd_divmc", "bd_sqrt", "bd_round", "bd_setscale", "bd_setscale0", "bd_pow",
            "bd_strip", "bd_neg", "bd_abs", "bd_min", "bd_max", "bd_mpl", "bd_mpr", "bd_cmp", "bd_prec",
            "bd_bi", "bd_int", "bd_long", "bd_dbl", "bd_str", "bd_plain"};
        String operation = operations[random.nextInt(operations.length)];
        BigInteger a = randomBig(random);
        BigInteger b = randomBig(random);
        BigDecimal x = randomDecimal(random);
        BigDecimal y = random.nextInt(3) == 0 ? x.multiply(randomDecimal(random)) : randomDecimal(random);
        int precision = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(40);
        int mode = random.nextInt(8);
        MathContext context = new MathContext(precision, RoundingMode.valueOf(mode));
        String command = null;
        try {
            switch (operation) {
                case "bi_add": command = operation + " " + a + " " + b; return new Case(command, a.add(b).toString());
                case "bi_sub": command = operation + " " + a + " " + b; return new Case(command, a.subtract(b).toString());
                case "bi_mul": command = operation + " " + a + " " + b; return new Case(command, a.multiply(b).toString());
                case "bi_div": command = operation + " " + a + " " + b; return new Case(command, a.divide(b).toString());
                case "bi_rem": command = operation + " " + a + " " + b; return new Case(command, a.remainder(b).toString());
                case "bi_mod": command = operation + " " + a + " " + b; return new Case(command, a.mod(b).toString());
                case "bi_pow": {
                    int exponent = random.nextInt(25) - 1;
                    command = operation + " " + a + " " + exponent;
                    return new Case(command, a.pow(exponent).toString());
                }
                case "bi_sqrt": command = operation + " " + a; return new Case(command, a.sqrt().toString());
                case "bi_gcd": command = operation + " " + a + " " + b; return new Case(command, a.gcd(b).toString());
                case "bi_shl": {
                    int distance = random.nextInt(200) - 100;
                    command = operation + " " + a + " " + distance;
                    return new Case(command, a.shiftLeft(distance).toString());
                }
                case "bi_shr": {
                    int distance = random.nextInt(200) - 100;
                    command = operation + " " + a + " " + distance;
                    return new Case(command, a.shiftRight(distance).toString());
                }
                case "bi_neg": command = operation + " " + a; return new Case(command, a.negate().toString());
                case "bi_abs": command = operation + " " + a; return new Case(command, a.abs().toString());
                case "bi_min": command = operation + " " + a + " " + b; return new Case(command, a.min(b).toString());
                case "bi_max": command = operation + " " + a + " " + b; return new Case(command, a.max(b).toString());
                case "bi_cmp": command = operation + " " + a + " " + b; return new Case(command, String.valueOf(a.compareTo(b)));
                case "bi_signum": command = operation + " " + a; return new Case(command, String.valueOf(a.signum()));
                case "bi_bitlen": command = operation + " " + a; return new Case(command, String.valueOf(a.bitLength()));
                case "bi_int": command = operation + " " + a; return new Case(command, String.valueOf(a.intValue()));
                case "bi_long": command = operation + " " + a; return new Case(command, String.valueOf(a.longValue()));
                case "bi_dbl": command = operation + " " + a; return new Case(command, bits(a.doubleValue()));
                case "bi_str": command = operation + " " + a; return new Case(command, a.toString());
                case "bd_add": command = operation + " " + x + " " + y; return new Case(command, show(x.add(y)));
                case "bd_sub": command = operation + " " + x + " " + y; return new Case(command, show(x.subtract(y)));
                case "bd_mul": command = operation + " " + x + " " + y; return new Case(command, show(x.multiply(y)));
                case "bd_addmc":
                    command = operation + " " + x + " " + y + " " + precision + " " + mode;
                    return new Case(command, show(x.add(y, context)));
                case "bd_mulmc":
                    command = operation + " " + x + " " + y + " " + precision + " " + mode;
                    return new Case(command, show(x.multiply(y, context)));
                case "bd_div": command = operation + " " + x + " " + y; return new Case(command, show(x.divide(y)));
                case "bd_divs": {
                    int scale = random.nextInt(50) - 8;
                    command = operation + " " + x + " " + y + " " + scale + " " + mode;
                    return new Case(command, show(x.divide(y, scale, RoundingMode.valueOf(mode))));
                }
                case "bd_divm":
                    command = operation + " " + x + " " + y + " " + mode;
                    return new Case(command, show(x.divide(y, RoundingMode.valueOf(mode))));
                case "bd_divmc":
                    command = operation + " " + x + " " + y + " " + precision + " " + mode;
                    return new Case(command, show(x.divide(y, context)));
                case "bd_sqrt":
                    command = operation + " " + x + " " + precision + " " + mode;
                    return new Case(command, show(x.sqrt(context)));
                case "bd_round":
                    command = operation + " " + x + " " + precision + " " + mode;
                    return new Case(command, show(x.round(context)));
                case "bd_setscale": {
                    int scale = random.nextInt(50) - 20;
                    command = operation + " " + x + " " + scale + " " + mode;
                    return new Case(command, show(x.setScale(scale, RoundingMode.valueOf(mode))));
                }
                case "bd_setscale0": {
                    int scale = random.nextInt(50) - 20;
                    command = operation + " " + x + " " + scale;
                    return new Case(command, show(x.setScale(scale)));
                }
                case "bd_pow": {
                    int exponent = random.nextInt(9) - 1;
                    command = operation + " " + x + " " + exponent;
                    return new Case(command, show(x.pow(exponent)));
                }
                case "bd_strip": command = operation + " " + x; return new Case(command, show(x.stripTrailingZeros()));
                case "bd_neg": command = operation + " " + x; return new Case(command, show(x.negate()));
                case "bd_abs": command = operation + " " + x; return new Case(command, show(x.abs()));
                case "bd_min": command = operation + " " + x + " " + y; return new Case(command, show(x.min(y)));
                case "bd_max": command = operation + " " + x + " " + y; return new Case(command, show(x.max(y)));
                case "bd_mpl": {
                    int places = random.nextInt(40) - 20;
                    command = operation + " " + x + " " + places;
                    return new Case(command, show(x.movePointLeft(places)));
                }
                case "bd_mpr": {
                    int places = random.nextInt(40) - 20;
                    command = operation + " " + x + " " + places;
                    return new Case(command, show(x.movePointRight(places)));
                }
                case "bd_cmp": command = operation + " " + x + " " + y; return new Case(command, String.valueOf(x.compareTo(y)));
                case "bd_prec": command = operation + " " + x; return new Case(command, String.valueOf(x.precision()));
                case "bd_bi": command = operation + " " + x; return new Case(command, x.toBigInteger().toString());
                case "bd_int": command = operation + " " + x; return new Case(command, String.valueOf(x.intValue()));
                case "bd_long": command = operation + " " + x; return new Case(command, String.valueOf(x.longValue()));
                case "bd_dbl": command = operation + " " + x; return new Case(command, bits(x.doubleValue()));
                case "bd_str": command = operation + " " + x; return new Case(command, x.toString());
                case "bd_plain": command = operation + " " + x; return new Case(command, x.toPlainString());
                default: throw new IllegalStateException(operation);
            }
        } catch (ArithmeticException | IllegalArgumentException failure) {
            return new Case(command, "EXC " + failure.getClass().getSimpleName() + " " + failure.getMessage());
        }
    }

    private static String availableCppCompiler() {
        for (String candidate : new String[]{"clang++", "g++"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) return candidate;
            } catch (IOException | InterruptedException ignored) {
                // Try the next compiler.
            }
        }
        return null;
    }
}
