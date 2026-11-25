---
title: "JLM1: a language model that runs on the board"
description: "How Juno trains a small character-level transformer in Java, stores it as JUNO.BIN and runs it on a Nano ESP32 web chat."
date: 2026-11-24
layout: post
link: /blog/2026-11-24-jlm1-on-board-language-model/
tags: [ai, language-model, nano-esp32, http]
blogVisible: true
---

JLM1 is a small language model that answers questions about Juno, and it runs on the board itself: no cloud, no
internet connection, no host computer. It is trained in pure Java on the build machine, stored in one file,
`JUNO.BIN`, and run on a Nano ESP32 by an engine that Juno compiles from Java. This article shows the web chat example
and explains what is inside the model.

| Field | Value |
|---|---|
| Sources | [`api/ai`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/api/ai) (examples), [`juno-lm`](https://github.com/jabrena/juno/tree/main/juno-ai/juno-lm) (trainer and format), [`JlmLanguageModel`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/internal/JlmLanguageModel.java) (board engine) |
| Boards supported | [`Nano ESP32`](../../boards/arduino-nano-esp32) for the examples; the engine needs PSRAM, which the Nano ESP32 and the ESP32-CAM have |
| Features used | `MCU.getLanguageModel()`, PSRAM, Secret Store, Wi-Fi, JDK `HttpServer`, SD card (optional), serial output |

## Try it: LanguageModelSecretStoreChat

`LanguageModelSecretStoreChat` serves a chat page from the board. Store the Wi-Fi credentials once, then flash it and
read the address it prints. `-Djuno.lm.model` names the model to link into the firmware, so the upload carries the
program and its model together:

```bash
./mvnw -f juno-examples/pom.xml juno:secret -Djuno.board=arduino-nano-esp32 -Djuno.port=<port> -Djuno.secret.key=wifi.ssid
./mvnw -f juno-examples/pom.xml juno:secret -Djuno.board=arduino-nano-esp32 -Djuno.port=<port> -Djuno.secret.key=wifi.password
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.ai.LanguageModelSecretStoreChat \
  -Djuno.board=arduino-nano-esp32 -Djuno.port=<port> \
  -Djuno.lm.model=../juno-ai/juno-lm/src/main/resources/lm/JUNO.BIN
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.port=<port>
```

The serial monitor shows `JLM1: model online` and `JLM1: open http://<address>`. Open that address in a browser on
the same network, or call the API directly:

```bash
curl http://<address>/api/status
# {"loaded":true,"layers":4,"width":96}
curl -X POST --data 'What is Juno?' http://<address>/api/chat
# Juno compiles Java programs into firmware for microcontrollers.
```

The program does four things, all in ordinary Java:

1. `MCU.getLanguageModel().begin()` copies `JUNO.BIN`, which the build linked into the firmware's flash from
   `-Djuno.lm.model`, into PSRAM, and allocates the attention cache next to it. A program that calls `begin()` without
   that option fails to build, so a missing model shows up on the laptop, not on the board.
2. It reads `wifi.ssid` and `wifi.password` from the board's Secret Store and joins the network, so no password is in
   the code or on an SD card.
3. It starts the JDK's `com.sun.net.httpserver.HttpServer` on port 80. `LanguageModelWebChat` registers three routes:
   `GET /` (the page), `GET /api/status` and `POST /api/chat`.
4. For `/api/chat` it passes the request body to `ask(question, length, creativity)`, then calls `nextChar()` until it
   returns `END`, writing each character to the response as soon as it is generated. The page shows the answer
   appearing letter by letter, and nothing is allocated per answer.

Three variants share the same page: `LanguageModelAccessPointChat` opens its own Wi-Fi network instead of joining
one, `LanguageModelSdCardSecretStoreChat` loads `JUNO.BIN` from the Nano Connector Carrier's microSD card with
`begin(card, "/JUNO.BIN")` (it needs no `-Djuno.lm.model`), and `LanguageModelSdCardHttpsChat` serves the same chat over HTTPS with a certificate from
the card. `LanguageModelSerialChat` needs no network: type a question on the serial port.

## What 96 × 4 × 256 means

JLM1 is a decoder-only transformer that reads and writes one character at a time. Its shape is written as
width × layers × hidden:

| Dimension | Value | Meaning |
|---|---|---|
| Width | 96 | Every character becomes a vector of 96 numbers (the embedding) |
| Layers | 4 | Four transformer blocks, applied one after the other |
| Heads | 4 | Each block's attention splits the 96 numbers into 4 heads of 24 |
| Hidden | 256 | The feed-forward part of each block expands to 256 numbers and back |
| Context | 128 | Question plus answer can use 128 positions |
| Vocabulary | 97 | PAD, END and the 95 printable ASCII characters |
| Parameters | 366,528 | All the numbers the training learned |

Each block normalizes its input (RMS norm), lets every position look back at the earlier ones (attention), and adds
the result to a running state; then it normalizes again and passes the state through the 96 → 256 → 96 feed-forward
network with a ReLU. After the last block, the same embedding table that turned characters into vectors scores every
character as the next one, which saves a separate output table.

A question is fed one character per position, followed by END; the model then picks one answer character per position
until it picks END or the context is full. Before the board answers, the engine looks at the first 8 picks: if the most
likely character had a probability below 0.5 at any of them, it answers `I do not know.` instead of guessing.

## Inside JUNO.BIN

`JUNO.BIN` is 381,907 bytes in the JLM1 format, which Juno defines in `juno-lm`:

- a 16-byte header: the magic `JLM1`, then width, layers, heads, hidden, context and vocabulary size as little-endian
  16-bit numbers;
- the alphabet, one byte per character token, so a model declares which characters it reads and writes;
- the tables: the token and position embeddings, then for each layer its attention gains, query, key, value and output
  matrices, feed-forward gains and the two feed-forward matrices, and finally the last gains.

Every matrix row is stored as one 32-bit float scale followed by one signed byte per weight, so a weight is
`byte × scale`. That 8-bit quantization makes the file about four times smaller than 32-bit floats, and it is what the
board computes with: there is no conversion step at load time. The engine reads the file where it lies in PSRAM.

On a Nano ESP32 the model uses about 382 KB of PSRAM for its weights and 102,400 bytes for its attention cache (keys
and values for 4 layers × 128 positions, also stored as signed bytes with a scale), out of 8 MB. It never touches the
program's 64 KiB arena, apart from about 20 KB of fixed buffers allocated once.

## The engine is Java

`JlmLanguageModel` in `juno-api` is the engine, written in Java and compiled by Juno like any other program. Java
decides everything: it parses the header and the alphabet, normalizes the question (lower case, punctuation as
spaces), runs the layer and head loops, picks or samples each character, applies the `I do not know.` fallback and
streams the answer. Only the loops over float arrays are small native kernels: an int8 matrix-row product, one fused
attention call per head, RMS norm, residual add, ReLU and `expf`.

That split matters on a microcontroller. Written as plain Java loops, the engine was about 1.8 times slower than a C
engine, because each loop iteration and each float operation carried a call on the ESP32's Xtensa core. With the
kernels it is a little faster than the C engine it replaced, measured on the board with the serial chat's self-tests:

| Question | Answer length | Time on the Nano ESP32 |
|---|---|---|
| Hello | 6 | 301 ms |
| How much PSRAM has the Nano ESP32? | 5 | 995 ms |
| What is Juno? | 63 | 2,165 ms |
| Which boards does Juno support? | 79 | 3,312 ms |

That is roughly 30 ms per character, question included.

## How it is trained

`juno-lm` trains the model on the build machine, in pure Java with hand-written backpropagation and no ML library. The
training data is plain text in `juno-ai/juno-lm/src/main/resources/lm/facts`: one fact per line, several wordings of a
question and the answer:

```text
What is Juno? | Tell me about Juno | What does Juno do? => Juno compiles Java programs into firmware for microcontrollers.
```

Board specifications live in a table that templates expand into many questions, and `unknown.txt` teaches the model to
answer `I do not know.` to questions outside Juno. Training runs 16,000 steps of 32 examples with Adam (peak learning
rate 0.003, warm-up, cosine decay), dropout 0.1 and weight decay 0.01, then quantizes the weights and writes
`JUNO.BIN`. It takes about 40 minutes on an 8-core laptop:

```bash
./mvnw -pl juno-ai/juno-lm -Plm-model process-classes -Dlm.steps=16000
```

The build fails if the model answers fewer than 95% of the trained questions exactly. A separate held-out set of
wordings that are never trained measures how well it copes with new phrasings. The tracked model, trained on 477
facts and 1,892 question wordings, scores:

| Set | Exact answers |
|---|---|
| Trained wordings | 1,885 of 1,892 (99.6%) |
| Held-out wordings, never trained | 26 of 48 (54.2%) |

Training length mattered more than size: the same data with 8,000 steps reached only 31.3% on the held-out set, and
16,000 steps reached 54.2%. An earlier experiment with six layers instead of four did not score better, while each
character costs more time on the board.

## What to expect

JLM1 is small, so it behaves like a well-read index rather than a chat assistant. It answers best when a question is
close to one it was trained on, it remembers nothing between questions, and a question is cut at 72 characters. When
it is unsure, it says so; when a new wording lands near the wrong fact, it can still answer confidently and wrongly,
which the held-out numbers above measure. A new model only needs new facts and a training run: pass the new
`JUNO.BIN` to `-Djuno.lm.model` and flash again, or copy it onto an SD card and `begin(card, path)` loads it without
reflashing.

The network design follows WollyMonjac's [TINY 2.1](https://github.com/WollyMonjac/Tiny-2.1) (MIT), which runs a model
of the same shape under DOS. Juno's trainer, the JLM1 format and the board engine are its own, and `JlmBuilder convert`
turns a TINY 2.1 model file into JLM1.
