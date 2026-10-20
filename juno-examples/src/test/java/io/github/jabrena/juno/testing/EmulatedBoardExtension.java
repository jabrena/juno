package io.github.jabrena.juno.testing;


import io.github.jabrena.juno.api.hardware.EmulatedMemory;
import io.github.jabrena.juno.api.hardware.EmulatedUnoQ;
import io.github.jabrena.juno.api.hardware.gpio.EmulatedShield;
import io.github.jabrena.juno.api.hardware.led.EmulatedLedMatrix;
import io.github.jabrena.juno.api.hardware.time.SimulatedClock;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Stands the emulated TFT shield, the simulated clock, an UNO Q board model, the emulated arena and an UNO Q LED
 * matrix in for the board before any test class runs, so example code that gets its own {@code MCU.getGpio()},
 * {@code MCU.getClock()}, {@code MCU.getBoardModel()}, {@code MCU.getBoardMemory()} or {@code MCU.getLedMatrix()}
 * (a game's real classes, run whole) meets them instead of the native ports. Registered for every test through JUnit's extension auto-detection; a unit test that
 * wants a mock passes {@code mock} explicitly instead.
 */
public final class EmulatedBoardExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        EmulatedShield.install();
        SimulatedClock.install();
        EmulatedUnoQ.install();
        EmulatedMemory.install();
        EmulatedLedMatrix.install();
    }
}
