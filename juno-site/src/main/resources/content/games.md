---
title: "TFT Games"
description: "The playable games built for the ELEGOO TFT touch shield, with CPU-played demos."
layout: page
---

Juno ships 14 games (and a paint demo) for the ELEGOO 2.8" TFT touch screen shield on the Arduino UNO R4 WiFi. Every one is plain Java in [`juno-examples`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/games/) (the paint demo lives in [`api/tft`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/api/tft/)), compiled ahead of time by Juno to Cortex-M4 assembly, with no JVM or interpreter on the board: just the Java subset described in [FEATURES.md](../features) and the `TftTouchShield` API described in [TFT-TOUCH-SHIELD.md](../tft-touch-shield).

## How to play

Set up `arduino-cli` and the UNO R4 core as described in [ARDUINO.md](../arduino), plug the shield onto the board, and install the reactor once so the example module can resolve the Juno plugin:

```bash
./mvnw install
```

Then flash a game by its class name. Every game in this page lists its own command; they all follow this pattern:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.<Game>
```

`juno:upload` finds the board by itself, or takes `-Djuno.port=<PORT>`. To check that a game builds without touching any hardware, use `juno:verify` instead of `juno:upload`.

Most games target one board, so that single command is all they need. A few, like Tempest, declare
`@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})` because they use nothing board-specific — for
those, add `-Djuno.board=<id>` (`arduino-uno-q` or `arduino-uno-r4-wifi`) to say which one to build for; omitting it
when more than one board is declared fails with the list of choices instead of guessing.

The screenshots were rendered on a desktop by running each game's unmodified code, including the real `TftTouchShield` driver and its font, against an emulation of the shield's ILI9341 display controller (see [Testing without the board](#testing-without-the-board)). The real panel's colors vary slightly.

## Contents

- [Arcade](#arcade): [Pac-Man](#pac-man), [Missile Command](#missile-command), [Tempest](#tempest), [Star Wars](#star-wars), [The Empire Strikes Back](#the-empire-strikes-back), [Red Baron](#red-baron), [Star Trek](#star-trek), [Space Paranoids](#space-paranoids), [DOOM](#doom), [Lunar Lander](#lunar-lander)
- [Board and strategy](#board-and-strategy): [Chess](#chess), [Battleship](#battleship)
- [Cards and casino](#cards-and-casino): [Blackjack](#blackjack), [Texas Hold'em](#texas-holdem)

## Arcade

Real-time games driven by touch.

### Pac-Man

<img src="../images/games/pacman-cpu.gif" alt="Pac-Man on the TFT shield, played by the CPU" width="240">

Eat every dot in the maze while four ghosts chase you. Play it yourself or watch the CPU. Runs on the Arduino UNO Q.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.pacman.PacMan
```

### Missile Command

<img src="../images/games/missile-command-cpu.gif" alt="Missile Command on the TFT shield, a full game played by the CPU" width="240">

Defend six cities from falling warheads by tapping the sky to launch interceptors. Play it yourself or watch the CPU. Runs on the Arduino UNO Q.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.missilecommand.MissileCommand
```

### Tempest

<img src="../images/games/tempest-cpu.gif" alt="Tempest on the TFT shield, flown by the CPU" width="240">

Move a claw around the rim of a tube and shoot the enemies climbing up it. Play it yourself or watch the CPU. Runs on both the UNO Q and the UNO R4 WiFi; pick one with `-Djuno.board`:

```bash
# Arduino UNO Q
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.tempest.Tempest \
  -Djuno.board=arduino-uno-q
```

```bash
# Arduino UNO R4 WiFi
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.tempest.Tempest \
  -Djuno.board=arduino-uno-r4-wifi
```

### Star Wars

<img src="../images/games/star-wars-cpu.gif" alt="Star Wars on the TFT shield, eight waves flown by the CPU" width="320">

Fly an X-wing against TIE fighters, then down the Death Star trench to hit the exhaust port. Play it yourself or watch the CPU. Runs on the Arduino UNO Q.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.starwars.StarWars
```

### The Empire Strikes Back

<img src="../images/games/empire-strikes-back-cpu.gif" alt="The Empire Strikes Back on the TFT shield, three minutes flown by the CPU" width="320">

