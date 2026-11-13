package io.github.jabrena.juno.hardware.device;

import io.github.jabrena.juno.hardware.device.grove.GrovePicoShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveAnalogGas;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveGpsAir530;
import io.github.jabrena.juno.hardware.device.grove.sensors.GrovePirMotion;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveSoundV16;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveTemperatureV12;
import io.github.jabrena.juno.hardware.device.imu.Bmi088;
import io.github.jabrena.juno.hardware.device.servo.PwmServoShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.internal.NativeClock;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Each convenience constructor takes its collaborator from {@code MCU}. With {@code MCU} replaced by mocks, it must
 * drive the same collaborators in the same way as the explicit constructor does.
 */
@DisplayName("Convenience constructors drive their collaborators exactly as the explicit forms do")
class McuConvenienceBehaviorTest {

    @AfterEach
    void restoreBoardServices() {
        NativeGpio.setGpio(NativeGpio.INSTANCE);
        NativeClock.setClock(NativeClock.INSTANCE);
        NativeI2c.setI2c(NativeI2c.INSTANCE);
    }

    @Test
    @DisplayName("GrovePicoShield() reads the Pico's pins as GrovePicoShield(MCU.getGpio()) does")
    void shouldMatchGrovePicoShieldWithAnExplicitGpio() {
        Gpio board = mock(Gpio.class);
        NativeGpio.setGpio(board);
        new GrovePicoShield();
        Gpio explicit = mock(Gpio.class);
        new GrovePicoShield(explicit);

        assertThat(interactions(board)).isEqualTo(interactions(explicit));
    }

    @Test
    @DisplayName("GroveTemperatureV12() samples the board's pins as the explicit form does")
    void shouldMatchGroveTemperatureV12WithAnExplicitGpio() {
        Gpio board = mock(Gpio.class);
        NativeGpio.setGpio(board);
        new GroveTemperatureV12().rawValue(0);
        Gpio explicit = mock(Gpio.class);
        new GroveTemperatureV12(explicit).rawValue(0);

        assertThat(interactions(board)).isEqualTo(interactions(explicit));
    }

    @Test
    @DisplayName("GrovePirMotion() configures its signal pin as the explicit form does")
    void shouldMatchGrovePirMotionWithAnExplicitGpio() {
        Gpio board = mock(Gpio.class);
        NativeGpio.setGpio(board);
        new GrovePirMotion();
        Gpio explicit = mock(Gpio.class);
        new GrovePirMotion(explicit);

        assertThat(interactions(board)).isEqualTo(interactions(explicit));
    }

    @Test
    @DisplayName("GroveAnalogGas() and GroveSoundV16() read the board's analog pins as the explicit forms do")
    void shouldMatchAnalogSensorsWithAnExplicitGpio() {
        Gpio board = mock(Gpio.class);
        NativeGpio.setGpio(board);
        new GroveAnalogGas().rawValue(0);
        new GroveSoundV16().rawValue(1);
        Gpio explicit = mock(Gpio.class);
        new GroveAnalogGas(explicit).rawValue(0);
        new GroveSoundV16(explicit).rawValue(1);

        assertThat(interactions(board)).isEqualTo(interactions(explicit));
    }

    @Test
    @DisplayName("GroveGpsAir530(pin) takes its pins and timer from the board, as the explicit form does")
    void shouldMatchGroveGpsAir530WithExplicitServices() {
        Gpio board = mock(Gpio.class);
        Clock boardClock = mock(Clock.class);
        NativeGpio.setGpio(board);
        NativeClock.setClock(boardClock);
        new GroveGpsAir530(2);
        Gpio explicit = mock(Gpio.class);
        Clock explicitClock = mock(Clock.class);
        new GroveGpsAir530(explicit, explicitClock, 2);

        assertThat(interactions(board)).isEqualTo(interactions(explicit));
        assertThat(interactions(boardClock)).isEqualTo(interactions(explicitClock));
    }

    @Test
    @DisplayName("PwmServoShield(address) writes to the board's I2C bus as the explicit form does")
    void shouldMatchPwmServoShieldWithAnExplicitI2c() {
        I2c board = mock(I2c.class);
        NativeI2c.setI2c(board);
        new PwmServoShield(PwmServoShield.ADDRESS_DEFAULT);
        I2c explicit = mock(I2c.class);
        new PwmServoShield(explicit, PwmServoShield.ADDRESS_DEFAULT);

        assertThat(interactions(board)).isEqualTo(interactions(explicit));
    }

    @Test
    @DisplayName("Bmi088() probes the board's I2C bus and clock as Bmi088(MCU.getI2c(), MCU.getClock()) does")
    void shouldMatchBmi088WithExplicitServices() {
        // Given: a mock never acknowledges the sensor, so both forms fail the same way during construction
        I2c board = mock(I2c.class);
        Clock boardClock = mock(Clock.class);
        NativeI2c.setI2c(board);
        NativeClock.setClock(boardClock);
        I2c explicit = mock(I2c.class);
        Clock explicitClock = mock(Clock.class);

        // When & Then
        assertThatThrownBy(() -> new Bmi088())
                .isInstanceOf(IllegalStateException.class).hasMessage("BMI088 did not acknowledge initialization");
        assertThatThrownBy(() -> new Bmi088(explicit, explicitClock))
                .isInstanceOf(IllegalStateException.class).hasMessage("BMI088 did not acknowledge initialization");

        assertThat(interactions(board)).isEqualTo(interactions(explicit));
        assertThat(interactions(boardClock)).isEqualTo(interactions(explicitClock));
    }

    private static List<String> interactions(Object mock) {
        return Mockito.mockingDetails(mock).getInvocations().stream().map(McuConvenienceBehaviorTest::describe).toList();
    }

    private static String describe(Invocation invocation) {
        return invocation.getMethod().getName() + Arrays.toString(invocation.getArguments());
    }
}
