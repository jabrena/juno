package io.github.jabrena.juno.hardware.device.usb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CP210x USB-to-UART bridge")
class Cp210xSerialTest {
    private FakeUsbBus bus;
    private UsbHostShield usb;
    private Cp210xSerial serial;

    @BeforeEach
    void startTheShield() {
        bus = new FakeUsbBus();
        usb = new UsbHostShield(bus.gpio, bus);
        usb.begin();
        serial = new Cp210xSerial(usb);
    }

    @Test
    @DisplayName("Opens the UART: enable, baud rate, 8N1, DTR/RTS low, purge")
    void shouldOpen() {
        assertThat(serial.begin(115_200)).isTrue();

        assertThat(serial.isOpen()).isTrue();
        assertThat(serial.lastError()).isZero();
        assertThat(bus.vendorRequests()).satisfiesExactly(
                request -> assertThat(request).containsExactly(0x00, 0x0001, 0),
                request -> assertThat(request).containsExactly(0x1E, 0, 0, 0x00, 0xC2, 0x01, 0x00),
                request -> assertThat(request).containsExactly(0x03, 0x0800, 0),
                request -> assertThat(request).containsExactly(0x07, 0x0300, 0),
                request -> assertThat(request).containsExactly(0x12, 0x000F, 0));
    }

    @Test
    @DisplayName("Does not open without a device")
    void shouldNotOpenWithoutADevice() {
        bus.connected = false;

        assertThat(serial.begin(115_200)).isFalse();
        assertThat(serial.lastError()).isEqualTo(UsbHostShield.ERROR_NOT_ATTACHED);
    }

    @Test
    @DisplayName("Does not open a device from another vendor")
    void shouldRejectAnotherVendor() {
        bus.vendorId = 0x0403;

        assertThat(serial.begin(115_200)).isFalse();
        assertThat(serial.isOpen()).isFalse();
    }

    @Test
    @DisplayName("Does not open when the bridge refuses a configuration request")
    void shouldFailWhenARequestStalls() {
        bus.stallRequest = 0x03;

        assertThat(serial.begin(115_200)).isFalse();
        assertThat(serial.lastError()).isEqualTo(UsbHostShield.ERROR_STALL);
    }

    @Test
    @DisplayName("Does not open when the device refuses its configuration")
    void shouldFailWhenSetConfigurationStalls() {
        bus.stallRequest = UsbHostShield.REQUEST_SET_CONFIGURATION;

        assertThat(serial.begin(115_200)).isFalse();
        assertThat(serial.lastError()).isEqualTo(UsbHostShield.ERROR_STALL);
    }

    @Test
    @DisplayName("Does not open when the configuration descriptor cannot be read")
    void shouldFailWithoutAConfiguration() {
        usb.attach();
        bus.stallRequest = UsbHostShield.REQUEST_GET_DESCRIPTOR;

        assertThat(serial.begin(115_200)).isFalse();
    }

    @Test
    @DisplayName("Writes bytes to the UART and reads what it received, byte by byte and in blocks")
    void shouldMoveBytes() {
        byte[] buffer = new byte[8];
        serial.begin(115_200);

        assertThat(serial.write(0x41)).isTrue();
        assertThat(serial.write(new byte[] {0x42, 0x43}, 0, 2)).isTrue();
        assertThat(bus.uartOut()).containsExactly(0x41, 0x42, 0x43);

        assertThat(serial.read()).isEqualTo(-1);
        bus.receive(1, 2, 3, 4, 5);
        assertThat(serial.available()).isEqualTo(5);
        assertThat(serial.read()).isEqualTo(1);
        assertThat(serial.read(buffer, 0, 2)).isEqualTo(2);
        assertThat(serial.read(buffer, 2, 8)).isEqualTo(2);
        assertThat(buffer).startsWith(2, 3, 4, 5);
        assertThat(serial.read(buffer, 0, 8)).isZero();
    }

    @Test
    @DisplayName("Drives DTR and RTS, changes the baud rate and purges")
    void shouldControlTheLines() {
        serial.begin(115_200);
        bus.vendorRequests().clear();

        assertThat(serial.setDtr(true)).isTrue();
        assertThat(serial.setRts(false)).isTrue();
        assertThat(serial.setBaudRate(9600)).isTrue();
        bus.receive(9);
        serial.flush();

        assertThat(bus.vendorRequests()).satisfiesExactly(
                request -> assertThat(request).containsExactly(0x07, 0x0101, 0),
                request -> assertThat(request).containsExactly(0x07, 0x0200, 0),
                request -> assertThat(request).containsExactly(0x1E, 0, 0, 0x80, 0x25, 0, 0),
                request -> assertThat(request).containsExactly(0x12, 0x000F, 0));
        assertThat(serial.read()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Neither reads nor writes before it is open")
    void shouldStayClosed() {
        assertThat(serial.read()).isEqualTo(-1);
        assertThat(serial.available()).isEqualTo(UsbHostShield.ERROR_NOT_ATTACHED);
        assertThat(serial.write(1)).isFalse();
    }

    @Test
    @DisplayName("Closes on a failed write and records the error")
    void shouldCloseOnAFailedWrite() {
        serial.begin(115_200);
        bus.bulkOutResult = 5;

        assertThat(serial.write(1)).isFalse();
        assertThat(serial.lastError()).isEqualTo(UsbHostShield.ERROR_STALL);
        assertThat(serial.isOpen()).isFalse();
    }

    @Test
    @DisplayName("Closes when the device stops answering a read, and opens again with begin")
    void shouldCloseOnAFailedRead() {
        serial.begin(115_200);
        bus.deadController = true;

        assertThat(serial.read()).isEqualTo(-1);
        assertThat(serial.lastError()).isEqualTo(UsbHostShield.ERROR_NO_RESPONSE);
        assertThat(serial.isOpen()).isFalse();

        bus.deadController = false;
        assertThat(serial.begin(115_200)).isTrue();
        assertThat(serial.isOpen()).isTrue();
    }
}
