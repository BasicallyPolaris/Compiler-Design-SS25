package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.type.Type;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;


public record BoolLiteralTree(String value, Span span) implements ExpressionTree {



    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }

    public boolean parseValue() {
        return value.equals("true");
    }

    @Override
    public BasicType getType() {
        return BasicType.BOOL;
    }

}