---
title: "Create your first project"
description: "Create a new Git repository called testing-juno, add Juno as a submodule, configure the Maven plugin and upload a first program to an Arduino UNO R4 WiFi."
layout: page
---

This page creates a new Git repository called `testing-juno`. It adds Juno as a git submodule, configures the Maven
plugin, and uploads a first program to an Arduino UNO R4 WiFi.

You need the tools from [Getting Started](../getting-started#1-prerequisites): JDK 27, Maven, Arduino CLI and Git.

## 1. Create the repository

```bash
mkdir testing-juno
cd testing-juno
git init
```

By the end of this page the project will look like this:

```text
testing-juno/
├── juno/                                  git submodule: jabrena/juno
├── src/main/java/com/example/testingjuno/App.java
├── .gitmodules
├── .mvn/jvm.config
├── mvnw
└── pom.xml
```

## 2. Add Juno as a submodule

```bash
git submodule add https://github.com/jabrena/juno.git juno
```

`git submodule add` clones Juno into `juno/` and records it in a `.gitmodules` file. To build against a specific Juno
revision, pin the submodule to a commit with `git -C juno checkout <commit>`.

## 3. Build Juno into your local Maven repository

The project depends on `juno-api` and the Maven plugin at version `0.1.0-SNAPSHOT`. Maven resolves them from your local
repository (`~/.m2`), not from the submodule, so build the submodule once with its own wrapper:

```bash
cd juno
./mvnw clean install -DskipTests -Dspotless.check.skip=true -Dpmd.skip=true \
  -pl juno-api,juno-compiler,juno-maven-plugin -am
cd ..
```

Run this again whenever you move the submodule to a new commit.

## 4. Create the Maven project

Create a `pom.xml` in the root of `testing-juno`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>testing-juno</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>27</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <juno.version>0.1.0-SNAPSHOT</juno.version>
        <juno.board>arduino-uno-r4-wifi</juno.board>
        <juno.main>com.example.testingjuno.App</juno.main>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.github.jabrena</groupId>
            <artifactId>juno-api</artifactId>
            <version>${juno.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>${maven.compiler.release}</release>
                </configuration>
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
</project>
```

Each part has a job:

| Element | Purpose |
|---|---|
| `maven.compiler.release` | The JDK level the program is compiled for. Juno requires 27. |
| `juno.version` | Must match the version installed from the submodule in step 3. |
| `juno-api` dependency | The only artifact your program compiles against: the board API, with no compiler code. |
| `<mainClass>` | The entry-point class, fully qualified. It is the `juno.main` property. |
| `<board>` | The target board id, here `arduino-uno-r4-wifi`. It is the `juno.board` property. |

Every plugin option can also be passed on the command line. A `-D` property overrides the value in `pom.xml` for that run:

| Plugin option | Property | Purpose |
|---|---|---|
| `mainClass` | `juno.main` | The entry point to compile. |
| `board` | `juno.board` | The board id. Needed only when the entry point's `@Board` lists several boards. |
| `port` | `juno.port` | The serial port, when more than one matching board is connected. |
| `fqbn` | `juno.fqbn` | Overrides the board's Arduino FQBN. |
| `arduinoCli` | `juno.arduinoCli` | The `arduino-cli` executable, if it is not on the `PATH`. |
| `outputDirectory` | `juno.outputDirectory` | Where the generated sketch folders are written. |
| `arenaSize` | `juno.Xmx` | The memory arena size for the program. |
| `threadStackSize` | `juno.Xss` | The stack size of each Juno thread. |

Juno's compiler needs a few JDK modules opened, so add `.mvn/jvm.config` with these lines:

```text
--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
```

Then add the Maven wrapper so `./mvnw` works without a separate Maven install:

```bash
mvn -N wrapper:wrapper
```

## 5. Install the board core and libraries

Run this once per machine. It installs the Renesas UNO core and the optional libraries the examples need:

```bash
./mvnw juno:install-deps
```

## 6. Write the program

Create `src/main/java/com/example/testingjuno/App.java`. The board is selected by its `@Board` annotation, and the program
scrolls a message across the 12 × 8 LED matrix:

```java
package com.example.testingjuno;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

@Board(ArduinoUnoR4WiFiBoard.class)
public final class App {
    public static void main(String[] args) {
        LedMatrix matrix = ArduinoUno.getLedMatrix();
        matrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];

        while (true) {
            for (int offset = 12; offset >= -60; offset--) {
                LedCanvas.clear(frame);
                LedCanvas.drawText(frame, "Hello, Juno", offset, 0);
                LedCanvas.show(frame);
                Delay.millis(80);
            }
        }
    }
}
```

`drawText` takes a compile-time string literal, so the message is written directly in the source.

## 7. Build and upload

Connect the board with a USB-C cable. Compile, verify and upload in one command:

```bash
./mvnw compile juno:upload
```

On Windows, use `.\mvnw.cmd compile juno:upload`. To check the program without touching the board, run `juno:verify`
instead:

```bash
./mvnw compile juno:verify
```

If several boards are connected, list them and choose the port:

```bash
./mvnw juno:boards
./mvnw compile juno:upload -Djuno.port=<PORT>
```

On Windows, quote the property: `.\mvnw.cmd compile juno:upload "-Djuno.port=COM11"`.

## 8. Commit the project

```bash
git add .gitmodules juno pom.xml mvnw .mvn src
git commit -m "chore: first Juno project"
```

Others can then clone it with `git clone --recursive` and build it the same way.

## 9. Review ideas for your next steps

- **Change the message:** edit the text and the scroll range in `App.java`, then run `./mvnw compile juno:upload` again.
- **Add a second program:** create another class with `@Board`, then upload it with `-Djuno.main=<fully-qualified-name>`
  instead of editing `pom.xml`.
- **Update Juno:** move the submodule to a new commit with `git -C juno checkout <commit>`, rebuild it with the step 3
  command, then compile again.
- **Track the submodule version:** commit the new submodule pointer after each update, so everyone builds the same Juno.

## Troubleshooting

- **The `juno-maven-plugin` or `juno-api` version cannot be resolved:** rebuild the submodule (step 3). The `juno.version`
  property must match the version it installs.
- **"Missing required Juno entry point":** set `<mainClass>` in `pom.xml`, or pass `-Djuno.main=<fully-qualified-name>`.
- **The upload cannot find the port:** run `./mvnw juno:boards` and pass `-Djuno.port=<PORT>`.
- **Several boards match:** pass `-Djuno.board=<id>` when the entry point's `@Board` lists more than one board.

For every goal and option, see the [Maven Plugin](../juno-maven-plugin) page.
