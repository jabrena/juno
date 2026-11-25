---
title: "JEV decision models"
description: "Use JEV1 decision models from Java programs running on a Juno board."
layout: page
---

`DecisionModel` evaluates a fixed set of legal actions against a caller-owned state and returns the highest-scoring
action. Load a model once with `MCU.getDecisionModel().begin()`, then call `evaluate(state, legalActions)` whenever the
application needs a decision.

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.ai.TicTacToeWebGame \
  -Djuno.board=arduino-portenta-h7 \
  -Djuno.dm.model=../juno-ai/juno-models/src/main/resources/dm/TIC_TAC_TOE.JEV
```

JEV web examples use `wifi.ssid` and `wifi.password` from `MCU.getSecretStore()` and do not require an SD card. The
HTML, CSS and JavaScript are separate classpath resources. The examples run on the Nano ESP32, AI Thinker ESP32-CAM
and Portenta H7.

See the detailed [JEV decision-model article](../../blog/2026-11-25-jev-decision-models/).
