package edu.kit.kastel.vads.compiler.ir.node;

import edu.kit.kastel.vads.compiler.ir.IrGraph;

import java.util.List;
import java.util.stream.Collectors;

public final class Block extends Node {
    private final String blockName;

    public Block(IrGraph graph, String blockName) {
        super(graph);
        this.blockName = blockName;
    }

    // If you need a way to get control flow predecessors specifically
    public List<Node> controlFlowPredecessors() {
        return predecessors().stream()
                .filter(node -> node instanceof CondJumpNode || node instanceof JumpNode)
                .collect(Collectors.toList());
    }

    public String blockName() {
        return blockName;
    }
}
