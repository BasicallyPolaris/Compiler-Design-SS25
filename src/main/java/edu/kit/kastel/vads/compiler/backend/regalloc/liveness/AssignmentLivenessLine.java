package edu.kit.kastel.vads.compiler.backend.regalloc.liveness;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.node.Node;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class AssignmentLivenessLine extends LivenessLine {

    public AssignmentLivenessLine(Node root, Operation operation, Register target, List<Register> parameters) {
        this.rootNode = root;
        this.operation = operation;
        this.target = target;
        this.parameters = parameters;
        this.liveInVariables = new HashSet<Register>();
        this.jumpTarget = null;
    }

    @Override
    public String toString() {
        String params = parameters.stream()
                .map(Register::toString)
                .collect(Collectors.joining(", "));

        return "Root: " + rootNode + " - " + target + " = " + operation + "(" + params + ")" + "Liveness Variables: " + liveInVariables;
    }
}
