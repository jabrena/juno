---
title: "Getting Started"
description: "Install the tools you need before creating your first Juno project."
layout: page
---

## Install the prerequisites

Before creating a Juno project, install the tools below and make sure they are available in your shell. Juno programs
are written in Java, built with Maven and compiled for Arduino boards through Arduino CLI.

### JDK 27 and Maven

The easiest way to install the versions used by Juno is [SDKMAN!](https://sdkman.io). The repository pins these
versions in its `.sdkmanrc`:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 27.0.0.0+35-tem
sdk install maven 3.10.0
java --version    # should report version 27
mvn --version     # should report Maven 3.10.0 running on Java 27
```

If you already manage Java and Maven another way, install JDK 27 and Maven 3.10.0 and check that those versions are
selected before continuing.

### Git

Git is needed to work with project sources and examples. Install it from [git-scm.com](https://git-scm.com/downloads)
and verify it:

```bash
git --version
```

### Arduino CLI

The Juno Maven plugin uses [Arduino CLI](https://arduino.github.io/arduino-cli/) to compile and upload sketches.
Install it with Homebrew on macOS or the official install script elsewhere:

```bash
brew install arduino-cli
# or: curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh
arduino-cli version
```

### Continue

Once the prerequisites are installed, continue with [Create your first project](create-your-first-project)
to generate a standalone Juno project and compile its first sketch.
