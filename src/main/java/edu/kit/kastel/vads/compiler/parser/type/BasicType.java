package edu.kit.kastel.vads.compiler.parser.type;

import edu.kit.kastel.vads.compiler.lexer.Keyword;
import edu.kit.kastel.vads.compiler.lexer.KeywordType;

import java.util.Locale;

public enum BasicType implements Type {
    BOOL,
    INT;

    public KeywordType getKeywordType() {
        return switch (this) {
            case BOOL -> KeywordType.BOOL;
            case INT -> KeywordType.INT;
            default -> throw new IllegalArgumentException("Unknown keyword type: " + this);
        };
    }

    @Override
    public String asString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
