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
import io.github.jabrena.juno.remote.lego.rcx.RcxSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/** Configures and continuously reads touch sensors on RCX inputs 1, 2, and 3 through Nano Carrier IR. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxTouchSensors {
    private static final int CHANNEL = 1;
    private static final int START_MILLIS = 10000;
    private static final int RETRY_MILLIS = 1000;
    private static final int READ_PERIOD_MILLIS = 1000;

    private NanoCarrierRcxTouchSensors() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxSensor sensor1 = remote.getSensor1();
        RcxSensor sensor2 = remote.getSensor2();
        RcxSensor sensor3 = remote.getSensor3();
        RcxTouchSensor touch1 = new RcxTouchSensor(sensor1);
        RcxTouchSensor touch2 = new RcxTouchSensor(sensor2);
        RcxTouchSensor touch3 = new RcxTouchSensor(sensor3);

        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        waitForBrick(remote);

        Serial.println("RCX touch sensors: channel 1");
        printConfiguration(1, touch1.begin());
        printConfiguration(2, touch2.begin());
        printConfiguration(3, touch3.begin());

        while (true) {
            printReading(1, sensor1);
            printReading(2, sensor2);
            printReading(3, sensor3);
            Delay.millis(READ_PERIOD_MILLIS);
        }
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

    private static void printConfiguration(int input, boolean configured) {
        Serial.print("Input ");
        Serial.print(input);
        Serial.println(configured ? ": touch sensor configured" : ": configuration failed");
    }

    private static void printReading(int input, RcxSensor sensor) {
        int value = sensor.readValue();
        Serial.print("Input ");
        Serial.print(input);
        Serial.print(": ");
        if (value < 0) {
            Serial.println("no answer");
        } else if (value == 1) {
            Serial.println("pressed");
        } else {
            Serial.println("released");
        }
    }
}
