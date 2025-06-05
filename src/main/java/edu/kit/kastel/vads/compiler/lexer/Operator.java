package edu.kit.kastel.vads.compiler.lexer;

import edu.kit.kastel.vads.compiler.Span;

public record Operator(OperatorType type, Span span) implements Token {

    @Override
    public boolean isOperator(OperatorType operatorType) {
        return type() == operatorType;
    }

    @Override
    public String asString() {
        return type().toString();
    }

    public enum OperatorType {
        ASSIGN("="),
        ASSIGN_MINUS("-="),
        MINUS("-"),
        ASSIGN_PLUS("+="),
        PLUS("+"),
        ASSIGN_MUL("*="),
        MUL("*"),
        ASSIGN_DIV("/="),
        DIV("/"),
        ASSIGN_MOD("%="),
        MOD("%"),
        ASSIGN_BIT_AND("&="),
        BIT_AND("&"),
        ASSIGN_BIT_XOR("^="),
        BIT_XOR("^"),
        ASSIGN_BIT_OR("|="),
        BIT_OR("|"),
        ASSIGN_BIT_SHIFT_LEFT("<<="),
        BIT_SHIFT_LEFT("<<"),
        ASSIGN_BIT_SHIFT_RIGHT(">>="),
        BIT_SHIFT_RIGHT(">>"),
        LESS("<"),
        LESS_EQUAL("<="),
        MORE(">"),
        MORE_EQUAL(">="),
        EQUAL("=="),
        NOT_EQUAL("!="),
        LOG_NOT("!"),
        BIT_NOT("~"),
        LOG_AND("&&"),
        LOG_OR("||"),
        COND_EXP1("?"),
        COND_EXP2(":"),
        ;

        private final String value;

        OperatorType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }
}
