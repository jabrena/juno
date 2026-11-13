package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;

/**
 * Plays RcxSound's five-note sequence once on RCX channel 1 through the Nano ESP32.
 * The Nano Connector Carrier holds the Grove emitter on SCL/A5 and receiver on A0.
 * After a ten-second startup delay, Serial reports each tone's acknowledgement at 115200 baud.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxSound {
    private static final RcxChannel CHANNEL = RcxChannel.CHANNEL_1;

    private NanoCarrierRcxSound() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(emitter, receiver, MCU.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL);
        RcxSound.play(remote);
    }
}
