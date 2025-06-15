package edu.kit.kastel.vads.compiler.parser.visitor;

import edu.kit.kastel.vads.compiler.parser.ast.*;

/// A visitor that traverses a tree in postorder
///
/// @param <T> a type for additional data
/// @param <R> a type for a return type
public class RecursivePostorderVisitor<T, R> implements Visitor<T, R> {
    private final Visitor<T, R> visitor;

    public RecursivePostorderVisitor(Visitor<T, R> visitor) {
        this.visitor = visitor;
    }

    @Override
    public R visit(AssignmentTree assignmentTree, T data) {
        R r = assignmentTree.lValue().accept(this, data);
        r = assignmentTree.expression().accept(this, accumulate(data, r));
        r = this.visitor.visit(assignmentTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(BinaryOperationTree binaryOperationTree, T data) {
        R r = binaryOperationTree.lhs().accept(this, data);
        r = binaryOperationTree.rhs().accept(this, accumulate(data, r));
        r = this.visitor.visit(binaryOperationTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(BlockTree blockTree, T data) {
        R r;
        T d = data;
        for (StatementTree statement : blockTree.statements()) {
            r = statement.accept(this, d);
            d = accumulate(d, r);
        }
        r = this.visitor.visit(blockTree, d);
        return r;
    }

    @Override
    public R visit(DeclarationTree declarationTree, T data) {
        T currentData = data;
        R lastResult;

        // 1. Visit type
        lastResult = declarationTree.type().accept(this, currentData);
        currentData = accumulate(currentData, lastResult);

        // 2. Visit name
        lastResult = declarationTree.name().accept(this, currentData);
        currentData = accumulate(currentData, lastResult);

        // 3. Visit initializer (if it exists)
        if (declarationTree.initializer() != null) {
            lastResult = declarationTree.initializer().accept(this, currentData);
            currentData = accumulate(currentData, lastResult);
        }

        // 4. Visit the DeclarationTree node itself
        lastResult = this.visitor.visit(declarationTree, currentData);

        // 5. Visit statements that come after the declaration
        for (StatementTree statement : declarationTree.statements()) {
            lastResult = statement.accept(this, currentData);
            currentData = accumulate(currentData, lastResult);
        }

        return lastResult;
    }

    @Override
    public R visit(FunctionTree functionTree, T data) {
        R r = functionTree.returnType().accept(this, data);
        r = functionTree.name().accept(this, accumulate(data, r));
        r = functionTree.body().accept(this, accumulate(data, r));
        r = this.visitor.visit(functionTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(IdentExpressionTree identExpressionTree, T data) {
        R r = identExpressionTree.name().accept(this, data);
        r = this.visitor.visit(identExpressionTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(LiteralTree literalTree, T data) {
        return this.visitor.visit(literalTree, data);
    }

    @Override
    public R visit(BoolLiteralTree boolLiteralTree, T data) {
        return this.visitor.visit(boolLiteralTree, data);
    }

    @Override
    public R visit(LValueIdentTree lValueIdentTree, T data) {
        R r = lValueIdentTree.name().accept(this, data);
        r = this.visitor.visit(lValueIdentTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(NameTree nameTree, T data) {
        return this.visitor.visit(nameTree, data);
    }

    @Override
    public R visit(NegateTree negateTree, T data) {
        R r = negateTree.expression().accept(this, data);
        r = this.visitor.visit(negateTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(ProgramTree programTree, T data) {
        R r;
        T d = data;
        for (FunctionTree tree : programTree.topLevelTrees()) {
            r = tree.accept(this, d);
            d = accumulate(data, r);
        }
        r = this.visitor.visit(programTree, d);
        return r;
    }

    @Override
    public R visit(ReturnTree returnTree, T data) {
        R r = returnTree.expression().accept(this, data);
        r = this.visitor.visit(returnTree, accumulate(data, r));
        return r;
    }


    @Override
    public R visit(TypeTree typeTree, T data) {
        return this.visitor.visit(typeTree, data);
    }

    @Override
    public R visit(IfTree ifTree, T data) {
        R r;
        T d = data;
        r = ifTree.condition().accept(this, d);
        d = accumulate(data, r);
        r = ifTree.thenStatement().accept(this, d);
        d = accumulate(data, r);
        if (ifTree.elseStatement() != null) {
            r = ifTree.elseStatement().accept(this, d);
            d = accumulate(data, r);
        }
        r = this.visitor.visit(ifTree, d);
        return r;
    }

    @Override
    public R visit(WhileTree whileTree, T data) {
        R r;
        T d = data;
        r = whileTree.condition().accept(this, d);
        d = accumulate(data, r);
        r = whileTree.body().accept(this, d);
        d = accumulate(data, r);
        r = this.visitor.visit(whileTree, d);
        return r;
    }

    @Override
    public R visit(LogNotTree logNotTree, T data) {
        R r = logNotTree.expression().accept(this, data);
        r = this.visitor.visit(logNotTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(BitNotTree bitNotTree, T data) {
        R r = bitNotTree.expression().accept(this, data);
        r = this.visitor.visit(bitNotTree, accumulate(data, r));
        return r;
    }

    @Override
    public R visit(SequentialStatementTree sequentialStatementTree, T data) {
        T d = data;
        R r;

        // 1. Visit the Root
        r = sequentialStatementTree.statement().accept(this, d);
        d = accumulate(d, r);

        // 2. Visit statements that come after the first statement at root
        for (StatementTree statement : sequentialStatementTree.statements()) {
            r = statement.accept(this, d);
            d = accumulate(d, r);
        }

        return r;
    }

    @Override
    public R visit(CondExprTree condExprTree, T data) {
        return null;
    }

    protected T accumulate(T data, R value) {
        return data;
    }
}
