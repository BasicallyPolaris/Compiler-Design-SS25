package edu.kit.kastel.vads.compiler.lexer;

import edu.kit.kastel.vads.compiler.Position;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.lexer.Separator.SeparatorType;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class Lexer {
    private final String source;
    private int pos;
    private int lineStart;
    private int line;

    private Lexer(String source) {
        this.source = source;
    }

    public static Lexer forString(String source) {
        return new Lexer(source);
    }

    public Optional<Token> nextToken() {
        ErrorToken error = skipWhitespace();
        if (error != null) {
            return Optional.of(error);
        }
        if (this.pos >= this.source.length()) {
            return Optional.empty();
        }
        Token t = switch (peek()) {
            case '(' -> separator(SeparatorType.PAREN_OPEN);
            case ')' -> separator(SeparatorType.PAREN_CLOSE);
            case '{' -> separator(SeparatorType.BRACE_OPEN);
            case '}' -> separator(SeparatorType.BRACE_CLOSE);
            case ';' -> separator(SeparatorType.SEMICOLON);
            case '?' -> new Operator(OperatorType.COND_EXP1, buildSpan(1));
            case ':' -> new Operator(OperatorType.COND_EXP2, buildSpan(1));
            case '~' -> new Operator(OperatorType.BIT_NOT, buildSpan(1));
            case '-' -> singleOrAssign(OperatorType.MINUS, OperatorType.ASSIGN_MINUS);
            case '+' -> singleOrAssign(OperatorType.PLUS, OperatorType.ASSIGN_PLUS);
            case '*' -> singleOrAssign(OperatorType.MUL, OperatorType.ASSIGN_MUL);
            case '/' -> singleOrAssign(OperatorType.DIV, OperatorType.ASSIGN_DIV);
            case '%' -> singleOrAssign(OperatorType.MOD, OperatorType.ASSIGN_MOD);
            case '=' -> singleOrAssign(OperatorType.ASSIGN, OperatorType.EQUAL);
            case '!' -> singleOrAssign(OperatorType.LOG_NOT, OperatorType.NOT_EQUAL);
            case '^' -> singleOrAssign(OperatorType.BIT_XOR, OperatorType.ASSIGN_BIT_XOR);
            case '|' -> singleOrDuplicateOperator(OperatorType.BIT_OR, OperatorType.ASSIGN_BIT_OR, OperatorType.LOG_OR);
            case '&' ->
                    singleOrDuplicateOperator(OperatorType.BIT_AND, OperatorType.ASSIGN_BIT_AND, OperatorType.LOG_AND);
            case '<' ->
                    lessOrMoreShift(OperatorType.LESS, OperatorType.BIT_SHIFT_LEFT, OperatorType.LESS_EQUAL, OperatorType.ASSIGN_BIT_SHIFT_LEFT);
            case '>' ->
                    lessOrMoreShift(OperatorType.MORE, OperatorType.BIT_SHIFT_RIGHT, OperatorType.MORE_EQUAL, OperatorType.ASSIGN_BIT_SHIFT_RIGHT);
            default -> {
                if (isBoolean()) {
                    yield lexBool();
                } else if (isIdentifierChar(peek())) {
                    if (isNumeric(peek())) {
                        yield lexNumber();
                    }
                    yield lexIdentifierOrKeyword();
                }
                yield new ErrorToken(String.valueOf(peek()), buildSpan(1));
            }
        };

        return Optional.of(t);
    }

    private @Nullable ErrorToken skipWhitespace() {
        enum CommentType {
            SINGLE_LINE,
            MULTI_LINE
        }
        CommentType currentCommentType = null;
        int multiLineCommentDepth = 0;
        int commentStart = -1;
        while (hasMore(0)) {
            switch (peek()) {
                case ' ', '\t' -> this.pos++;
                case '\n', '\r' -> {
                    this.pos++;
                    this.lineStart = this.pos;
                    this.line++;
                    if (currentCommentType == CommentType.SINGLE_LINE) {
                        currentCommentType = null;
                    }
                }
                case '/' -> {
                    if (currentCommentType == CommentType.SINGLE_LINE) {
                        this.pos++;
                        continue;
                    }
                    if (hasMore(1)) {
                        if (peek(1) == '/' && currentCommentType == null) {
                            currentCommentType = CommentType.SINGLE_LINE;
                        } else if (peek(1) == '*') {
                            currentCommentType = CommentType.MULTI_LINE;
                            multiLineCommentDepth++;
                        } else if (currentCommentType == CommentType.MULTI_LINE) {
                            this.pos++;
                            continue;
                        } else {
                            return null;
                        }
                        commentStart = this.pos;
                        this.pos += 2;
                        continue;
                    }
                    // are we in a multi line comment of any depth?
                    if (multiLineCommentDepth > 0) {
                        this.pos++;
                        continue;
                    }
                    return null;
                }
                default -> {
                    if (currentCommentType == CommentType.MULTI_LINE) {
                        if (peek() == '*' && hasMore(1) && peek(1) == '/') {
                            this.pos += 2;
                            multiLineCommentDepth--;
                            currentCommentType = multiLineCommentDepth == 0 ? null : CommentType.MULTI_LINE;
                        } else {
                            this.pos++;
                        }
                        continue;
                    } else if (currentCommentType == CommentType.SINGLE_LINE) {
                        this.pos++;
                        continue;
                    }
                    return null;
                }
            }
        }
        if (!hasMore(0) && currentCommentType == CommentType.MULTI_LINE) {
            return new ErrorToken(this.source.substring(commentStart), buildSpan(0));
        }
        return null;
    }

    private Separator separator(SeparatorType parenOpen) {
        return new Separator(parenOpen, buildSpan(1));
    }

    private Token lexIdentifierOrKeyword() {
        int off = 1;
        while (hasMore(off) && isIdentifierChar(peek(off))) {
            off++;
        }
        String id = this.source.substring(this.pos, this.pos + off);
        // This is a naive solution. Using a better data structure (hashmap, trie) likely performs better.
        for (KeywordType value : KeywordType.values()) {
            if (value.keyword().equals(id)) {
                return new Keyword(value, buildSpan(off));
            }
        }
        return new Identifier(id, buildSpan(off));
    }

    //we check before method use whether true and false are the only cases -> use else as default
    private Token lexBool() {
        if (isTrue()) {
            return new BooleanLiteral("true", buildSpan(4));
        } else {
            return new BooleanLiteral("false", buildSpan(5));
        }
    }

    private Token lexNumber() {
        if (isHexPrefix()) {
            int off = 2;
            while (hasMore(off) && isHex(peek(off))) {
                off++;
            }
            if (off == 2) {
                // 0x without any further hex digits
                return new ErrorToken(this.source.substring(this.pos, this.pos + off), buildSpan(2));
            }
            return new NumberLiteral(this.source.substring(this.pos, this.pos + off), 16, buildSpan(off));
        }
        int off = 1;
        while (hasMore(off) && isNumeric(peek(off))) {
            off++;
        }
        if (peek() == '0' && off > 1) {
            // leading zero is not allowed
            return new ErrorToken(this.source.substring(this.pos, this.pos + off), buildSpan(off));
        }
        return new NumberLiteral(this.source.substring(this.pos, this.pos + off), 10, buildSpan(off));
    }

    private boolean isHexPrefix() {
        return peek() == '0' && hasMore(1) && (peek(1) == 'x' || peek(1) == 'X');
    }

    private boolean isIdentifierChar(char c) {
        return c == '_'
                || c >= 'a' && c <= 'z'
                || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9';
    }

    private boolean isBoolean() {
        return (isFalse() || isTrue());
    }

    private boolean isTrue() {
        return (peekStringEquals("true"));
    }

    private boolean isFalse() {
        return (peekStringEquals("false"));
    }

    private boolean isNumeric(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isHex(char c) {
        return isNumeric(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private Token singleOrAssign(OperatorType single, OperatorType assign) {
        if (hasMore(1) && peek(1) == '=') {
            return new Operator(assign, buildSpan(2));
        }
        return new Operator(single, buildSpan(1));
    }

    private Token lessOrMoreShift(OperatorType single, OperatorType tuple, OperatorType equalTuple, OperatorType triple) {
        char singleOperator = single.toString().charAt(0);

        if (hasMore(2) && peek(1) == singleOperator && peek(2) == '=') {
            return new Operator(triple, buildSpan(3));
        } else if (hasMore(1) && peek(1) == singleOperator) {
            return new Operator(tuple, buildSpan(2));
        } else if (hasMore(1) && peek(1) == '=') {
            return new Operator(equalTuple, buildSpan(2));
        }
        return new Operator(single, buildSpan(1));
    }

    private Token singleOrDuplicateOperator(OperatorType single, OperatorType single_assign, OperatorType duplicate) {
        char singleOperator = single.toString().charAt(0);

        if (!hasMore(1)) return new Operator(single, buildSpan(1));

        if (peek(1) == singleOperator) return new Operator(duplicate, buildSpan(2));
        if (peek(1) == '=') return new Operator(single_assign, buildSpan(2));

        return new Operator(single, buildSpan(1));
    }

    private Span buildSpan(int proceed) {
        int start = this.pos;
        this.pos += proceed;
        Position.SimplePosition s = new Position.SimplePosition(this.line, start - this.lineStart);
        Position.SimplePosition e = new Position.SimplePosition(this.line, start - this.lineStart + proceed);
        return new Span.SimpleSpan(s, e);
    }

    private char peek() {
        return this.source.charAt(this.pos);
    }

    private boolean peekStringEquals(String expected) {
        int length = expected.length();
        if (hasMore(length)) {
            boolean peekIsExpected = true;

            for (int i = 0; i < length; i++) {
                if (expected.charAt(i) != peek(i)) {
                    peekIsExpected = false;
                    break;
                }
            }

            return peekIsExpected;
        }

        return false;
    }

    private boolean hasMore(int offset) {
        return this.pos + offset < this.source.length();
    }

    private char peek(int offset) {
        return this.source.charAt(this.pos + offset);
    }

}
