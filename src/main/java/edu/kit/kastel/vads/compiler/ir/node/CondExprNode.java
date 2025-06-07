package edu.kit.kastel.vads.compiler.ir.node;

public final class CondExprNode extends Node  {

    public CondExprNode(Block block, Node left, Node middle, Node right) {
        super(block, left, middle, right);
    }
}
