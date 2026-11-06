package io.github.jabrena.juno.hardware.device.motor;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("L298N motor")
class L298nMotorTest {
    private Gpio gpio;
    private L298nMotor motor;

    @BeforeEach
    void wireTheMotorToMockedPins() {
        gpio = mock(Gpio.class);
        motor = new L298nMotor(gpio, 3, 4, 5);
    }

    @Test
    @DisplayName("Begin configures all pins and coasts")
    void shouldConfigurePinsAndCoastWhenBegun() {
        motor.begin();

        InOrder order = inOrder(gpio);
        order.verify(gpio).pinMode(3, Gpio.getOutput());
        order.verify(gpio).pinMode(4, Gpio.getOutput());
        order.verify(gpio).pinMode(5, Gpio.getOutput());
        order.verify(gpio).analogWrite(3, 0);
        order.verify(gpio).digitalWrite(4, false);
        order.verify(gpio).digitalWrite(5, false);
    }

    @Test
    @DisplayName("Forward drives the bridge and scales the configured power to PWM")
    void shouldDriveForwardAtTheConfiguredPower() {
        motor.setPower(50);
        clearInvocations(gpio);

        motor.forward();

        verify(gpio).digitalWrite(4, true);
        verify(gpio).digitalWrite(5, false);
        verify(gpio).analogWrite(3, 127);
        assertThat(motor.isMoving()).isTrue();
    }

    @Test
    @DisplayName("Power is clamped and zero power is not considered movement")
    void shouldClampPower() {
        motor.setPower(101);
        motor.forward();
        assertThat(motor.getPower()).isEqualTo(100);

        motor.setPower(-1);

        assertThat(motor.getPower()).isZero();
        assertThat(motor.isMoving()).isFalse();
    }

    @Test
    @DisplayName("Backward reverses the bridge and stop brakes it")
    void shouldReverseAndBrake() {
        motor.setPower(25);

        motor.backward();
        clearInvocations(gpio);
        motor.stop();

        verify(gpio).digitalWrite(4, false);
        verify(gpio).analogWrite(3, 255);
        assertThat(motor.isMoving()).isFalse();
    }

    @Test
    @DisplayName("Rejects repeated motor pins")
    void shouldRejectRepeatedPins() {
        assertThatThrownBy(() -> new L298nMotor(gpio, 3, 3, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
