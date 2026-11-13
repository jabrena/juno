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
 * Downloads the RCX firmware through the Nano Connector Carrier's Grove emitter on A0 and receiver on A2, the sockets
 * {@link NanoCarrierRcxRegulatedDifferentialDrive} uses, so the I2C socket stays free for the BMI088.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxFirmwareDownload {
    private NanoCarrierRcxFirmwareDownload() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA2());
        RcxLink link = new RcxLink(emitter, receiver, MCU.getClock());
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxFirmwareDownload.download(remote);
    }
}
