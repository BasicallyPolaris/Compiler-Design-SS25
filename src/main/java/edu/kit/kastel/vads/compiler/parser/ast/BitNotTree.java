package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record BitNotTree(ExpressionTree expression, Span minusPos) implements ExpressionTree {
    @Override
    public Span span() {
        return minusPos().merge(expression().span());
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }

    @Override
    public BasicType getType() {
        return BasicType.INT;
    }
}
