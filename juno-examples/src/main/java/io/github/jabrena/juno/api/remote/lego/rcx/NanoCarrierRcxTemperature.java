package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxTemperatureSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/** Reads an RCX temperature sensor on input 1 over the Nano Connector Carrier's Grove infrared pair. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxTemperature {
    private static final RcxChannel CHANNEL = RcxChannel.CHANNEL_1;
    private static final int START_MILLIS = 10000;
    private static final int PERIOD_MILLIS = 1000;
    private static final int RETRY_MILLIS = 1000;

    private NanoCarrierRcxTemperature() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(emitter, receiver, MCU.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL);
        RcxTemperatureSensor temperature = new RcxTemperatureSensor(remote.getSensor1());

        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        Serial.println("RCX temperature sensor: input 1, channel 1");

        while (true) {
            printTemperature(temperature.temperatureTenths());
            Delay.millis(PERIOD_MILLIS);
        }
    }

    private static void printTemperature(int tenths) {
        Serial.print("temperature ");
        if (tenths < 0) {
            Serial.print("-");
            tenths = -tenths;
        }
        Serial.print(tenths / 10);
        Serial.print(".");
        Serial.print(tenths % 10);
        Serial.println(" C");
    }
}
