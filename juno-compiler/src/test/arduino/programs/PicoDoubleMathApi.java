package demo;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;

/** Double-precision transcendental math on the RP2350 toolchain. */
@Board(RaspberryPico2WifiBoard.class)
public final class PicoDoubleMathApi {
    public static void main(String[] args) {
        double value = 0.5;
        Math.sin(value);
        Math.cos(value);
        Math.tan(value);
        Math.exp(value);
        Math.log(value);
        Math.pow(value, 2.0);
        Math.hypot(value, 2.0);
    }
}
