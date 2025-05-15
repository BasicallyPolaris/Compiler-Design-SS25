package edu.kit.kastel.vads.compiler.backend.regalloc;

import java.util.Objects;

public class PhysicalRegister implements Register {
    X86_64Register register;
    int stackOffset;

    public PhysicalRegister(X86_64Register register) {
        this.register = register;
        this.stackOffset = -1;
    }

    public PhysicalRegister(int stackOffset) {
        this.register = X86_64Register.SPILL;
        this.stackOffset = stackOffset;
    }

    @Override
    public String toString() {
        return X86_64PhysicalRegisters.getAssemblyName(register, stackOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(register);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}