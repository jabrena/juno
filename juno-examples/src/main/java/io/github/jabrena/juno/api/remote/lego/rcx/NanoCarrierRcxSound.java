package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;

/**
 * Plays RcxSound's five-note sequence once on RCX channel 1 through the Nano ESP32.
 * The Nano Connector Carrier holds the Grove emitter on SCL/A5 and receiver on A0.
 * After a ten-second startup delay, Serial reports each tone's acknowledgement at 115200 baud.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxSound {
    private static final int CHANNEL = 1;

    private NanoCarrierRcxSound() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getI2cScl());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA0());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxSound.play(remote);
    }
}
