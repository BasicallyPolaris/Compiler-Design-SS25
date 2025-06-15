package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.parser.type.BasicType;

public sealed interface ExpressionTree extends Tree permits BinaryOperationTree, BitNotTree, BoolLiteralTree, CondExprTree, IdentExpressionTree, LiteralTree, NegateTree {

    public BasicType getType();
}
