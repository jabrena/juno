# ELEGOO 2.8" TFT Touch Screen Shield

[`TftTouchShield`](../juno/src/main/java/io/github/jabrena/juno/api/tft/TftTouchShield.java)
drives the ELEGOO 2.8" TFT touch screen shield for UNO ("Pantalla Táctil TFT de 2,8 pulgadas"): a
240x320 ILI9341 color display on an 8-bit parallel bus, a 4-wire resistive touch panel, and a
microSD socket.

Like the [LCD Keypad Shield](LCD-KEYPAD-SHIELD.md), it needs no new compiler intrinsic and no
extra Arduino library: every operation is built from
[`Gpio`](../juno/src/main/java/io/github/jabrena/juno/api/io/Gpio.java) pin operations and
[`Delay`](../juno/src/main/java/io/github/jabrena/juno/api/Delay.java). The trade-off is speed:
each bus byte costs several `digitalWrite` calls, so a full-screen clear takes on the order of a
second and a line of text tens of milliseconds. It suits status screens and simple touch UIs, not
animation. Solid fills in colors whose two RGB565 bytes are equal (`BLACK`, `WHITE`) are the
fastest, because the driver only rewrites data pins whose level changes.

## Wiring

Seat the shield directly on the UNO R4 WiFi. The driver assumes the shield's standard pinout, as
used by ELEGOO's `Elegoo_TFTLCD` and `TouchScreen` libraries:

| Function | Pin |
|---|---|
| LCD `RD` / `WR` / `RS` / `CS` / `RESET` | `A0` / `A1` / `A2` / `A3` / `A4` |
| LCD data bits 0-1 | digital 8-9 |
| LCD data bits 2-7 | digital 2-7 |
| Touch `Y+` / `X-` (analog) | `A3` / `A2` (shared with LCD `CS` / `RS`) |
| Touch `Y-` / `X+` | digital 9 / 8 (shared with LCD data bits 1 / 0) |
| SD `CS` / `MOSI` / `MISO` / `SCK` | digital 10 / 11 / 12 / 13 |

The touch panel shares pins with the display bus, so `readTouch()` temporarily reconfigures them
and restores the bus before it returns. The SD socket shares nothing with the display, so
[`SdCard.begin()`](STORAGE.md) (chip select on D10) works unchanged alongside it.

## API

```java
import io.github.jabrena.juno.api.tft.TftTouchShield;

TftTouchShield.begin();                                // pins + reset + ILI9341 init, portrait
TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);  // PORTRAIT, LANDSCAPE, *_FLIPPED
int w = TftTouchShield.width();                        // 240 or 320, depending on rotation
int h = TftTouchShield.height();

TftTouchShield.fillScreen(TftTouchShield.BLACK);
TftTouchShield.fillRect(10, 10, 100, 40, TftTouchShield.color(255, 128, 0)); // RGB888 -> RGB565
TftTouchShield.drawRect(10, 10, 100, 40, TftTouchShield.WHITE);
TftTouchShield.drawHorizontalLine(0, 60, w, TftTouchShield.GRAY);
TftTouchShield.drawVerticalLine(160, 0, h, TftTouchShield.GRAY);
TftTouchShield.drawPixel(5, 5, TftTouchShield.RED);
TftTouchShield.fillCircle(200, 120, 20, TftTouchShield.YELLOW);
TftTouchShield.drawCircle(200, 120, 24, TftTouchShield.WHITE);

if (TftTouchShield.beginPixels(0, 0, 20, 20)) {        // stream a custom 20x20 bitmap
    for (int i = 0; i < 20 * 20; i++) {
        TftTouchShield.pushPixel(TftTouchShield.GREEN); // row by row, from the top left
    }
}

TftTouchShield.setCursor(10, 80);                      // pixel coordinates of the next character
TftTouchShield.setTextSize(2);                         // 1 = 6x8-pixel cells, 2 = 12x16, ...
TftTouchShield.setTextColor(TftTouchShield.YELLOW, TftTouchShield.BLACK);
TftTouchShield.print("Status: ");
TftTouchShield.print(42);
TftTouchShield.println("");                            // '\n' and the right edge also wrap
TftTouchShield.print(bytes, length);                   // ASCII text from a byte[] buffer

if (TftTouchShield.readTouch()) {                      // true while the panel is pressed
    int x = TftTouchShield.touchX();                   // screen coordinates, current rotation
    int y = TftTouchShield.touchY();
}
```

Text uses the same 5x7 ASCII font as the LED matrix
([`LedMatrixFontAscii`](../juno/src/main/java/io/github/jabrena/juno/api/led/LedMatrixFontAscii.java)).
Each character paints its whole cell in the background color, so printing over older text
replaces it; pad shorter strings with spaces to erase leftovers.

