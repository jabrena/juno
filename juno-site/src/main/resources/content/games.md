---
title: "TFT Games"
description: "The playable games built for the ELEGOO TFT touch shield, with CPU-played demos."
layout: page
---

Juno ships 14 games (and a paint demo) for the ELEGOO 2.8" TFT touch screen shield on the Arduino UNO R4 WiFi. Every one is plain Java in [`juno-examples`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/games/) (the paint demo lives in [`api/tft`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/api/tft/)), compiled ahead of time by Juno to Cortex-M4 assembly, with no JVM or interpreter on the board: just the Java subset described in [FEATURES.md](/features) and the `TftTouchShield` API described in [TFT-TOUCH-SHIELD.md](/tft-touch-shield).

## How to play

Set up `arduino-cli` and the UNO R4 core as described in [ARDUINO.md](/arduino), plug the shield onto the board, and install the reactor once so the example module can resolve the Juno plugin:

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

- [Arcade](#arcade): [Pac-Man](#pac-man), [Missile Command](#missile-command), [Tempest](#tempest), [Star Wars](#star-wars), [The Empire Strikes Back](#the-empire-strikes-back), [Red Baron](#red-baron), [Star Trek](#star-trek), [Space Paranoids](#space-paranoids), [Lunar Lander](#lunar-lander)
- [Board and strategy](#board-and-strategy): [Chess](#chess), [Battleship](#battleship)
- [Cards and casino](#cards-and-casino): [Blackjack](#blackjack), [Texas Hold'em](#texas-holdem)

## Arcade

Real-time games driven by touch.

### Pac-Man

<img src="/images/games/pacman-cpu.gif" alt="Pac-Man on the TFT shield, played by the CPU" width="240">

The cover animates two ghosts chasing Pac-Man across the screen, an energizer turning the tables so he chases them back, and the title zooming into a soft drop shadow. Then choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot that favors dots and energizers, flees an active ghost nearby and hunts down a frightened one instead, missing a turn now and then like a person would; tap the header during a life to switch between the two. The arcade's 28x31-tile maze with all 244 dots and four energizers. Touch and hold beside, above or below Pac-Man to steer; he turns at the next junction where it fits. The ghosts follow the arcade's targeting rules (Blinky chases, Pinky ambushes, Inky mirrors, Clyde wanders) with scatter and chase phases. Three lives, an extra one at 10,000 points. Pac-Man targets the **Arduino UNO Q** (`@Board(ArduinoUnoQ.class)`).

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.pacman.PacMan
```

### Missile Command

<img src="/images/games/missile-command-cpu.gif" alt="Missile Command on the TFT shield, a full game played by the CPU" width="240">

The cover opens with two warheads streaking toward the cities, an interceptor climbing to meet them, three color-cycling fireballs, and the title zooming into a hot orange shadow. Then choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot that intercepts whichever warhead is closest to the ground, leading its aim to meet it, hesitating and missing now and then, and never wasting a shot on a warhead another interceptor is already heading for; tap the header during a wave to switch between the two. Warheads rain down on six cities and three bases; tap the sky to launch an interceptor that detonates where you tapped. Fireballs chain-react, and from wave 2 some warheads split halfway down. The final score remains on screen until a tap returns to the animated title. Missile Command targets the **Arduino UNO Q** (`@Board(ArduinoUnoQ.class)`).

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.missilecommand.MissileCommand
```

### Tempest

<img src="/images/games/tempest-cpu.gif" alt="Tempest on the TFT shield, flown by the CPU" width="240">

A vector-style tube seen end-on. It opens with cyan, magenta, and yellow rings racing through the tube, the claw launching from the vanishing point and orbiting the rim, and the multicolor title zooming into place. After the title, choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot that lets flippers climb into view before sweeping onto whichever is closest to the rim, occasionally hesitating or misjudging its lane, and firing the Superzapper when swarmed; tap the header during the game to switch between the two. Touch near the rim to move your claw around it (it fires while you hold), and tap the center for the once-per-level Superzapper. Each level's tube grows out of its vanishing point under a title card naming its shape (circle, square, star, clover); clearing it shows a bonus card, then the claw rides down its lane as the rings rush in after it, like the arcade's warp. The Superzapper sweeps a white ring through the tube, a lost claw shows how many are left, and at game over the tube collapses before the final score; a tap then returns to the animated title. Tempest uses nothing board-specific, so it declares both boards the shield fits (`@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})`); pick one with `-Djuno.board`:

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

<img src="/images/games/star-wars-cpu.gif" alt="Star Wars on the TFT shield, eight waves flown by the CPU" width="320">

After Atari's 1983 vector arcade game, in landscape. It opens like the film: "A long time ago in a galaxy far, far away....", the logo receding into the distance, and a Star Destroyer sliding overhead in pursuit of a rebel ship above a planet, before the title settles into place; every game over returns there. After the title, choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot that lets targets close in, sweeps its crosshair onto them, misses now and then, and weaves past the trench catwalks; tap the header during the game to switch between the two. From an X-wing's cockpit, fight TIE fighters in space (Darth Vader's can only be driven off), shoot the tops off the laser towers on the Death Star's surface, then fly the trench and hit the exhaust port. Drag to aim and tap to fire; the four cannons converge where you tapped. In the trench the X-wing follows the crosshair, so you steer around catwalks as you aim. Six shields, one back per destroyed Death Star, and a bonus if the only shot you fire in the trench is the one that hits the port. Star Wars targets the **Arduino UNO Q** (`@Board(ArduinoUnoQ.class)`).

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.starwars.StarWars
```

### The Empire Strikes Back

<img src="/images/games/empire-strikes-back-cpu.gif" alt="The Empire Strikes Back on the TFT shield, three minutes flown by the CPU" width="320">

After Atari's 1985 vector arcade game, in landscape. It opens like the film: snow falls on Hoth as an Imperial probe streaks down and lands, two AT-ATs stride in from the distance, and the title assembles letter by letter; every game over returns there. After the title, choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot that lets targets close in before sweeping the crosshair onto them (and is sometimes slow to spot one until it is right in front), dodges asteroids and fireballs, and misses now and then like a person would; tap the header during the game to switch between the two. The Empire Strikes Back targets the **Arduino UNO Q** (`@Board(ArduinoUnoQ.class)`). Fly a snowspeeder over Hoth and destroy the hovering probe droids; bring down the AT-AT walkers striding across your path, whose armor only gives way to three hits on the head (the smaller AT-STs fall to one); then take the Millennium Falcon through a field of tumbling asteroids while TIE fighters attack. Drag to steer and aim (your craft follows the crosshair) and tap to fire the twin lasers; enemy fireballs home in slowly, so shoot them or move quickly. Six shields, one back per wave, and every round flown without losing a shield earns a letter of JEDI, with a bonus for the whole word.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.empirestrikesback.EmpireStrikesBack
```

### Red Baron

<img src="/images/games/red-baron-cpu.gif" alt="Red Baron on the TFT shield, three waves flown by the CPU" width="320">

After Atari's 1981 vector arcade game, in landscape. It opens in the cockpit: "CONTACT!", the propeller spins up into a blur, the plane races down the runway and pulls up, and the title is spelled out letter by letter; every game over returns there. After the title, choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot chase the nearest target, break away from enemy fire and climb over the pyramids, with an aim that wanders enough to miss now and then; tap the header during the game to switch between the two. A First World War dogfight from the cockpit of a biplane, over a landscape ringed by mountains. The whole screen is a joystick: press and drag from that point to bank and turn (the horizon rolls as you do) or to climb and dive, and tap to fire both machine guns at whatever is in the sight. Each wave has a dogfight, where enemy biplanes make firing passes and a blimp drifts by for bonus points, and a ground attack, where you fly low to strafe hangars and flak guns without crashing into the pyramids. Three planes, one more every 20,000 points. Red Baron targets the **Arduino UNO Q** (`@Board(ArduinoUnoQ.class)`): with the TFT shield on the UNO Q, the same command builds it for the board's STM32U585 microcontroller and uploads it.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.redbaron.RedBaron
```

### Star Trek

<img src="/images/games/star-trek-cpu.gif" alt="Star Trek on the TFT shield, a full game played by the CPU" width="320">

After Sega's 1982 vector arcade game, in landscape. It opens with a starfield rushing past as the Enterprise goes to warp, then the title zooms in with *Strategic Operations Simulator* typing out beneath it; every game over returns there. Command the Enterprise through sectors of Klingon battlecruisers. The left of the screen is the tactical scanner, a top-down view centered on the Enterprise; the top right is the view ahead from the bridge. Hold a finger on the scanner to turn towards it and run the impulse engines, and press the buttons to fire phasers (a short-range beam straight ahead that recharges quickly), photon torpedoes (few, but their blast destroys everything around them) or warp away out of trouble. Klingons make attack passes and fire torpedoes that drain your shields, and some go after the starbase: dock with it once per sector to restore shields, torpedoes and warps before they destroy it. Anti-matter saucers join from sector 2, and every fourth sector the probe Nomad lays mines. Clear a sector of Klingons (and Nomad) for a bonus; the game ends when the shields reach zero. After the title, choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot hunt Klingons, dock when its shields run low and use phasers, photon torpedoes and warp on its own; tap the header during the game to switch between the two. Star Trek targets the **Arduino UNO Q** (`@Board(ArduinoUnoQ.class)`).

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.startrek.StarTrek
```

### Space Paranoids

<img src="/images/games/space-paranoids-cpu.gif" alt="Space Paranoids on the TFT shield, three minutes driven by the CPU" width="320">

After the arcade game from TRON, in landscape. It opens like the film: a laser scan line digitizes the grid, which then rushes past as a hunter spins in, and the title materializes letter by letter; every game over returns there. After the title, choose the pilot: **HUMAN** to play yourself, or **CPU** to watch an autopilot hunt along the shortest path, head for an energy pool when its shield runs low and shoot what it sees, with an aim that wanders enough to miss now and then; tap the header during the game to switch between the two. Space Paranoids targets the **Arduino UNO Q** (`@Board(ArduinoUnoQ.class)`). Drive a tank through a wireframe maze and destroy every flying hunter before the sector's timer runs out, while enemy tanks and gun turrets fire back. Hold `<`/`>` to turn, `^`/`v` to drive and `FIRE` to shoot (tapping the view fires too); the radar between the buttons shows the maze from above. Hits drain your shield and green energy pools recharge it. Three lives, one more every 10,000 points, and every sector is a new maze with more enemies. The walls are ray cast with hidden lines removed.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.spaceparanoids.SpaceParanoids
```

### Lunar Lander

<img src="/images/games/lunar-lander-cpu.gif" alt="Lunar Lander on the TFT shield, flown by the CPU" width="240">

The cover opens into a pilot selection: choose **HUMAN** to hold `<`/`>` to rotate and `BURN` to thrust, or **CPU** to watch the autopilot select a reachable pad and manage the descent. Tap the header during a descent to switch control. Land upright and slowly with both feet on a yellow pad to score 50 times its multiplier (x2, x3, x5). Crashes cost fuel, and the game ends when the fuel runs out.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.games.lunarlander.LunarLander
```

## Board and strategy

Turn-based games against a computer opponent that searches ahead.

### Chess

<img src="/images/games/chess-cpu.gif" alt="Chess on the TFT shield, played by the CPU" width="240">

An animated board assembles beneath a centered, pulsing title, then loops after 60 seconds until tapped. Choose **HUMAN** to play White against the 3-ply engine, or **CPU** to watch the same engine play both colors; tap the header during a human game to hand White to the CPU. Tap a piece to see its legal moves, then a destination; `UNDO` takes back a move pair and `NEW` starts over. Castling, en passant, promotion, check, checkmate and stalemate are handled. A finished match shows its result briefly, then returns to the animated title. The full three-minute GIF explicitly shows the title, CPU selection, a complete match and the return to the title. Chess supports both UNO Q and UNO R4 WiFi; select the connected board explicitly.

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

<img src="/images/games/battleship-cpu.gif" alt="Battleship on the TFT shield, played by the CPU" width="240">

The cover opens with a radar sweep, incoming shells and a centered, color-cycling title, repeating after the 60-second attract timeout. Choose **HUMAN** to fire on the large enemy grid, or **CPU** to watch two commanders exchange salvos; the small grid shows your fleet and the opponent's shots. The targeting AI hunts on a checkerboard, follows adjacent hits and extends a discovered line until the ship sinks. Destroying either random fleet ends the game and returns to the title. The three-minute GIF shows the title, CPU selection, a complete battle and the return to the title. Battleship supports both UNO Q and UNO R4 WiFi; select the connected board explicitly.

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

<img src="/images/games/blackjack-cpu.gif" alt="Blackjack on the TFT shield, played by the CPU" width="240">

Cards fan across green felt while chips orbit the centered, animated title; the cover repeats after 60 seconds until tapped. Choose **HUMAN** to set the bet with `-`/`+` and `DEAL`, then use `HIT`, `STAND` or `DBL`, or choose **CPU** to watch an automated player hit below 17 and double suitable 9–11 totals. The dealer stands on all 17s and blackjack pays 3:2. You start with 100 chips; losing the bankroll ends the session and returns to the title. The four-minute GIF shows the title, CPU selection, a complete session and the return to the title. Blackjack supports both UNO Q and UNO R4 WiFi; select the connected board explicitly.

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

<img src="/images/games/texas-holdem-cpu.gif" alt="Texas Hold'em on the TFT shield, played by the CPU" width="240">

The animated cover fans three cards over a moving chip rack beneath centered `TEXAS HOLD'EM` and `NO LIMIT` lines, and loops on the 60-second timeout. Choose **HUMAN** to take the fourth seat or **CPU** to let all four seats play automatically. No-limit Hold'em starts everyone with 1000 chips and doubles the blinds every eight hands. Use `FOLD`, `CHECK`/`CALL`, or set an amount with `-`/`+`/`ALL` and `BET`/`RAISE`; side pots are handled. Ann plays tight, Bob loose and Cal aggressive, while every CPU estimates its equity by repeatedly dealing the unknown cards. Going broke or winning every chip ends the tournament and returns to the cover. The six-minute, forty-second GIF runs through the title, CPU selection and an entire tournament, ending only after the title returns. Texas Hold'em supports both UNO Q and UNO R4 WiFi; select the connected board explicitly.

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
  `EmpireStrikesBackTest`, `RedBaronTest`, `StarTrekTest`, `SpaceParanoidsTest` and
  `LunarLanderTest` check rules and computer players: the poker hand ranking against a brute-force
  reference, no chip lost across all-ins and side pots, and that autopilots clear Missile Command
  waves, Tempest levels, Space Paranoids and Star Trek sectors, whole Red Baron and
  Empire Strikes Back waves, and nearly every dot of a Pac-Man maze.

This runs the games' Java on the JVM, not the code Juno generates for the board.

## Compiling with the real Arduino toolchain in Docker

`ArduinoCliCompileTest` does what `juno:verify` does for every game — Juno generates the sketch,
then `arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi` builds and links it with the real
UNO R4 core — inside a Docker container started with [Testcontainers](https://testcontainers.com),
so you need Docker but no local Arduino installation. It fails if a game stops compiling or no
longer fits the board's flash or RAM, and prints each game's usage. It is opt-in:

```bash
./mvnw install -DskipTests
./mvnw -f juno-examples/pom.xml -Parduino-cli test
```

The first run builds the image from
[`juno-examples/src/test/docker/arduino-cli/Dockerfile`](https://github.com/jabrena/juno/tree/main/juno-examples/src/test/docker/arduino-cli/Dockerfile)
(`arduino-cli` plus the `arduino:renesas_uno` core, several hundred MB) and Docker caches it for
later runs. To use an image you built or pulled yourself, add
`-Djuno.arduinoCliImage=<image>`. Without Docker the test is skipped. If your network blocks
Docker Hub, also set `TESTCONTAINERS_RYUK_DISABLED=true`, since Testcontainers otherwise pulls its
cleanup container from there.

What neither test covers is the program running on the chip itself: timing, touch feel and the
panel's real colors still need the board and `juno:upload`.
