---
title: "Wifi"
description: "Connecting a Juno program to a Wi-Fi network on the UNO R4 WiFi, UNO WiFi Rev2, UNO Q and other boards with Wi-Fi."
layout: page
---

`Wifi` connects a Juno program to a Wi-Fi network. Get the radio once at startup, call `begin` with the network's name
and password, and wait until `status` reports that the connection is up.

## Connecting to Wi-Fi

Get the board's radio once, at startup. `ArduinoUno.getWifi()` returns it on the boards with onboard Wi-Fi (UNO R4
WiFi, UNO WiFi Rev2 and UNO Q). The UNO R3 has no radio and no network support, so the compiler rejects
`ArduinoUno.getWifi()` there. A unit test passes `mock(Wifi.class)`.

The credentials stay on the microSD card (see [SD Card](../sdcard)), so they are not written in the source. `Wifi.begin`
starts the connection; poll `Wifi.status()` until it equals `Wifi.STATUS_CONNECTED` before using the network:

```java
SdCard sd = new DataLoggerShield().getSdCard();
Wifi wifi = ArduinoUno.getWifi();

if (sd.begin()) {
    try (InputStream file = sd.open("application.properties")) {
        if (file != null) {
            Properties properties = new Properties();
            properties.load(file);
            wifi.begin(properties.getProperty("wifi.ssid"), properties.getProperty("wifi.password"));
        }
    } catch (IOException exception) {
        Serial.println("application.properties could not be read");
    }
}
```

A program that runs for a long time should also decide what to do after a disconnection, for example by calling
`Wifi.begin` again before its next request.

## Remote logging with syslog

Once a program is on the network, `Syslog` sends log lines as RFC 5424 syslog datagrams over UDP, so the board can be
followed without a USB cable; `juno:monitor -Djuno.syslog=5514` receives them on a computer on the same network.

Create the logger once, at startup, on the program's own `DatagramSocket` (a program has a single UDP socket on the
board, so a program that also uses UDP shares it), with a destination, the host and app names that identify the sender,
and a buffer that holds one frame. Sending to the broadcast address reaches any monitor on the local network without
configuring the computer's address:

```java
InetAddress everyone = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
Syslog syslog = new Syslog(new DatagramSocket(5515), everyone, Syslog.DEFAULT_PORT, "nano-rcx", "regulated-drive",
        new byte[128]);

syslog.info("travel started");
// A line built from fields: no String concatenation, so logging in a loop allocates nothing.
syslog.begin(Syslog.DEBUG).text("t=").number(elapsed).text(" left=").number(left).text(" right=").number(right).send();
```

Each call sends one datagram such as `<135>1 - nano-rcx regulated-drive - - - t=400 left=118 right=-121`: facility
`local0`, the severity (`ERROR`, `WARNING`, `NOTICE`, `INFO`, `DEBUG` and the rest of RFC 5424's), and a nil timestamp,
since a board need not know the time; the monitor stamps the arrival. A message longer than the buffer is truncated, and
a failed send is counted in `failures()` instead of thrown, so logging never breaks a control loop.

On the computer:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514
```

```
[INFO] Listening for syslog on UDP port 5514; press Ctrl+C to stop
[INFO] 12:04:07.218 NOTICE    nano-rcx regulated-drive: Armed
[INFO] 12:04:07.412 DEBUG     nano-rcx regulated-drive: travel t=400 state=RUNNING faults=0 left=118 right=-121
```

[`NanoCarrierRcxRegulatedDifferentialDrive`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxRegulatedDifferentialDrive.java)
logs an RCX robot's regulated drive this way: every poll of a move sends the elapsed time, the regulator's state and
both wheels' tachometers.

## Troubleshooting

- **Wi-Fi never reaches `STATUS_CONNECTED`:** check the network name and password, and the signal strength. A bounded
  retry loop keeps the failure visible.
