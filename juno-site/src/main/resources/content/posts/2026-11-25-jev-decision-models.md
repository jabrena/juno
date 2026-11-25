---
title: "JEV: decision models that run on the board"
description: "How Juno stores a decision model as a JEV1 file, picks the best legal action with it on a Nano ESP32, and builds a perfect tic-tac-toe player from a minimax oracle."
date: 2026-11-25
layout: post
link: /blog/2026-11-25-jev-decision-models/
tags: [ai, decision-model, nano-esp32, http]
blogVisible: true
---

A language model answers questions; a decision model chooses. Given the state of a game or a machine and the actions
that are allowed right now, a JEV model returns the one it scores highest. Juno builds these models on the build
machine in pure Java, stores each one in a single JEV1 file, and evaluates it on the board without allocating. This
article explains the format, how the board uses it, and the first model: a tic-tac-toe player that never loses.

| Field | Value |
|---|---|
| Sources | [`juno-dm`](https://github.com/jabrena/juno/tree/main/juno-ai/juno-dm) (format, trainer, reference inference), [`juno-models`](https://github.com/jabrena/juno/tree/main/juno-ai/juno-models) (datasets, oracles, tracked `.JEV` files), [`api/ai`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/api/ai) (examples) |
| Boards supported | [`Nano ESP32`](../../boards/arduino-nano-esp32) for the examples; a model linked into flash needs no PSRAM, a model loaded from an SD card does |
| Features used | `MCU.getDecisionModel()`, flash tables, PSRAM (optional), SD card (optional), Secret Store, Wi-Fi, JDK `HttpServer` |

## What a JEV model computes

JEV stands for *Juno EValuation*. A model has a fixed number of **actions** (the moves it can choose between) and a
fixed number of **features** (the numbers that describe a state). Every action owns one row of weights and a bias, and
its score for a state is a dot product:

```text
score(action) = bias[action] + Σ weight[action][feature] × state[feature]
```

The caller passes the state and the list of actions that are legal in it; the model scores only those and returns the
best one, keeping the first on a tie. An illegal action can never be chosen, because it is never scored. That is the
whole engine: no layers, no activation functions, no sampling. What makes a model good is entirely in its features and
its weights, and both are decided on the build machine.

## The JEV1 file

A JEV1 file is big-endian and has no padding:

| Bytes | Content |
|---|---|
| 4 | The magic `JEV1` |
| 1 | Version, `1` |
| 2 | Action count |
| 2 | Feature count |
| per action | a 32-bit float scale, a 32-bit float bias, then one signed byte per feature |

A weight is `byte × scale`, with one scale per action: `QuantizedJevModel.from(model)` picks the scale so that the
row's largest weight becomes ±127. The size is therefore exact and predictable, `9 + actions × (8 + features)` bytes,
and both the compiler and the board check it before using a file.

## On the board

`DecisionModel` in `juno-api` mirrors `LanguageModel`. Take it once at startup with `MCU.getDecisionModel()` and call
`begin()`:

```java
DecisionModel model = MCU.getDecisionModel();
ModelStatus status = model.begin();          // the model -Djuno.dm.model linked into flash
int move = model.evaluate(STATE, LEGAL_ACTIONS);
```

- `begin()` uses the JEV1 file that the build names with `-Djuno.dm.model`. The compiler reads it, checks its header
  and length, and emits it as a `const` table, so it lives in flash and one `juno:flash` uploads the program and its
  model together. A program that calls `begin()` without the option fails to build, on the laptop rather than on the
  board.
- `begin(card, path)` copies a JEV1 file from a mounted SD card into PSRAM through a 1 KiB buffer instead, so a model
  can change without reflashing. Only this path needs PSRAM.
- `evaluate(state, legalActions)` reads both caller-owned `int[]` buffers in place and allocates nothing, so it can run
  in a main loop. It returns `-1` when no model is loaded, the state has the wrong length, or none of the legal
  actions belongs to the model.

The evaluator itself is one small C function, the same for every model: for each legal action it reads that action's
row where it lies (flash or PSRAM), adds `weight × scale × state[feature]` for every non-zero feature, and keeps the
best. Skipping zero features matters: the tic-tac-toe model below has 4,520 features, and exactly one of them is
non-zero for any position.

## The first model: tic-tac-toe

`juno-models` holds the datasets and oracles that turn a problem into a JEV1 file. The tic-tac-toe model is built in
three steps, all deterministic:

1. `TicTacToeDataset.exhaustive()` walks the game tree from the empty board and keeps every reachable position that is
   not finished: 4,520 of them.
2. `TicTacToeOracle` solves the game with memoized minimax, scoring each move from the side to move: `1` wins, `0`
   draws, `-1` loses with best play.
3. `TicTacToeModelFactory` makes one **feature per position** (a one-hot encoding) and one action per cell. The
   weight of action *c* on feature *p* is the oracle's value of playing *c* in position *p*.

Because exactly one feature is set, a move's score is simply its minimax value, and choosing the highest-scoring legal
move is playing perfectly. Every weight is `-1`, `0` or `1`, so quantization is lossless: each row's scale is `1/127`
and its bytes are `-127`, `0` or `127`. The file, `TIC_TAC_TOE.JEV`, is 9 actions × 4,520 features, which is
`9 + 9 × (8 + 4,520) = 40,761` bytes.

This is a lookup table dressed as a model, and that is deliberate: it is small enough to verify completely, so it
tests the format, the quantization and the board evaluator rather than a learning algorithm. `TicTacToeOptimalityTest`
in `juno-dm` and `TicTacToeModelTest` in `juno-models` check every one of the 4,520 positions against the oracle, for
the float model and the quantized one, and the tracked `TIC_TAC_TOE.JEV` must match the factory's output byte for
byte.

The price of a one-hot model is that the program must know which feature a position is. `TicTacToeWebGame` keeps a
table in flash that maps each position, written as a base-3 number, to its feature index, sorted so a binary search
finds it. A computer move then takes about 80 ms on a Nano ESP32.

## Try it: TicTacToeWebGame

`TicTacToeWebGame` joins Wi-Fi with the credentials in the board's Secret Store and serves a game page; you play `O`,
the model plays `X`:

```bash
./mvnw -f juno-examples/pom.xml juno:secret -Djuno.board=arduino-nano-esp32 -Djuno.port=<port> -Djuno.secret.key=wifi.ssid
./mvnw -f juno-examples/pom.xml juno:secret -Djuno.board=arduino-nano-esp32 -Djuno.port=<port> -Djuno.secret.key=wifi.password
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.ai.TicTacToeWebGame \
  -Djuno.board=arduino-nano-esp32 -Djuno.port=<port> \
  -Djuno.dm.model=../juno-ai/juno-models/src/main/resources/dm/TIC_TAC_TOE.JEV
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.port=<port>
```

The monitor prints the board's address; open it in a browser on the same network. The page calls three routes,
`GET /api/state`, `POST /api/move` and `POST /api/reset`, and the board answers each from a buffer it allocated once at
startup. `TicTacToeDecisionModel` is the minimal version with no network: it loads the model and prints its opening
move on the serial port every five seconds.

## Training instead of solving

Not every problem fits in a table. `JevTrainer` in `juno-dm` learns weights from labelled examples instead: each
`JevTrainingExample` is a feature vector, the legal actions and the action an expert chose. The trainer is a
deterministic multi-class perceptron: whenever the current model would pick a different action than the expert, it
moves the expert's row towards the features and the chosen row away from them. With a fixed example order and epoch
count it always produces the same model, so a tracked `.JEV` file can be regenerated byte for byte.

A learned model uses features that generalize, such as "this move completes a line", instead of one feature per
state, so it can play positions it has never seen. It also stops being perfect: the honest measure becomes how often
it agrees with an oracle on positions it was not trained on. That is the direction for the next models in
`juno-models`.

## What to expect

A JEV model is as good as the features it is given: the evaluator is linear and looks one decision ahead, so it does
not search. In exchange it is tiny, its cost is known in advance (one pass over the legal actions' rows), and it never
allocates, which is what a microcontroller can afford inside a control loop. Build a new model on the laptop, pass the
file to `-Djuno.dm.model` and flash again, or copy it to an SD card and load it with `begin(card, path)`.
