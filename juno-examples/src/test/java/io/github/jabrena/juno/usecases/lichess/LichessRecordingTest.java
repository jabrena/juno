package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.EmulatedShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.lichess.challenge.ChallengeView;
import io.github.jabrena.juno.usecases.lichess.challenge.LichessModel;
import io.github.jabrena.juno.usecases.lichess.challenge.PuzzleBoard;
import io.github.jabrena.juno.usecases.lichess.commons.LichessViews;
import io.github.jabrena.juno.usecases.lichess.cover.CoverView;
import io.github.jabrena.juno.usecases.lichess.win.WinView;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Writes the frames of the daily puzzle on demand, for documentation media: the cover, the challenge board with the
 * player's moves and Lichess's replies, and the win screen. The puzzle is a snapshot of
 * {@code https://lichess.org/api/puzzle/daily}, so the network is replaced by that response. ffmpeg turns the frames
 * into a video.
 *
 * <p>{@code ./mvnw -pl juno-examples test -Dtest=LichessRecordingTest -Djuno.recording.lichess=true}
 */
class LichessRecordingTest {

    private final TftTouchShield tft = new TftTouchShield(mock(Gpio.class));
    private static final String DAILY_PUZZLE = "{\"game\":{\"id\":\"7J4rhbpE\"},\"puzzle\":{\"id\":\"fWQJU\","
            + "\"rating\":1869,\"plays\":87728,\"solution\":[\"f8h8\",\"h6g5\",\"h8h5\",\"g5g4\",\"f2f3\"],"
            + "\"themes\":[\"endgame\",\"long\",\"mateIn3\"],\"fen\":\"5R2/5Bp1/5b1k/4p2p/5p2/6P1/P2rrP1P/5RK1 w - - 0 1\","
            + "\"lastMove\":\"f5f4\",\"initialPly\":55}}";
    /** The same geometry as the challenge board's first square, so a touch lands in the square it names. */
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 44;
    private static final int CELL = 28;
    private static final int FRAME_MILLIS = 40;
    private static final int IDLE_FRAMES = 12;
    private static final int MOVE_FRAMES = 20;
    private static final int WIN_FRAMES = 75;

    @Test
    @EnabledIfSystemProperty(named = "juno.recording.lichess", matches = "true")
    void recordsTheDailyPuzzle() throws Exception {
        Path output = Path.of(System.getProperty("basedir", "."), "target", "recordings", "lichess");
        Files.createDirectories(output);
        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);

        byte[] response = DAILY_PUZZLE.getBytes();
        LichessModel model = new LichessModel();
        assertThat(model.loadResponse(response, response.length, 200, LichessModel.DAILY)).isTrue();

        SimulatedClock clock = new SimulatedClock();
        RecordingNavigator navigator = new RecordingNavigator();
        CoverView cover = new CoverView(tft, navigator);
        ChallengeView challenge = new ChallengeView(tft, navigator, model, clock);
        WinView win = new WinView(tft, navigator, clock);
        Recorder recorder = new Recorder(output, clock);

        cover.enter();
        recorder.capture(tft, IDLE_FRAMES, cover::render);
        cover.touch(120, 225);
        challenge.enter();
        recorder.capture(tft, IDLE_FRAMES, challenge::render);

        String[] moves = {"f8h8", "h6g5", "h8h5", "g5g4", "f2f3"};
        for (int index = 0; index < moves.length; index += 2) {
            byte[] uci = moves[index].getBytes();
            int move = PuzzleBoard.parseUci(uci, uci.length);
            touchSquare(challenge, PuzzleBoard.from(move));
            recorder.capture(tft, MOVE_FRAMES / 4, challenge::render);
            touchSquare(challenge, PuzzleBoard.to(move));
            recorder.capture(tft, MOVE_FRAMES, challenge::render);
        }
        assertThat(navigator.shown).as("the puzzle should be solved").isEqualTo(LichessViews.WIN);

        win.enter();
        recorder.capture(tft, WIN_FRAMES, win::render);
        assertThat(recorder.written).as("recording should contain frames").isPositive();
        System.out.println("LICHESS-RECORDING " + recorder.written + " frames in " + output);
    }

    /** Touches the centre of {@code square}, using the board's own mapping for the white player. */
    private static void touchSquare(ChallengeView challenge, int square) {
        int x = BOARD_X + (square % 8) * CELL + CELL / 2;
        int y = BOARD_Y + (square / 8) * CELL + CELL / 2;
        challenge.touch(x, y);
    }

    private static final class SimulatedClock implements Clock {
        private int now;

        void advance() {
            now += FRAME_MILLIS;
        }

        @Override
        public int millis() {
            return now;
        }

        @Override
        public int micros() {
            return now * 1000;
        }
    }

    private static final class RecordingNavigator implements Navigator {
        private int shown = -1;

        @Override
        public void show(int viewId) {
            shown = viewId;
        }

        @Override
        public void back() {
        }
    }

    private static final class Recorder {
        private final Path output;
        private final SimulatedClock clock;
        private int written;

        Recorder(Path output, SimulatedClock clock) {
            this.output = output;
            this.clock = clock;
        }

        void capture(TftTouchShield tft, int frames, Runnable render) throws Exception {
            for (int frame = 0; frame < frames; frame++) {
                clock.advance();
                render.run();
                ImageIO.write(screen(tft), "png", output.resolve("frame-%05d.png".formatted(written)).toFile());
                written++;
            }
        }

        private static BufferedImage screen(TftTouchShield tft) {
            int width = tft.width();
            int height = tft.height();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = EmulatedShield.FRAMEBUFFER[y * EmulatedShield.STRIDE + x];
                    int red = (color >> 11) & 31;
                    int green = (color >> 5) & 63;
                    int blue = color & 31;
                    image.setRGB(x, y, (red * 255 / 31) << 16 | (green * 255 / 63) << 8 | (blue * 255 / 31));
                }
            }
            return image;
        }
    }
}
