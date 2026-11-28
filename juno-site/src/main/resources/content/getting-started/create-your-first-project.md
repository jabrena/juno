---
title: "Create your first project"
description: "Create a standalone Juno project from the published Maven archetype, generate an Arduino sketch and verify it before uploading."
layout: page
---
{|
This page creates a standalone Juno project called `my-first-juno-project`. It consumes the published Juno artifacts
from Maven Central: it does not clone the Juno repository or add it as a Git submodule.

## 1. Check the prerequisites

Install these tools before creating the project. Juno requires JDK 27, Maven, Arduino CLI and Git. The repository's
[Getting Started prerequisites](../#1-prerequisites) show an SDKMAN-based installation for Java and Maven, plus
Arduino CLI installation options.

Verify that each tool is available in the shell you will use for the commands below:

```bash
java --version       # must report Java 27
mvn --version        # must report Maven and Java 27
arduino-cli version
git --version
```

If a command is not found, install the corresponding tool first: [JDK 27 and Maven](../#1-prerequisites),
[Arduino CLI](https://docs.arduino.cc/arduino-cli/installation/) and [Git](https://git-scm.com/downloads).
Uploading to a physical board is optional; `juno:verify` only compiles and checks the generated sketch.

## 2. Create your project directory

```bash
mkdir my-first-juno-project
cd my-first-juno-project
```

The project is created from the published Maven archetype. It uses the Juno BOM to keep the API and compiler-side
artifacts on one version. No Juno repository checkout or submodule is needed.

## 3. Generate the Maven project

Run this command from the parent directory of the project directory. Maven creates `my-first-juno-project/`:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=io.github.jabrena \
  -DarchetypeArtifactId=juno-archetype \
  -DarchetypeVersion=0.2.0 \
  -DgroupId=com.example \
  -DartifactId=my-first-juno-project \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackage=com.example.juno \
  -DjunoVersion=0.2.0 \
  -DinteractiveMode=false
```

If you are already inside `my-first-juno-project`, run the same command with `-DoutputDirectory=.`. The archetype
version identifies the project template; `junoVersion` selects the published Juno release.

The generated project has this shape:

```text
my-first-juno-project/
├── .mvn/
│   └── jvm.config
├── pom.xml
└── src/main/java/com/example/juno/
    └── Blink.java
```

## 4. Understand the generated `pom.xml`

The important generated sections are:

```xml
<groupId>com.example</groupId>
<artifactId>my-first-juno-project</artifactId>
<version>1.0.0-SNAPSHOT</version>

<properties>
    <java.version>27</java.version>
    <maven-compiler-plugin.version>3.16.0</maven-compiler-plugin.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <juno.version>0.2.0</juno.version>
    <juno.board>arduino-uno-r4-wifi</juno.board>
    <juno.main>com.example.juno.Blink</juno.main>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.jabrena</groupId>
            <artifactId>juno-bom</artifactId>
            <version>${juno.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.jabrena</groupId>
        <artifactId>juno-api</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>${maven-compiler-plugin.version}</version>
            <configuration><release>${java.version}</release></configuration>
        </plugin>
        <plugin>
            <groupId>io.github.jabrena</groupId>
            <artifactId>juno-maven-plugin</artifactId>
            <version>${juno.version}</version>
            <configuration>
                <board>${juno.board}</board>
                <mainClass>${juno.main}</mainClass>
            </configuration>
        </plugin>
    </plugins>
</build>
```

The project coordinates identify your application. Java 27 is the JDK level required by Juno. `juno.version` selects
the Juno release. The imported `juno-bom` supplies versions for Juno artifacts, so `juno-api` needs no version of
its own. `juno.board` selects the board and `juno.main` names the entry point. The compiler plugin targets Java 27;
the Juno Maven plugin generates, verifies and uploads the Arduino sketch.

POM values are defaults. Command-line `-D` properties override them for one run:

| POM value | Command-line property | Use |
| --- | --- | --- |
| `juno.board` | `-Djuno.board=<board-id>` | Select another supported board. |
| `juno.main` | `-Djuno.main=<fully-qualified-class>` | Compile another entry point. |
| plugin `port` | `-Djuno.port=<serial-port>` | Select a connected board when needed. |
| plugin `fqbn` | `-Djuno.fqbn=<fqbn>` | Override the Arduino board identifier. |
| plugin `arduinoCli` | `-Djuno.arduinoCli=<path>` | Use Arduino CLI outside `PATH`. |
| plugin `outputDirectory` | `-Djuno.outputDirectory=<path>` | Change generated output location. |

## 5. Understand `.mvn/jvm.config`

The archetype includes `.mvn/jvm.config` with the JDK compiler module exports and opens required by Juno's compiler.
Maven reads it automatically, so no separate runtime setup is needed.

## 6. Understand the generated `Blink` example

```java
package com.example.juno;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

public final class Blink {
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
        while (true) {
            led.high();
            Delay.millis(500);
            led.low();
            Delay.millis(500);
        }
    }
}
```

`MCU.getGpio().getBuiltinLed()` obtains the board's built-in LED. `DigitalOutput.of(...)` gives the program a digital
output, and `led.high()` / `led.low()` switch it on and off. The two 500 millisecond delays create one-second blink
cycles. The board abstraction keeps this program portable across supported boards. It intentionally produces no serial
output.

## 7. Install board dependencies

Install the Arduino cores and libraries needed by Juno examples once per machine:

```bash
./mvnw juno:install-deps
```

Add a Maven wrapper with `mvn -N wrapper:wrapper`, or use Maven installed on your `PATH`.

## 8. Generate and verify the sketch

```bash
./mvnw compile juno:compile
./mvnw compile juno:verify
```

`juno:verify` compiles/checks the generated sketch with Arduino CLI; it does not upload anything. Generated sketch and
assembly files are normally below `target/juno/<board>/`; the Java source and POM remain the portable source of truth.

## 9. Optionally flash the board

Uploading is optional. Connect the board over USB, list matching boards and flash it:

```bash
./mvnw juno:boards
./mvnw compile juno:flash -Djuno.port=<PORT>
```

Use `-Djuno.board=<board-id>` when the program supports more than one board. The port is the USB serial device
reported by `juno:boards`; do not use a network address for uploading.

For every goal and option, see the [Maven Plugin](../../features/juno-maven-plugin) page.

## 10. What to expect after flashing the UNO R4 WiFi

<img src="../../images/boards/arduino-uno-r4-wifi.png" alt="Arduino UNO R4 WiFi" width="280">

The default project targets the Arduino UNO R4 WiFi. After `juno:flash` finishes, the board resets and the built-in
LED should blink repeatedly: on for 500 milliseconds, then off for 500 milliseconds. This confirms that the generated
Java program is running on the board.

The `Blink` example intentionally produces no serial output, so a quiet serial monitor is expected. If the LED does
not blink, check that the selected `juno.board` is `arduino-uno-r4-wifi`, the USB serial `-Djuno.port` is correct, and
the flash command completed without errors.
|}
