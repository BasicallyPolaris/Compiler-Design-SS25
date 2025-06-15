package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.type.Type;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

import java.util.Formattable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeAnalysis implements NoOpVisitor<List<ReturnTree>> {
    Map<Name, Type  > declarations = new HashMap<>();

    @Override
    public Unit visit(FunctionTree node, List<ReturnTree> data) {
//        for (ReturnTree returnTree : data) {
//            if (returnTree.expression().getType() != node.returnType().type()) {
//                throw new SemanticException("Type mismatch: " + returnTree.expression().getType() + " does not match expected type: " + node.returnType().type());
//            }
//        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(ReturnTree node, List<ReturnTree> data) {
        data.add(node);
        return NoOpVisitor.super.visit(node,data);
    }
    @Override
    public Unit visit(DeclarationTree node, List<ReturnTree> data) {
        if (node.initializer() != null) {
            if (node.initializer().getType() != node.type().type()) {
                throw new SemanticException("Type mismatch " + node.initializer().getType() + " does not match expected type: " + node.type().type());
            }
            declarations.put(node.name().name(), node.initializer().getType());
        }

        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(AssignmentTree node, List<ReturnTree> data) {
        LValueIdentTree identTree = (LValueIdentTree) node.lValue();
        Type varType = identTree.getType();
        if (varType != node.expression().getType()) {
            throw new SemanticException("Type mismatch: " + varType + " does not match expected type: " + node.expression().getType());
        }
        if (node.operator().type() != Operator.OperatorType.ASSIGN) {
            Type opType = OperatorTypeExtensions.inputType(node.operator().type());
            if (opType != varType) {
                throw new SemanticException("Type mismatch: " + opType + " does not match expected type: " + varType);
            }
        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(BinaryOperationTree node, List<ReturnTree> data) {
        Type lhsType = node.lhs().getType();
        Type rhsType = node.rhs().getType();
        Type inputType = OperatorTypeExtensions.inputType(node.operatorType());
        if (inputType != BasicType.VOID) {
            if (lhsType != inputType) {
                throw new SemanticException("Type mismatch: " + lhsType + " does not match expected type: " + inputType);
            }
            if (rhsType != inputType) {
                throw new SemanticException("Type mismatch: " + rhsType + " does not match expected type: " + inputType);
            }
        }
        if (lhsType != rhsType) {
            throw new SemanticException("Type mismatch: " + lhsType + " does not match expected type: " + rhsType);
        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(BitNotTree node, List<ReturnTree> data) {
        if (node.expression().getType() != node.getType()) {
            throw new SemanticException("Type mismatch: " + node.expression().getType() + " does not match expected type: " + node.getType());
        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(LogNotTree node, List<ReturnTree> data) {
        if (node.expression().getType() != node.getType()) {
            throw new SemanticException("Type mismatch: " + node.expression().getType() + " does not match expected type: " + node.getType());
        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(NegateTree node, List<ReturnTree> data) {
        if (node.expression().getType() != node.getType()) {
            throw new SemanticException("Type mismatch: " + node.expression().getType() + " does not match expected type: " + node.getType());
        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(IfTree node, List<ReturnTree> data) {
        if (node.condition().getType() != BasicType.BOOL) {
            throw new SemanticException("Type mismatch: " + node.condition().getType() + " does not match expected type: " + BasicType.BOOL);
        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(WhileTree node, List<ReturnTree> data) {
        if (node.condition().getType() != BasicType.BOOL) {
            throw new SemanticException("Type mismatch: " + node.condition().getType() + " does not match expected type: " + BasicType.BOOL);
        }
        return NoOpVisitor.super.visit(node,data);
    }

    @Override
    public Unit visit(CondExprTree node, List<ReturnTree> data) {
        if (node.cond().getType() != BasicType.BOOL) {
            throw new SemanticException("Type mismatch: " + node.cond().getType() + " does not match expected type: " + BasicType.BOOL);
        }
        if (node.exp1().getType() != node.exp2().getType()) {
            throw new SemanticException("Type mismatch: " + node.exp1().getType() + " does not match expected type: " +node.exp2().getType());
        }
        return NoOpVisitor.super.visit(node,data);
    }
}
