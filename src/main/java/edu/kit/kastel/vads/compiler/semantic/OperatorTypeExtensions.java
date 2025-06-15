package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.type.Type;

public class OperatorTypeExtensions {
    public static BasicType outType(Operator.OperatorType operatorType) {
        return switch (operatorType) {
            case LOG_NOT, LOG_AND, LOG_OR, LESS, LESS_EQUAL, MORE, MORE_EQUAL, EQUAL, NOT_EQUAL -> BasicType.BOOL;
            case BIT_NOT, MINUS, MUL, DIV, MOD, PLUS, BIT_SHIFT_LEFT, BIT_SHIFT_RIGHT, BIT_AND, BIT_XOR, BIT_OR ->
                    BasicType.INT;
            case ASSIGN, ASSIGN_PLUS, ASSIGN_MINUS, ASSIGN_MUL, ASSIGN_DIV, ASSIGN_MOD, ASSIGN_BIT_AND, ASSIGN_BIT_XOR,
                 ASSIGN_BIT_OR, ASSIGN_BIT_SHIFT_LEFT, ASSIGN_BIT_SHIFT_RIGHT ->
                    throw new RuntimeException("Determining output type for assignment " + operatorType + " is not supported");
            default -> throw new IllegalArgumentException("Unknown operator type: " + operatorType);
        };
    }

    public static BasicType inputType(Operator.OperatorType operatorType) {
        return switch (operatorType) {
            case EQUAL, NOT_EQUAL -> BasicType.VOID;
            case LOG_AND, LOG_NOT, LOG_OR -> BasicType.BOOL;
            case BIT_NOT, MINUS, MUL, DIV, MOD, PLUS, BIT_SHIFT_LEFT, BIT_SHIFT_RIGHT, BIT_AND, BIT_XOR, BIT_OR, LESS,
                 LESS_EQUAL, MORE, MORE_EQUAL -> BasicType.INT;
            case ASSIGN_PLUS -> inputType(Operator.OperatorType.PLUS);
            case ASSIGN_MINUS -> inputType(Operator.OperatorType.MINUS);
            case ASSIGN_MUL -> inputType(Operator.OperatorType.MUL);
            case ASSIGN_DIV -> inputType(Operator.OperatorType.DIV);
            case ASSIGN_MOD -> inputType(Operator.OperatorType.MOD);
            case ASSIGN_BIT_AND -> inputType(Operator.OperatorType.BIT_AND);
            case ASSIGN_BIT_XOR -> inputType(Operator.OperatorType.BIT_XOR);
            case ASSIGN_BIT_OR -> inputType(Operator.OperatorType.BIT_OR);
            case ASSIGN_BIT_SHIFT_LEFT -> inputType(Operator.OperatorType.BIT_SHIFT_LEFT);
            case ASSIGN_BIT_SHIFT_RIGHT -> inputType(Operator.OperatorType.BIT_SHIFT_RIGHT);
            case ASSIGN ->
                    throw new RuntimeException("Determining input type for assignment " + operatorType + " is not supported");
            default -> throw new IllegalArgumentException("Unknown operator type: " + operatorType);
        };
    }
}