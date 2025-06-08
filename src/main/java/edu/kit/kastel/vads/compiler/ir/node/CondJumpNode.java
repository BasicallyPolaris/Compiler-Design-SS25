package edu.kit.kastel.vads.compiler.ir.node;

public final class CondJumpNode extends Node {
    private final Block trueTarget;
    private final Block falseTarget;

    public CondJumpNode(Block block, Node condition, Block trueTarget, Block falseTarget) {
        super(block, condition); // condition is predecessor 0
        this.trueTarget = trueTarget;
        this.falseTarget = falseTarget;
    }

    public Node condition() {
        return predecessor(0);
    }

    public Block trueTarget() {
        return trueTarget;
    }

    public Block falseTarget() {
        return falseTarget;
    }

    @Override
    protected String info() {
        return condition() + " ? " + trueTarget + " : " + falseTarget;
    }
}