package edu.kit.kastel.vads.compiler.backend.regalloc;

import java.util.List;
import java.util.Set;

public sealed abstract class LivenessLine permits AssignmentLivenessLine, NoAssignmentLivenessLine {
    public Register target;
    protected int lineNumber;
    protected Operation operation;
    protected List<Register> parameters;
    public Set<Register> liveInVariables;

    @Override
    public abstract String toString();
}
