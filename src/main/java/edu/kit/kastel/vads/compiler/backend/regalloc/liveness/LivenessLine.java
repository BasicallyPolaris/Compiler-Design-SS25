package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.List;
import java.util.Set;

public sealed abstract class LivenessLine permits AssignmentLivenessLine, NoAssignmentLivenessLine {
    public Register target;
    protected int lineNumber;
    public Operation operation;
    public List<Register> parameters;
    public Set<Register> liveInVariables;

    @Override
    public abstract String toString();
}
