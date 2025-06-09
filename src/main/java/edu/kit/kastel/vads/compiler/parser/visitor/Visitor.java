package edu.kit.kastel.vads.compiler.parser.visitor;

import edu.kit.kastel.vads.compiler.parser.ast.*;

public interface Visitor<T, R> {

    R visit(AssignmentTree assignmentTree, T data);

    R visit(BinaryOperationTree binaryOperationTree, T data);

    R visit(BlockTree blockTree, T data);

    R visit(DeclarationTree declarationTree, T data);

    R visit(FunctionTree functionTree, T data);

    R visit(IdentExpressionTree identExpressionTree, T data);

    R visit(LiteralTree literalTree, T data);

    R visit(LValueIdentTree lValueIdentTree, T data);

    R visit(NameTree nameTree, T data);

    R visit(NegateTree negateTree, T data);

    R visit(ProgramTree programTree, T data);

    R visit(ReturnTree returnTree, T data);

    R visit(TypeTree typeTree, T data);

    //TODO: Implement new visits
    R visit(IfTree ifTree, T data);

    R visit(WhileTree whileTree, T data);
    
    R visit(SequentialStatementTree sequentialStatementTree, T data);

    R visit(LogNotTree logNotTree, T data);

    R visit(BitNotTree bitNotTree, T data);

    R visit(CondExprTree condExprTree, T data);

    R visit(BoolLiteralTree boolLiteralTree, T data);
}
