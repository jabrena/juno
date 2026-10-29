package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxMotor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/** Tests speed, direction, braking, and coasting for a motor connected to RCX output A. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxMotorA {
    private static final int CHANNEL = 1;
    private static final int START_MILLIS = 10000;
    private static final int POWER_STEP_MILLIS = 2000;
    private static final int DIRECTION_MILLIS = 3000;
    private static final int STOPPING_MILLIS = 2000;
    private static final int BETWEEN_TESTS_MILLIS = 1000;
    private static final int RETRY_MILLIS = 1000;

    private NanoCarrierRcxMotorA() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxMotor motor = remote.getMotorA();

        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        waitForBrick(remote);

        Serial.println("RCX motor A test: channel 1");
        testPower(motor, 2);
        testPower(motor, 4);
        testPower(motor, RcxMotor.MAX_POWER);
        testDirection(motor, true);
        testDirection(motor, false);
        testStop(motor);
        testCoast(motor);
        motor.stop();
        Serial.println("RCX motor A test finished");
    }

    private static void waitForBrick(RemoteRcx remote) {
        int millivolts = remote.getBattery().millivolts();
        while (millivolts < 0) {
            Serial.println("No RCX response; check channel 1 and IR alignment");
            Delay.millis(RETRY_MILLIS);
            millivolts = remote.getBattery().millivolts();
        }
        Serial.print("RCX battery: ");
        Serial.print(millivolts);
        Serial.println(" mV");
    }

    private static void testPower(RcxMotor motor, int power) {
        Serial.print("Forward power ");
        Serial.println(power);
        motor.setPower(power);
        motor.forward();
        Delay.millis(POWER_STEP_MILLIS);
        motor.stop();
        Delay.millis(BETWEEN_TESTS_MILLIS);
    }

    private static void testDirection(RcxMotor motor, boolean forward) {
        Serial.println(forward ? "Forward direction" : "Backward direction");
        motor.setPower(4);
        if (forward) {
            motor.forward();
        } else {
            motor.backward();
        }
        Delay.millis(DIRECTION_MILLIS);
        motor.stop();
        Delay.millis(BETWEEN_TESTS_MILLIS);
    }

    private static void testStop(RcxMotor motor) {
        Serial.println("Brake with stop");
        motor.setPower(4);
        motor.forward();
        Delay.millis(STOPPING_MILLIS);
        motor.stop();
        Delay.millis(BETWEEN_TESTS_MILLIS);
    }

    private static void testCoast(RcxMotor motor) {
        Serial.println("Coast with flt");
        motor.setPower(4);
        motor.forward();
        Delay.millis(STOPPING_MILLIS);
        motor.flt();
        Delay.millis(BETWEEN_TESTS_MILLIS);
    }
}
