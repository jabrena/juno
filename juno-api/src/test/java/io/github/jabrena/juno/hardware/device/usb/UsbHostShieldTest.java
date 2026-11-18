package io.github.jabrena.juno.hardware.device.usb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("USB Host Shield (MAX3421E)")
class UsbHostShieldTest {
    private FakeUsbBus bus;
    private UsbHostShield usb;

    @BeforeEach
    void plugACp2102IntoTheShield() {
        bus = new FakeUsbBus();
        usb = new UsbHostShield(bus.gpio, bus);
    }

    @Test
    @DisplayName("Starts the controller in host mode and reports its revision")
    void shouldBegin() {
        assertThat(usb.begin()).isTrue();
        assertThat(usb.revision()).isEqualTo(0x13);
        assertThat(usb.isAttached()).isFalse();
    }

    @Test
    @DisplayName("Fails to begin when the oscillator never settles")
    void shouldRejectAStoppedOscillator() {
        bus.oscillatorOk = false;

        assertThat(usb.begin()).isFalse();
    }

    @Test
    @DisplayName("Fails to begin when the chip does not identify as a MAX3421E")
    void shouldRejectAnUnknownRevision() {
        bus.revision = 0xFF;

        assertThat(usb.begin()).isFalse();
    }

    @Test
    @DisplayName("Enumerates a full-speed device: address, descriptors and IDs")
    void shouldAttach() {
        usb.begin();

        assertThat(usb.isDeviceConnected()).isTrue();
        assertThat(usb.attach()).isTrue();
        assertThat(usb.isAttached()).isTrue();
        assertThat(usb.isLowSpeed()).isFalse();
        assertThat(usb.vendorId()).isEqualTo(0x10C4);
        assertThat(usb.productId()).isEqualTo(0xEA60);
        assertThat(usb.maxPacketSize0()).isEqualTo(64);
        assertThat(usb.isDeviceConnected()).isTrue();
    }

    @Test
    @DisplayName("Does not attach when no device holds the bus")
    void shouldNotAttachWithoutADevice() {
        bus.connected = false;
        usb.begin();

        assertThat(usb.isDeviceConnected()).isFalse();
        assertThat(usb.attach()).isFalse();
    }

    @Test
    @DisplayName("Does not attach when the controller never completes a transfer")
    void shouldNotAttachWithADeadController() {
        usb.begin();
        bus.deadController = true;

        assertThat(usb.attach()).isFalse();
    }

    @Test
    @DisplayName("Does not attach when the device stalls SET_ADDRESS")
    void shouldNotAttachWhenAddressingStalls() {
        usb.begin();
        bus.stallRequest = UsbHostShield.REQUEST_SET_ADDRESS;

        assertThat(usb.attach()).isFalse();
    }

    @Test
    @DisplayName("Refuses every transfer before a device is attached")
    void shouldRefuseTransfersBeforeAttach() {
        byte[] buffer = new byte[64];
        usb.begin();

        assertThat(usb.controlIn(0x80, 6, 0x100, 0, buffer, 8)).isEqualTo(UsbHostShield.ERROR_NOT_ATTACHED);
        assertThat(usb.controlOut(0, 9, 1, 0, buffer, 0)).isEqualTo(UsbHostShield.ERROR_NOT_ATTACHED);
        assertThat(usb.bulkIn(1, buffer, 0, 64)).isEqualTo(UsbHostShield.ERROR_NOT_ATTACHED);
        assertThat(usb.bulkOut(1, buffer, 0, 1, 64)).isEqualTo(UsbHostShield.ERROR_NOT_ATTACHED);
    }

    @Test
    @DisplayName("Reads the configuration descriptor and selects a configuration")
    void shouldConfigure() {
        byte[] configuration = new byte[64];
        usb.begin();
        usb.attach();

        assertThat(usb.configurationDescriptor(configuration, 64)).isEqualTo(32);
        assertThat(configuration[1]).isEqualTo((byte) 2);
        assertThat(usb.setConfiguration(1)).isZero();
    }

