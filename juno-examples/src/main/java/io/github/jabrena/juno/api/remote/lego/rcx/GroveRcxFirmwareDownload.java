package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;

/**
 * Downloads the RCX firmware from an UNO R4 WiFi through a Grove Base Shield: the Grove infrared emitter in the D3
 * socket and the Grove infrared receiver in the D4 socket. Point both modules at the switched-on RCX from 10 to 50 cm
 * and keep them still for about ten minutes; see {@link RcxFirmwareDownload} for the download and recovery behavior.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class GroveRcxFirmwareDownload {
    private GroveRcxFirmwareDownload() {
    }

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(shield.getD4());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(shield.getD3());
        RcxLink link = new RcxLink(emitter, receiver, ArduinoUno.getClock());
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.BROADCAST);
        RcxFirmwareDownload.download(remote);
    }
}
