package edu.kit.kastel.vads.compiler.backend.regalloc;

public interface Register {
    @Override
    public String toString();

    @Override
    public boolean equals(Object obj);

    @Override
    public int hashCode();

    public boolean isValid();
}
