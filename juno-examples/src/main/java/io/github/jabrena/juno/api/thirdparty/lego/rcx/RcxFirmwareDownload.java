package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.thirdparty.lego.rcxfirmware.RcxFirmwareImage;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Downloads Juno's firmware image to an RCX brick over infrared, as the LEGO IR tower does, printing its progress.
 * Wire the IR transmitter's DAT to pin 3 and the IR receiver's OUT to pin 2, point both at the switched-on RCX from
 * 10 to 50 cm, and keep them still for about ten minutes. A brick that already runs firmware is left alone, so
 * resetting the board afterwards (opening the serial monitor does) cannot delete it.
 *
 * <p>Only acknowledged bytes count as progress. Missing replies are retried with a quiet gap; if the boot ROM
 * loses its block sequence, the download starts again with smaller blocks. Five sessions bound recovery time.
 */
public final class RcxFirmwareDownload {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int RETRY_MILLIS = 1000;
    private static final int BLOCK_GAP_MILLIS = 100;
    private static final int RETRY_GAP_MILLIS = 300;
    private static final int START_MILLIS = 5000;

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxRemote remote = new RcxRemote(link);
        RcxFirmware firmware = new RcxFirmware(link);
        RcxBrick brick = new RcxBrick(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        remote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        byte[] block = new byte[RcxFirmware.BLOCK_SIZE];
        RcxDownloadProgress progress = new RcxDownloadProgress(RcxFirmwareImage.LENGTH);
        Serial.print("RCX firmware bytes: ");
        Serial.println(RcxFirmwareImage.LENGTH);
        int installed = firmware.installed();
        while (installed == RcxFirmware.NO_REPLY) {
            Serial.println("Waiting for the RCX: is it on, facing the IR modules?");
            Delay.millis(RETRY_MILLIS);
            installed = firmware.installed();
        }
        if (installed == 1) {
            stop("The RCX already runs firmware: nothing to do");
        }
        while (progress.restart()) {
            Serial.print("Download session ");
            Serial.print(progress.session());
            Serial.print("; block bytes ");
            Serial.println(progress.size());
            Delay.millis(RETRY_MILLIS);
            if (!firmware.delete()) {
                Serial.println("Delete: no acknowledgement");
                continue;
            }
            Delay.millis(BLOCK_GAP_MILLIS);
            if (!firmware.start(RcxFirmwareImage.ENTRY, RcxFirmwareImage.CHECKSUM)) {
                Serial.println("Start: no acknowledgement");
                continue;
            }
            if (!download(block, progress, firmware)) {
                Serial.println("Restarting transfer");
                continue;
            }
            Serial.println("Unlocking");
            Serial.println(firmware.unlock() ? "Unlock answered" : "Unlock: no answer");
            Delay.millis(RETRY_MILLIS);
            if (firmware.installed() == 1) {
                Serial.println(brick.playSound(RcxBrick.SOUND_FAST_SWEEP_UP)
                        ? "Firmware running; sound acknowledged" : "Firmware running; sound unconfirmed");
                stop("Done");
            }
            // An unlock reply can be lost even though the firmware starts. Do not erase an uncertain state.
            stop("Firmware start unconfirmed");
        }
        stop("Download stopped after 5 sessions");
    }

    private static boolean download(byte[] block, RcxDownloadProgress progress, RcxFirmware firmware) {
        while (!progress.complete()) {
            int size = progress.size();
            for (int i = 0; i < size; i++) {
                block[i] = RcxFirmwareImage.byteAt(progress.offset() + i);
            }
            Serial.print("Sending block ");
            Serial.print(progress.index());
            Serial.print(" at byte ");
            Serial.println(progress.offset());
            Delay.millis(BLOCK_GAP_MILLIS);
            int status = firmware.transfer(progress.index(), block, size);
            boolean resume = progress.accept(status);
            if (status == 0) {
                Serial.print("Confirmed bytes: ");
                Serial.println(progress.offset());
            } else {
                Serial.print("No progress; status ");
                Serial.println(status);
                Delay.millis(RETRY_GAP_MILLIS);
            }
            if (!resume) {
                return false;
            }
        }
        return true;
    }

    private static void stop(String message) {
        Serial.println(message);
        while (true) {
            Delay.millis(RETRY_MILLIS);
        }
    }
}
