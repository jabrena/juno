package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;

/**
 * Pong on the ELEGOO 2.8" TFT touch screen shield, you against the computer. Your paddle at the
 * bottom follows your finger; the computer's paddle at the top chases the ball with a speed limit,
 * so it can be beaten. Where the ball meets a paddle sets its new angle, and every paddle hit makes
 * it a little faster. First to {@value #WINNING_SCORE} points wins; tap to serve and to play again.
 *
 * <p>Positions are fixed point (1/16 pixel) for smooth, fractional speeds. Each frame erases and
 * redraws only the ball and the paddles that moved.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Pong {
    private static final int WINNING_SCORE = 7;
    private static final int FRAME_MILLIS = 20;
    private static final int FIXED = 16;

    // Layout (portrait, 240x320).
    private static final int FIELD_TOP = 24;
    private static final int FIELD_BOTTOM = 320;
    private static final int FIELD_WIDTH = 240;
    private static final int PADDLE_WIDTH = 44;
    private static final int PADDLE_HEIGHT = 6;
    private static final int CPU_Y = FIELD_TOP + 8;
    private static final int PLAYER_Y = FIELD_BOTTOM - 14;
    private static final int BALL = 6;
    private static final int CPU_SPEED = 3;

    private static final int START_SPEED_Y = 3 * FIXED;
    private static final int MAX_SPEED_Y = 7 * FIXED;
    private static final int MAX_SPEED_X = 5 * FIXED;

    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int PLAYER_COLOR = TftTouchShield.CYAN;
    private static final int CPU_COLOR = TftTouchShield.ORANGE;

    private static int ballX;
    private static int ballY;
    private static int speedX;
    private static int speedY;
    private static int shownBallX;
    private static int shownBallY;
    private static int playerX;
    private static int cpuX;
    private static int shownPlayerX;
    private static int shownCpuX;
    private static int playerScore;
    private static int cpuScore;

    private Pong() {
    }

    public static void main(String[] args) {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        showMessage("Tap to play");
        waitForTap();
        Random.seed(Clock.micros());

        while (true) {
            playerScore = 0;
            cpuScore = 0;
            TftTouchShield.fillScreen(TftTouchShield.BLACK);
            playerX = (FIELD_WIDTH - PADDLE_WIDTH) / 2;
            cpuX = playerX;
            shownPlayerX = -1;
            shownCpuX = -1;
            drawPaddles();
            drawScore();
            boolean serveDown = Random.nextInt(2) == 0;
            while (playerScore < WINNING_SCORE && cpuScore < WINNING_SCORE) {
                serve(serveDown);
                int winner = playPoint();
                if (winner > 0) {
                    playerScore = playerScore + 1;
                    serveDown = false;
                } else {
                    cpuScore = cpuScore + 1;
                    serveDown = true;
                }
                eraseBall();
                drawScore();
                Delay.millis(700);
            }
            if (playerScore > cpuScore) {
                showMessage("You win! Tap");
            } else {
                showMessage("CPU wins - tap");
            }
            waitForTap();
        }
    }

    // ---- Game flow ----

    private static void serve(boolean down) {
        ballX = (FIELD_WIDTH / 2 - BALL / 2) * FIXED;
        ballY = ((FIELD_TOP + FIELD_BOTTOM) / 2 - BALL / 2) * FIXED;
        speedX = (Random.nextInt(5) - 2) * FIXED / 2;
        speedY = START_SPEED_Y;
        if (!down) {
            speedY = -START_SPEED_Y;
        }
        shownBallX = -1;
        drawBall();
    }

    /** Plays until the ball leaves the field: returns 1 when you win the point, -1 when the CPU does. */
    private static int playPoint() {
        int next = Clock.millis();
        while (true) {
            while (Clock.millis() < next) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;

            if (TftTouchShield.readTouch()) {
                playerX = clamp(TftTouchShield.touchX() - PADDLE_WIDTH / 2, 0, FIELD_WIDTH - PADDLE_WIDTH);
            }
            int target = ballX / FIXED + BALL / 2 - PADDLE_WIDTH / 2;
            if (speedY > 0) {
                // Drift back to the center while the ball moves away.
                target = (FIELD_WIDTH - PADDLE_WIDTH) / 2;
            }
            cpuX = cpuX + clamp(target - cpuX, -CPU_SPEED, CPU_SPEED);
            cpuX = clamp(cpuX, 0, FIELD_WIDTH - PADDLE_WIDTH);

            ballX = ballX + speedX;
            ballY = ballY + speedY;
            if (ballX < 0) {
                ballX = -ballX;
                speedX = -speedX;
            } else if (ballX > (FIELD_WIDTH - BALL) * FIXED) {
                ballX = 2 * (FIELD_WIDTH - BALL) * FIXED - ballX;
                speedX = -speedX;
            }
            int y = ballY / FIXED;
            if (speedY > 0 && y + BALL >= PLAYER_Y && y + BALL <= PLAYER_Y + PADDLE_HEIGHT + 4) {
                bounce(playerX, PLAYER_Y - BALL);
            } else if (speedY < 0 && y <= CPU_Y + PADDLE_HEIGHT && y >= CPU_Y - 4) {
                bounce(cpuX, CPU_Y + PADDLE_HEIGHT);
            }
            drawPaddles();
            drawBall();

            if (ballY / FIXED > FIELD_BOTTOM - BALL) {
                return -1;
            }
            if (ballY / FIXED < FIELD_TOP) {
                return 1;
            }
        }
    }

    /** Bounces off a paddle at {@code paddleX} when the ball overlaps it, angling by hit position. */
    private static void bounce(int paddleX, int restY) {
        int center = ballX / FIXED + BALL / 2;
        if (center < paddleX - 2 || center > paddleX + PADDLE_WIDTH + 2) {
            return;
        }
        int offset = center - (paddleX + PADDLE_WIDTH / 2);
        speedX = clamp(speedX + offset * FIXED / 6, -MAX_SPEED_X, MAX_SPEED_X);
        int speed = Math.min(MAX_SPEED_Y, Math.abs(speedY) + FIXED / 4);
        if (speedY > 0) {
            speedY = -speed;
        } else {
            speedY = speed;
        }
        ballY = restY * FIXED;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }

    // ---- Input ----

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(10);
        }
        int misses = 0;
        while (misses < 3) {
            if (TftTouchShield.readTouch()) {
                misses = 0;
            } else {
                misses = misses + 1;
            }
            Delay.millis(10);
        }
    }

    // ---- Drawing ----

    private static void drawBall() {
        int x = ballX / FIXED;
        int y = ballY / FIXED;
        if (x == shownBallX && y == shownBallY) {
            return;
        }
        eraseBall();
        if (y >= FIELD_TOP && y + BALL <= FIELD_BOTTOM) {
            TftTouchShield.fillRect(x, y, BALL, BALL, TftTouchShield.WHITE);
            shownBallX = x;
            shownBallY = y;
        }
    }

    private static void eraseBall() {
        if (shownBallX >= 0) {
            TftTouchShield.fillRect(shownBallX, shownBallY, BALL, BALL, TftTouchShield.BLACK);
            // Erasing where the ball overlapped a paddle leaves a hole: redraw that paddle fully.
            if (shownBallY + BALL > PLAYER_Y && shownPlayerX >= 0) {
                TftTouchShield.fillRect(shownPlayerX, PLAYER_Y, PADDLE_WIDTH, PADDLE_HEIGHT, PLAYER_COLOR);
            }
            if (shownBallY < CPU_Y + PADDLE_HEIGHT && shownCpuX >= 0) {
                TftTouchShield.fillRect(shownCpuX, CPU_Y, PADDLE_WIDTH, PADDLE_HEIGHT, CPU_COLOR);
            }
            shownBallX = -1;
        }
    }

    // Redraws only the strips a paddle gained and lost since it was last drawn.
    private static void drawPaddles() {
        shownPlayerX = movePaddle(shownPlayerX, playerX, PLAYER_Y, PLAYER_COLOR);
        shownCpuX = movePaddle(shownCpuX, cpuX, CPU_Y, CPU_COLOR);
    }

    private static int movePaddle(int shown, int x, int y, int color) {
        if (shown == x) {
            return x;
        }
        if (shown < 0 || Math.abs(x - shown) >= PADDLE_WIDTH) {
            if (shown >= 0) {
                TftTouchShield.fillRect(shown, y, PADDLE_WIDTH, PADDLE_HEIGHT, TftTouchShield.BLACK);
            }
            TftTouchShield.fillRect(x, y, PADDLE_WIDTH, PADDLE_HEIGHT, color);
        } else if (x > shown) {
            TftTouchShield.fillRect(shown, y, x - shown, PADDLE_HEIGHT, TftTouchShield.BLACK);
            TftTouchShield.fillRect(shown + PADDLE_WIDTH, y, x - shown, PADDLE_HEIGHT, color);
        } else {
            TftTouchShield.fillRect(x + PADDLE_WIDTH, y, shown - x, PADDLE_HEIGHT, TftTouchShield.BLACK);
            TftTouchShield.fillRect(x, y, shown - x, PADDLE_HEIGHT, color);
        }
        return x;
    }

    private static void drawScore() {
        TftTouchShield.fillRect(0, 0, FIELD_WIDTH, FIELD_TOP - 2, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(8, 4);
        TftTouchShield.setTextColor(CPU_COLOR, HEADER_BACKGROUND);
        TftTouchShield.print("CPU ");
        TftTouchShield.print(cpuScore);
        TftTouchShield.setTextColor(PLAYER_COLOR, HEADER_BACKGROUND);
        TftTouchShield.setCursor(140, 4);
        TftTouchShield.print("You ");
        TftTouchShield.print(playerScore);
    }

    private static void showMessage(String text) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor((FIELD_WIDTH - text.length() * 12) / 2, 150);
        TftTouchShield.print(text);
    }
}
