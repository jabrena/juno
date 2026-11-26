---
title: "AI"
description: "Juno's on-board decision-model and language-model APIs."
layout: page
---

Juno's AI APIs run models on the board. The compiler links a model from `juno-models` into the firmware, while the
board-side APIs keep inference predictable and suitable for a constrained application loop.

| API | What it provides | Complete example |
|---|---|---|
| [JEV](jev) | Scores caller-owned state features for a caller-owned list of legal actions and returns the best action. | [`ConnectFourWebGame`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/ai/ConnectFourWebGame.java) |
| [JLM](lm) | Runs the JLM1 character-level transformer and streams generated characters from the board. | [`LanguageModelSecretStoreChat`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/ai/LanguageModelSecretStoreChat.java) |
