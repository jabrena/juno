/**
 * Miner 2, a decentralized miner built on {@link io.github.jabrena.juno.api.mvc}: every board runs
 * {@link io.github.jabrena.juno.usecases.miner.Miner2} as an equal node of one proof-of-work chain, with BLOCKS,
 * CHAIN, PEERS and NET views over one model and the node's networking and mining as a single-threaded background
 * task. The chain lives in {@code chain}, the peers and node identities in {@code peers}.
 */
package io.github.jabrena.juno.usecases.miner;