Fly a snowspeeder over Hoth against probe droids and AT-AT walkers, then the Millennium Falcon through an asteroid field. Play it yourself or watch the CPU. Runs on the Arduino UNO Q.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.empirestrikesback.EmpireStrikesBack
```

### Red Baron

<img src="../images/games/red-baron-cpu.gif" alt="Red Baron on the TFT shield, three waves flown by the CPU" width="320">

Dogfight enemy biplanes and strafe ground targets from the cockpit of a First World War plane. Play it yourself or watch the CPU. Runs on the Arduino UNO Q.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.redbaron.RedBaron
```

### Star Trek

<img src="../images/games/star-trek-cpu.gif" alt="Star Trek on the TFT shield, a full game played by the CPU" width="320">

Command the Enterprise through sectors of Klingon battlecruisers with phasers, photon torpedoes and warp. Play it yourself or watch the CPU. Runs on the Arduino UNO Q.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.startrek.StarTrek
```

### Space Paranoids

<img src="../images/games/space-paranoids-cpu.gif" alt="Space Paranoids on the TFT shield, three minutes driven by the CPU" width="320">

Drive a tank through a wireframe maze and destroy the flying hunters before time runs out. Play it yourself or watch the CPU. Runs on the Arduino UNO Q.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.spaceparanoids.SpaceParanoids
```

### DOOM

<img src="../images/games/doom-cpu.gif" alt="DOOM on the TFT shield: the lava cover, then the CPU autopilot walking a WAD map" width="320">

Wireframe DOOM in landscape, drawn with DOOM's own BSP renderer: walls, doors and monsters from all four episodes. Pick **HUMAN** (touch to turn, walk and fire) or **CPU** (an autopilot that plays like a person and sometimes dies), then choose the episode and skill. Movement keeps DOOM's rules: the marine climbs steps of at most 24 units, slides along walls, and cannot pass the windows and other lines the map marks impassable. Runs on the Arduino UNO Q.

At startup the game scans your own `DOOM1.WAD` on the shield's SD card with `java.io.RandomAccessFile` (see [Storage](/storage)), but does not load a map yet. After the menus it loads the selected episode's first map. Reaching an exit opens a DOOM-style intermission with kills, items, discovered secrets and elapsed time before the following map loads. Finishing E?M8 opens an episode story card and then returns to the title; tapping the title opens the pilot, episode and skill menus again. Difficulty controls the WAD's easy, medium and hard monster and pickup placements. With no card, no file, or too little arena, it plays a small original test map built into flash instead; DOOM's levels are never committed. Copy `DOOM1.WAD` to the card's root, then build with a bigger arena, since the largest supported map tables are held in RAM:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.doom.Doom -Djuno.Xmx=92k
```

### Lunar Lander

<img src="../images/games/lunar-lander-cpu.gif" alt="Lunar Lander on the TFT shield, flown by the CPU" width="240">

Land a spacecraft softly on a pad before the fuel runs out. Play it yourself or watch the CPU.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.lunarlander.LunarLander
```

## Board and strategy

Turn-based games against a computer opponent that searches ahead.

### Chess

<img src="../images/games/chess-cpu.gif" alt="Chess on the TFT shield, played by the CPU" width="240">

Play White against a 3-ply chess engine, or watch it play itself. Runs on both the UNO Q and the UNO R4 WiFi; select the connected board explicitly.

```bash
# Arduino UNO Q
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.chess.Chess \
  -Djuno.board=arduino-uno-q
```

```bash
# Arduino UNO R4 WiFi
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.chess.Chess \
  -Djuno.board=arduino-uno-r4-wifi
```

### Battleship

<img src="../images/games/battleship-cpu.gif" alt="Battleship on the TFT shield, played by the CPU" width="240">

Sink the enemy fleet on a grid before it sinks yours. Play it yourself or watch two CPU commanders. Runs on both the UNO Q and the UNO R4 WiFi; select the connected board explicitly.

```bash
# Arduino UNO Q
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.battleship.Battleship \
  -Djuno.board=arduino-uno-q
```

```bash
# Arduino UNO R4 WiFi
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.battleship.Battleship \
  -Djuno.board=arduino-uno-r4-wifi
```

## Cards and casino

Card games and games of chance, played for chips or credits only.

### Blackjack

<img src="../images/games/blackjack-cpu.gif" alt="Blackjack on the TFT shield, played by the CPU" width="240">

