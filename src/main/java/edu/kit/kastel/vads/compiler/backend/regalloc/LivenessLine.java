package edu.kit.kastel.vads.compiler.backend.regalloc;

import java.util.List;

public sealed abstract class LivenessLine permits AssignmentLivenessLine, NoAssignmentLivenessLine {
    protected int lineNumber;
    protected Operation operation;
    protected List<Register> parameters;
    protected List<Register> liveInVariables;

    @Override
    public abstract String toString();
}
