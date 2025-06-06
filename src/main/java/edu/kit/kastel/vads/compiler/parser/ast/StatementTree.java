package edu.kit.kastel.vads.compiler.parser.ast;

public sealed interface StatementTree extends Tree permits SequentialStatementTree, AssignmentTree, BlockTree, IfTree, WhileTree, ContinueTree, BreakTree, DeclarationTree, ReturnTree, NopTree {
}
