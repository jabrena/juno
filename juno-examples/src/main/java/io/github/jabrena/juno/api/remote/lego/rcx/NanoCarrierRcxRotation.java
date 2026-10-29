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
import io.github.jabrena.juno.remote.lego.rcx.RcxRotationSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/** Tests RCX rotation steps and degrees with a motor on output A and a rotation sensor on input 1. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxRotation {
    private static final int CHANNEL = 1;
    private static final int START_MILLIS = 10000;
    private static final int MOTOR_POWER = 3;
    private static final int MOTOR_RUN_MILLIS = 4000;
    private static final int POLL_MILLIS = 250;
    private static final int RETRY_MILLIS = 1000;

    private NanoCarrierRcxRotation() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxMotor motor = remote.getMotorA();
        RcxRotationSensor rotation = new RcxRotationSensor(remote.getSensor1());

        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        waitForBrick(remote);
        while (!rotation.begin()) {
            Serial.println("Rotation sensor configuration failed; retrying");
            Delay.millis(RETRY_MILLIS);
        }

        Serial.println("RCX rotation test: motor A, sensor 1, channel 1");
        rotation.reset();
        printRotation("after reset", rotation);

        runMotor("forward", motor, rotation, true);
        rotation.reset();
        printRotation("after second reset", rotation);
        runMotor("backward", motor, rotation, false);

        motor.stop();
        Serial.println("Turn the axle manually to observe more readings");
        monitorRotation(rotation);
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

    private static void runMotor(String direction, RcxMotor motor, RcxRotationSensor rotation, boolean forward) {
        Serial.print("Motor ");
        Serial.print(direction);
        Serial.println(" at power 3");
        motor.setPower(MOTOR_POWER);
        if (forward) {
            motor.forward();
        } else {
            motor.backward();
        }
        Delay.millis(MOTOR_RUN_MILLIS);
        motor.stop();
        printRotation(direction + " complete", rotation);
    }

    private static void monitorRotation(RcxRotationSensor rotation) {
        int lastSteps = rotation.steps();
        while (true) {
            int steps = rotation.steps();
            if (steps != lastSteps) {
                printRotation("changed", rotation);
                lastSteps = steps;
            }
            Delay.millis(POLL_MILLIS);
        }
    }

    private static void printRotation(String label, RcxRotationSensor rotation) {
        Serial.print(label);
        Serial.print(": steps ");
        Serial.print(rotation.steps());
        Serial.print(", degrees ");
        Serial.println(rotation.degrees());
    }
}