    @Test
    @DisplayName("Reports a stalled control request")
    void shouldReportAStall() {
        byte[] data = new byte[4];
        usb.begin();
        usb.attach();
        bus.stallRequest = 0x1E;

        assertThat(usb.controlOut(0x41, 0x1E, 0, 0, data, 4)).isEqualTo(UsbHostShield.ERROR_STALL);
    }

    @Test
    @DisplayName("Retries a control transfer the device NAKs, then succeeds")
    void shouldRetryNaks() {
        byte[] buffer = new byte[18];
        usb.begin();
        usb.attach();
        bus.script(4, 4, 4);

        assertThat(usb.controlIn(0x80, 6, 0x100, 0, buffer, 18)).isEqualTo(18);
    }

    @Test
    @DisplayName("Gives up after three time-outs in a row")
    void shouldGiveUpAfterTimeouts() {
        byte[] buffer = new byte[18];
        usb.begin();
        usb.attach();
        bus.script(14, 14, 14);

        assertThat(usb.controlIn(0x80, 6, 0x100, 0, buffer, 18)).isEqualTo(UsbHostShield.ERROR_TIMEOUT);
    }

    @Test
    @DisplayName("Returns an unexpected transaction result as its negative code")
    void shouldReportOtherErrors() {
        byte[] buffer = new byte[18];
        usb.begin();
        usb.attach();
        bus.script(0x0B);

        assertThat(usb.controlIn(0x80, 6, 0x100, 0, buffer, 18)).isEqualTo(-0x0B);
    }

    @Test
    @DisplayName("Reports a bulk IN the device NAKs as no data")
    void shouldReturnZeroWhenNothingArrived() {
        byte[] buffer = new byte[64];
        usb.begin();
        usb.attach();
        usb.setConfiguration(1);

        assertThat(usb.bulkIn(1, buffer, 0, 64)).isZero();
    }

    @Test
    @DisplayName("Resynchronises a bulk IN data toggle error")
    void shouldRecoverAnInToggleError() {
        byte[] buffer = new byte[64];
        usb.begin();
        usb.attach();
        usb.setConfiguration(1);
        bus.receive(1, 2, 3);
        bus.script(6);

        assertThat(usb.bulkIn(1, buffer, 0, 64)).isEqualTo(3);
        assertThat(buffer[2]).isEqualTo((byte) 3);
    }

    @Test
    @DisplayName("Reports a bulk IN without data in the receive FIFO")
    void shouldReportAMissingPacket() {
        byte[] buffer = new byte[64];
        usb.begin();
        usb.attach();
        usb.setConfiguration(1);
        bus.script(0);

        assertThat(usb.bulkIn(1, buffer, 0, 64)).isEqualTo(UsbHostShield.ERROR_NO_DATA);
    }

    @Test
    @DisplayName("Sends bulk OUT data, re-arming the packet after a NAK and a toggle error")
    void shouldRetryBulkOut() {
        byte[] data = {(byte) 0xA5, 0x25};
        usb.begin();
        usb.attach();
        usb.setConfiguration(1);
        bus.script(4, 6, 6);

        assertThat(usb.bulkOut(1, data, 0, 2, 64)).isEqualTo(2);
        assertThat(bus.uartOut()).containsExactly(0xA5, 0x25);
    }

    @Test
    @DisplayName("Gives up a bulk OUT after repeated time-outs or a stall")
    void shouldFailBulkOut() {
        byte[] data = {1};
        usb.begin();
        usb.attach();
        usb.setConfiguration(1);
        bus.script(14, 14, 14);

        assertThat(usb.bulkOut(1, data, 0, 1, 64)).isEqualTo(UsbHostShield.ERROR_TIMEOUT);

        bus.bulkOutResult = 5;
        assertThat(usb.bulkOut(1, data, 0, 1, 64)).isEqualTo(UsbHostShield.ERROR_STALL);
    }

    @Test
    @DisplayName("Splits a long bulk OUT into packets of the endpoint's size")
    void shouldSplitLongWrites() {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        usb.begin();
        usb.attach();
        usb.setConfiguration(1);

        assertThat(usb.bulkOut(1, data, 0, 100, 64)).isEqualTo(100);
        assertThat(bus.uartOut()).hasSize(100).startsWith(0, 1, 2).endsWith(98, 99);
    }
}
