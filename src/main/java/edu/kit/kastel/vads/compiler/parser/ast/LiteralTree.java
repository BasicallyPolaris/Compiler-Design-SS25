package edu.kit.kastel.vads.compiler.parser.ast;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.ParseException;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;
import java.util.Optional;
import java.util.OptionalLong;

public record LiteralTree(String value, int base, Span span) implements ExpressionTree {
    @Override
    public <T, R> R accept(Visitor<T, R> visitor, T data) {
        return visitor.visit(this, data);
    }

    public OptionalLong parseValue() {
        int end = value.length();
        return switch (base) {
            case 16 -> parseHex(end);
            case 10 -> parseDec(end);
            default -> throw new IllegalArgumentException("unexpected base " + base);
        };
    }

    private OptionalLong parseDec(int end) {
        long l;
        try {
            l = Long.parseLong(value, 0, end, base);
        } catch (NumberFormatException _) {
            return OptionalLong.empty();
        }
        if (l < 0 || l > Integer.toUnsignedLong(Integer.MIN_VALUE)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(l);
    }

    private OptionalLong parseHex(int end) {
        try {
            return OptionalLong.of(Integer.parseUnsignedInt(value, 2, end, 16));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

}
