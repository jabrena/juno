package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;

/** Downloads the RCX firmware through the Nano Connector Carrier's Grove emitter on SCL/A5 and receiver on A0. */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxFirmwareDownload {
    private NanoCarrierRcxFirmwareDownload() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getI2cScl());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA0());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, receiver, emitter, ArduinoNano.getClock());
        RcxFirmwareDownload.download(remote);
    }
}
