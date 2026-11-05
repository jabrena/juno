package io.github.jabrena.juno.hardware.device.tft;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plays every TFT game for a few simulated seconds in the emulator and compares the screen with its
 * screenshot in {@code src/test/resources/screenshots/games} (a test fixture, not published
 * anywhere — the site's Games page shows the CPU-played {@code .gif}s instead, not these stills).
 * A layout change, text that no longer fits, or a drawing bug shows up as a mismatch; the actual
 * screen and a diff are then written to {@code target/screenshots}.
 *
 * <p>After an intended visual change, regenerate the screenshots with
 * {@code ./mvnw -pl juno-examples test -Dtest=GameScreenshotTest -Djuno.updateScreenshots=true}.
 */
class GameScreenshotTest {
    /** Tolerated share of differing pixels, for last-bit differences in floating-point math. */
    private static final double TOLERANCE = 0.001;

    private static final Path SCREENSHOTS = Path.of(System.getProperty("basedir", "."))
            .resolve("src/test/resources/screenshots")
            .normalize();
    private static final Path ACTUAL = Path.of(System.getProperty("basedir", ".")).resolve("target/screenshots");

    /**
     * Game, screenshot name, and script: taps as {@code millis:x,y}, then {@code end:millis}, in the
     * game's own screen coordinates and simulated time.
     */
    static Stream<Arguments> games() {
        return Stream.of(
                game("api.hardware.device.tft.TouchPaintTFT", "tft-touch-paint", paintStrokes("end:12000")),
                game("api.hardware.device.tft.TouchPaintTFT", "tft-touch-paint-cleared", paintStrokes("12500:210,20 end:13500")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("games")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void matchesItsScreenshot(String game, String screenshot, String script) throws Exception {
        assertMatches("games", game, screenshot, script);
    }

    static void assertMatches(String folder, String game, String screenshot, String script) throws Exception {
        BufferedImage actual = play(game, script);
        Path golden = SCREENSHOTS.resolve(folder).resolve(screenshot + ".png");
        if (Boolean.getBoolean("juno.updateScreenshots")) {
            Files.createDirectories(golden.getParent());
            ImageIO.write(actual, "png", golden.toFile());
            return;
        }
        assertThat(golden).as("screenshot %s (create it with -Djuno.updateScreenshots=true)", golden).exists();
        BufferedImage expected = ImageIO.read(golden.toFile());
        assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).isEqualTo(expected.getHeight());

        BufferedImage diff = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        int different = 0;
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                boolean same = actual.getRGB(x, y) == expected.getRGB(x, y);
                if (!same) {
                    different = different + 1;
                }
                diff.setRGB(x, y, same ? (actual.getRGB(x, y) >> 2) & 0x3F3F3F : 0xFF00FF);
            }
        }
        int allowed = (int) (actual.getWidth() * actual.getHeight() * TOLERANCE);
        if (different > allowed) {
            save(actual, screenshot + ".png");
            save(diff, screenshot + "-diff.png");
        }
        assertThat(different)
                .as("%s: pixels differing from %s (actual and diff saved in %s)", game, golden, ACTUAL)
                .isLessThanOrEqualTo(allowed);
    }

    static BufferedImage play(String game, String script) throws Exception {
        List<TftEmulator.Tap> taps = new ArrayList<>();
        int end = 0;
        for (String step : script.trim().split("\\s+")) {
            String[] parts = step.split(":");
            if (parts[0].equals("end")) {
                end = Integer.parseInt(parts[1]);
            } else {
                String[] xy = parts[1].split(",");
                taps.add(new TftEmulator.Tap(Integer.parseInt(parts[0]), Integer.parseInt(xy[0]),
                        Integer.parseInt(xy[1])));
            }
        }
        // Programs are relative to io.github.jabrena.juno, e.g. games.chess.Chess or hardware.device.tft.TouchPaintTFT.
        String mainClass = "io.github.jabrena.juno." + game;
        return TftEmulator.run(mainClass, end, taps);
    }

    private static void save(BufferedImage image, String name) throws IOException {
        Files.createDirectories(ACTUAL);
        ImageIO.write(image, "png", ACTUAL.resolve(name).toFile());
    }

    private static Arguments game(String game, String screenshot, String script) {
        return Arguments.of(game, screenshot, script);
    }

    /** A wave, a curve and a diagonal, drawn as a stream of short taps. */
    private static String paintStrokes(String tail) {
        StringBuilder script = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            script.append(600 + i * 90).append(':').append(20 + i * 5).append(',')
                    .append(150 + (int) Math.round(60 * Math.sin(i / 4.0))).append(' ');
        }
        for (int i = 0; i < 36; i++) {
            script.append(5000 + i * 90).append(':').append(200 - i * 5).append(',')
                    .append(230 + (int) Math.round(30 * Math.cos(i / 3.0))).append(' ');
        }
        script.append("8500:120,20 ");
        for (int i = 0; i < 30; i++) {
            script.append(8700 + i * 90).append(':').append(60 + i * 4).append(',').append(100 + i * 4).append(' ');
        }
        return script.append(tail).toString();
    }
}
