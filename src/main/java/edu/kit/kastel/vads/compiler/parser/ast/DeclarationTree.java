package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record DeclarationTree(TypeTree type, NameTree name, @Nullable ExpressionTree initializer,
                              List<StatementTree> statements) implements StatementTree {

    public DeclarationTree {
        statements = new ArrayList<StatementTree>(statements);
    }

    @Override
    public Span span() {
        Span baseSpan;
        if (initializer() != null) {
            baseSpan = type().span().merge(initializer().span());
        } else {
            baseSpan = type().span().merge(name().span());
        }

        for (StatementTree statement : statements) {
            baseSpan = baseSpan.merge(statement.span());
        }

        return baseSpan;
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }
}
