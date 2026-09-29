package io.github.jabrena.juno.api.tft;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Verifies Missile Command's full game-over lifecycle through its public entry point. */
class MissileCommandLifecycleTest {
    private static final String MAIN_CLASS =
            "io.github.jabrena.juno.games.missilecommand.MissileCommand";

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void returnsToTheCoverAfterGameOver() throws Exception {
        BufferedImage cover = TftEmulator.run(MAIN_CLASS, 500, List.of());

        BufferedImage afterGame = TftEmulator.run(MAIN_CLASS, 185_000, List.of(
                new TftEmulator.Tap(600, 120, 160),
                new TftEmulator.Tap(1_200, 180, 180)));

        assertThat(pixels(afterGame)).containsExactly(pixels(cover));
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