Beat the dealer to 21 with chips. Play it yourself or watch an automated player. Runs on both the UNO Q and the UNO R4 WiFi; select the connected board explicitly.

```bash
# Arduino UNO Q
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.blackjack.Blackjack \
  -Djuno.board=arduino-uno-q
```

```bash
# Arduino UNO R4 WiFi
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.blackjack.Blackjack \
  -Djuno.board=arduino-uno-r4-wifi
```

### Texas Hold'em

<img src="../images/games/texas-holdem-cpu.gif" alt="Texas Hold'em on the TFT shield, played by the CPU" width="240">

No-limit Texas Hold'em against three CPU opponents. Play a seat yourself or watch all four play. Runs on both the UNO Q and the UNO R4 WiFi; select the connected board explicitly.

```bash
# Arduino UNO Q
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.texasholdem.TexasHoldem \
  -Djuno.board=arduino-uno-q
```

```bash
# Arduino UNO R4 WiFi
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.texasholdem.TexasHoldem \
  -Djuno.board=arduino-uno-r4-wifi
```

## Testing without the board

`./mvnw test` plays every game on an emulated shield, on any machine and in CI. The tests in
`juno-examples/src/test/java` replace the handful of `native` hardware classes with plain Java test
doubles, which come first on the test classpath (the `juno:compile`/`juno:upload` goals still use
the real ones):

- `Gpio` decodes the ILI9341 bus that the real `TftTouchShield` driver bit-bangs into a
  framebuffer, and answers the touch panel's analog reads, so tests can tap the screen.
- `Clock` and `Delay` run on simulated time: waiting is instant and each clock reading costs a
  millisecond, so frame loops and time-budgeted searches behave the same on every run.
- `Random` is a seeded `java.util.Random`, and `Serial` discards its output.

On top of these:

- `GameScreenshotTest` plays each game for a few simulated seconds with scripted taps and compares
  the screen with its picture in [`docs/images/games`](https://github.com/jabrena/juno/tree/main/docs/images/games). A mismatch writes the actual
  screen and a diff to `juno-examples/target/screenshots`. After an intended visual change,
  regenerate the pictures with
  `./mvnw -pl juno-examples test -Dtest=GameScreenshotTest -Djuno.updateScreenshots=true`.
- `CardGamesTest`, `PacManTest`, `MissileCommandTest`, `TempestTest`, `StarWarsTest`,
  `EmpireStrikesBackTest`, `RedBaronTest`, `StarTrekTest`, `SpaceParanoidsTest`, `DoomTest` and
  `LunarLanderTest` check rules and computer players: the poker hand ranking against a brute-force
  reference, no chip lost across all-ins and side pots, and that autopilots clear Missile Command
  waves, Tempest levels, Space Paranoids and Star Trek sectors, whole Red Baron and
  Empire Strikes Back waves, and nearly every dot of a Pac-Man maze.

This runs the games' Java on the JVM, not the code Juno generates for the board.

## Compiling with the real Arduino toolchain in Docker

`juno:verify` compiles a game with the real toolchain, and you can run it on any game. The compiler's own
suite, `ArduinoCliCompileIT` in the `juno` module, does the same for small programs that each exercise one
API or shield (GPIO, Serial, Servo, I2C, TFT touch, LCD keypad, ...) once per board they declare —
Juno generates the sketch, then `arduino-cli compile` builds and links it with the real UNO R4 or UNO Q
core — inside a Docker container started with [Testcontainers](https://testcontainers.com),
so you need Docker but no local Arduino installation. It is opt-in:

```bash
./mvnw -f juno/pom.xml -Parduino-cli verify
```

The first run builds the image from
[`juno/src/test/docker/arduino-cli/Dockerfile`](https://github.com/jabrena/juno/tree/main/juno/src/test/docker/arduino-cli/Dockerfile)
(`arduino-cli` plus the `arduino:renesas_uno` and `arduino:zephyr` cores, several hundred MB) and Docker caches it for
later runs. To use an image you built or pulled yourself, add
`-Djuno.arduinoCliImage=<image>`. Without Docker the test is skipped. If your network blocks
Docker Hub, also set `TESTCONTAINERS_RYUK_DISABLED=true`, since Testcontainers otherwise pulls its
cleanup container from there.

What neither test covers is the program running on the chip itself: timing, touch feel and the
panel's real colors still need the board and `juno:upload`.
