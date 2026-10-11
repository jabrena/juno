/**
 * Blockchain, a network game for several boards with the TFT touch shield: factor numbers together and
 * chain the verified answers.
 *
 * <p>Every board runs {@link io.github.jabrena.juno.games.blockchain.BlockchainGame} and picks its role
 * on the touch screen. A LEADER (an UNO Q) hands out numbers to WORKERs (UNO R4 WiFi boards) and keeps a
 * SHA-256 hash chain of their verified answers. MINERs need no leader: each mines a Bitcoin-style
 * proof-of-work chain whose blocks carry a verified factorization, and the branch with the most work wins.
 */
package io.github.jabrena.juno.games.blockchain;
