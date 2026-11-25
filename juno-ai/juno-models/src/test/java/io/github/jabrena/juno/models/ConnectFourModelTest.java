package io.github.jabrena.juno.models;

import io.github.jabrena.juno.dm.JevModelCodec;
import io.github.jabrena.juno.dm.QuantizedJevInference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectFourModelTest {
    private static final ConnectFourFeatures FEATURES = new ConnectFourFeatures();
    private static ConnectFourDataset training;
    private static ConnectFourDataset heldOut;
    private static QuantizedJevInference model;

    @BeforeAll
    static void loadTrackedModel() throws IOException {
        training = ConnectFourModelArtifact.dataset();
        heldOut = ConnectFourDataset.generate(
                new ConnectFourOracle(ConnectFourModelArtifact.ORACLE_DEPTH), 2L, 100, ConnectFourModelArtifact.EXPLORATION);
        model = new QuantizedJevInference(new JevModelCodec().read(trackedArtifact()));
    }

    @Test
    void invalidPositionsAreRejected() {
        long bottomLeft = 1L;
        assertThatThrownBy(() -> new ConnectFourPosition(bottomLeft, bottomLeft)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectFourPosition(0L, bottomLeft)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectFourPosition(0b11L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectFourPosition(0b10L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectFourPosition(1L << ConnectFourPosition.ROWS, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConnectFourPosition.empty().play(ConnectFourPosition.COLUMNS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFullColumnCannotBePlayed() {
        ConnectFourPosition position = play(0, 0, 0, 0, 0, 0);

        assertThat(position.canPlay(0)).isFalse();
        assertThat(position.legalActions()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThatThrownBy(() -> position.play(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fourInALineWinsInEveryDirection() {
        assertThat(play(0, 1, 0, 1, 0, 1, 0).isTerminal()).as("vertical").isTrue();
        assertThat(play(0, 0, 1, 1, 2, 2, 3).isTerminal()).as("horizontal").isTrue();
        assertThat(play(0, 1, 1, 2, 2, 3, 2, 3, 3, 6, 3).isTerminal()).as("diagonal up").isTrue();
        assertThat(play(6, 5, 5, 4, 4, 3, 4, 3, 3, 0, 3).isTerminal()).as("diagonal down").isTrue();
        assertThat(play(0, 0, 1, 1, 2, 2).isTerminal()).isFalse();
        assertThat(play(0, 0, 1, 1, 2, 2, 3).legalActions()).isEmpty();
    }

    @Test
    void everyLineOfFourIsListedOnce() {
        assertThat(ConnectFourLines.ALL).hasSize(69).doesNotHaveDuplicates();
        assertThat(Arrays.stream(ConnectFourLines.ALL))
                .allMatch(line -> Long.bitCount(line) == 4 && (line & ~ConnectFourPosition.BOARD) == 0);
    }

    @Test
    void theOracleTakesAWinAndBlocksALoss() {
        var oracle = new ConnectFourOracle(ConnectFourModelArtifact.ORACLE_DEPTH);

        assertThat(oracle.bestAction(play(0, 1, 0, 1, 0, 1))).isEqualTo(0);
        assertThat(oracle.bestAction(play(0, 1, 0, 1, 0))).isEqualTo(0);
        assertThatThrownBy(() -> new ConnectFourOracle(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> oracle.bestAction(play(0, 1, 0, 1, 0, 1, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void featuresDescribeEachColumnMove() {
        float[] features = FEATURES.of(play(0, 1, 0, 1, 0));
        int column0 = 0;
        int column1 = ConnectFourFeatures.PER_COLUMN;

        assertThat(features).hasSize(ConnectFourFeatures.COUNT);
        assertThat(features[column0 + ConnectFourFeatures.BLOCK]).isEqualTo(1f);
        assertThat(features[column1 + ConnectFourFeatures.WIN]).isZero();
        assertThat(features[column1 + ConnectFourFeatures.LOSES]).isEqualTo(1f);
        assertThat(features[column0 + ConnectFourFeatures.LOSES]).isZero();
        assertThat(features[3 * ConnectFourFeatures.PER_COLUMN + ConnectFourFeatures.CENTRE]).isEqualTo(3f);
        assertThat(FEATURES.of(play(0, 0, 0, 0, 0, 0))).startsWith(new float[ConnectFourFeatures.PER_COLUMN]);
        assertThat(FEATURES.of(play(0, 1, 0, 1, 0, 1, 0))).containsOnly(0f);
        assertThatThrownBy(() -> FEATURES.encode(ConnectFourPosition.empty(), new float[1]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trackedArtifactMatchesTheCanonicalModelFactory() throws IOException {
        assertThat(Arrays.equals(ConnectFourModelArtifact.encoded(), trackedArtifact())).isTrue();
    }

    @Test
    void theModelTakesEveryImmediateWin() {
        int chances = 0;
        for (ConnectFourDataset.Labelled example : heldOut.examples()) {
            ConnectFourPosition position = example.position();
            if (Arrays.stream(position.legalActions()).anyMatch(position::wins)) {
                chances++;
                assertThat(position.wins(move(position))).as("position %s", position).isTrue();
            }
        }
        assertThat(chances).isGreaterThan(50);
    }

    @Test
    void theModelNeverLetsTheOpponentWinWhenAMoveAvoidsIt() {
        int avoidable = 0;
        for (ConnectFourDataset.Labelled example : heldOut.examples()) {
            ConnectFourPosition position = example.position();
            int[] legal = position.legalActions();
            boolean canWin = Arrays.stream(legal).anyMatch(position::wins);
            if (!canWin && Arrays.stream(legal).anyMatch(column -> !opponentWinsAfter(position, column))) {
                avoidable++;
                assertThat(opponentWinsAfter(position, move(position))).as("position %s", position).isFalse();
            }
        }
        assertThat(avoidable).isGreaterThan(1000);
    }

    @Test
    void theModelAgreesWithTheOracleOnPositionsItWasNotTrainedOn() {
        // Measured: 61.7% of the training positions and 61.4% of the held-out ones; a linear model cannot match a search.
        assertThat(agreement(training)).isGreaterThan(0.60);
        assertThat(agreement(heldOut)).isGreaterThan(0.60);
    }

    @Test
    void theModelBeatsARandomPlayerWithEitherColour() {
        Random random = new Random(7L);
        int asRed = 0;
        int asYellow = 0;
        for (int game = 0; game < 100; game++) {
            asRed += winner(random, true) == 1 ? 1 : 0;
            asYellow += winner(random, false) == -1 ? 1 : 0;
        }
        assertThat(asRed).isGreaterThanOrEqualTo(98);
        assertThat(asYellow).isGreaterThanOrEqualTo(98);
    }

    /** Plays the model against random moves; returns 1 when red wins, -1 when yellow wins, 0 for a draw. */
    private static int winner(Random random, boolean modelIsRed) {
        ConnectFourPosition position = ConnectFourPosition.empty();
        while (!position.isTerminal()) {
            int[] legal = position.legalActions();
            position = position.play(position.redTurn() == modelIsRed ? move(position) : legal[random.nextInt(legal.length)]);
        }
        return ConnectFourPosition.hasWon(position.red()) ? 1 : ConnectFourPosition.hasWon(position.yellow()) ? -1 : 0;
    }

    private static boolean opponentWinsAfter(ConnectFourPosition position, int column) {
        ConnectFourPosition next = position.play(column);
        return Arrays.stream(next.legalActions()).anyMatch(next::wins);
    }

    private static double agreement(ConnectFourDataset dataset) {
        long agreed = dataset.examples().stream().filter(example -> move(example.position()) == example.action()).count();
        return agreed / (double) dataset.size();
    }

    private static int move(ConnectFourPosition position) {
        int[] legal = position.legalActions();
        return model.evaluate(FEATURES.of(position), legal, legal.length);
    }

    private static ConnectFourPosition play(int... columns) {
        ConnectFourPosition position = ConnectFourPosition.empty();
        for (int column : columns) {
            position = position.play(column);
        }
        return position;
    }

    private static byte[] trackedArtifact() throws IOException {
        try (InputStream artifact = ConnectFourModelTest.class.getResourceAsStream("/dm/CONNECT_FOUR.JEV")) {
            return Objects.requireNonNull(artifact, "dm/CONNECT_FOUR.JEV").readAllBytes();
        }
    }
}
