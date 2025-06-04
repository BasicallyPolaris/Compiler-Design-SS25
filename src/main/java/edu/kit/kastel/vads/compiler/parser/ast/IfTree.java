package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record IfTree(ExpressionTree expression, StatementTree ifStatement, StatementTree thenStatement) implements StatementTree {

    @Override
    public Span span() {
        return ifStatement.span().merge(thenStatement.span());
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }
}
