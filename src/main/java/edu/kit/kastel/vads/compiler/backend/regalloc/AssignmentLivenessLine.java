package edu.kit.kastel.vads.compiler.backend.regalloc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AssignmentLivenessLine extends LivenessLine {


    public AssignmentLivenessLine(int lineNumber, Operation operation, Register target, List<Register> parameters) {
        this.lineNumber = lineNumber;
        this.operation = operation;
        this.target = target;
        this.parameters = parameters;
        this.liveInVariables = Set.of();
    }

    @Override
    public String toString() {
        String params = parameters.stream()
                .map(Register::toString)
                .collect(Collectors.joining(", "));
        if (params.isEmpty()) {
            return lineNumber + ": " + target + " = " + operation;
        }
        return lineNumber + ": " + target + " = " + operation + "(" + params + ")";
    }
}
