package edu.kit.kastel.vads.compiler.parser;

import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.parser.ast.*;

import java.util.List;

/// This is a utility class to help with debugging the parser.
public class Printer {

    private final Tree ast;
    private final StringBuilder builder = new StringBuilder();
    private boolean requiresIndent;
    private int indentDepth;

    public Printer(Tree ast) {
        this.ast = ast;
    }

    public static String print(Tree ast) {
        Printer printer = new Printer(ast);
        printer.printRoot();
        return printer.builder.toString();
    }

    private void printRoot() {
        printTree(this.ast);
    }

    private void printTree(Tree tree) {
        switch (tree) {
            case BlockTree(List<StatementTree> statements, _) -> {
                print("{");
                lineBreak();
                this.indentDepth++;
                for (StatementTree statement : statements) {
                    printTree(statement);
                }
                this.indentDepth--;
                print("}");
            }
            case FunctionTree(var returnType, var name, var body) -> {
                printTree(returnType);
                space();
                printTree(name);
                print("()");
                space();
                printTree(body);
            }
            case NameTree(var name, _, _) -> print(name.asString());
            case ProgramTree(var topLevelTrees) -> {
                for (FunctionTree function : topLevelTrees) {
                    printTree(function);
                    lineBreak();
                }
            }
            case SequentialStatementTree(var firstStatement, var statements) -> {
                printTree(firstStatement);
                for (StatementTree statement : statements) {
                    printTree(statement);
                }
            }
            case TypeTree(var type, _) -> print(type.asString());
            case BinaryOperationTree(var lhs, var rhs, var op) -> {
                print("(");
                printTree(lhs);
                print(")");
                space();
                this.builder.append(op);
                space();
                print("(");
                printTree(rhs);
                print(")");
            }
            case LiteralTree(var value, _, _) -> this.builder.append(value);
            case BoolLiteralTree(var value, _) -> this.builder.append(value);
            case NegateTree(var expression, _) -> {
                print("-(");
                printTree(expression);
                print(")");
            }
            case AssignmentTree(var lValue, var op, var expression) -> {
                printTree(lValue);
                space();
                this.builder.append(op.asString());
                space();
                printTree(expression);
                semicolon();
            }
            case DeclarationTree(var type, var name, var initializer, List<StatementTree> Statements) -> {
                printTree(type);
                space();
                printTree(name);
                if (initializer != null) {
                    print(" = ");
                    printTree(initializer);
                }
                semicolon();
                for (StatementTree statement : Statements) {
                    printTree(statement);
                }
            }
            case ReturnTree(var expr, _) -> {
                print("return ");
                printTree(expr);
                semicolon();
            }
            case LValueIdentTree(var name) -> printTree(name);
            case IdentExpressionTree(var name) -> printTree(name);
            //TODO: Implement missing cases
            case BreakTree breakTree -> {
                print("break;");
                lineBreak();
            }
            case ContinueTree continueTree -> {
                print("continue;");
                lineBreak();
            }
            case IfTree(var expression, var ifStatement, var elseStatement) -> {
                print("if(");
                printTree(expression);
                print(") ");
                printTree(ifStatement);
                if (elseStatement != null) {
                    print("else ");
                    printTree(elseStatement);
                }
                lineBreak();
            }
            case NopTree nopTree -> {
                print("");
            }
            case WhileTree(var expression, var statement) -> {
                print("while(");
                printTree(expression);
                print(") ");
                printTree(statement);
                lineBreak();
            }
            case CondExprTree(var cond, var exp1, var exp2) -> {
                printTree(cond);
                print(" ? ");
                printTree(exp1);
                print(" : ");
                printTree(exp2);
            }
            case BitNotTree(var expression, _) -> {
                print("~(");
                printTree(expression);
                print(")");
            }
        }
    }

    private void print(String str) {
        if (this.requiresIndent) {
            this.requiresIndent = false;
            this.builder.append(" ".repeat(4 * this.indentDepth));
        }
        this.builder.append(str);
    }

    private void lineBreak() {
        this.builder.append("\n");
        this.requiresIndent = true;
    }

    private void semicolon() {
        this.builder.append(";");
        lineBreak();
    }

    private void space() {
        this.builder.append(" ");
    }

}
