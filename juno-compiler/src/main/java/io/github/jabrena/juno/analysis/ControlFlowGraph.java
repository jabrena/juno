package io.github.jabrena.juno.analysis;

import java.util.List;
import java.util.Optional;

/** The basic blocks of one method, in offset order, with branch targets resolved into block-to-block edges. */
public record ControlFlowGraph(List<BasicBlock> blocks) {
    public BasicBlock entry() {
        return blocks.get(0);
    }

    public Optional<BasicBlock> blockAt(int offset) {
        return blocks.stream().filter(block -> block.start() == offset).findFirst();
    }
}
