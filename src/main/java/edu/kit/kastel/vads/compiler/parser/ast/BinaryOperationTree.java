package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import edu.kit.kastel.vads.compiler.semantic.OperatorTypeExtensions;

public record BinaryOperationTree(
    ExpressionTree lhs, ExpressionTree rhs, Operator.OperatorType operatorType
) implements ExpressionTree {
    @Override
    public Span span() {
        return lhs().span().merge(rhs().span());
    }

    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }

    @Override
    public BasicType getType() {
        return OperatorTypeExtensions.outType(operatorType);
    }
}
