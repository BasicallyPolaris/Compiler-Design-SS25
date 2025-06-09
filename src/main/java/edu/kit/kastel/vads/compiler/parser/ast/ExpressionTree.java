package edu.kit.kastel.vads.compiler.parser.ast;

public sealed interface ExpressionTree extends Tree permits BinaryOperationTree, BitNotTree, BoolLiteralTree, CondExprTree, IdentExpressionTree, LiteralTree, LogNotTree, NegateTree {
}
