package edu.kit.kastel.vads.compiler.backend.regalloc;

public enum X86_64Register {
    RAX, // Stores quotient during div
    RBX,
    RCX,
    RDX, // Stores remainder during div
    RSI,
    RDI,
    R8,
    R9,
    R10,
    R11,
    R12,
    R13,
    R14, // Spill register source
    R15, // Spill register destination
    RSP, // Stack Pointer
    RBP, // Base pointer
    SPILL, // Spilled register
}