### Touch calibration

Touch positions are mapped with ELEGOO's published calibration range for this panel (raw X
120-900, raw Y 70-920), with the axis directions confirmed on real hardware (raw Y runs opposite to
the display's y). If a panel reports offset or mirrored positions, run `TftTouchPaint`, touch near
each edge, read the logged `rawX`/`rawY` values, and pass the raw readings for portrait x = 0,
x = 239, y = 0, and y = 319 to:

```java
TftTouchShield.calibrateTouch(rawLeft, rawRight, rawTop, rawBottom);
```

A "left" reading larger than the "right" one mirrors that axis. `touchPressure()` reports the
pressure estimate from the last read; presses between 10 and 1000 count as touches.

## Examples

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.TftTouchPaint
```

[`TftTouchPaint`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/TftTouchPaint.java)
is a minimal finger-paint program in `PORTRAIT_FLIPPED` rotation: pick a color from the palette
along the top, then draw. Each touch is logged to Serial with screen and raw coordinates, for
calibration. Confirmed working on a real UNO R4 WiFi with this shield.

```bash
./mvnw -f juno-examples/pom.xml juno:install-deps   # SdFat, once
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.net.WifiStatusTFT
```

[`WifiStatusTFT`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/WifiStatusTFT.java)
is the TFT version of [`WifiStatusSD`](STORAGE.md): it loads `wifi.ssid`/`wifi.password` from
`application.properties` on the shield's own microSD card, connects, and shows the connection
state, SSID, and `Wifi.status()` value in landscape. If loading or connecting fails, tap the
screen to reload the card and retry.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.net.weather.WeatherTFT
```

[`WeatherTFT`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/weather/WeatherTFT.java)
is a desk weather station in the style of the small "weather clock" cubes. After connecting with
the SD-card credentials, it geolocates the board's public IP with [ipinfo.io](https://ipinfo.io),
passes the returned coordinates to [Open-Meteo](https://open-meteo.com), and shows the city, wind
speed, a large seven-segment clock, the condition with an icon, the temperature, the date and
weekday, and temperature/humidity bars. The clock is set from the HTTPS `Date` response header plus
Open-Meteo's `utc_offset_seconds` and kept running with `Clock.millis()`. Weather refreshes every
ten minutes, or immediately when the screen is tapped. The footer shows the public IP ipinfo.io saw
and the coordinates sent to Open-Meteo. The first HTTPS request right after WiFi connects can fail,
so the location lookup waits two seconds and then retries every five (the status line counts the
attempts). Confirmed running on a real UNO R4 WiFi with this shield.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Minesweeper
```

[`Minesweeper`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Minesweeper.java) is
the classic game on a 10x11 field with 16 mines, in `PORTRAIT_FLIPPED` rotation: tap a covered cell
to open it (the first tap is always safe), press and hold to plant or remove a flag, tap an opened
number whose neighbouring flags match it to open the rest of its neighbours, and tap the face
button to start over. The header shows the mines left to flag and the elapsed seconds.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Chess
```

[`Chess`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Chess.java) plays you
(White) against a small built-in engine (Black). Pieces are 20x20 bitmaps shaped like the Unicode
chess symbols, streamed with `beginPixels`/`pushPixel`. Tap one of your pieces to see its legal
destinations, then tap one to move; `UNDO` takes back your last move together with the engine's reply (repeatable
back to the start of the game), and `NEW` starts over. The rules are complete apart from draw
claims: moves into check are rejected, check, checkmate and stalemate are detected, and castling,
en passant and promotion (always to a queen) work. The fifty-move rule, threefold repetition and
insufficient material are not detected. The engine is a 3-ply negamax search with alpha-beta
pruning and a material plus piece-placement evaluation; each of its moves and its thinking time are
logged to Serial. Its move generator matches the standard perft reference counts (start position,
"Kiwipete", and endgame positions), apart from the under-promotions it deliberately omits.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.TicTacToe
```

[`TicTacToe`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/TicTacToe.java) is the
"vanishing" variant of the handheld electronic games: each player owns at most three marks, and
placing a fourth removes that player's oldest one, which is drawn dimmed while it is next to go, so
there are no draws. You play X against the computer's O (a 6-ply alpha-beta search); `1P` switches to
two players sharing the screen, `NEW` starts a new round, and the footer keeps the score.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Simon
```

[`Simon`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Simon.java) is the classic
memory game: four colored pads around a central hub light up in a growing sequence that you repeat
by tapping them. Each round adds a step and plays a little faster; a wrong pad (the right one then
blinks) or five seconds without a tap ends the game. Tap the hub to start; it shows the score, and the
header the best score since power-up. The shield has no speaker, so it is lights only.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Blackjack
```

[`Blackjack`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Blackjack.java) plays
against the dealer on a green felt table: set your bet with `-`/`+` and `DEAL`, then `HIT`, `STAND`
or `DBL` (double down on your first two cards). The dealer stands on all 17s, blackjack pays 3:2,
and ties push; splitting and insurance are not offered. You start with 100 chips (refilled when you
run out), and a single 52-card deck is reshuffled when fewer than 15 cards remain. Suit symbols are
9x9 bitmaps, since the display font has no ♠♥♦♣ glyphs.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Solitaire
```

[`Solitaire`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Solitaire.java) — Klondike
(draw one): tap a card to pick it up (a tableau card brings the cards on top of it along), then tap
where it should go; tapping a picked-up card again sends it to its foundation. Tap the stock to turn
a card, and the empty stock to turn the waste back over. Once every card is face up the rest plays
itself. Scoring follows the usual Windows rules, and `NEW` deals again. The 32x44 cards reuse
`Blackjack`'s suit bitmaps.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Yahtzee
```

[`Yahtzee`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Yahtzee.java) — solitaire
Yahtzee over 13 rounds: `ROLL` up to three times, tapping dice between rolls to hold them, then tap a
box on the scorecard, which previews what the dice would score in every open box. Includes the
35-point upper bonus, 100-point Yahtzee bonuses and the official joker rules; the header keeps the
best score since power-up.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.ConnectFour
```

[`ConnectFour`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/ConnectFour.java) — Connect Four against the computer: tap a column to drop a red disc; the computer (yellow) answers with a 4-ply alpha-beta search that tries central columns first. The loser of a game moves first in the next one, and the footer keeps the score.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Checkers
```

[`Checkers`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Checkers.java) — English draughts against the computer: men move diagonally forward, kings (gold crown) diagonally both ways; captures are mandatory and multi-jumps continue with the same piece (tap each landing square). The computer runs a 5-ply alpha-beta minimax. Its move generator matches the standard English draughts perft counts to depth 7.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Game2048
```

[`Game2048`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Game2048.java) — 2048: swipe to slide the tiles (or tap near an edge of the grid, for panels that report swipes poorly); equal tiles merge. Reach 2048 to win and keep going; the header shows the score and best score.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Battleship
```

[`Battleship`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Battleship.java) — Battleship against the computer on 10x10 grids with randomly placed fleets (5, 4, 3, 3, 2): tap the large enemy grid to fire, while your own fleet and the computer's shots show in the small grid below. The computer hunts on a checkerboard pattern and follows lines of hits until a ship sinks.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Snake
```

[`Snake`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Snake.java) — Snake that the CPU plays by itself (shortest path to the food by breadth-first search, taken only when a flood fill shows enough room left; it averages over 100 food in simulation) and restarts after each crash. Tap the header to take over (`YOU`) and back (`CPU`). When playing yourself, tap above/below the head (or left/right of it, when moving vertically) to turn; each bite grows the snake and speeds it up. Only the head, the previous head and the tail are redrawn each step.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Tetris
```

[`Tetris`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Tetris.java) — Tetris: a 10x20 well with next-piece preview, score, lines and levels; the `<`/`>` (auto-repeat), `ROT` and `DROP` buttons sit beside the well, and tapping the well also rotates. Each frame only the changed cells are redrawn.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.Pong
```

[`Pong`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/Pong.java) — Pong against the computer in portrait: your paddle follows your finger, the computer's paddle chases the ball with a speed limit, and the hit position sets the bounce angle. First to 7 wins.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.LunarLander
```

[`LunarLander`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/LunarLander.java) —
Lunar Lander: hold `<`/`>` to rotate in 15-degree steps and `BURN` to fire the engine. Set down
upright and slowly (the header's speeds turn red when too fast) with both feet on a yellow pad to
score 50 times its multiplier (x2, x3, x5; narrower pads pay more). A crash costs 250 fuel, fuel
carries over between descents, and the game ends when it runs out. The lander is a 15x15 bitmap
rotated per pixel into a streamed 17x17 block, since the display has no line primitive.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.FlappyBird
```

[`FlappyBird`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/FlappyBird.java) — A Flappy Bird style game: tap to flap through the pipe gaps. Scrolling pipes only repaint their leading and trailing edge strips each frame, and the bird is a streamed 17x12 sprite.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.tft.GameOfLife
```

[`GameOfLife`](../juno-examples/src/main/java/io/github/jabrena/juno/api/tft/GameOfLife.java) — Conway's
Game of Life on a 40x46 grid whose edges wrap around. `RUN`/`STOP`, `STEP`, `RAND` and `CLR` control
the simulation; while stopped, drag on the grid to draw cells (a stroke starting on a live cell erases).
Only cells that change are redrawn, and the header shows the generation and population.
