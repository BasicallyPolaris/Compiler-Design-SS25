package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.List;
import java.util.Set;

public sealed abstract class LivenessLine permits AssignmentLivenessLine, JumpLivenessLine, NoAssignmentLivenessLine {
    public Register target;
    public int jumpTarget;
    protected int lineNumber;
    public Operation operation;
    public List<Register> parameters;
    public Set<Register> liveInVariables;

    @Override
    public abstract String toString();
}
