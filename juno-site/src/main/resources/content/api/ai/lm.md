---
title: "JLM language models"
description: "Use JLM1 language models from Java programs running on a Juno board."
layout: page
---

`LanguageModel` runs the JLM1 character-level transformer on the board. `begin()` loads `JUNO.BIN` into external model
memory, and `ask`/`nextChar` provide the streaming inference API.

## What is a JLM model?

JLM means **Juno Language Model**. JLM1 is a decoder-only, character-level transformer: it reads a question one
character at a time and generates one answer character at a time until it emits `END` or reaches the context limit.
The model is trained and quantized on the build machine in pure Java; the board performs the inference locally without
a cloud service or a JVM.

`JUNO.BIN` contains the model dimensions, alphabet and signed-byte weight tables. Each matrix row stores a float scale
and quantized weights, so the board uses the same representation that is stored in the file and does not convert a
large float model during startup. The tracked model is a 96-wide, four-layer transformer with a 128-character context
and a 97-token vocabulary. The current `JUNO.BIN` is about 382 KB.

## API and memory

Call `MCU.getLanguageModel()` once and then:

```java
LanguageModel model = MCU.getLanguageModel();
model.begin();
model.ask("What is Juno?", 0);
int character;
while ((character = model.nextChar()) != LanguageModel.END) {
    Serial.print((char) character);
}
```

`begin()` loads the file named by `-Djuno.lm.model`; omitting that property is a build-time error when the program uses
the model. `ask` starts a generation and `nextChar` streams it without allocating per character. The model weights and
attention cache live in external memory, while the Java arena holds only the fixed application buffers.

## Current board support

| Board | External model memory | Current status |
|---|---|---|
| [Nano ESP32](../../../boards/arduino-nano-esp32) | 8 MB PSRAM | Web and serial chat examples supported; the model and attention cache use PSRAM. |
| [AI Thinker ESP32-CAM](../../../boards/ai-thinker-esp32-cam) | 4 MB PSRAM | The web example is supported; the camera and model share the board's external-memory budget. |
| [Portenta H7](../../../boards/arduino-portenta-h7) | 8 MB SDRAM | The web example is supported; JLM model memory uses SDRAM. |

The current web example reads `wifi.ssid` and `wifi.password` from `MCU.getSecretStore()` and does not require an SD
card. `LanguageModelSdCardSecretStoreChat` is the separate SD-card variant for the Nano Connector Carrier.
```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.ai.LanguageModelSecretStoreChat \
  -Djuno.board=arduino-portenta-h7 \
  -Djuno.lm.model=../juno-ai/juno-models/src/main/resources/lm/JUNO.BIN
```

The Nano ESP32 and ESP32-CAM use PSRAM; the Portenta H7 uses external SDRAM. The web example reads Wi-Fi credentials
from the Secret Store and does not require an SD card. `JUNO.BIN` is maintained in
`juno-ai/juno-models/src/main/resources/lm/`.
