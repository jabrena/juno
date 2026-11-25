package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.dm.JevModelCodec;
import io.github.jabrena.juno.dm.QuantizedJevInference;
import io.github.jabrena.juno.models.ConnectFourFeatures;
import io.github.jabrena.juno.models.ConnectFourPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectFourGameTest {
    private final ConnectFourFeatures host = new ConnectFourFeatures();

    @Test
    void theBoardComputesTheFeaturesTheModelWasTrainedOn() {
        Random random = new Random(3L);
        var game = new ConnectFourGame();
        float[] expected = new float[ConnectFourFeatures.COUNT];
        int positions = 0;
        for (int round = 0; round < 300; round++) {
            game.reset();
            ConnectFourPosition position = ConnectFourPosition.empty();
            while (!position.isTerminal()) {
                host.encode(position, expected);
                int[] actual = game.encode();
                for (int feature = 0; feature < ConnectFourFeatures.COUNT; feature++) {
                    assertThat((float) actual[feature]).as("position %s feature %d", position, feature)
                            .isEqualTo(expected[feature]);
                }
                positions++;
                int[] legal = position.legalActions();
                int column = legal[random.nextInt(legal.length)];
                assertThat(game.play(column)).isTrue();
                position = position.play(column);
            }
            assertThat(game.isOver()).isTrue();
            int winner = ConnectFourPosition.hasWon(position.red()) ? ConnectFourGame.FIRST
                    : ConnectFourPosition.hasWon(position.yellow()) ? ConnectFourGame.SECOND : ConnectFourGame.EMPTY;
            assertThat(game.winner()).isEqualTo(winner);
        }
        assertThat(positions).isGreaterThan(5000);
    }

    @Test
    void theSelfTestPositionsBlockAndWin() throws IOException {
        var model = new QuantizedJevInference(new JevModelCodec().read(model()));
        var game = new ConnectFourGame();
        play(game, 1, 0, 2, 0, 6, 0);
        assertThat(choose(model, game)).isEqualTo(0);
        game.reset();
        play(game, 3, 0, 3, 1, 3, 6);
        assertThat(choose(model, game)).isEqualTo(3);
    }

    @Test
    void illegalMovesChangeNothing() {
        var game = new ConnectFourGame();
        play(game, 0, 0, 0, 0, 0, 0);

        assertThat(game.play(0)).isFalse();
        assertThat(game.play(-1)).isFalse();
        assertThat(game.play(ConnectFourGame.COLUMNS)).isFalse();
        assertThat(game.legalActions()).containsExactly(1, 2, 3, 4, 5, 6, 1);
        assertThat(game.turn()).isEqualTo(ConnectFourGame.FIRST);
    }

    private static int choose(QuantizedJevInference model, ConnectFourGame game) {
        int[] state = game.encode();
        float[] features = new float[state.length];
        for (int index = 0; index < state.length; index++) features[index] = state[index];
        int[] legal = game.legalActions();
        return model.evaluate(features, legal, legal.length);
    }

    private static void play(ConnectFourGame game, int... columns) {
        for (int column : columns) assertThat(game.play(column)).isTrue();
    }

    private static byte[] model() throws IOException {
        try (InputStream file = ConnectFourFeatures.class.getResourceAsStream("/dm/CONNECT_FOUR.JEV")) {
            return Objects.requireNonNull(file, "dm/CONNECT_FOUR.JEV").readAllBytes();
        }
    }
}
