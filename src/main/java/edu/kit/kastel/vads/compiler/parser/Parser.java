package edu.kit.kastel.vads.compiler.parser;

import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.lexer.Keyword;
import edu.kit.kastel.vads.compiler.lexer.KeywordType;
import edu.kit.kastel.vads.compiler.lexer.NumberLiteral;
import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.lexer.Separator;
import edu.kit.kastel.vads.compiler.lexer.Separator.SeparatorType;
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.lexer.Token;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final TokenSource tokenSource;

    public Parser(TokenSource tokenSource) {
        this.tokenSource = tokenSource;
    }

    public ProgramTree parseProgram() {
        ProgramTree programTree = new ProgramTree(List.of(parseFunction()));
        if (this.tokenSource.hasMore()) {
            throw new ParseException("expected end of input but got " + this.tokenSource.peek());
        }
        return programTree;
    }

    private FunctionTree parseFunction() {
        // TODO (In Lab 3: Function should not expect int as default-return if other
        // returns are possible)
        Keyword returnType = this.tokenSource.expectKeyword(KeywordType.INT);
        Identifier identifier = this.tokenSource.expectIdentifier();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        BlockTree body = parseBlock();

        // TODO: Only expect exactly one function to be naimed main per file ?
        if (!identifier.value().equals("main")) {
            System.out.println(identifier.value());
            throw new ParseException("expected main function but got " + identifier);
        }

        return new FunctionTree(
                new TypeTree(BasicType.INT, returnType.span()),
                name(identifier),
                body);
    }

    private BlockTree parseBlock() {
        Separator bodyOpen = this.tokenSource.expectSeparator(SeparatorType.BRACE_OPEN);
        List<StatementTree> statements = new ArrayList<>();
        while (!(this.tokenSource.peek() instanceof Separator sep && sep.type() == SeparatorType.BRACE_CLOSE)) {
            statements.add(parseStatement());
        }
        Separator bodyClose = this.tokenSource.expectSeparator(SeparatorType.BRACE_CLOSE);
        return new BlockTree(statements, bodyOpen.span().merge(bodyClose.span()));
    }

    private StatementTree parseStatement() {
        StatementTree statement;
        if (this.tokenSource.peek().isKeyword(KeywordType.INT)) {
            statement = parseDeclaration(BasicType.INT, SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            statement = parseDeclaration(BasicType.BOOL, SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.RETURN)) {
            statement = parseReturn();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.WHILE)) {
            statement = parseWhile();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.FOR)) {
            statement = parseFor();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.IF)) {
            statement = parseIf();
        } else if (this.tokenSource.peek().isSeparator(SeparatorType.BRACE_OPEN)) {
            statement = parseBlock();
        } else {
            statement = parseSimple();
        }
        return statement;
    }

    private StatementTree parseDeclaration(BasicType basicType, SeparatorType expectedSeparator) {
        return parseDeclaration(basicType, expectedSeparator, true);
    }

    private StatementTree parseDeclaration(BasicType basicType, SeparatorType expectedSeparator,
            boolean shouldParseRecursively) {
        Keyword type = this.tokenSource.expectKeyword(basicType.getKeywordType());
        Identifier ident = this.tokenSource.expectIdentifier();
        ExpressionTree expr = null;
        if (this.tokenSource.peek().isOperator(OperatorType.ASSIGN)) {
            this.tokenSource.expectOperator(OperatorType.ASSIGN);
            expr = parseExpression();
        }
        this.tokenSource.expectSeparator(expectedSeparator);

        // To get the scope, parse all further statements as in scope statements of this
        // declaration
        List<StatementTree> statements = new ArrayList<>();
        if (shouldParseRecursively) {
            while (!(this.tokenSource.peek() instanceof Separator sep && sep.type() == SeparatorType.BRACE_CLOSE)) {
                statements.add(parseStatement());
            }
        }

        return new DeclarationTree(new TypeTree(basicType, type.span()), name(ident), expr, statements);
    }

    private StatementTree parseSimple() {
        return parseSimple(true);
    }

    private StatementTree parseSimple(boolean expectSeparator) {
        LValueTree lValue = parseLValue();
        Operator assignmentOperator = parseAssignmentOperator();
        ExpressionTree expression = parseExpression();
        if (expectSeparator)
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);

        return new AssignmentTree(lValue, assignmentOperator, expression);
    }

    private Operator parseAssignmentOperator() {
        if (this.tokenSource.peek() instanceof Operator op) {
            return switch (op.type()) {
                case ASSIGN, ASSIGN_DIV, ASSIGN_MINUS, ASSIGN_MOD, ASSIGN_MUL, ASSIGN_PLUS, ASSIGN_BIT_OR,
                        ASSIGN_BIT_AND, ASSIGN_BIT_XOR, ASSIGN_BIT_SHIFT_LEFT, ASSIGN_BIT_SHIFT_RIGHT -> {
                    this.tokenSource.consume();
                    yield op;
                }
                default -> throw new ParseException("expected assignment but got " + op.type());
            };
        }
        throw new ParseException("expected assignment but got " + this.tokenSource.peek());
    }

    private LValueTree parseLValue() {
        if (this.tokenSource.peek().isSeparator(SeparatorType.PAREN_OPEN)) {
            this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
            LValueTree inner = parseLValue();
            this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
            return inner;
        }
        Identifier identifier = this.tokenSource.expectIdentifier();
        return new LValueIdentTree(name(identifier));
    }

    private StatementTree parseReturn() {
        Keyword ret = this.tokenSource.expectKeyword(KeywordType.RETURN);
        ExpressionTree expression = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);

        return new ReturnTree(expression, ret.span().start());
    }

    private ExpressionTree parseExpression() {
        ExpressionTree lhs = parseExpressionLogOr();
        // a ? b : ( c ? d : e )
        // parse cond expr
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.COND_EXP1)) {
                this.tokenSource.consume();
                // a ? parsed
                ExpressionTree mhs = parseExpression();
                this.tokenSource.expectOperator(OperatorType.COND_EXP2);
                // (a ? b :) parsed
                ExpressionTree rhs = parseExpression();
                lhs = new CondExprTree(lhs, mhs, rhs);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionLogOr() {
        ExpressionTree lhs = parseExpressionLogAnd();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.LOG_OR)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionLogAnd(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionLogAnd() {
        ExpressionTree lhs = parseExpressionBitOr();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.LOG_AND)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionBitOr(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionBitOr() {
        ExpressionTree lhs = parseExpressionBitXor();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.BIT_OR)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionBitXor(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionBitXor() {
        ExpressionTree lhs = parseExpressionBitAnd();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.BIT_XOR)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionBitAnd(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionBitAnd() {
        ExpressionTree lhs = parseExpressionEquals();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.BIT_AND)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionEquals(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionEquals() {
        ExpressionTree lhs = parseExpressionCompares();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.EQUAL || type == OperatorType.NOT_EQUAL)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionCompares(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionCompares() {
        ExpressionTree lhs = parseExpressionShifts();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.LESS || type == OperatorType.LESS_EQUAL || type == OperatorType.MORE
                            || type == OperatorType.MORE_EQUAL)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionShifts(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionShifts() {
        ExpressionTree lhs = parseExpressionSums();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.BIT_SHIFT_LEFT || type == OperatorType.BIT_SHIFT_RIGHT)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseExpressionSums(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseExpressionSums() {
        ExpressionTree lhs = parseTerm();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.PLUS || type == OperatorType.MINUS)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseTerm(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseTerm() {
        ExpressionTree lhs = parseFactor();
        while (true) {
            if (this.tokenSource.peek() instanceof Operator(var type, _)
                    && (type == OperatorType.MUL || type == OperatorType.DIV || type == OperatorType.MOD)) {
                this.tokenSource.consume();
                lhs = new BinaryOperationTree(lhs, parseFactor(), type);
            } else {
                return lhs;
            }
        }
    }

    private ExpressionTree parseFactor() {
        return switch (this.tokenSource.peek()) {
            case Separator(var type, _) when type == SeparatorType.PAREN_OPEN -> {
                this.tokenSource.consume();
                ExpressionTree expression = parseExpression();
                this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
                yield expression;
            }
            case Operator(var type, _) when type == OperatorType.MINUS -> {
                Span span = this.tokenSource.consume().span();
                yield new NegateTree(parseFactor(), span);
            }
            case Operator(var type, _) when type == OperatorType.LOG_NOT -> {
                Span span = this.tokenSource.consume().span();
                yield new LogNotTree(parseFactor(), span);
            }
            case Operator(var type, _) when type == OperatorType.BIT_NOT -> {
                Span span = this.tokenSource.consume().span();
                yield new BitNotTree(parseFactor(), span);
            }
            case Identifier ident -> {
                this.tokenSource.consume();
                yield new IdentExpressionTree(name(ident));
            }
            case NumberLiteral(String value, int base, Span span) -> {
                this.tokenSource.consume();
                yield new LiteralTree(value, base, span);
            }
            case Token t -> throw new ParseException("invalid factor " + t);
        };
    }

    private StatementTree parseIf() {
        this.tokenSource.expectKeyword(KeywordType.IF);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        ExpressionTree conditionExpression;
        conditionExpression = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);

        StatementTree bodyIf = parseStatement();

        // Check for else Statement
        if (this.tokenSource.peek().isKeyword(KeywordType.ELSE)) {
            this.tokenSource.consume();
            StatementTree bodyElse = parseStatement();
            return new IfTree(conditionExpression, bodyIf, bodyElse);
        } else {
            return new IfTree(conditionExpression, bodyIf, null);
        }
    }

    private StatementTree parseWhile() {
        this.tokenSource.expectKeyword(KeywordType.WHILE);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        // TODO: How to force data types (in this case: bool) for expressions
        ExpressionTree expression = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        BlockTree body = parseBlock();

        return new WhileTree(expression, body);
    }

    private StatementTree parseFor() {
        this.tokenSource.expectKeyword(KeywordType.FOR);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        StatementTree initialStatement = null;
        ExpressionTree conditionExpression;
        StatementTree incrementStatement = null;

        // Check for initial Statement
        if (this.tokenSource.peek().isKeyword(KeywordType.INT)) {
            initialStatement = parseDeclaration(BasicType.INT, SeparatorType.SEMICOLON, false);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            initialStatement = parseDeclaration(BasicType.BOOL, SeparatorType.SEMICOLON, false);
        } else if (this.tokenSource.peek().isSeparator(SeparatorType.PAREN_OPEN)
                || this.tokenSource.peek() instanceof Identifier) {
            initialStatement = parseSimple();
        } else {
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        }

        // Check for inner expression
        conditionExpression = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);

        // Check for final Statement
        if (this.tokenSource.peek().isKeyword(KeywordType.INT)) {
            incrementStatement = parseDeclaration(BasicType.INT, SeparatorType.PAREN_CLOSE, false);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            incrementStatement = parseDeclaration(BasicType.BOOL, SeparatorType.PAREN_CLOSE, false);
        } else if (this.tokenSource.peek().isSeparator(SeparatorType.PAREN_OPEN)
                || this.tokenSource.peek() instanceof Identifier) {
            incrementStatement = parseSimple(false);
        }
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);

        BlockTree body = parseBlock();

        // Adopt parsed block to have increment statement at end of the loop
        if (incrementStatement != null) {
            List<StatementTree> currentStatements = body.statements();
            List<StatementTree> newStatements = new ArrayList<>(currentStatements);
            newStatements.add(incrementStatement);
            body = new BlockTree(newStatements, body.span());
        }

        // If it's a declaration, return the nested DeclarationTree as ForLoop
        if (initialStatement instanceof DeclarationTree initialDeclarationTree) {
            initialDeclarationTree.statements().addFirst(new WhileTree(conditionExpression, body));
            return initialDeclarationTree;
        }

        // Otherwise return a SequentialStatementTree or the simple While Loop
        if (initialStatement != null) {
            return new SequentialStatementTree(initialStatement, List.of(new WhileTree(conditionExpression, body)));
        }

        return new WhileTree(conditionExpression, body);
    }

    private static NameTree name(Identifier ident) {
        return new NameTree(Name.forIdentifier(ident), ident.span());
    }
}
