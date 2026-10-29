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

/** Tests lamps connected to all three RCX motor outputs through the Nano Connector Carrier. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxLamps {
    private static final int CHANNEL = 1;
    private static final int START_MILLIS = 10000;
    private static final int LAMP_MILLIS = 2000;
    private static final int BETWEEN_LAMPS_MILLIS = 500;
    private static final int RETRY_MILLIS = 1000;

    private NanoCarrierRcxLamps() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxMotor lampA = remote.getMotorA();
        RcxMotor lampB = remote.getMotorB();
        RcxMotor lampC = remote.getMotorC();
        RcxMotor allLamps = lampA.and(lampB).and(lampC);

        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        waitForBrick(remote);

        Serial.println("RCX lamps: channel 1");
        testLamp("A", lampA);
        testLamp("B", lampB);
        testLamp("C", lampC);

        Serial.println("A+B+C on");
        allLamps.setPower(RcxMotor.MAX_POWER);
        allLamps.forward();
        Delay.millis(LAMP_MILLIS);
        allLamps.stop();
        Serial.println("A+B+C off");
        Serial.println("RCX lamp test finished");
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

    private static void testLamp(String name, RcxMotor lamp) {
        Serial.print("Lamp ");
        Serial.print(name);
        Serial.println(" on");
        lamp.setPower(RcxMotor.MAX_POWER);
        lamp.forward();
        Delay.millis(LAMP_MILLIS);
        lamp.stop();
        Serial.print("Lamp ");
        Serial.print(name);
        Serial.println(" off");
        Delay.millis(BETWEEN_LAMPS_MILLIS);
    }
}
