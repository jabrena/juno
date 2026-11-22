package io.github.jabrena.juno.hardware.device.ti;

import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TI-84 Plus CE link over the USB Host Shield (DUSB)")
class TiLinkTest {
    private final int[] now = {0};
    private FakeCalculator calculator;
    private TiLink link;

    @BeforeEach
    void plugTheCalculatorIn() {
        calculator = new FakeCalculator();
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(invocation -> now[0] += 10);
        link = new TiLink(calculator.usb, clock);
    }

    @Test
    @DisplayName("Connects: buffer size handshake, then normal mode")
    void shouldConnect() {
        assertThat(link.connect()).isTrue();

        assertThat(link.isConnected()).isTrue();
        assertThat(link.bufferSize()).isEqualTo(1023);
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_NONE);
        assertThat(calculator.virtualTypesReceived()).containsExactly(TiLink.VPKT_PING);
        assertThat(calculator.virtualPacket(0)).containsExactly(0, 3, 0, 1, 0, 0, 0, 0, 0x07, 0xD0);
    }

    @Test
    @DisplayName("Sends L1 with the CE's UTF-8 name, its type header and the reals")
    void shouldSendAList() {
        link.connect();

        assertThat(link.sendList(1, new int[] {23, 24, -5, 0}, 3, 0)).isTrue();

        assertThat(calculator.virtualTypesReceived()).containsExactly(TiLink.VPKT_PING, TiLink.VPKT_RTS,
                TiLink.VPKT_VAR_CONTENTS, TiLink.VPKT_EOT);
        assertThat(unsigned(calculator.lastVirtualPacket(TiLink.VPKT_RTS))).containsExactly(
                0, 4, 0x4C, 0xE2, 0x82, 0x81, 0, 0, 0, 0, 29, 1, 0, 3,
                0, 2, 0, 4, 0xF0, 0x0F, 0, 1,
                0, 3, 0, 1, 0,
                0, 8, 0, 4, 0, 0, 0, 0);
        assertThat(unsigned(calculator.lastVirtualPacket(TiLink.VPKT_VAR_CONTENTS))).containsExactly(
                3, 0,
                0x00, 0x81, 0x23, 0, 0, 0, 0, 0, 0,
                0x00, 0x81, 0x24, 0, 0, 0, 0, 0, 0,
                0x80, 0x80, 0x50, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("Names L2 to L6 with the matching subscript")
    void shouldNameEveryList() {
        link.connect();

        assertThat(link.sendList(6, new int[] {1}, 1, 0)).isTrue();

        assertThat(Arrays.copyOfRange(unsigned(calculator.lastVirtualPacket(TiLink.VPKT_RTS)), 0, 7))
                .containsExactly(0, 4, 0x4C, 0xE2, 0x82, 0x86, 0);
    }

    @Test
    @DisplayName("Sends a fourteen-digit time stamp exactly")
    void shouldSendLongValues() {
        link.connect();

        assertThat(link.sendList(1, new long[] {20261121170016L}, 1, 0)).isTrue();

        assertThat(unsigned(calculator.lastVirtualPacket(TiLink.VPKT_VAR_CONTENTS))).containsExactly(
                1, 0, 0x00, 0x8D, 0x20, 0x26, 0x11, 0x21, 0x17, 0x00, 0x16);
    }

    @Test
    @DisplayName("Splits a virtual packet over several raw chunks when the calculator grants a small buffer")
    void shouldSplitIntoChunks() {
        calculator.grantedBuffer = 16;
        link.connect();
        int[] values = new int[TiLink.MAX_LIST_LENGTH];
        Arrays.fill(values, 7);

        assertThat(link.sendList(2, values, values.length, 0)).isTrue();

        byte[] contents = calculator.lastVirtualPacket(TiLink.VPKT_VAR_CONTENTS);
        assertThat(contents).hasSize(2 + TiLink.MAX_LIST_LENGTH * 9);
        assertThat(unsigned(Arrays.copyOfRange(contents, contents.length - 9, contents.length)))
                .containsExactly(0x00, 0x80, 0x70, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("Waits through delay acknowledgements")
    void shouldWaitThroughDelays() {
        calculator.delays = 2;
        link.connect();

        assertThat(link.sendList(1, new int[] {1}, 1, 0)).isTrue();
    }

    @Test
    @DisplayName("Reports the calculator's error code when it refuses the variable")
    void shouldReportRejections() {
        calculator.rejectWith = 0x000E;
        link.connect();

        assertThat(link.sendList(1, new int[] {1}, 1, 0)).isFalse();

        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_REJECTED);
        assertThat(link.lastCalculatorError()).isEqualTo(0x000E);
        assertThat(link.lastPacketType()).isEqualTo(TiLink.VPKT_ERROR);
        assertThat(link.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Rejects bad arguments without touching the link")
    void shouldRejectArguments() {
        link.connect();
        int[] values = {1, 2};

        assertThat(link.sendList(0, values, 1, 0)).isFalse();
        assertThat(link.sendList(7, values, 1, 0)).isFalse();
        assertThat(link.sendList(1, values, 3, 0)).isFalse();
        assertThat(link.sendList(1, values, -1, 0)).isFalse();
        assertThat(link.sendList(1, values, 1, -1)).isFalse();
        assertThat(link.sendList(1, new int[TiLink.MAX_LIST_LENGTH + 1], TiLink.MAX_LIST_LENGTH + 1, 0)).isFalse();

        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_ARGUMENT);
        assertThat(link.isConnected()).isTrue();
    }

    @Test
    @DisplayName("Does not send before connecting")
    void shouldRequireAConnection() {
        assertThat(link.sendList(1, new int[] {1}, 1, 0)).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_NO_DEVICE);
    }

    @Test
    @DisplayName("Fails to connect without the configuration, the right IDs, the device or the shield")
    void shouldFailToConnect() {
        calculator.bus.stallRequest = 9;
        assertThat(link.connect()).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_USB);

        calculator.bus.stallRequest = -1;
        calculator.bus.productId = 0xE003;
        assertThat(link.connect()).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_NOT_A_TI84);

        calculator.bus.productId = TiLink.PRODUCT_ID_TI84_PLUS;
        calculator.bus.vendorId = 0x08F7;
        assertThat(link.connect()).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_NOT_A_TI84);

        calculator.bus.connected = false;
        assertThat(link.connect()).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_NO_DEVICE);

        calculator.bus.oscillatorOk = false;
        assertThat(link.connect()).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_NO_SHIELD);
    }

    @Test
    @DisplayName("Fails with a protocol error on an unexpected answer")
    void shouldReportProtocolErrors() {
        calculator.wrongBufferAnswer = true;
        assertThat(link.connect()).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_PROTOCOL);
        assertThat(link.lastPacketType()).isEqualTo(DusbTransport.RAW_DATA_ACK);

        calculator = new FakeCalculator();
        calculator.wrongModeAck = true;
        link = new TiLink(calculator.usb, clockThatAdvances());
        assertThat(link.connect()).isFalse();
        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_PROTOCOL);
        assertThat(link.lastPacketType()).isEqualTo(TiLink.VPKT_DATA_ACK);
    }

    @Test
    @DisplayName("Times out when the calculator stops answering")
    void shouldTimeOut() {
        calculator.silent = true;

        assertThat(link.connect()).isFalse();

        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_TIMEOUT);
    }

    @Test
    @DisplayName("Reports a USB error when a bulk transfer fails")
    void shouldReportUsbErrors() {
        calculator.bus.bulkOutResult = 5;

        assertThat(link.connect()).isFalse();

        assertThat(link.lastError()).isEqualTo(TiLink.ERROR_USB);
    }

    private Clock clockThatAdvances() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(invocation -> now[0] += 10);
        return clock;
    }

    private static int[] unsigned(byte[] bytes) {
        int[] values = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            values[i] = bytes[i] & 0xFF;
        }
        return values;
    }
}
