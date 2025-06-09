package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;

import java.util.List;
import java.util.Set;
import edu.kit.kastel.vads.compiler.ir.node.Node;

public sealed abstract class LivenessLine permits AssignmentLivenessLine, JumpLivenessLine, NoAssignmentLivenessLine {
    protected Node rootNode;
    public Register target;
    public Operation operation;
    public List<Register> parameters;
    public Set<Register> liveInVariables;
    public Node jumpTarget;

    @Override
    public abstract String toString();
}
