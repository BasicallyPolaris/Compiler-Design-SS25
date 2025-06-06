package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;

import java.util.List;

public record SequentialStatementTree(StatementTree statement, List<StatementTree> statements) implements StatementTree {

    public SequentialStatementTree {
        statements = List.copyOf(statements);
    }

    @Override
    public Span span() {
        Span baseSpan = statement.span();

        for (StatementTree sequentialStatement : statements) {
            return baseSpan.merge(sequentialStatement.span());
        }

        return baseSpan;
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }
}
