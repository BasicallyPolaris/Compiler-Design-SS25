package edu.kit.kastel.vads.compiler.ir.node;

import org.jspecify.annotations.Nullable;

public final class IfElseNode extends Node  {

    public IfElseNode(Block block, Node expression, Node ifNode, Node elseNode) {
        super(block, expression, ifNode, elseNode);
    }
}
