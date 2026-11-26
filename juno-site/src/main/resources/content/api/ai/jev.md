---
title: "JEV decision models"
description: "Use JEV1 decision models from Java programs running on a Juno board."
layout: page
---

`DecisionModel` evaluates a fixed set of legal actions against a caller-owned state and returns the highest-scoring
action. Load a model once with `MCU.getDecisionModel().begin()`, then call `evaluate(state, legalActions)` whenever the
application needs a decision.

## What is a JEV model?

JEV means **Juno EValuation**. It is a compact, quantized decision model for problems where the program supplies a
fixed-length feature vector and a list of currently legal actions. Each action has one row of weights and a bias; the
model computes a score for every legal action and returns the highest-scoring one. An illegal action is never scored.

```text
score(action) = bias[action] + Σ weight[action][feature] × state[feature]
```

JEV is not a general neural-network runtime: it has no layers, activation functions or sampling. The quality of the
decision comes from the features and weights produced on the build machine. The board evaluator reads caller-owned
`int[]` buffers and allocates nothing during `evaluate`.

## JEV1 files

The build-time `juno-dm` module trains or constructs a model and writes one deterministic `JEV1` file. Its header stores
the version, action count and feature count, followed by one float scale, one float bias and one signed-byte weight per
feature for each action. The weight is `byte × scale`, so the file size is predictable:

```text
9 + actions × (8 + features) bytes
```

The compiler validates the header and file length, embeds a linked model in flash, and rejects a missing or malformed
model before the board is flashed. The tracked models currently include `TIC_TAC_TOE.JEV` and `CONNECT_FOUR.JEV` in
`juno-ai/juno-models/src/main/resources/dm/`.

## Current board support

| Board | Model storage | Current status |
|---|---|---|
| [Nano ESP32](../../../boards/arduino-nano-esp32) | Flash for linked models; optional SD-card loading | Web and serial examples supported; hardware-tested paths exist. |
| [AI Thinker ESP32-CAM](../../../boards/ai-thinker-esp32-cam) | Flash for linked models; optional SD-card loading | The web example is supported. |
| [Portenta H7](../../../boards/arduino-portenta-h7) | Flash for linked models | The web example is supported; no SD card is required. |

JEV models do not require PSRAM or SDRAM when linked from flash. The optional `begin(card, path)` overload requires a
board or carrier with an SD card.

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.ai.TicTacToeWebGame \
  -Djuno.board=arduino-portenta-h7 \
  -Djuno.dm.model=../juno-ai/juno-models/src/main/resources/dm/TIC_TAC_TOE.JEV
```

JEV web examples use `wifi.ssid` and `wifi.password` from `MCU.getSecretStore()` and do not require an SD card. The
HTML, CSS and JavaScript are separate classpath resources. The examples run on the Nano ESP32, AI Thinker ESP32-CAM
and Portenta H7.
