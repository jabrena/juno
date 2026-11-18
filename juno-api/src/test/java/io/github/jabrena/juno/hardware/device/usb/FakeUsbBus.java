package io.github.jabrena.juno.hardware.device.usb;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A MAX3421E on a fake SPI bus with one full-speed CP2102 plugged in, enough of both for the drivers' tests. The chip
 * select is a mocked {@link Gpio}, whose writes frame each SPI command. Bytes the device "transmits on its UART" are
 * recorded in {@link #uartOut()}; tests queue what the UART "receives" with {@link #receive(int...)}.
 */
public final class FakeUsbBus implements Spi {
    static final int CHIP_SELECT = 10;

    private static final int RCVFIFO = 1;
    private static final int SNDFIFO = 2;
    private static final int SUDFIFO = 4;
    private static final int RCVBC = 6;
    private static final int SNDBC = 7;
    private static final int USBIRQ = 13;
    private static final int REVISION = 18;
    private static final int HIRQ = 25;
    private static final int MODE = 27;
    private static final int PERADDR = 28;
    private static final int HCTL = 29;
    private static final int HXFR = 30;
    private static final int HRSL = 31;

    private static final byte[] DEVICE_DESCRIPTOR = {
        18, 1, 0x10, 0x01, 0, 0, 0, 64, (byte) 0xC4, 0x10, 0x60, (byte) 0xEA, 0, 1, 1, 2, 3, 1};
    private static final byte[] CONFIGURATION_DESCRIPTOR = {
        9, 2, 32, 0, 1, 1, 0, (byte) 0x80, 50,
        9, 4, 0, 0, 2, (byte) 0xFF, 0, 0, 2,
        7, 5, (byte) 0x81, 2, 64, 0, 0,
        7, 5, 0x01, 2, 64, 0, 0};

    public final Gpio gpio = mock(Gpio.class);

    private final int[] registers = new int[32];
    private final Deque<Integer> receiveFifo = new ArrayDeque<>();
    private final ByteArrayOutputStream sendFifo = new ByteArrayOutputStream();
    private final ByteArrayOutputStream setupFifo = new ByteArrayOutputStream();
    private final Deque<Integer> controlData = new ArrayDeque<>();
    private final ByteArrayOutputStream controlOut = new ByteArrayOutputStream();
    private final Deque<Integer> uartIn = new ArrayDeque<>();
    private final ByteArrayOutputStream uartOut = new ByteArrayOutputStream();
    private final List<int[]> vendorRequests = new ArrayList<>();
    private final Deque<Integer> scriptedResults = new ArrayDeque<>();

    private boolean selected;
    private int command = -1;
    private int sendCount;
    private int deviceAddress;
    private int pendingAddress = -1;
    private int[] setup = new int[8];
    private int deviceSendToggle;
    private int deviceReceiveToggle;
    private int hostSendToggle;
    private int hostReceiveToggle;

    public boolean connected = true;
    boolean oscillatorOk = true;
    int revision = 0x13;
    int vendorId = 0x10C4;
    /** HXFR never completes: no device or a broken SPI link. */
    public boolean deadController;
    /** The bulk OUT endpoint answers with this result instead of accepting data (0 = accept). */
    int bulkOutResult;
    /** Interrupt and control requests answer STALL from this request number on (-1 = never). */
    int stallRequest = -1;
    /** Bytes delivered per bulk IN packet, at most 64. */
    int bulkPacket = 64;
    public final List<Integer> lidarCommands = new ArrayList<>();
    public final List<Integer> motorPwm = new ArrayList<>();
    private final Deque<Integer> lidarPending = new ArrayDeque<>();
    public boolean lidarAnswers = true;
    /** Bytes the lidar streams after a scan command, queued on demand by the test. */
    public final Deque<Integer> scanStream = new ArrayDeque<>();

    public FakeUsbBus() {
        when(gpio.digitalPin("D10")).thenReturn(CHIP_SELECT);
        when(gpio.getOutput()).thenReturn(1);
        doAnswer(invocation -> {
            int pin = invocation.getArgument(0);
            boolean high = invocation.getArgument(1);
            if (pin == CHIP_SELECT) {
                selected = !high;
                command = -1;
            }
            return null;
        }).when(gpio).digitalWrite(anyInt(), anyBoolean());
    }

    /** Queues a result code the next HXFR transactions report before the normal one (e.g. NAK, TIMEOUT). */
    void script(int... results) {
        for (int result : results) {
            scriptedResults.add(result);
        }
    }

    public void receive(int... bytes) {
        for (int value : bytes) {
            uartIn.add(value & 0xFF);
        }
    }

    byte[] uartOut() {
        return uartOut.toByteArray();
    }

    public List<int[]> vendorRequests() {
        return vendorRequests;
    }

    @Override
    public void begin() {
        // nothing to configure
    }

    @Override
    public void setClock(int frequencyHz) {
        // the fake answers at any clock
    }

    @Override
    public void transfer(byte[] buffer, int length) {
        for (int index = 0; index < length; index++) {
            buffer[index] = (byte) transfer(buffer[index] & 0xFF);
        }
    }

    @Override
    public void write(byte[] buffer, int length) {
        for (int index = 0; index < length; index++) {
            transfer(buffer[index] & 0xFF);
        }
    }

    @Override
    public int transfer(int value) {
        if (!selected) {
            throw new IllegalStateException("SPI transfer without chip select");
        }
        if (command < 0) {
            command = value;
            return 0;
        }
        int register = command >> 3;
        boolean write = (command & 0x02) != 0;
        if (write) {
            write(register, value & 0xFF);
            return 0;
        }
        return read(register);
    }

    private void write(int register, int value) {
        switch (register) {
            case SNDFIFO -> sendFifo.write(value);
            case SUDFIFO -> setupFifo.write(value);
            case SNDBC -> sendCount = value;
            case HIRQ -> registers[HIRQ] &= ~value;
            case HCTL -> writeHostControl(value);
            case HXFR -> transaction(value);
            default -> registers[register] = value;
        }
    }

    private int read(int register) {
        return switch (register) {
            case RCVFIFO -> receiveFifo.isEmpty() ? 0 : receiveFifo.poll();
            case USBIRQ -> oscillatorOk ? 0x01 : 0;
            case REVISION -> revision;
            case HIRQ -> registers[HIRQ] | 0x40;
            case HCTL -> 0;
            case HRSL -> hostResult();
            default -> registers[register];
        };
    }

    private int hostResult() {
        int state = connected ? 0x80 : 0;
        return state | (hostSendToggle << 5) | (hostReceiveToggle << 4) | (registers[HRSL] & 0x0F);
    }

    private void writeHostControl(int value) {
        if ((value & 0x80) != 0) {
            hostSendToggle = 1;
        }
        if ((value & 0x40) != 0) {
            hostSendToggle = 0;
        }
        if ((value & 0x20) != 0) {
            hostReceiveToggle = 1;
        }
        if ((value & 0x10) != 0) {
            hostReceiveToggle = 0;
        }
        if ((value & 0x01) != 0) {
            deviceAddress = 0;
            deviceSendToggle = 0;
            deviceReceiveToggle = 0;
        }
    }

    private void transaction(int value) {
        if (deadController) {
            return;
        }
        int token = value & 0xF0;
        int endpoint = value & 0x0F;
        int result = scriptedResults.isEmpty() ? -1 : scriptedResults.poll();
        if (result < 0) {
            result = registers[PERADDR] != deviceAddress && token != 0x10 ? 14 : run(token, endpoint);
        }
        registers[HRSL] = result;
        registers[HIRQ] |= 0x80;
    }

    private int run(int token, int endpoint) {
        return switch (token) {
            case 0x10 -> setup();
            case 0x00 -> endpoint == 0 ? controlIn() : bulkIn();
            case 0x20 -> endpoint == 0 ? controlDataOut() : bulkOut();
            case 0x80 -> statusIn();
            case 0xA0 -> 0;
            default -> 2;
        };
    }

    private int setup() {
        byte[] packet = setupFifo.toByteArray();
        setupFifo.reset();
        for (int i = 0; i < 8; i++) {
            setup[i] = packet[i] & 0xFF;
        }
        controlData.clear();
        controlOut.reset();
        int request = setup[1];
        if (stallRequest >= 0 && request == stallRequest) {
            return 5;
        }
        int requestType = setup[0];
        int value = setup[2] | (setup[3] << 8);
        int length = setup[6] | (setup[7] << 8);
        if (requestType == 0x80 && request == 6) {
            byte[] descriptor = ((value >> 8) == 1 ? DEVICE_DESCRIPTOR : CONFIGURATION_DESCRIPTOR).clone();
            if ((value >> 8) == 1) {
                descriptor[8] = (byte) vendorId;
                descriptor[9] = (byte) (vendorId >> 8);
            }
            for (int i = 0; i < descriptor.length && i < length; i++) {
                controlData.add(descriptor[i] & 0xFF);
            }
        } else if (requestType == 0xC1 && request == 0x1D) {
            controlData.addAll(List.of(0x00, 0xC2, 0x01, 0x00));
        }
        return 0;
    }

    private int controlIn() {
        receiveFifo.clear();
        int count = 0;
        while (!controlData.isEmpty() && count < 64) {
            receiveFifo.add(controlData.poll());
            count++;
        }
        registers[RCVBC] = count;
        registers[HIRQ] |= 0x04;
        hostReceiveToggle ^= 1;
        return 0;
    }

    private int controlDataOut() {
        byte[] data = sendFifo.toByteArray();
        sendFifo.reset();
        controlOut.write(data, 0, Math.min(sendCount, data.length));
        hostSendToggle ^= 1;
        return 0;
    }

    private int statusIn() {
        int requestType = setup[0];
        int request = setup[1];
        int value = setup[2] | (setup[3] << 8);
        if (requestType == 0x00 && request == 5) {
            deviceAddress = value;
        } else if (requestType == 0x00 && request == 9) {
            deviceSendToggle = 0;
            deviceReceiveToggle = 0;
        } else if (requestType == 0x41) {
            byte[] data = controlOut.toByteArray();
            int[] recorded = new int[3 + data.length];
            recorded[0] = request;
            recorded[1] = value;
            recorded[2] = setup[4] | (setup[5] << 8);
            for (int i = 0; i < data.length; i++) {
                recorded[3 + i] = data[i] & 0xFF;
            }
            vendorRequests.add(recorded);
            if (request == 0x12) {
                uartIn.clear();
            }
        }
        return 0;
    }

    private int bulkIn() {
        if (hostReceiveToggle != deviceSendToggle) {
            return 6;
        }
        pumpLidar();
        if (uartIn.isEmpty()) {
            return 4;
        }
        receiveFifo.clear();
        int count = 0;
        while (!uartIn.isEmpty() && count < bulkPacket) {
            receiveFifo.add(uartIn.poll());
            count++;
        }
        registers[RCVBC] = count;
        registers[HIRQ] |= 0x04;
        hostReceiveToggle ^= 1;
        deviceSendToggle ^= 1;
        return 0;
    }

    private int bulkOut() {
        if (bulkOutResult != 0) {
            return bulkOutResult;
        }
        byte[] data = sendFifo.toByteArray();
        sendFifo.reset();
        if (hostSendToggle == deviceReceiveToggle) {
            for (int i = 0; i < sendCount && i < data.length; i++) {
                uartOut.write(data[i]);
                lidarByte(data[i] & 0xFF);
            }
            deviceReceiveToggle ^= 1;
        }
        hostSendToggle ^= 1;
        return 0;
    }

    /** A minimal RPLIDAR on the bridge's UART: answers health, info and scan, records motor PWM. */
    private void lidarByte(int value) {
        lidarPending.add(value);
        if (lidarPending.peekFirst() != 0xA5) {
            lidarPending.poll();
            return;
        }
        if (lidarPending.size() < 2) {
            return;
        }
        Integer[] bytes = lidarPending.toArray(new Integer[0]);
        int commandByte = bytes[1];
        if (commandByte == 0xF0) {
            if (bytes.length < 6) {
                return;
            }
            motorPwm.add(bytes[3] | (bytes[4] << 8));
            lidarCommands.add(commandByte);
            lidarPending.clear();
            return;
        }
        lidarCommands.add(commandByte);
        lidarPending.clear();
        if (!lidarAnswers) {
            return;
        }
        switch (commandByte) {
            case 0x52 -> receive(0xA5, 0x5A, 0x03, 0, 0, 0, 0x06, 1, 0, 0);
            case 0x50 -> receive(0xA5, 0x5A, 0x14, 0, 0, 0, 0x04, 0x28, 20, 1, 2,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 0x20 -> receive(0xA5, 0x5A, 0x05, 0, 0, 0x40, 0x81);
            default -> {
                // STOP, RESET: no response
            }
        }
    }

    /** Feeds queued scan bytes into the UART a packet at a time, as the lidar streams them. */
    private void pumpLidar() {
        for (int i = 0; i < 64 && !scanStream.isEmpty(); i++) {
            uartIn.add(scanStream.poll());
        }
    }

    /** The five bytes of one measurement node. */
    public static int[] node(boolean start, int degrees, int millimetres) {
        int angle = degrees * 64;
        int distance = millimetres * 4;
        int first = (start ? 1 : 2) | (15 << 2);
        return new int[] {first, ((angle & 0x7F) << 1) | 1, (angle >> 7) & 0xFF, distance & 0xFF, (distance >> 8) & 0xFF};
    }
}
