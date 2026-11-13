package demo;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.BluetoothLe;

/** Raspberry Pi Pico 2 W Bluetooth LE central API. */
@Board(RaspberryPico2WifiBoard.class)
public final class PicoBleApi {
    public static void main(String[] args) {
        BluetoothLe bluetooth = MCU.getBluetoothLe();
        byte[] advertisement = new byte[16];
        byte[] message = new byte[16];
        bluetooth.connect("00001623-1212-efde-1623-785feabcd123", "00001624-1212-efde-1623-785feabcd123",
                1000, advertisement, advertisement.length);
        bluetooth.write(message, message.length);
        bluetooth.read(message, message.length);
        bluetooth.isConnected();
        bluetooth.disconnect();
    }
}
