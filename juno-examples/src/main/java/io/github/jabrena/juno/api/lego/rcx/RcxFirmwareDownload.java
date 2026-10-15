package io.github.jabrena.juno.api.lego.rcx;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.lego.rcxfirmware.RcxFirmwareImage;

/**
 * Downloads Juno's firmware image to an RCX brick over infrared, as the LEGO IR tower does, printing its progress.
 * Wire the IR transmitter's DAT to pin 3 and the IR receiver's OUT to pin 2, point both at the switched-on RCX from
 * 10 to 50 cm, and keep them still for about ten minutes. A brick that already runs firmware is left alone, so
 * resetting the board afterwards (opening the serial monitor does) cannot delete it.
 *
 * <p>The brick ignores a block that repeats one it already stored, so when a reply is lost the next block
 * goes out as if the brick had stored it; if that one is not answered either, the earlier block is sent
 * again. A wrong guess cannot slip through: the brick checks the whole image before it unlocks it.
 */
public final class RcxFirmwareDownload {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int RETRY_MILLIS = 1000;
    private static final int ATTEMPTS = 10;
    private static final int START_MILLIS = 2000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        RcxRemote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        byte[] block = new byte[RcxFirmware.BLOCK_SIZE];
        Serial.print("RCX firmware bytes: ");
        Serial.println(RcxFirmwareImage.LENGTH);
        int installed = RcxFirmware.installed();
        while (installed == RcxFirmware.NO_REPLY) {
            Serial.println("Waiting for the RCX: is it on, facing the IR modules?");
            Delay.millis(RETRY_MILLIS);
            installed = RcxFirmware.installed();
        }
        if (installed == 1) {
            stop("The RCX already runs firmware: nothing to do");
        }
        Serial.println("Deleting the old firmware");
        if (!RcxFirmware.delete()) {
            stop("Delete firmware: no answer");
        }
        Serial.println("Old firmware deleted");
        Serial.println("Starting the download");
        if (!RcxFirmware.start(RcxFirmwareImage.ENTRY, RcxFirmwareImage.CHECKSUM)) {
            stop("Start download: no answer");
        }
        int index = 1;
        int address = 0;
        boolean unconfirmed = false;
        int failures = 0;
        while (address < RcxFirmwareImage.LENGTH) {
            int size = Math.min(RcxFirmware.BLOCK_SIZE, RcxFirmwareImage.LENGTH - address);
            for (int i = 0; i < size; i++) {
                block[i] = RcxFirmwareImage.byteAt(address + i);
            }
            Serial.print("Sending block ");
            Serial.println(index);
            int status = RcxFirmware.transfer(index, block, size);
            if (status == 0) {
                Serial.print("Block ");
                Serial.print(index);
                Serial.print(" ok, bytes ");
                Serial.println(address + size);
                unconfirmed = false;
                failures = 0;
                address += size;
                index++;
            } else if (status == RcxFirmware.NO_REPLY && !unconfirmed) {
                Serial.print("Block ");
                Serial.print(index);
                Serial.println(" no reply: assuming stored");
                unconfirmed = true;
                address += size;
                index++;
            } else {
                if (unconfirmed) {
                    index--;
                    address -= RcxFirmware.BLOCK_SIZE;
                    unconfirmed = false;
                }
                failures++;
                Serial.print("  block ");
                Serial.print(index);
                Serial.print(" again, status ");
                Serial.println(status);
                if (failures >= ATTEMPTS) {
                    stop("Download stopped");
                }
            }
        }
        Serial.println("Unlocking");
        Serial.println(RcxFirmware.unlock() ? "Unlock answered" : "Unlock: no answer");
        Delay.millis(RETRY_MILLIS);
        Serial.println(RcxBrick.playSound(RcxBrick.SOUND_FAST_SWEEP_UP) ? "Firmware running" : "No answer");
        stop("Done");
    }

    private static void stop(String message) {
        Serial.println(message);
        while (true) {
            Delay.millis(RETRY_MILLIS);
        }
    }
}
