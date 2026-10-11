/**
 * Building blocks shared by the two blockchain games: SHA-256, integer factorization, big-endian byte
 * encoding, Wi-Fi/UDP start-up and the TFT screen helpers.
 *
 * <p>{@link io.github.jabrena.juno.usecases.ledger.LedgerGame} uses them for centralized coordination: one
 * leader hands out numbers and keeps the record. {@link io.github.jabrena.juno.usecases.miner.MinerGame}
 * uses them for decentralized consensus: equal nodes mine a Bitcoin-style proof-of-work chain.
 */
package io.github.jabrena.juno.games.blockchain;
