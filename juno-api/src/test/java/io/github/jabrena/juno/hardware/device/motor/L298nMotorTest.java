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
import static org.mockito.Mockito.when;

@DisplayName("L298N motor")
class L298nMotorTest {
    private Gpio gpio;
    private L298nMotor motor;
    private int enablePin;
    private int in1Pin;
    private int in2Pin;

    @BeforeEach
    void wireTheMotorToMockedPins() {
        gpio = mock(Gpio.class);
        when(gpio.digitalPin("D3")).thenReturn(3);
        when(gpio.digitalPin("D4")).thenReturn(4);
        when(gpio.digitalPin("D5")).thenReturn(5);
        enablePin = gpio.digitalPin("D3");
        in1Pin = gpio.digitalPin("D4");
        in2Pin = gpio.digitalPin("D5");
        motor = new L298nMotor(gpio, enablePin, in1Pin, in2Pin);
    }

    @Test
    @DisplayName("Construction configures all pins and coasts")
    void shouldConfigurePinsAndCoastWhenBegun() {
        InOrder order = inOrder(gpio);
        order.verify(gpio).pinMode(enablePin, gpio.getOutput());
        order.verify(gpio).pinMode(in1Pin, gpio.getOutput());
        order.verify(gpio).pinMode(in2Pin, gpio.getOutput());
        order.verify(gpio).analogWrite(enablePin, 0);
        order.verify(gpio).digitalWrite(in1Pin, false);
        order.verify(gpio).digitalWrite(in2Pin, false);
    }

    @Test
    @DisplayName("Forward drives the bridge and scales the configured power to PWM")
    void shouldDriveForwardAtTheConfiguredPower() {
        motor.setPower(50);
        clearInvocations(gpio);

        motor.forward();

        verify(gpio).digitalWrite(in1Pin, true);
        verify(gpio).digitalWrite(in2Pin, false);
        verify(gpio).analogWrite(enablePin, 127);
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

        verify(gpio).digitalWrite(in1Pin, false);
        verify(gpio).analogWrite(enablePin, 255);
        assertThat(motor.isMoving()).isFalse();
    }

    @Test
    @DisplayName("Rejects repeated motor pins")
    void shouldRejectRepeatedPins() {
        assertThatThrownBy(() -> new L298nMotor(gpio, enablePin, enablePin, in2Pin))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
