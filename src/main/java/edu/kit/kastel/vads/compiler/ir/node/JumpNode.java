package edu.kit.kastel.vads.compiler.ir.node;

public final class JumpNode extends Node {
    private final Block target;

    public JumpNode(Block block, Block target) {
        super(block); // No predecessors for unconditional jump
        this.target = target;
    }

    public Block target() {
        return target;
    }

    @Override
    protected String info() {
        return "-> " + target;
    }
}