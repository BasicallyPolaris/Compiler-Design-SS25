package edu.kit.kastel.vads.compiler.ir.node;


public final class IfNode extends Node {

    public IfNode(Block block, Node expression, Node ifNode) {
        super(block, expression, ifNode);
    }
}
