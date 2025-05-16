package edu.kit.kastel.vads.compiler.backend.regalloc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents physical x86_64 registers with their conventional uses and assembly representations.
 * Based on the System V AMD64 ABI calling convention.
 */
public class X86_64PhysicalRegisters {
    // Static mapping from X86_64Register enum to PhysicalRegister objects
    private static final Map<X86_64Register, PhysicalRegister> REGISTERS = new HashMap<>();
    private static final List<PhysicalRegister> ACCESSIBLE_REGISTERS = new ArrayList<>();
    public static final int REG_SIZE_B = 4;

    // Initialize the register map with enum values and physical register objects
    static {
        for (X86_64Register reg : X86_64Register.values()) {
            switch (reg) {
                case RBP, RSP, RAX, RDX, R14, R15 -> REGISTERS.put(reg, new PhysicalRegister(reg));
                default -> {
                    if (!(reg == X86_64Register.SPILL)) {
                        PhysicalRegister physicalRegister = new PhysicalRegister(reg);
                        REGISTERS.put(reg, physicalRegister);
                        ACCESSIBLE_REGISTERS.add(physicalRegister);
                    }
                }
            }
        }
    }

    /**
     * Gets the physical register corresponding to the given X86_64Register enum value.
     *
     * @param register The X86_64Register enum value
     * @return The corresponding PhysicalRegister object
     */
    public static PhysicalRegister get(X86_64Register register) {
        return REGISTERS.get(register);
    }

    public static PhysicalRegister get(int index) {
        if (index < 0) throw new IndexOutOfBoundsException();

        if (index < ACCESSIBLE_REGISTERS.size()) {
            return ACCESSIBLE_REGISTERS.get(index);
        }

        return new PhysicalRegister((index - ACCESSIBLE_REGISTERS.size()) * REG_SIZE_B);
    }

    public static String getAssemblyName(X86_64Register register) {
        return getAssemblyName(register, -1);
    }

    /**
     * Gets the string representation of a register in x86_64 assembly.
     *
     * @param register The X86_64Register enum value
     * @return The assembly representation of the register
     */
    public static String getAssemblyName(X86_64Register register, int stackOffset) {
        if (register == X86_64Register.SPILL && stackOffset >= 0) {
            return (stackOffset * REG_SIZE_B) + "(" + getAssemblyName(X86_64Register.RSP) + ")";
        }

        return switch (register) {
            case RAX -> "%eax";
            case RBX -> "%ebx";
            case RCX -> "%ecx";
            case RDX -> "%edx";
            case RSI -> "%esi";
            case RDI -> "%edi";
            case R8 -> "%r8d";
            case R9 -> "%r9d";
            case R10 -> "%r10d";
            case R11 -> "%r11d";
            case R12 -> "%r12d";
            case R13 -> "%r13d";
            case R14 -> "%r14d";
            case R15 -> "%r15d";
            case RSP -> "%rsp";
            case RBP -> "%rbp";
            default -> throw new IllegalArgumentException("Unknown register: " + register);
        };
    }
}
