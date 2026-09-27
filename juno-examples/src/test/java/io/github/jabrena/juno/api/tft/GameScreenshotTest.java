package io.github.jabrena.juno.api.tft;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Plays every TFT game for a few simulated seconds in the emulator and compares the screen with its
 * screenshot in {@code docs/images/games}, which {@code docs/GAMES.md} shows. A layout change, text
 * that no longer fits, or a drawing bug shows up as a mismatch; the actual screen and a diff are
 * then written to {@code target/screenshots}.
 *
 * <p>After an intended visual change, regenerate the screenshots with
 * {@code ./mvnw -pl juno-examples test -Dtest=GameScreenshotTest -Djuno.updateScreenshots=true}.
 */
class GameScreenshotTest {
    /** Tolerated share of differing pixels, for last-bit differences in floating-point math. */
    private static final double TOLERANCE = 0.001;

    private static final Path GOLDEN = Path.of(System.getProperty("basedir", ".")).resolve("../docs/images/games")
            .normalize();
    private static final Path ACTUAL = Path.of(System.getProperty("basedir", ".")).resolve("target/screenshots");

    /**
     * Game, screenshot name, and script: taps as {@code millis:x,y}, then {@code end:millis}, in the
     * game's own screen coordinates and simulated time.
     */
    static Stream<Arguments> games() {
        return Stream.of(
                game("Backgammon", "backgammon", "500:290,110 4000:290,110 end:7000"),
                game("Battleship", "battleship", "500:120,160 end:6000"),
                game("Blackjack", "blackjack", "500:190,280 end:3000"),
                game("Checkers", "checkers", "500:22,198 900:50,170 end:6000"),
                game("Chess", "chess", "500:134,226 900:134,170 end:8000"),
                game("ConnectFour", "connect-four", "500:120,160 end:6000"),
                game("Game2048", "game-2048", "500:120,160 1200:230,200 1900:10,200 2600:120,300 end:3200"),
                game("GameOfLife", "game-of-life", "500:120,160 end:6000"),
                game("LunarLander", "lunar-lander", "500:120,160 1500:120,290 2200:120,290 end:2600"),
                game("Mancala", "mancala", "500:143,146 end:7000"),
                game("Minesweeper", "minesweeper", "500:120,160 end:6000"),
                game("MissileCommand", "missile-command", "500:120,160 1500:60,120 2200:180,90 2600:120,150 end:3000"),
                game("Othello", "othello", "500:106,114 end:6000"),
                game("PacMan", "pacman", "500:120,160 end:6000"),
                game("Pong", "pong", "500:120,160 end:6000"),
                game("RockPaperScissorsLizardSpock", "rock-paper-scissors-lizard-spock",
                        "500:216,280 3000:24,280 5500:120,280 end:8000"),
                game("RussianRoulette", "russian-roulette", "500:180,290 3500:180,290 end:6500"),
                game("Simon", "simon", "500:120,190 end:1500"),
                game("SlotMachine", "slot-machine", "500:160,285 end:5000"),
                game("Snake", "snake", "500:120,160 end:6000"),
                game("Solitaire", "solitaire", "500:120,160 end:6000"),
                game("SpaceInvaders", "space-invaders", "500:120,160 end:6000"),
                game("SpaceParanoids", "space-paranoids", "500:160,130 end:9000"),
                game("StarWars", "star-wars", "500:160,130 6500:116,146 end:6700"),
                game("Tempest", "tempest", "500:120,160 end:6000"),
                game("Tetris", "tetris", "500:120,160 end:6000"),
                game("TexasHoldem", "texas-holdem", "500:120,300 end:6000"),
                game("TftTouchPaint", "tft-touch-paint", paintStrokes()),
                game("TicTacToe", "tic-tac-toe", "500:120,160 end:6000"),
                game("WhacAMole", "whac-a-mole", "500:120,160 end:2500"),
                game("Yahtzee", "yahtzee", "500:120,285 2000:30,205 2400:120,205 3000:120,285 end:5000"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("games")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void matchesItsScreenshot(String game, String screenshot, String script) throws Exception {
        BufferedImage actual = play(game, script);
        Path golden = GOLDEN.resolve(screenshot + ".png");
        if (Boolean.getBoolean("juno.updateScreenshots")) {
            Files.createDirectories(GOLDEN);
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
        return TftEmulator.run("io.github.jabrena.juno.api.tft." + game, end, taps);
    }

    private static void save(BufferedImage image, String name) throws IOException {
        Files.createDirectories(ACTUAL);
        ImageIO.write(image, "png", ACTUAL.resolve(name).toFile());
    }

    private static Arguments game(String game, String screenshot, String script) {
        return Arguments.of(game, screenshot, script);
    }

    /** A wave, a curve and a diagonal, drawn as a stream of short taps. */
    private static String paintStrokes() {
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
        return script.append("end:12000").toString();
    }
}
