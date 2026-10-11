/**
 * A network game for several boards over UDP: factor numbers together and chain the verified answers.
 *
 * <p>Two ways to play. {@link io.github.jabrena.juno.games.blockchain.FactorLedgerLeader} (an UNO Q)
 * hands out numbers to {@link io.github.jabrena.juno.games.blockchain.FactorWorker}s (UNO R4 WiFi
 * boards) and keeps a SHA-256 hash chain of their verified answers. In
 * {@link io.github.jabrena.juno.games.blockchain.ChainNode} there is no leader: every board mines a
 * Bitcoin-style proof-of-work chain whose blocks each carry a verified factorization, and the branch
 * with the most work wins. Progress is printed over serial at 115200 baud.
 */
package io.github.jabrena.juno.games.blockchain;
