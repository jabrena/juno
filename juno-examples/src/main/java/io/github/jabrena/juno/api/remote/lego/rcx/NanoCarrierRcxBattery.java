package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxBattery;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/** Reads the battery voltage from the RCX selected as channel 1 through the Nano Connector Carrier's Grove IR pair. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxBattery {
    private static final int START_MILLIS = 2000;
    private static final int PERIOD_MILLIS = 2000;

    private NanoCarrierRcxBattery() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        Serial.println("RCX battery probe: TX A0, RX A5, channel 1");

        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, 1, receiver, emitter, ArduinoNano.getClock());

        while (true) {
            readBattery(remote);
            Delay.millis(PERIOD_MILLIS);
        }
    }

    private static void readBattery(RemoteRcx remote) {
        remote.begin();
        RcxBattery battery = remote.getBattery();
        int millivolts = battery.millivolts();
        if (millivolts < 0) {
            Serial.println("no answer");
        } else {
            Serial.print(millivolts);
            Serial.println(" mV");
        }
    }
}
