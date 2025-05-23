package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.Objects;

public record VirtualRegister(int id) implements Register {
    @Override
    public String toString() {
        return "%" + id();
    }

    @Override
    public boolean equals(Object o) {
        if (getClass() != o.getClass()) return false;
        VirtualRegister that = (VirtualRegister) o;

        return id() == that.id();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id());
    }

    public boolean isValid() {
        return this.id >= 0;
    }
}
