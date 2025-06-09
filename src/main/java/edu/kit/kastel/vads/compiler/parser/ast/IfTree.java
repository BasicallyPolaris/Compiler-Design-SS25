package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import org.jspecify.annotations.Nullable;

public record IfTree(ExpressionTree condition, StatementTree thenStatement, @Nullable StatementTree elseStatement) implements StatementTree {

    @Override
    public Span span() {
        if (elseStatement != null) {
            return condition.span().merge(thenStatement.span().merge(elseStatement.span()));
        } else {
            return condition.span().merge(thenStatement.span());
        }

    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }
}
