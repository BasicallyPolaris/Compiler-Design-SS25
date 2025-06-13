package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

public enum Operation {
    BINARY_OP,
    ASSIGN,
    RETURN,
    GOTO,
    CONDITIONAL_GOTO,
    PHI_ASSIGN,
}