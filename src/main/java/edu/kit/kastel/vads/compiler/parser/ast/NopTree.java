package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

public record NopTree() implements StatementTree {
    @Override
    public Span span() {
        return null;
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return null;
    }
}
