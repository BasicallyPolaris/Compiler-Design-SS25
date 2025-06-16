package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record CondExprTree(
        ExpressionTree cond, ExpressionTree exp1, ExpressionTree exp2) implements ExpressionTree {
    @Override
    public Span span() {
        return cond().span().merge(exp1.span().merge(exp2().span()));
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }

    @Override
    public BasicType getType() {
        if (exp1.getType() == exp2.getType()) {
            return exp1.getType();
        } else {
            return BasicType.VOID;
        }
    }
}
