package edu.kit.kastel.vads.compiler.ir.node;


public final class WhileNode extends Node {

    public WhileNode(Block block, Node expression, Node statements) {
        super(block, expression, statements);
    }
}
