package io.github.jabrena.juno.testing;


import io.github.jabrena.juno.api.io.gpio.EmulatedShield;
import io.github.jabrena.juno.api.time.SimulatedClock;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Stands the emulated TFT shield and the simulated clock in for the board before any test class runs, so example
 * code that creates its own {@code MCU.getGpio()} or {@code MCU.getClock()} (a game's real classes, run whole) meets them
 * instead of the native ports. Registered for every test through JUnit's extension auto-detection; a unit test that
 * wants a mock passes {@code mock} explicitly instead.
 */
public final class EmulatedBoardExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        EmulatedShield.install();
        SimulatedClock.install();
    }
}
