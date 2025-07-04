package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record IdentExpressionTree(NameTree name) implements ExpressionTree {
    @Override
    public Span span() {
        return name().span();
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }

    //TODO: How to overrite for the nametree if declared???
    @Override
    public BasicType getType() {
        return name.getType();
    }
}
