---
title: "Syslog"
description: "Send allocation-free RFC 5424 log messages over Wi-Fi and receive them with juno:monitor."
layout: page
---

[`Syslog`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/udp/Syslog.java)
sends log messages as RFC 5424 datagrams over UDP. It is the network counterpart to
[Serial](../serial): use it when the board is installed away from the computer or when a USB cable is not practical.
The board and the computer running `juno:monitor` must be on the same network.

Syslog uses the board's existing UDP socket. A program can own only one UDP socket, so create the logger after the
socket and reuse it for the rest of the program. Create its caller-owned buffer once at startup; logging in a loop
then allocates no objects.

## Create a logger

After connecting the board to Wi-Fi, create a destination address and a logger. The default receiver port is `5514`,
because the standard syslog port `514` usually requires elevated privileges on the computer:

```java
import io.github.jabrena.juno.hardware.net.udp.Syslog;

import java.net.DatagramSocket;
import java.net.InetAddress;

InetAddress receiver = InetAddress.getByAddress(new byte[] {
        (byte) 192, (byte) 168, (byte) 1, (byte) 42
});
DatagramSocket socket = new DatagramSocket(5515);
Syslog syslog = new Syslog(socket, receiver, Syslog.DEFAULT_PORT, "nano-rcx", "drive", new byte[128]);

syslog.info("program started");
syslog.warning("battery is low");
```

Use the computer's address for a single receiver. The broadcast address `255.255.255.255` sends the datagram to every
syslog monitor on the local network:

```java
InetAddress everyone = InetAddress.getByAddress(new byte[] {
        (byte) 255, (byte) 255, (byte) 255, (byte) 255
});
Syslog syslog = new Syslog(new DatagramSocket(5515), everyone, Syslog.DEFAULT_PORT,
        "nano-rcx", "drive", new byte[128]);
```

The `hostName` and `appName` identify the sender in every message. They must be printable ASCII without spaces, with
maximum lengths of 255 and 48 characters respectively. A buffer must contain at least 32 bytes; a 128-byte buffer is a
useful size for short telemetry messages.

## Build messages without concatenation

The convenience methods send one complete line at a fixed severity:

| Method | Severity |
|---|---|
| `error(String)` | Error condition |
| `warning(String)` | Warning condition |
| `notice(String)` | Normal but significant condition |
| `info(String)` | Informational message |
| `debug(String)` | Debug message |
| `log(int, String)` | A caller-selected RFC 5424 severity |

For telemetry, build the message in the reusable logger buffer with `begin`, `text`, `number` and `send`:

```java
syslog.begin(Syslog.DEBUG)
        .text("elapsed=").number(elapsedMillis)
        .text(" left=").number(leftSteps)
        .text(" right=").number(rightSteps)
        .send();
```

`send()` returns `false` when the datagram could not be sent and increments `failures()`. A message longer than the
buffer is truncated; logging therefore does not throw or interrupt the control loop. Characters outside printable
ASCII are sent as `?`.

## Receive messages with `juno:monitor`

Start the network monitor on the computer before or after flashing the board:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514
```

No board or serial port is needed for this mode. The monitor prints the arrival time, severity, sender and message:

```text
[INFO] Listening for syslog on UDP port 5514; press Ctrl+C to stop
[INFO] 12:04:07.218 NOTICE    nano-rcx drive: program started
[INFO] 12:04:07.412 DEBUG     nano-rcx drive: elapsed=400 left=118 right=-121
```

If the monitor receives nothing, first confirm that both devices are on the same network, that the receiver port is
`5514`, and that the computer firewall allows inbound UDP. Use the computer's address instead of broadcast when the
network blocks broadcast datagrams.

[`NanoCarrierRcxRegulatedDifferentialDrive`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxRegulatedDifferentialDrive.java)
is a complete example: it connects to an RCX through the Nano Connector Carrier and sends drive, sensor and fault
diagnostics over Wi-Fi while the robot runs.
