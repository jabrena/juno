---
title: "JLM language models"
description: "Use JLM1 language models from Java programs running on a Juno board."
layout: page
---

`LanguageModel` runs the JLM1 character-level transformer on the board. `begin()` loads `JUNO.BIN` into external model
memory, and `ask`/`nextChar` provide the streaming inference API.

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.ai.LanguageModelSecretStoreChat \
  -Djuno.board=arduino-portenta-h7 \
  -Djuno.lm.model=../juno-ai/juno-models/src/main/resources/lm/JUNO.BIN
```

The Nano ESP32 and ESP32-CAM use PSRAM; the Portenta H7 uses external SDRAM. The web example reads Wi-Fi credentials
from the Secret Store and does not require an SD card. `JUNO.BIN` is maintained in
`juno-ai/juno-models/src/main/resources/lm/`.

See the detailed [JLM1 article](../../blog/2026-11-24-jlm1-on-board-language-model/).
