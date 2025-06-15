package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

import java.util.HashSet;
import java.util.Set;

/// Checks that functions return.
/// Currently only works for straight-line code.
class ReturnAnalysis implements NoOpVisitor<ReturnAnalysis.ReturnState> {

    static class ReturnState {
        Set<StatementTree> returns = new HashSet<>();
    }

    @Override
    public Unit visit(ReturnTree returnTree, ReturnState data) {
        data.returns.add(returnTree);
        return NoOpVisitor.super.visit(returnTree, data);
    }

    @Override
    public Unit visit(FunctionTree functionTree, ReturnState data) {
        BlockTree body = functionTree.body();
        if (!data.returns.contains(body)) {
            throw new SemanticException("function " + functionTree.name() + " does not return");
        }
        data.returns.clear();
        return NoOpVisitor.super.visit(functionTree, data);
    }

    @Override
    public Unit visit(BlockTree blockTree, ReturnState data) {
        for (StatementTree statement : blockTree.statements()) {
            if (data.returns.contains(statement)) {
                data.returns.add(blockTree);
            }
        }
        return NoOpVisitor.super.visit(blockTree, data);
    }

    @Override
    public Unit visit(SequentialStatementTree sequentialStatementTree, ReturnState data) {
        if (data.returns.contains(sequentialStatementTree.statement())) {
            data.returns.add(sequentialStatementTree);
        }
        for (StatementTree statement : sequentialStatementTree.statements()) {
            if (data.returns.contains(statement)) {
                data.returns.add(sequentialStatementTree);
            }
        }
        return NoOpVisitor.super.visit(sequentialStatementTree, data);
    }

    @Override
    public Unit visit(DeclarationTree declarationTree, ReturnState data) {
        for (StatementTree statement : declarationTree.statements()) {
            if (data.returns.contains(statement)) {
                data.returns.add(declarationTree);
            }
        }
        return NoOpVisitor.super.visit(declarationTree, data);
    }

    @Override
    public Unit visit(IfTree ifTree, ReturnState data) {
        if (ifTree.elseStatement() != null) {
            if (data.returns.contains(ifTree.thenStatement()) && data.returns.contains(ifTree.elseStatement())) {
                data.returns.add(ifTree);
            }
        } else {
            if (data.returns.contains(ifTree.thenStatement())) {
                data.returns.add(ifTree);
            }
        }
        return NoOpVisitor.super.visit(ifTree, data);
    }
}
