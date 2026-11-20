package io.github.jabrena.juno.language.concurrency;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** JVM checks for the N-Queens search that {@link NQueensBenchmark} times on the board. */
class NQueensTest {

    @ParameterizedTest(name = "N={0}")
    @CsvSource({"4, 2", "6, 4", "8, 92", "10, 724", "12, 14200"})
    void countsTheKnownNumberOfSolutions(int n, int solutions) {
        assertThat(NQueens.count(n, 0, 1)).isEqualTo(solutions);
    }

    @ParameterizedTest(name = "N={0}")
    @CsvSource({"4, 2", "6, 4", "8, 92", "10, 724", "12, 14200"})
    void evenAndOddColumnsSplitTheWorkInHalves(int n, int solutions) {
        int even = NQueens.count(n, 0, 2);
        int odd = NQueens.count(n, 1, 2);

        assertThat(even + odd).isEqualTo(solutions);
        assertThat(even).isEqualTo(odd);
    }
}
