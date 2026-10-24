package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLightSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxMotor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Drives an RCX brick's motors on outputs A and B with the brick's direct motor commands, which, unlike the
 * remote's buttons, have seven power levels: it ramps both motors up through the forward levels 1 to 7 and
 * back down, then does the same backwards, forever, printing the light sensor on input 2 (as a percentage)
 * and the battery voltage as it goes. Wire an IR LED with a series resistor to pin 3 and an IR receiver
 * module to pin 2 (the brick acknowledges each command), both pointed at the RCX. Each change of level
 * is several commands, so a step takes a few hundred milliseconds by itself. Pick the board with
 * {@code -Djuno.board}.
 */
@SuppressWarnings("java:S2189")
public class RcxDirectDrive {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int STEP_MILLIS = 800;
    private static final int PAUSE_SECONDS = 2;

    private final RcxMotor motors;
    private final RcxLightSensor light;

    RcxDirectDrive(RcxMotor motors, RcxLightSensor light) {
        this.motors = motors;
        this.light = light;
    }

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        RcxDirectDrive app = new RcxDirectDrive(remote.getMotorA().and(remote.getMotorB()), new RcxLightSensor(remote.getSensor2()));
        Serial.println("RCX battery: " + remote.getBattery().millivolts() + " mV");
        if (!app.light.begin()) {
            Serial.println("The RCX did not answer: is it in range, with the IR receiver wired?");
            return;
        }

        while (true) {
            app.ramp(1);
            app.ramp(-1);
            Delay.seconds(PAUSE_SECONDS);
        }
    }

    /** Ramps the speed up to full in the given direction (1 forwards, -1 backwards), then back to a stop. */
    private void ramp(int direction) {
        for (int level = 1; level <= RcxMotor.MAX_POWER; level++) {
            step(direction * level);
        }
        for (int level = RcxMotor.MAX_POWER - 1; level >= 1; level--) {
            step(direction * level);
        }
        motors.drive(0);
    }

    private void step(int speed) {
        motors.drive(speed);
        Serial.println("Speed " + speed + ", light " + light.lightPercent() + " %");
        Delay.millis(STEP_MILLIS);
    }
}
